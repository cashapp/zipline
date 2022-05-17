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

import app.cash.zipline.EventListener
import app.cash.zipline.Zipline
import app.cash.zipline.loader.fetcher.Fetcher
import app.cash.zipline.loader.fetcher.HttpFetcher
import app.cash.zipline.loader.fetcher.fetch
import app.cash.zipline.loader.fetcher.fetchManifest
import app.cash.zipline.loader.fetcher.pin
import app.cash.zipline.loader.fetcher.unpin
import app.cash.zipline.loader.receiver.FsSaveReceiver
import app.cash.zipline.loader.receiver.Receiver
import app.cash.zipline.loader.receiver.ZiplineLoadReceiver
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
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
  private val serializersModule: SerializersModule,
  private val eventListener: EventListener,
  private val httpClient: ZiplineHttpClient,
  private val fetchers: List<Fetcher> = listOf(HttpFetcher(httpClient, eventListener)),
) {
  private var concurrentDownloadsSemaphore = Semaphore(3)
  var concurrentDownloads = 3
    set(value) {
      require(value > 0)
      field = value
      concurrentDownloadsSemaphore = Semaphore(value)
    }

  suspend fun loadOrFallBack(
    applicationName: String,
    serializersModule: SerializersModule,
    manifestUrl: String,
  ): Zipline {
    return try {
      val zipline = Zipline.create(
        dispatcher, serializersModule
      )
      load(zipline, manifestUrl, applicationName)
      zipline
    } catch (e: Exception) {
      val zipline = Zipline.create(
        dispatcher, serializersModule
      )
      // Load from either pinned in cache or embedded by forcing network failure
      load(zipline, fetchZiplineManifest(applicationName, ""), applicationName)
      zipline
    }
  }

  suspend fun loadContinuously(
    applicationName: String,
    manifestUrlFlow: Flow<String>,
    pollingInterval: Duration,
  ): Flow<Zipline> = manifestFlow(applicationName, manifestUrlFlow, pollingInterval)
    .mapNotNull { manifest ->
      val zipline = Zipline.create(dispatcher, serializersModule, eventListener)
      load(zipline, manifest, applicationName)
      zipline
    }

  /** Load application into Zipline without fallback on failure functionality */
  suspend fun load(
    zipline: Zipline,
    manifestUrl: String,
    applicationName: String = DEFAULT_application_name,
  ) = load(zipline, fetchZiplineManifest(applicationName, manifestUrl), applicationName)

  suspend fun load(
    zipline: Zipline,
    manifest: ZiplineManifest,
    applicationName: String = DEFAULT_application_name,
  ) = receive(ZiplineLoadReceiver(zipline), manifest, applicationName)

  suspend fun download(
    downloadDir: Path,
    downloadFileSystem: FileSystem,
    manifestUrl: String,
    applicationName: String = DEFAULT_application_name,
  ) {
    download(
      downloadDir = downloadDir,
      downloadFileSystem = downloadFileSystem,
      manifest = fetchZiplineManifest(applicationName, manifestUrl),
      applicationName = applicationName
    )
  }

  suspend fun download(
    downloadDir: Path,
    downloadFileSystem: FileSystem,
    manifest: ZiplineManifest,
    applicationName: String = DEFAULT_application_name,
  ) {
    downloadFileSystem.createDirectories(downloadDir)
    downloadFileSystem.write(downloadDir / getApplicationManifestFileName(applicationName)!!) {
      write(Json.encodeToString(manifest).encodeUtf8())
    }
    receive(
      receiver = FsSaveReceiver(downloadFileSystem, downloadDir),
      manifest = manifest,
      applicationName = applicationName
    )
  }

  /**
   * Continuously downloads [urlFlow] and emits when it changes.
   *
   * TODO(jwilson): use a web socket instead of polling every 500ms.
   */
  private suspend fun manifestFlow(
    applicationName: String,
    urlFlow: Flow<String>,
    pollingInterval: Duration?,
  ): Flow<ZiplineManifest> {
    val rebounced = when {
      pollingInterval != null -> urlFlow.rebounce(pollingInterval)
      else -> urlFlow
    }

    return rebounced.mapNotNull { url ->
      fetchZiplineManifest(applicationName, url)
    }.distinctUntilChanged()
  }

  private suspend fun receive(
    receiver: Receiver,
    manifest: ZiplineManifest,
    applicationName: String,
  ) {
    coroutineScope {
      val loads = manifest.modules.map {
        ModuleJob(applicationName, it.key, it.value, receiver)
      }
      try {
        for (load in loads) {
          val loadJob = launch { load.run() }
          val downstreams = loads.filter { load.id in it.module.dependsOnIds }
          for (downstream in downstreams) {
            downstream.upstreams += loadJob
          }
        }
      } catch (e: Exception) {
        // On exception, unpin this manifest; and rethrow so that the load attempt fails
        fetchers.unpin(applicationName, manifest)
        throw e
      }

      // Pin stable application manifest after a successful load, and unpin all others
      fetchers.pin(
        applicationName = applicationName,
        manifest = manifest
      )
    }
  }

  private inner class ModuleJob(
    val applicationName: String,
    val id: String,
    val module: ZiplineModule,
    val receiver: Receiver,
  ) {
    val upstreams = mutableListOf<Job>()

    /**
     * Fetch and receive ZiplineFile module
     */
    suspend fun run() {
      val byteString = fetchers
        .fetch(
          concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
          applicationName = applicationName,
          id = id,
          sha256 = module.sha256,
          url = module.url,
        )

      upstreams.joinAll()
      withContext(dispatcher) {
        receiver.receive(byteString, id, module.sha256)
      }
    }
  }

  private suspend fun fetchZiplineManifest(
    applicationName: String,
    manifestUrl: String,
  ): ZiplineManifest {
    val manifest = fetchers.fetchManifest(
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      applicationName = applicationName,
      id = getApplicationManifestFileName(applicationName),
      url = manifestUrl,
    )
    return httpClient.resolveUrls(manifest, manifestUrl)
  }

  companion object {
    const val DEFAULT_application_name = "default"
    private const val APPLICATION_MANIFEST_FILE_NAME_SUFFIX = "manifest.zipline.json"
    internal fun getApplicationManifestFileName(applicationName: String = DEFAULT_application_name) =
      "$applicationName.$APPLICATION_MANIFEST_FILE_NAME_SUFFIX"
  }
}
