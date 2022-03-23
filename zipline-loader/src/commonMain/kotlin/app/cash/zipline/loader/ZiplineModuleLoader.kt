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
import app.cash.zipline.loader.fetcher.FsCachingFetcher
import app.cash.zipline.loader.fetcher.FsEmbeddedFetcher
import app.cash.zipline.loader.fetcher.HttpFetcher
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
import okio.ByteString
import okio.FileSystem
import okio.Path

/**
 * Gets code from an HTTP server or a local cache,
 * and loads it into a zipline instance. This attempts
 * to load code as quickly as possible, and will
 * concurrently download and load code.
 */
internal class ZiplineModuleLoader(
  private val dispatcher: CoroutineDispatcher,
  private val fetchers: List<Fetcher>,
  private val receiver: Receiver,
) {
  suspend fun load(
    manifest: ZiplineManifest
  ) {
    coroutineScope {
      val loads = manifest.modules.map {
        ModuleLoad(it.key, it.value)
      }
      for (load in loads) {
        val loadJob = launch { load.load() }

        val downstreams = loads.filter { load.id in it.module.dependsOnIds }
        for (downstream in downstreams) {
          downstream.upstreams += loadJob
        }
      }
    }
  }

  private inner class ModuleLoad(
    val id: String,
    val module: ZiplineModule,
  ) {
    val upstreams = mutableListOf<Job>()

    /**
     * Follow given strategy to get ZiplineFile and then process
     */
    suspend fun load() {
      var byteString: ByteString? = null
      for (fetcher in fetchers) {
        byteString = fetcher.fetch(id, module.sha256, module.url)
        if (byteString != null) break
      }
      if (byteString == null) throw IllegalStateException(
        "Unable to get ByteString for [module=$module]"
      )

      upstreams.joinAll()
      withContext(dispatcher) {
        receiver.receive(byteString, id, module.sha256)
      }
    }
  }

  companion object {
    fun createDownloadOnly(
      dispatcher: CoroutineDispatcher,
      httpClient: ZiplineHttpClient,
      concurrentDownloadsSemaphore: Semaphore,
      downloadDir: Path,
      downloadFileSystem: FileSystem,
    ) = ZiplineModuleLoader(
      dispatcher = dispatcher,
      fetchers = listOf(
        HttpFetcher(
          httpClient = httpClient,
          concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
        ),
      ),
      receiver = FsSaveReceiver(
        downloadFileSystem = downloadFileSystem,
        downloadDir = downloadDir
      )
    )

    fun createProduction(
      dispatcher: CoroutineDispatcher,
      httpClient: ZiplineHttpClient,
      concurrentDownloadsSemaphore: Semaphore,
      embeddedDir: Path,
      embeddedFileSystem: FileSystem,
      cache: ZiplineCache,
      zipline: Zipline,
    ) = ZiplineModuleLoader(
      dispatcher = dispatcher,
      fetchers = listOf(
        FsEmbeddedFetcher(
          embeddedDir = embeddedDir,
          embeddedFileSystem = embeddedFileSystem
        ),
        FsCachingFetcher(
          cache = cache,
          delegate = HttpFetcher(
            httpClient = httpClient,
            concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
          ),
        ),
      ),
      receiver = ZiplineLoadReceiver(
        zipline = zipline
      ),
    )
  }
}
