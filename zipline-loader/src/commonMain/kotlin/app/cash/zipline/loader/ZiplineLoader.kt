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
import app.cash.zipline.internal.runApplication
import app.cash.zipline.loader.internal.fetcher.FsCachingFetcher
import app.cash.zipline.loader.internal.fetcher.FsEmbeddedFetcher
import app.cash.zipline.loader.internal.fetcher.HttpFetcher
import app.cash.zipline.loader.internal.fetcher.LoadedManifest
import app.cash.zipline.loader.internal.fetcher.fetch
import app.cash.zipline.loader.internal.getApplicationManifestFileName
import app.cash.zipline.loader.internal.receiver.FsSaveReceiver
import app.cash.zipline.loader.internal.receiver.Receiver
import app.cash.zipline.loader.internal.receiver.ZiplineLoadReceiver
import app.cash.zipline.loader.internal.systemEpochMsClock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import okio.FileSystem
import okio.Path

/**
 * Gets code from an HTTP server, or optional local cache or embedded filesystem, and handles with a
 * receiver (by default, loads it into a zipline instance).
 *
 * Loader attempts to load code as quickly as possible with concurrent network downloads and code
 * loading.
 */
class ZiplineLoader internal constructor(
  private val dispatcher: CoroutineDispatcher,
  private val manifestVerifier: ManifestVerifier,
  private val httpFetcher: HttpFetcher,
  private val eventListener: EventListener,
  private val nowEpochMs: () -> Long,
  private val embeddedDir: Path?,
  private val embeddedFileSystem: FileSystem?,
  private val cache: ZiplineCache?,
) {
  constructor(
    dispatcher: CoroutineDispatcher,
    manifestVerifier: ManifestVerifier,
    httpClient: ZiplineHttpClient,
    eventListener: EventListener = EventListener.NONE,
    nowEpochMs: () -> Long = systemEpochMsClock,
  ) : this(
    dispatcher = dispatcher,
    manifestVerifier = manifestVerifier,
    httpFetcher = HttpFetcher(httpClient, eventListener),
    eventListener = eventListener,
    nowEpochMs = nowEpochMs,
    embeddedDir = null,
    embeddedFileSystem = null,
    cache = null,
  )

  fun withEmbedded(
    embeddedDir: Path,
    embeddedFileSystem: FileSystem
  ): ZiplineLoader = copy(
    embeddedDir = embeddedDir,
    embeddedFileSystem = embeddedFileSystem,
  )

  fun withCache(
    cache: ZiplineCache,
  ): ZiplineLoader = copy(
    cache = cache
  )

  private fun copy(
    embeddedDir: Path? = this.embeddedDir,
    embeddedFileSystem: FileSystem? = this.embeddedFileSystem,
    cache: ZiplineCache? = this.cache,
  ): ZiplineLoader {
    return ZiplineLoader(
      dispatcher = dispatcher,
      manifestVerifier = manifestVerifier,
      httpFetcher = httpFetcher,
      eventListener = eventListener,
      nowEpochMs = nowEpochMs,
      embeddedDir = embeddedDir,
      embeddedFileSystem = embeddedFileSystem,
      cache = cache,
    )
  }

  private var concurrentDownloadsSemaphore = Semaphore(3)

  /** Callers can modify this as desired to change the default network download concurrency level. */
  var concurrentDownloads = 3
    set(value) {
      require(value > 0)
      field = value
      concurrentDownloadsSemaphore = Semaphore(value)
    }

  private val embeddedFetcher: FsEmbeddedFetcher? = run {
    FsEmbeddedFetcher(
      embeddedDir = embeddedDir ?: return@run null,
      embeddedFileSystem = embeddedFileSystem ?: return@run null,
    )
  }

  private val cachingFetcher: FsCachingFetcher? = run {
    FsCachingFetcher(
      cache = cache ?: return@run null,
      delegate = httpFetcher,
    )
  }

  /** Fetch modules local-first since we have a hash and all content is the same. */
  private val moduleFetchers = listOfNotNull(embeddedFetcher, cachingFetcher ?: httpFetcher)

  /**
   * Loads code from [manifestUrlFlow] each time it emits, skipping loads if the code to load is
   *    the same as what's already loaded.
   *
   * If the network is unreachable and there is local code newer, then this
   *   will load local code, either from the embedded directory or the cache.
   *
   * @param manifestUrlFlow a flow that should emit each time a load should be attempted. This
   *     may emit periodically to trigger polling. It should also emit for loading triggers like
   *     app launch, app foregrounding, and network connectivity changed.
   */
  fun load(
    applicationName: String,
    manifestUrlFlow: Flow<String>,
    serializersModule: SerializersModule = EmptySerializersModule(),
    initializer: (Zipline) -> Unit = {},
  ): Flow<LoadResult> {
    return channelFlow {
      var isFirstLoad = true
      var previousManifest: ZiplineManifest? = null

      manifestUrlFlow.collect { manifestUrl ->
        // Each time a manifest URL is emitted, download and initialize a Zipline for that URL.
        //  - skip if the manifest hasn't changed from the previous load.
        //  - pin the application if the load succeeded; unpin if it failed.
        val now = nowEpochMs()

        val loadedFromNetwork = loadFromNetwork(
          previousManifest,
          now,
          applicationName,
          manifestUrl,
          serializersModule,
          initializer,
        )
        if (loadedFromNetwork != null) {
          previousManifest = loadedFromNetwork
        }

        // If network loading failed (due to network error, or bad code), attempt to load from the
        // cache or embedded file system. This doesn't update pins!
        if (previousManifest == null && isFirstLoad) {
          isFirstLoad = false
          val loadedFromLocal = loadFromLocal(
            now,
            applicationName,
            serializersModule,
            initializer,
          )
          if (loadedFromLocal != null) {
            previousManifest = loadedFromLocal
          }
        }
      }
    }
  }

  private suspend fun ProducerScope<LoadResult>.loadFromNetwork(
    previousManifest: ZiplineManifest?,
    now: Long,
    applicationName: String,
    manifestUrl: String,
    serializersModule: SerializersModule,
    initializer: (Zipline) -> Unit,
  ): ZiplineManifest? {
    val startValue = eventListener.applicationLoadStart(applicationName, manifestUrl)
    var loadedManifest: LoadedManifest? = null

    try {
      loadedManifest = fetchManifestFromNetwork(applicationName, manifestUrl)
      if (loadedManifest.manifest == previousManifest) {
        // Unchanged. Update freshness timestamp in cache DB.
        cachingFetcher?.updateFreshAt(applicationName, loadedManifest, now)
        eventListener.applicationLoadSkipped(applicationName, manifestUrl, startValue)
        return null
      }

      val zipline = loadFromManifest(
        applicationName,
        loadedManifest,
        serializersModule,
        now,
        initializer,
      )
      cachingFetcher?.pin(applicationName, loadedManifest, now) // Pin after success.
      eventListener.applicationLoadSuccess(applicationName, manifestUrl, zipline, startValue)
      send(LoadResult.Success(zipline, loadedManifest.manifest, loadedManifest.freshAtEpochMs))
      return loadedManifest.manifest

    } catch (e: CancellationException) {
      // If emit() threw a CancellationException, consider that emit to be successful.
      // That's 'cause loadOnce() accepts an element and then immediately cancels the flow.
      throw e

    } catch (e: Exception) {
      eventListener.applicationLoadFailed(applicationName, manifestUrl, e, startValue)
      if (loadedManifest != null) {
        cachingFetcher?.unpin(applicationName, loadedManifest, now) // Unpin after failure.
      }
      send(LoadResult.Failure(e))
      return null
    }
  }

  private suspend fun ProducerScope<LoadResult>.loadFromLocal(
    now: Long,
    applicationName: String,
    serializersModule: SerializersModule,
    initializer: (Zipline) -> Unit,
  ): ZiplineManifest? {
    val loadedManifest = loadCachedOrEmbeddedManifest(applicationName, now)
      ?: return null

    val startValue = eventListener.applicationLoadStart(applicationName, null)
    try {
      val zipline = loadFromManifest(
        applicationName,
        loadedManifest,
        serializersModule,
        now,
        initializer,
      )
      eventListener.applicationLoadSuccess(applicationName, null, zipline, startValue)
      send(LoadResult.Success(zipline, loadedManifest.manifest, loadedManifest.freshAtEpochMs))
      return loadedManifest.manifest

    } catch (e: CancellationException) {
      // If emit() threw a CancellationException, consider that emit to be successful.
      // That's 'cause loadOnce() accepts an element and then immediately cancels the flow.
      throw e

    } catch (e: Exception) {
      send(LoadResult.Failure(e))
      eventListener.applicationLoadFailed(applicationName, null, e, startValue)
      return null
    }
  }

  suspend fun loadOnce(
    applicationName: String,
    manifestUrl: String,
    serializersModule: SerializersModule = EmptySerializersModule(),
    initializer: (Zipline) -> Unit = {},
  ): LoadResult = load(
    applicationName,
    flowOf(manifestUrl),
    serializersModule,
    initializer
  ).first()

  /**
   * After identifying a manifest to load this fetches all the code, loads it into a JS runtime,
   * and runs both the user's initializer and the manifest's specified main function.
   */
  internal suspend fun loadFromManifest(
    applicationName: String,
    loadedManifest: LoadedManifest,
    serializersModule: SerializersModule,
    nowEpochMs: Long,
    initializer: (Zipline) -> Unit,
  ): Zipline {
    val zipline = Zipline.create(dispatcher, serializersModule, eventListener)
    try {
      receive(
        ZiplineLoadReceiver(zipline, eventListener),
        loadedManifest,
        applicationName,
        nowEpochMs,
      )

      // Run caller lambda to validate and initialize the loaded code to confirm it works.
      val initializerStartValue = eventListener.initializerStart(zipline, applicationName)
      try {
        initializer(zipline)
      } finally {
        eventListener.initializerEnd(zipline, applicationName, initializerStartValue)
      }

      // Run the application after initializer has been run on Zipline engine.
      val mainFunctionStartValue = eventListener.mainFunctionStart(zipline, applicationName)
      try {
        loadedManifest.manifest.mainFunction?.let { mainFunction ->
          zipline.quickJs.runApplication(loadedManifest.manifest.mainModuleId, mainFunction)
        }
      } finally {
        eventListener.mainFunctionEnd(zipline, applicationName, mainFunctionStartValue)
      }

      return zipline
    } catch (e: Exception) {
      zipline.close()
      throw e
    }
  }

  suspend fun download(
    applicationName: String,
    downloadDir: Path,
    downloadFileSystem: FileSystem,
    manifestUrl: String,
  ) {
    val manifest = fetchManifestFromNetwork(applicationName, manifestUrl)
    val manifestWithFreshAt = manifest.encodeFreshAtMs()
    download(
      applicationName = applicationName,
      downloadDir = downloadDir,
      downloadFileSystem = downloadFileSystem,
      loadedManifest = manifestWithFreshAt,
    )
  }

  internal suspend fun download(
    applicationName: String,
    downloadDir: Path,
    downloadFileSystem: FileSystem,
    loadedManifest: LoadedManifest,
  ) {
    val now = nowEpochMs()
    downloadFileSystem.createDirectories(downloadDir)
    downloadFileSystem.write(downloadDir / getApplicationManifestFileName(applicationName)) {
      write(loadedManifest.manifestBytes)
    }
    receive(FsSaveReceiver(downloadFileSystem, downloadDir), loadedManifest, applicationName, now)
  }

  private suspend fun receive(
    receiver: Receiver,
    loadedManifest: LoadedManifest,
    applicationName: String,
    nowEpochMs: Long,
  ) {
    coroutineScope {
      val loads = loadedManifest.manifest.modules.map {
        ModuleJob(
          applicationName = applicationName,
          id = it.key,
          baseUrl = loadedManifest.manifest.baseUrl,
          nowEpochMs = nowEpochMs,
          module = it.value,
          receiver = receiver
        )
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
    val applicationName: String,
    val id: String,
    val baseUrl: String?,
    val module: ZiplineManifest.Module,
    val receiver: Receiver,
    val nowEpochMs: Long,
  ) {
    val upstreams = mutableListOf<Job>()

    /**
     * Fetch and receive ZiplineFile module.
     */
    suspend fun run() {
      val byteString = moduleFetchers.fetch(
        concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
        applicationName = applicationName,
        id = id,
        sha256 = module.sha256,
        nowEpochMs = nowEpochMs,
        baseUrl = baseUrl,
        url = module.url,
      )!!
      check(byteString.sha256() == module.sha256) {
        "checksum mismatch for $id"
      }
      upstreams.joinAll()
      withContext(dispatcher) {
        receiver.receive(byteString, id, module.sha256)
      }
    }
  }

  private fun loadCachedOrEmbeddedManifest(
    applicationName: String,
    nowEpochMs: Long,
  ): LoadedManifest? {
    val result = cachingFetcher?.loadPinnedManifest(applicationName, nowEpochMs)
      ?: embeddedFetcher?.loadEmbeddedManifest(applicationName)
      ?: return null

    // Defend against changes to the locally-cached copy.
    manifestVerifier.verify(result.manifestBytes, result.manifest)

    return result
  }

  private suspend fun fetchManifestFromNetwork(
    applicationName: String,
    manifestUrl: String,
  ): LoadedManifest {
    val result = concurrentDownloadsSemaphore.withPermit {
      httpFetcher.fetchManifest(
        applicationName = applicationName,
        url = manifestUrl,
        freshAtEpochMs = nowEpochMs(),
      )
    }

    // Defend against unauthorized changes in the supply chain.
    manifestVerifier.verify(result.manifestBytes, result.manifest)

    return result
  }
}
