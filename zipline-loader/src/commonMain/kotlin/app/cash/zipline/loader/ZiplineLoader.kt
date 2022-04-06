/*
 * Copyright (C) 2021 Square, Inc.
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

import app.cash.zipline.Zipline
import app.cash.zipline.loader.ZiplineDownloader.Companion.PREBUILT_MANIFEST_FILE_NAME
import com.squareup.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Gets code from an HTTP server or a local cache,
 * and loads it into a zipline instance. This attempts
 * to load code as quickly as possible, and will
 * concurrently download and load code.
 */
class ZiplineLoader(
  private val dispatcher: CoroutineDispatcher,
  private val httpClient: ZiplineHttpClient,
  private val embeddedDir: Path,
  private val embeddedFileSystem: FileSystem,
  cacheDbDriver: SqlDriver, // SqlDriver is already initialized to the platform and SQLite DB on disk
  cacheDir: Path,
  val cacheFileSystem: FileSystem,
  cacheMaxSizeInBytes: Int = 100 * 1024 * 1024,
  nowMs: () -> Long, // 100 MiB
) {
  private var concurrentDownloadsSemaphore = Semaphore(3)
  var concurrentDownloads = 3
    set(value) {
      require(value > 0)
      field = value
      concurrentDownloadsSemaphore = Semaphore(value)
    }

  // TODO add schema version checker and automigration for non-Android platforms
  private val cache = createZiplineCache(
    driver = cacheDbDriver,
    fileSystem = cacheFileSystem,
    directory = cacheDir,
    maxSizeInBytes = cacheMaxSizeInBytes.toLong(),
    nowMs = nowMs
  )

  suspend fun load(zipline: Zipline, manifestUrl: String) {
    load(zipline, downloadZiplineManifest(manifestUrl))
  }

  suspend fun load(
    zipline: Zipline,
    manifest: ZiplineManifest,
  ) {
    ZiplineModuleLoader.createProduction(
      dispatcher = dispatcher,
      httpClient = httpClient,
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      embeddedDir = embeddedDir,
      embeddedFileSystem = embeddedFileSystem,
      cache = cache,
      zipline = zipline,
    ).load(manifest)
  }

  private suspend fun downloadZiplineManifest(manifestUrl: String): ZiplineManifest {
    val manifestByteString = try {
      concurrentDownloadsSemaphore.withPermit {
        httpClient.download(manifestUrl)
      }
    } catch (e: IOException) {
      // If manifest fails to load over network, fallback to prebuilt in resources
      val prebuiltManifestPath = embeddedDir / PREBUILT_MANIFEST_FILE_NAME
      embeddedFileSystem.read(prebuiltManifestPath) {
        readByteString()
      }
    }

    val manifest = Json.decodeFromString(ZiplineManifest.serializer(), manifestByteString.utf8())
    return httpClient.resolveUrls(manifest, manifestUrl)
  }
}
