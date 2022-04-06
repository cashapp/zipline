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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/**
 * Concurrently downloads latest code to a local directory to include in app release package.
 */
class ZiplineDownloader(
  dispatcher: CoroutineDispatcher,
  private val httpClient: ZiplineHttpClient,
  private val downloadDir: Path,
  private val downloadFileSystem: FileSystem,
) {
  private var concurrentDownloadsSemaphore = Semaphore(3)
  var concurrentDownloads = 3
    set(value) {
      require(value > 0)
      field = value
      concurrentDownloadsSemaphore = Semaphore(value)
    }

  private val moduleLoader = ZiplineModuleLoader.createDownloadOnly(
    dispatcher = dispatcher,
    httpClient = httpClient,
    concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
    downloadDir = downloadDir,
    downloadFileSystem = downloadFileSystem,
  )

  suspend fun download(
    manifestUrl: String,
  ) {
    val manifest = downloadZiplineManifest(manifestUrl)

    downloadFileSystem.createDirectories(downloadDir)
    downloadFileSystem.write(downloadDir / PREBUILT_MANIFEST_FILE_NAME) {
      writeUtf8(Json.encodeToString(manifest))
    }

    moduleLoader.load(manifest)
  }

  private suspend fun downloadZiplineManifest(manifestUrl: String): ZiplineManifest {
    val manifestByteString = concurrentDownloadsSemaphore.withPermit {
      httpClient.download(manifestUrl)
    }
    val manifest = Json.decodeFromString(ZiplineManifest.serializer(), manifestByteString.utf8())
    return httpClient.resolveUrls(manifest, manifestUrl)
  }

  companion object {
    const val PREBUILT_MANIFEST_FILE_NAME = "manifest.zipline.json"
  }
}
