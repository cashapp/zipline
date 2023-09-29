/*
 * Copyright (C) 2022 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.zipline.loader.testing

import app.cash.zipline.QuickJs
import app.cash.zipline.ZiplineManifest
import app.cash.zipline.loader.CURRENT_ZIPLINE_VERSION
import app.cash.zipline.loader.ZiplineFile
import app.cash.zipline.loader.internal.fetcher.LoadedManifest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

class LoaderTestFixtures {
  val alphaJs = createJs("alpha")
  val alphaByteString = createZiplineFile(alphaJs, "alpha.js")
  val alphaSha256 = alphaByteString.sha256()
  val alphaSha256Hex = alphaSha256.hex()

  val bravoJs = createJs("bravo")
  val bravoByteString = createZiplineFile(bravoJs, "bravo.js")
  val bravoSha256 = bravoByteString.sha256()
  val bravoSha256Hex = bravoSha256.hex()

  val manifest = ZiplineManifest.create(
    modules = mapOf(
      "bravo" to ZiplineManifest.Module(
        url = BRAVO_RELATIVE_URL,
        sha256 = bravoByteString.sha256(),
        dependsOnIds = listOf("alpha"),
      ),
      "alpha" to ZiplineManifest.Module(
        url = ALPHA_RELATIVE_URL,
        sha256 = alphaByteString.sha256(),
        dependsOnIds = listOf(),
      ),
    ),
    mainFunction = "zipline.ziplineMain",
    baseUrl = MANIFEST_URL,
  )

  val manifestJsonString = manifest.encodeJson()
  val manifestByteString = manifestJsonString.encodeUtf8()

  val manifestNoBaseUrl = manifest.copy(
    baseUrl = null,
  )
  val manifestNoBaseUrlJsonString = manifest.encodeJson()
  val manifestNoBaseUrlByteString = manifestNoBaseUrlJsonString.encodeUtf8()

  val embeddedManifest = manifest.copy(
    freshAtEpochMs = 123L,
  )
  val embeddedManifestJsonString = Json.encodeToString(embeddedManifest)
  val embeddedManifestByteString = embeddedManifestJsonString.encodeUtf8()
  val embeddedLoadedManifest = LoadedManifest(
    manifestBytes = embeddedManifestByteString,
    manifest = embeddedManifest,
    freshAtEpochMs = embeddedManifest.freshAtEpochMs!!,
  )

  fun createZiplineFile(javaScript: String, fileName: String): ByteString {
    val quickJs = QuickJs.create()
    val compiledJavaScript = try {
      quickJs.compile(javaScript, fileName)
    } finally {
      quickJs.close()
    }
    val ziplineFile = ZiplineFile(
      CURRENT_ZIPLINE_VERSION,
      compiledJavaScript.toByteString(),
    )
    val buffer = Buffer()
    ziplineFile.writeTo(buffer)
    return buffer.readByteString()
  }

  companion object {
    const val ALPHA_RELATIVE_URL = "alpha.zipline"
    const val BRAVO_RELATIVE_URL = "bravo.zipline"
    const val ALPHA_URL = "https://example.com/files/alpha.zipline"
    const val BRAVO_URL = "https://example.com/files/bravo.zipline"
    const val MANIFEST_URL = "https://example.com/files/default.manifest.zipline.json"

    fun createRelativeManifest(
      seed: String,
      seedFileSha256: ByteString,
      includeUnknownFieldInJson: Boolean = false,
    ): LoadedManifest {
      val manifest = ZiplineManifest.create(
        modules = mapOf(
          seed to ZiplineManifest.Module(
            url = "$seed.zipline",
            sha256 = seedFileSha256,
          ),
        ),
        mainFunction = "zipline.ziplineMain",
      )

      // Synthesize an unknown field to test forward-compatibility.
      val manifestJson = when {
        includeUnknownFieldInJson -> {
          val jsonElement = Json.encodeToJsonElement(manifest)
          val map = jsonElement.jsonObject.toMutableMap()
          map["unknownKey"] = JsonPrimitive("unknownValue")
          Json.encodeToString(JsonObject(map))
        }

        else -> {
          Json.encodeToString(manifest)
        }
      }

      return LoadedManifest(
        manifestJson.encodeUtf8(),
        manifest,
        1L,
      )
    }

    fun createRelativeEmbeddedManifest(
      seed: String,
      seedFileSha256: ByteString,
      seedFreshAtEpochMs: Long,
      includeUnknownFieldInJson: Boolean = false,
    ): LoadedManifest {
      val loadedManifest = createRelativeManifest(seed, seedFileSha256, includeUnknownFieldInJson)
      return loadedManifest.copy(freshAtEpochMs = seedFreshAtEpochMs).encodeFreshAtMs()
    }

    fun assertDownloadedToEmbeddedManifest(
      expectedManifest: ZiplineManifest,
      actualManifestBytes: ByteString,
    ) {
      val expectedManifestWithoutBuiltAtEpochMs = expectedManifest.copy(
        freshAtEpochMs = null,
      )
      val actualManifest = Json.decodeFromString<ZiplineManifest>(actualManifestBytes.utf8())
      // freshAtEpochMs has been filled out on download.
      assertNotNull(actualManifest.freshAtEpochMs)
      val actualManifestWithoutFreshAtEpochMs = actualManifest.copy(
        freshAtEpochMs = null,
      )
      assertEquals(expectedManifestWithoutBuiltAtEpochMs, actualManifestWithoutFreshAtEpochMs)
    }

    fun createJs(seed: String) = jsBoilerplate(
      seed = seed,
      loadBody = """
              globalThis.log = globalThis.log || "";
              globalThis.log += "$seed loaded\n";
            """.trimIndent(),
      mainBody = """
              globalThis.mainLog = globalThis.mainLog || "";
              globalThis.mainLog += "$seed loaded\n";
            """.trimIndent(),
    )

    fun createFailureJs(seed: String) = jsBoilerplate(
      seed = seed,
      loadBody = "throw Error('$seed');",
      mainBody = "throw Error('$seed');",
    )

    private fun jsBoilerplate(seed: String, loadBody: String, mainBody: String) = """
      (function (root, factory) {
        if (typeof define === 'function' && define.amd)
          define(['exports'], factory);
        else if (typeof exports === 'object')
          factory(module.exports);
        else
          root.zipline_main = factory(typeof zipline_main === 'undefined' ? {} : zipline_main);
      }(this, function (_) {
         function ziplineMain() {
           $mainBody
         }
         //region block: exports
         function ${'$'}jsExportAll${'$'}(_) {
           // export global value for module name for easier test manipulation
           globalThis.seed = '$seed';

           // run test body code
           $loadBody

           // export scoped main function
           var ${'$'}zipline = _.zipline || (_.zipline = {});
           ${'$'}zipline.ziplineMain = ziplineMain;
         }
         ${'$'}jsExportAll${'$'}(_);
         //endregion
         return _;
      }));
      """.trimIndent()
  }
}
