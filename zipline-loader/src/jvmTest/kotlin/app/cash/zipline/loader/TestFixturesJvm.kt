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

package app.cash.zipline.loader

import app.cash.zipline.QuickJs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

class TestFixturesJvm(quickJs: QuickJs) {
  val alphaJs = """
      |globalThis.log = globalThis.log || "";
      |globalThis.log += "alpha loaded\n"
      |""".trimMargin()
  val alphaByteString = ziplineFile(quickJs, alphaJs, "alpha.js")
  val alphaSha256 = alphaByteString.sha256()
  val alphaSha256Hex = alphaSha256.hex()

  val bravoJs = """
      |globalThis.log = globalThis.log || "";
      |globalThis.log += "bravo loaded\n"
      |""".trimMargin()
  val bravoByteString = ziplineFile(quickJs, bravoJs, "bravo.js")
  val bravoSha256 = bravoByteString.sha256()
  val bravoSha256Hex = bravoSha256.hex()

  val manifestWithRelativeUrls = ZiplineManifest.create(
    modules = mapOf(
      "bravo" to ZiplineModule(
        url = bravoRelativeUrl,
        sha256 = bravoByteString.sha256(),
        dependsOnIds = listOf("alpha"),
      ),
      "alpha" to ZiplineModule(
        url = alphaRelativeUrl,
        sha256 = alphaByteString.sha256(),
        dependsOnIds = listOf(),
      ),
    )
  )

  val manifestWithRelativeUrlsByteString = Json.encodeToString(manifestWithRelativeUrls).encodeUtf8()

  val manifest = manifestWithRelativeUrls.copy(
    modules = manifestWithRelativeUrls.modules.mapValues { (_, module) ->
      module.copy(
        url = when (module.url) {
          bravoRelativeUrl -> bravoUrl
          alphaRelativeUrl -> alphaUrl
          else -> error("unexpected URL: ${module.url}")
        }
      )
    }
  )

  val manifestByteString = Json.encodeToString(manifest).encodeUtf8()

  private fun ziplineFile(quickJs: QuickJs, javaScript: String, fileName: String): ByteString {
    val ziplineFile = ZiplineFile(
      CURRENT_ZIPLINE_VERSION,
      quickJs.compile(javaScript, fileName).toByteString()
    )

    val buffer = Buffer()
    ziplineFile.writeTo(buffer)
    return buffer.readByteString()
  }

  companion object {
    const val alphaRelativeUrl = "alpha.zipline"
    const val bravoRelativeUrl = "bravo.zipline"
    const val alphaUrl = "https://example.com/files/alpha.zipline"
    const val bravoUrl = "https://example.com/files/bravo.zipline"
    const val manifestUrl = "https://example.com/files/manifest.zipline.json"
  }
}
