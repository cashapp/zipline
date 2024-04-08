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
import app.cash.zipline.ZiplineManifest
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
  private val eventListenerFactory: EventListener.Factory,
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
    httpFetcher = HttpFetcher(httpClient),
    eventListenerFactory = EventListener.Factory { _, _ -> eventListener },
    nowEpochMs = nowEpochMs,
    embeddedDir = null,
    embeddedFileSystem = null,
    cache = null,
  )

  @Deprecated(
    message = "Use `withEmbedded` which has FileSystem came first instead.",
    level = DeprecationLevel.HIDDEN,
  )
  fun withEmbedded(
    embeddedDir: Path,
    embeddedFileSystem: FileSystem,
  ): ZiplineLoader = withEmbedded(embeddedFileSystem, embeddedDir)

  fun withEmbedded(
    embeddedFileSystem: FileSystem,
    embeddedDir: Path,
  ): ZiplineLoader = copy(
    embeddedDir = embeddedDir,
    embeddedFileSystem = embeddedFileSystem,
  )

  fun withCache(
    cache: ZiplineCache,
  ): ZiplineLoader = copy(
    cache = cache,
  )

  fun withEventListenerFactory(
    eventListenerFactory: EventListener.Factory,
  ): ZiplineLoader = copy(
    eventListenerFactory = eventListenerFactory,
  )

  private fun copy(
    embeddedDir: Path? = this.embeddedDir,
    embeddedFileSystem: FileSystem? = this.embeddedFileSystem,
    cache: ZiplineCache? = this.cache,
    eventListenerFactory: EventListener.Factory = this.eventListenerFactory,
  ): ZiplineLoader {
    return ZiplineLoader(
      dispatcher = dispatcher,
      manifestVerifier = manifestVerifier,
      httpFetcher = httpFetcher,
      eventListenerFactory = eventListenerFactory,
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
      embeddedFileSystem = embeddedFileSystem ?: return@run null,
      embeddedDir = embeddedDir ?: return@run null,
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
   * Returns a flow of loaded applications.
   *
   * Local or Remote
   * ===============
   *
   * If either caching or embedded code are enabled in this loader, this will attempt to load that
   * local code. If the local code is fresh and loading it succeeds, the returned flow will
   * finish.
   *
   * Otherwise, this will load code from the network each time that [manifestUrlFlow] emits. It
   * will skip loads when the code is unchanged.
   *
   * Triggering Load Attempts
   * ========================
   *
   * The [manifestUrlFlow] should emit whenever a load attempt should be made. Some examples:
   *
   *  * If the URL to load the manifest from changes, as occurs when switching from development to
   *    production code.
   *  * If hot-reloading is enabled, and a hot reload should be attempted because the code may have
   *    changed.
   *  * If the most recent load attempt is [LoadResult.Failure] and that load should be retried.
   *
   * Loading Starts on Collect
   * =========================
   *
   * The returned flow is a _cold_ flow. No work will be performed unless there's a collect in
   * flight.
   *
   * Skips, Failures and Successes
   * =============================
   *
   * Each code update yields a few potential outcomes:
   *
   *  * Skipped, because the cached or embedded code is too old
   *  * Skipped, because the new code is the same as what's already loaded
   *  * Failed, due to a problem downloading a manifest
   *  * Failed, due to a problem downloading a binary
   *  * Failed, due to a crash initializing the code
   *  * Success
   *
   * Failures and success results are emitted to the returned flow.
   *
   * @param manifestUrlFlow a flow that emits whenever by the downstream service.
   * @param freshnessChecker checks if a cached ZiplineManifest is considered fresh. The default
   *     value is [DefaultFreshnessCheckerNotFresh] which completely prevents the cache and embedded
   *     code from being used.
   */
  fun load(
    applicationName: String,
    freshnessChecker: FreshnessChecker = DefaultFreshnessCheckerNotFresh,
    manifestUrlFlow: Flow<String>,
    serializersModule: SerializersModule = EmptySerializersModule(),
    initializer: (Zipline) -> Unit = {},
  ): Flow<LoadResult> {
    return channelFlow {
      var previousManifest: ZiplineManifest? = null
      val localManifest = loadFromLocal(
        nowEpochMs(),
        applicationName,
        freshnessChecker,
        serializersModule,
        initializer,
      )

      // If we successfully loaded local code, don't collect the remote code. We're done!
      if (localManifest != null) {
        previousManifest = localManifest
        return@channelFlow
      }

      manifestUrlFlow.collect { manifestUrl ->
        val loadedFromNetwork = loadFromNetwork(
          previousManifest,
          nowEpochMs(),
          applicationName,
          manifestUrl,
          serializersModule,
          initializer,
        )
        if (loadedFromNetwork != null) {
          previousManifest = loadedFromNetwork
        }
      }
    }
  }

  /**
   * Downloads ZiplineManifest each time [manifestUrlFlow] emits and loads Zipline with the newly
   * downloaded ZiplineManifest, skipping loads if the code to load is the same as what's already
   * loaded.
   *
   * Always loads from network, never from local.
   *
   * @param manifestUrlFlow a flow that should emit each time a load should be attempted. This
   *     may emit periodically to trigger polling. It should also emit for loading triggers like
   *     app launch, app foregrounding, and network connectivity changed.
   */
  @Deprecated(
    message = "Deprecated, will be removed in 1.9",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("load with FreshnessChecker in parameter list"),
  )
  fun load(
    applicationName: String,
    manifestUrlFlow: Flow<String>,
    serializersModule: SerializersModule = EmptySerializersModule(),
    initializer: (Zipline) -> Unit = {},
  ): Flow<LoadResult> {
    return load(
      applicationName = applicationName,
      freshnessChecker = DefaultFreshnessCheckerNotFresh,
      manifestUrlFlow = manifestUrlFlow,
      serializersModule = serializersModule,
      initializer = initializer,
    )
  }

  /**
   * Fetches ZiplineManifest from network, and after a successful fetch, loads Zipline with newly
   * fetched ZiplineManifest.
   */
  private suspend fun ProducerScope<LoadResult>.loadFromNetwork(
    previousManifest: ZiplineManifest?,
    now: Long,
    applicationName: String,
    manifestUrl: String,
    serializersModule: SerializersModule,
    initializer: (Zipline) -> Unit,
  ): ZiplineManifest? {
    val eventListener = eventListenerFactory.create(applicationName, manifestUrl)

    val startValue = eventListener.applicationLoadStart(applicationName, manifestUrl)
    var loadedManifest: LoadedManifest? = null

    try {
      loadedManifest = fetchManifestFromNetwork(applicationName, eventListener, manifestUrl)
      if (loadedManifest.manifest == previousManifest) {
        // Unchanged. Update freshness timestamp in cache DB.
        cachingFetcher?.updateFreshAt(applicationName, loadedManifest, now)
        eventListener.applicationLoadSkipped(applicationName, manifestUrl, startValue)
        return null
      }

      eventListener.manifestReady(applicationName, manifestUrl, loadedManifest.manifest)

      val zipline = loadFromManifest(
        applicationName,
        eventListener,
        loadedManifest,
        serializersModule,
        now,
        initializer,
      )
      cachingFetcher?.pin(applicationName, loadedManifest, now) // Pin after success.
      eventListener.applicationLoadSuccess(
        applicationName,
        manifestUrl,
        loadedManifest.manifest,
        zipline,
        startValue,
      )
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

  /**
   * Loads the locally-cached or embedded application if it is fresh, and returns its manifest.
   * This returns null if there is no cached or embedded application, if that application is not
   * fresh, or if loading it fails.
   */
  private suspend fun ProducerScope<LoadResult>.loadFromLocal(
    now: Long,
    applicationName: String,
    freshnessChecker: FreshnessChecker,
    serializersModule: SerializersModule,
    initializer: (Zipline) -> Unit,
  ): ZiplineManifest? {
    val eventListener = eventListenerFactory.create(applicationName, null)

    val loadedManifest = loadCachedOrEmbeddedManifest(applicationName, eventListener, now)
      ?: return null

    val startValue = eventListener.applicationLoadStart(applicationName, null)
    if (!freshnessChecker.isFresh(loadedManifest.manifest, loadedManifest.freshAtEpochMs)) {
      eventListener.applicationLoadSkippedNotFresh(applicationName, null, startValue)
      return null
    }

    try {
      eventListener.manifestReady(applicationName, null, loadedManifest.manifest)

      val zipline = loadFromManifest(
        applicationName,
        eventListener,
        loadedManifest,
        serializersModule,
        now,
        initializer,
      )
      eventListener.applicationLoadSuccess(
        applicationName,
        null,
        loadedManifest.manifest,
        zipline,
        startValue,
      )
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
    freshnessChecker: FreshnessChecker,
    manifestUrl: String,
    serializersModule: SerializersModule = EmptySerializersModule(),
    initializer: (Zipline) -> Unit = {},
  ): LoadResult = load(
    applicationName = applicationName,
    freshnessChecker = freshnessChecker,
    manifestUrlFlow = flowOf(manifestUrl),
    serializersModule = serializersModule,
    initializer = initializer,
  ).first()

  /** Always loads from the network, never from local. */
  @Deprecated(
    message = "Deprecated, will be removed in 1.9",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("loadOnce with FreshnessChecker in parameter list"),
  )
  suspend fun loadOnce(
    applicationName: String,
    manifestUrl: String,
    serializersModule: SerializersModule = EmptySerializersModule(),
    initializer: (Zipline) -> Unit = {},
  ): LoadResult = load(
    applicationName = applicationName,
    freshnessChecker = DefaultFreshnessCheckerNotFresh,
    manifestUrlFlow = flowOf(manifestUrl),
    serializersModule = serializersModule,
    initializer = initializer,
  ).first()

  /**
   * After identifying a manifest to load this fetches all the code, loads it into a JS runtime,
   * and runs both the user's initializer and the manifest's specified main function.
   */
  @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
  internal suspend fun loadFromManifest(
    applicationName: String,
    eventListener: EventListener,
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
        eventListener,
        nowEpochMs,
      )

      // Run caller lambda to validate and initialize the loaded code to confirm it works.
      val initializerStartValue = eventListener.initializerStart(zipline, applicationName)
      try {
        initializer(zipline)
      } finally {
        eventListener.initializerEnd(zipline, applicationName, initializerStartValue)
      }

      // Run the application after initializer has been run.
      val mainFunctionStartValue = eventListener.mainFunctionStart(zipline, applicationName)
      try {
        loadedManifest.manifest.mainFunction?.let { mainFunction ->
          app.cash.zipline.internal.runApplication(
            zipline.quickJs,
            loadedManifest.manifest.mainModuleId,
            mainFunction,
          )
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

  @Deprecated(
    message = "Use `download` which has FileSystem came first instead.",
    level = DeprecationLevel.HIDDEN,
  )
  suspend fun download(
    applicationName: String,
    downloadDir: Path,
    downloadFileSystem: FileSystem,
    manifestUrl: String,
  ) = download(applicationName, downloadFileSystem, downloadDir, manifestUrl)

  suspend fun download(
    applicationName: String,
    downloadFileSystem: FileSystem,
    downloadDir: Path,
    manifestUrl: String,
  ) {
    val eventListener = eventListenerFactory.create(applicationName, manifestUrl)
    val manifest = fetchManifestFromNetwork(applicationName, eventListener, manifestUrl)
    val manifestWithFreshAt = manifest.encodeFreshAtMs()
    download(
      applicationName = applicationName,
      eventListener = eventListener,
      downloadFileSystem = downloadFileSystem,
      downloadDir = downloadDir,
      loadedManifest = manifestWithFreshAt,
    )
  }

  internal suspend fun download(
    applicationName: String,
    eventListener: EventListener,
    downloadFileSystem: FileSystem,
    downloadDir: Path,
    loadedManifest: LoadedManifest,
  ) {
    val now = nowEpochMs()
    downloadFileSystem.createDirectories(downloadDir)
    downloadFileSystem.write(downloadDir / getApplicationManifestFileName(applicationName)) {
      write(loadedManifest.manifestBytes)
    }
    receive(
      FsSaveReceiver(downloadFileSystem, downloadDir),
      loadedManifest,
      applicationName,
      eventListener,
      now,
    )
  }

  private suspend fun receive(
    receiver: Receiver,
    loadedManifest: LoadedManifest,
    applicationName: String,
    eventListener: EventListener,
    nowEpochMs: Long,
  ) {
    coroutineScope {
      val loads = loadedManifest.manifest.modules.map {
        ModuleJob(
          applicationName = applicationName,
          eventListener = eventListener,
          id = it.key,
          baseUrl = loadedManifest.manifest.baseUrl,
          nowEpochMs = nowEpochMs,
          module = it.value,
          receiver = receiver,
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
    val eventListener: EventListener,
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
        eventListener = eventListener,
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
    eventListener: EventListener,
    nowEpochMs: Long,
  ): LoadedManifest? {
    val result = cachingFetcher?.loadPinnedManifest(applicationName, nowEpochMs)
      ?: embeddedFetcher?.loadEmbeddedManifest(applicationName)
      ?: return null

    // Defend against changes to the locally-cached copy.
    val verifiedKey = manifestVerifier.verify(result.manifestBytes, result.manifest)
    if (verifiedKey != null) {
      eventListener.manifestVerified(applicationName, null, result.manifest, verifiedKey)
    }

    return result
  }

  private suspend fun fetchManifestFromNetwork(
    applicationName: String,
    eventListener: EventListener,
    manifestUrl: String,
  ): LoadedManifest {
    val result = concurrentDownloadsSemaphore.withPermit {
      httpFetcher.fetchManifest(
        applicationName = applicationName,
        eventListener = eventListener,
        url = manifestUrl,
        freshAtEpochMs = nowEpochMs(),
      )
    }

    // Defend against unauthorized changes in the supply chain.
    val verifiedKey = manifestVerifier.verify(result.manifestBytes, result.manifest)
    if (verifiedKey != null) {
      eventListener.manifestVerified(applicationName, manifestUrl, result.manifest, verifiedKey)
    }

    return result
  }
}
