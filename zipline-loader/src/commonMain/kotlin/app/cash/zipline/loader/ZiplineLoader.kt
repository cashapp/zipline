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
import app.cash.zipline.loader.fetcher.Fetcher
import app.cash.zipline.loader.fetcher.HttpFetcher
import app.cash.zipline.loader.fetcher.fetch
import app.cash.zipline.loader.receiver.FsSaveReceiver
import app.cash.zipline.loader.receiver.Receiver
import app.cash.zipline.loader.receiver.ZiplineLoadReceiver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path

/**
 * Gets code from an HTTP server, or optional local cache
 * or embedded filesystem, and handles with a receiver
 * (by default, loads it into a zipline instance).
 *
 * Loader attempts to load code as quickly as possible with
 * concurrent network downloads and code loading.
 */
class ZiplineLoader(
  private val dispatcher: CoroutineDispatcher,
  private val httpClient: ZiplineHttpClient,
  private val fetchers: List<Fetcher> = listOf(HttpFetcher(httpClient)),
) {
  private var concurrentDownloadsSemaphore = Semaphore(3)
  var concurrentDownloads = 3
    set(value) {
      require(value > 0)
      field = value
      concurrentDownloadsSemaphore = Semaphore(value)
    }

  suspend fun load(zipline: Zipline, manifestUrl: String) =
    load(zipline, downloadZiplineManifest(manifestUrl))

  suspend fun load(
    zipline: Zipline,
    manifest: ZiplineManifest,
  ) = receive(ZiplineLoadReceiver(zipline), manifest)

  suspend fun download(
    downloadDir: Path,
    downloadFileSystem: FileSystem,
    manifestUrl: String,
  ) {
    val manifest = downloadZiplineManifest(manifestUrl)
    download(downloadDir, downloadFileSystem, manifest)
  }

  suspend fun download(
    downloadDir: Path,
    downloadFileSystem: FileSystem,
    manifest: ZiplineManifest,
  ) {
    writeManifestToDisk(downloadFileSystem, downloadDir, manifest)
    receive(
      receiver = FsSaveReceiver(downloadFileSystem, downloadDir),
      manifest = manifest
    )
  }

  private suspend fun receive(
    receiver: Receiver,
    manifest: ZiplineManifest,
  ) {
    coroutineScope {
      val loads = manifest.modules.map {
        ModuleJob(it.key, it.value, receiver)
      }
      for (load in loads) {
        val loadJob = launch { load.run() }

        val downstreams = loads.filter { load.id in it.module.dependsOnIds }
        for (downstream in downstreams) {
          downstream.upstreams += loadJob
        }
      }
    }
  }

  private inner class ModuleJob(
    val id: String,
    val module: ZiplineModule,
    val receiver: Receiver,
  ) {
    val upstreams = mutableListOf<Job>()

    /**
     * Fetch and receive ZiplineFile module
     */
    suspend fun run() {
      val byteString = fetchers.fetch(concurrentDownloadsSemaphore, id, module.sha256, module.url)

      upstreams.joinAll()
      withContext(dispatcher) {
        receiver.receive(byteString, id, module.sha256)
      }
    }
  }

  private suspend fun downloadZiplineManifest(
    manifestUrl: String,
  ): ZiplineManifest {
    val manifestByteString = fetchers.fetch(
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      id = PREBUILT_MANIFEST_FILE_NAME,
      // Store manifest as the url SHA.
      sha256 = manifestUrl.encodeUtf8().sha256(),
      url = manifestUrl,
      // Override fileName to support a static manifest location in embedded fs.
      fileNameOverride = PREBUILT_MANIFEST_FILE_NAME,
    )

    val manifest = Json.decodeFromString(ZiplineManifest.serializer(), manifestByteString.utf8())

    return httpClient.resolveUrls(manifest, manifestUrl)
  }

  private fun writeManifestToDisk(
    fileSystem: FileSystem,
    dir: Path,
    manifest: ZiplineManifest,
  ) {
    fileSystem.createDirectories(dir)
    fileSystem.write(dir / PREBUILT_MANIFEST_FILE_NAME) {
      write(Json.encodeToString(manifest).encodeUtf8())
    }
  }

  companion object {
    const val PREBUILT_MANIFEST_FILE_NAME = "manifest.zipline.json"
  }
}
