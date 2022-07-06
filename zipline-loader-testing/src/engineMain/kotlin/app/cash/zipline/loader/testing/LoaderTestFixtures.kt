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

import app.cash.zipline.EventListener
import app.cash.zipline.QuickJs
import app.cash.zipline.loader.CURRENT_ZIPLINE_VERSION
import app.cash.zipline.loader.ZiplineCache
import app.cash.zipline.loader.ZiplineFile
import app.cash.zipline.loader.ZiplineHttpClient
import app.cash.zipline.loader.ZiplineLoader
import app.cash.zipline.loader.ZiplineManifest
import app.cash.zipline.loader.ZiplineModule
import com.squareup.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path

@OptIn(ExperimentalSerializationApi::class)
class LoaderTestFixtures(
  val quickJs: QuickJs,
) {
  val alphaJs = createJs("alpha")
  val alphaByteString = createZiplineFile(alphaJs, "alpha.js")
  val alphaSha256 = alphaByteString.sha256()
  val alphaSha256Hex = alphaSha256.hex()

  val bravoJs = createJs("bravo")
  val bravoByteString = createZiplineFile(bravoJs, "bravo.js")
  val bravoSha256 = bravoByteString.sha256()
  val bravoSha256Hex = bravoSha256.hex()

  val manifestWithRelativeUrls = ZiplineManifest.create(
    mainModuleId = "",
    mainFunction = "",
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

  val manifestWithRelativeUrlsJsonString = Json.encodeToString(manifestWithRelativeUrls)
  val manifestWithRelativeUrlsByteString = manifestWithRelativeUrlsJsonString.encodeUtf8()

  val manifest = manifestWithRelativeUrls.copy(
    mainModuleId = "./alpha.js",
    mainFunction = "zipline.main()",
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

  val manifestJsonString = Json.encodeToString(manifest)
  val manifestByteString = manifestJsonString.encodeUtf8()

  fun createZiplineFile(javaScript: String, fileName: String): ByteString {
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
    const val manifestUrl = "https://example.com/files/default.manifest.zipline.json"

    fun createRelativeManifest(
      seed: String,
      seedFileSha256: ByteString
    ) = ZiplineManifest.create(
      mainModuleId = "",
      mainFunction = "",
      modules = mapOf(
        seed to ZiplineModule(
          url = "$seed.zipline",
          sha256 = seedFileSha256,
        )
      )
    )

    fun createJs(seed: String) = """
      |globalThis.log = globalThis.log || "";
      |globalThis.log += "$seed loaded\n"
      |""".trimMargin()

    fun createFailureJs(seed: String) = """
      |throw Error('$seed');
      |""".trimMargin()

    fun createProductionZiplineLoader(
      dispatcher: CoroutineDispatcher,
      httpClient: ZiplineHttpClient,
      embeddedDir: Path,
      embeddedFileSystem: FileSystem,
      cache: ZiplineCache,
      serializersModule: SerializersModule = EmptySerializersModule,
      eventListener: EventListener = EventListener.NONE,
    ) = ZiplineLoader(
      dispatcher = dispatcher,
      eventListener = eventListener,
      serializersModule = serializersModule,
      httpClient = httpClient,
    ).withEmbedded(
      embeddedDir = embeddedDir,
      embeddedFileSystem = embeddedFileSystem,
    ).withCache(
      cache = cache,
    )

    fun createProductionZiplineLoader(
      dispatcher: CoroutineDispatcher,
      httpClient: ZiplineHttpClient,
      embeddedDir: Path,
      embeddedFileSystem: FileSystem,
      cacheDbDriver: SqlDriver,
      cacheDir: Path,
      cacheFileSystem: FileSystem,
      cacheMaxSizeInBytes: Long = 100L * 1024L * 1024L,  // 100 MiB
      serializersModule: SerializersModule = EmptySerializersModule,
      eventListener: EventListener = EventListener.NONE,
      nowMs: () -> Long,
    ) = ZiplineLoader(
      dispatcher = dispatcher,
      eventListener = eventListener,
      serializersModule = serializersModule,
      httpClient = httpClient,
    ).withEmbedded(
      embeddedDir = embeddedDir,
      embeddedFileSystem = embeddedFileSystem,
    ).withCache(
      driver = cacheDbDriver,
      fileSystem = cacheFileSystem,
      directory = cacheDir,
      maxSizeInBytes = cacheMaxSizeInBytes,
      nowMs = nowMs,
    )

    fun createDownloadZiplineLoader(
      dispatcher: CoroutineDispatcher,
      httpClient: ZiplineHttpClient,
      serializersModule: SerializersModule = EmptySerializersModule,
      eventListener: EventListener = EventListener.NONE,
    ) = ZiplineLoader(
      dispatcher = dispatcher,
      eventListener = eventListener,
      serializersModule = serializersModule,
      httpClient = httpClient,
    )
  }
}
