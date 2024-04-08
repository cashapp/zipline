/*
 * Copyright (C) 2022 Block, Inc.
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
import app.cash.zipline.loader.ManifestVerifier.Companion.NO_SIGNATURE_CHECKS
import app.cash.zipline.loader.internal.getApplicationManifestFileName
import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.testing.systemFileSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okio.Buffer
import okio.FileSystem

@OptIn(ExperimentalCoroutinesApi::class)
class LoaderTester(
  private val eventListenerFactory: EventListener.Factory = EventListenerNoneFactory,
  private val manifestVerifier: ManifestVerifier = NO_SIGNATURE_CHECKS,
) {
  val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "okio-${randomToken().hex()}"

  val httpClient = FakeZiplineHttpClient()
  private val dispatcher = UnconfinedTestDispatcher()
  private val cacheMaxSizeInBytes = 100 * 1024 * 1024
  var nowMillis = 1_000L

  private var zipline = Zipline.create(dispatcher)

  val embeddedFileSystem = systemFileSystem
  val embeddedDir = tempDir / "embedded"
  private var testFixtures = LoaderTestFixtures()

  private val cacheDir = tempDir / "cache"

  private val baseUrl = "https://example.com/files"

  /** True to inject an extra field in encoded JSON to test forwards-compatibility. */
  internal var includeUnknownFieldInJson = false

  internal lateinit var loader: ZiplineLoader
  internal lateinit var cache: ZiplineCache

  @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
  private val manifestMaxSize = app.cash.zipline.internal.MANIFEST_MAX_SIZE

  fun beforeTest() {
    systemFileSystem.createDirectories(tempDir, mustCreate = true)
    systemFileSystem.createDirectories(embeddedDir, mustCreate = true)
    cache = testZiplineCache(
      systemFileSystem,
      cacheDir,
      cacheMaxSizeInBytes.toLong(),
    )
    loader = testZiplineLoader(
      dispatcher = dispatcher,
      manifestVerifier = manifestVerifier,
      httpClient = httpClient,
      nowEpochMs = { nowMillis },
      eventListenerFactory = eventListenerFactory,
    ).withEmbedded(
      embeddedFileSystem = embeddedFileSystem,
      embeddedDir = embeddedDir,
    ).withCache(
      cache,
    )
  }

  fun afterTest() {
    cache.close()
  }

  fun seedEmbedded(applicationName: String, seed: String) {
    embeddedFileSystem.createDirectories(embeddedDir)
    val ziplineFileByteString =
      testFixtures.createZiplineFile(LoaderTestFixtures.createJs(seed), "$seed.js")
    val sha256 = ziplineFileByteString.sha256()
    val embeddedManifest = LoaderTestFixtures.createRelativeEmbeddedManifest(
      seed = seed,
      seedFileSha256 = sha256,
      seedFreshAtEpochMs = 5L,
      includeUnknownFieldInJson = includeUnknownFieldInJson,
    )
    embeddedFileSystem.write(embeddedDir / sha256.hex()) {
      write(ziplineFileByteString)
    }
    embeddedFileSystem.write(
      embeddedDir / getApplicationManifestFileName(
        applicationName,
      ),
    ) {
      write(embeddedManifest.manifestBytes)
    }
  }

  suspend fun success(
    applicationName: String,
    seed: String,
    freshnessChecker: FreshnessChecker,
  ): String {
    val success =
      load(applicationName, seed, count = 1, freshnessChecker).first() as LoadResult.Success
    val log = success.zipline.quickJs.evaluate("globalThis.log", "assert.js") as String
    return log.removeSuffix(" loaded\n")
  }

  fun load(
    applicationName: String,
    seed: String,
    count: Int = 1,
    freshnessChecker: FreshnessChecker,
  ): Flow<LoadResult> {
    val manifestUrl = "$baseUrl/$applicationName/${getApplicationManifestFileName(applicationName)}"
    val ziplineFileByteString =
      testFixtures.createZiplineFile(LoaderTestFixtures.createJs(seed), "$seed.js")
    val loadedManifest = LoaderTestFixtures.createRelativeManifest(
      seed,
      ziplineFileByteString.sha256(),
      includeUnknownFieldInJson,
    )
    httpClient.filePathToByteString = mapOf(
      manifestUrl to loadedManifest.manifestBytes,
      "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString,
    )
    return loader.load(
      applicationName = applicationName,
      manifestUrlFlow = List(count) { manifestUrl }.asFlow(),
      freshnessChecker = freshnessChecker,
    )
  }

  // Used to test the deprecated load function, and should be deleted when the deprecated function
  // is removed.
  suspend fun successForDeprecatedLoad(
    applicationName: String,
    seed: String,
  ): String {
    val success = deprecatedLoad(applicationName, seed, count = 1).first() as LoadResult.Success
    val log = success.zipline.quickJs.evaluate("globalThis.log", "assert.js") as String
    return log.removeSuffix(" loaded\n")
  }

  // Used to test the deprecated load function, and should be deleted when the deprecated function
  // is removed.
  private fun deprecatedLoad(
    applicationName: String,
    seed: String,
    count: Int = 1,
  ): Flow<LoadResult> {
    val manifestUrl = "$baseUrl/$applicationName/${getApplicationManifestFileName(applicationName)}"
    val ziplineFileByteString =
      testFixtures.createZiplineFile(LoaderTestFixtures.createJs(seed), "$seed.js")
    val loadedManifest = LoaderTestFixtures.createRelativeManifest(
      seed,
      ziplineFileByteString.sha256(),
      includeUnknownFieldInJson,
    )
    httpClient.filePathToByteString = mapOf(
      manifestUrl to loadedManifest.manifestBytes,
      "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString,
    )
    @Suppress("DEPRECATION_ERROR")
    return loader.load(
      applicationName = applicationName,
      manifestUrlFlow = List(count) { manifestUrl }.asFlow(),
    )
  }

  /** Returns the delta in the number of files, like -3 if 3 files were deleted. */
  suspend fun prune(): Int {
    return countFiles {
      cache.prune(0L)
    }
  }

  /** Returns the delta in the number of files, like 3 if 3 files were added. */
  suspend fun countFiles(block: suspend () -> Unit): Int {
    val before = cache.countFiles()
    block()
    val after = cache.countFiles()
    return after - before
  }

  suspend fun failureManifestFetchFails(applicationName: String): String {
    val seed = "fail"
    val manifestUrl = "$baseUrl/$applicationName/${getApplicationManifestFileName(applicationName)}"
    val ziplineFileByteString = testFixtures.createZiplineFile(
      LoaderTestFixtures.createJs(seed),
      "$seed.js",
    )
    httpClient.filePathToByteString = mapOf(
      "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString,
    )

    loadZiplineFromLastResult(applicationName, manifestUrl)

    return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
      " loaded\n",
    )
  }

  suspend fun failureManifestFetchingFails(applicationName: String): LoadResult {
    val seed = "fail"
    val ziplineFileByteString = testFixtures.createZiplineFile(
      LoaderTestFixtures.createJs(seed),
      "$seed.js",
    )
    httpClient.filePathToByteString = mapOf(
      "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString,
    )
    return loader.load(applicationName, FakeFreshnessCheckerFresh, flowOf("bogusUrl")).single()
  }

  suspend fun failureManifestTooLarge(applicationName: String): String {
    val seed = "too large"
    val ziplineFileByteString =
      testFixtures.createZiplineFile(LoaderTestFixtures.createJs(seed), "$seed.js")
    val loadedManifest = LoaderTestFixtures.createRelativeManifest(
      seed,
      ziplineFileByteString.sha256(),
      includeUnknownFieldInJson,
    )

    val manifestUrl = "$baseUrl/$applicationName/${getApplicationManifestFileName(applicationName)}"

    val tooLargeManifest = Buffer()
      .writeUtf8(" ".repeat(manifestMaxSize - loadedManifest.manifestBytes.size + 1))
      .write(loadedManifest.manifestBytes)
      .readByteString()
    httpClient.filePathToByteString = mapOf(
      manifestUrl to tooLargeManifest,
      "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString,
    )

    loadZiplineFromLastResult(applicationName, manifestUrl)

    return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
      " loaded\n",
    )
  }

  suspend fun failureManifestMalformedJson(applicationName: String): String {
    val seed = "malformed json"
    val manifestUrl = "$baseUrl/$applicationName/${getApplicationManifestFileName(applicationName)}"
    val ziplineFileByteString =
      testFixtures.createZiplineFile(LoaderTestFixtures.createJs(seed), "$seed.js")
    val loadedManifest = LoaderTestFixtures.createRelativeManifest(
      seed,
      ziplineFileByteString.sha256(),
      includeUnknownFieldInJson,
    )
    val malformedManifest = Buffer()
      .write(
        byteString = loadedManifest.manifestBytes,
        offset = 0,
        // Drop trailing '}'.
        byteCount = loadedManifest.manifestBytes.size - 1,
      )
      .readByteString()
    httpClient.filePathToByteString = mapOf(
      manifestUrl to malformedManifest,
      "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString,
    )

    loadZiplineFromLastResult(applicationName, manifestUrl)

    return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
      " loaded\n",
    )
  }

  suspend fun failureCacheNotFresh(applicationName: String): LoadResult {
    val manifestUrl = "$baseUrl/$applicationName/${getApplicationManifestFileName(applicationName)}"

    httpClient.filePathToByteString = mapOf()

    return load(
      applicationName,
      manifestUrl,
      freshnessChecker = DefaultFreshnessCheckerNotFresh,
    ).first()
  }

  suspend fun failureCodeFetchFails(applicationName: String): String {
    val seed = "unreachable"
    val ziplineFileByteString =
      testFixtures.createZiplineFile(LoaderTestFixtures.createJs(seed), "$seed.js")
    val loadedManifest = LoaderTestFixtures.createRelativeManifest(
      seed,
      ziplineFileByteString.sha256(),
      includeUnknownFieldInJson,
    )

    val manifestUrl = "$baseUrl/$applicationName/${getApplicationManifestFileName(applicationName)}"

    httpClient.filePathToByteString = mapOf(
      manifestUrl to loadedManifest.manifestBytes,
    )

    loadZiplineFromLastResult(applicationName, manifestUrl)

    return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
      " loaded\n",
    )
  }

  suspend fun failureCodeLoadFails(applicationName: String): String {
    val seed = "broken"
    val ziplineFileByteString = testFixtures.createZiplineFile(
      LoaderTestFixtures.createFailureJs(
        seed,
      ),
      "$seed.js",
    )
    val loadedManifest = LoaderTestFixtures.createRelativeManifest(
      seed,
      ziplineFileByteString.sha256(),
      includeUnknownFieldInJson,
    )

    val manifestUrl = "$baseUrl/$applicationName/${getApplicationManifestFileName(applicationName)}"

    httpClient.filePathToByteString = mapOf(
      manifestUrl to loadedManifest.manifestBytes,
      "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString,
    )

    loadZiplineFromLastResult(applicationName, manifestUrl)

    val loadedOutput = zipline.quickJs.evaluate("globalThis.log", "assert.js") as String
    return loadedOutput.removeSuffix(" loaded\n")
  }

  suspend fun failureCodeRunFails(applicationName: String): String {
    val seed = "crashes"
    val ziplineFileByteString =
      testFixtures.createZiplineFile(LoaderTestFixtures.createJs(seed), "$seed.js")
    val loadedManifest = LoaderTestFixtures.createRelativeManifest(
      seed,
      ziplineFileByteString.sha256(),
      includeUnknownFieldInJson,
    )

    val manifestUrl = "$baseUrl/$applicationName/${getApplicationManifestFileName(applicationName)}"

    httpClient.filePathToByteString = mapOf(
      manifestUrl to loadedManifest.manifestBytes,
      "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString,
    )

    loadZiplineFromLastResult(applicationName, manifestUrl) {
      val loadedSeed = (it.quickJs.evaluate("globalThis.log", "assert.js") as String)
        .removeSuffix(" loaded\n")
      if (loadedSeed == seed) throw IllegalArgumentException("Zipline code run failed")
    }

    return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String)
      .removeSuffix(" loaded\n")
  }

  /** First result is failure, second is the success from the pinned previous load */
  private suspend fun loadZiplineFromLastResult(
    applicationName: String,
    manifestUrl: String,
    initializer: (Zipline) -> Unit = {},
  ) {
    val results = loader.load(
      applicationName = applicationName,
      freshnessChecker = FakeFreshnessCheckerFresh,
      manifestUrlFlow = flowOf(manifestUrl),
      initializer = initializer,
    )
    zipline = (results.last() as LoadResult.Success).zipline
  }
}

object FakeFreshnessCheckerFresh : FreshnessChecker {
  override fun isFresh(manifest: ZiplineManifest, freshAtEpochMs: Long): Boolean {
    return true
  }
}
