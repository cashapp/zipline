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
import app.cash.zipline.loader.ManifestVerifier.Companion.NO_SIGNATURE_CHECKS
import app.cash.zipline.loader.internal.fetcher.MANIFEST_MAX_SIZE
import app.cash.zipline.loader.internal.getApplicationManifestFileName
import app.cash.zipline.loader.testing.LoaderTestFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okio.Buffer
import okio.FileSystem

@OptIn(ExperimentalCoroutinesApi::class)
class LoaderTester(
  private val eventListener: EventListener = EventListener.NONE,
  private val manifestVerifier: ManifestVerifier = NO_SIGNATURE_CHECKS
) {
  val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "okio-${randomToken().hex()}"

  val httpClient = FakeZiplineHttpClient()
  private val dispatcher = UnconfinedTestDispatcher()
  private val cacheMaxSizeInBytes = 100 * 1024 * 1024
  private var nowMillis = 1_000L

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

  fun beforeTest() {
    systemFileSystem.createDirectories(tempDir, mustCreate = true)
    systemFileSystem.createDirectories(embeddedDir, mustCreate = true)
    cache = testZiplineCache(
      systemFileSystem,
      cacheDir,
      cacheMaxSizeInBytes.toLong()
    )
    loader = testZiplineLoader(
      dispatcher = dispatcher,
      manifestVerifier = manifestVerifier,
      httpClient = httpClient,
      nowEpochMs = { nowMillis },
      eventListener = eventListener,
    ).withEmbedded(
      embeddedDir = embeddedDir,
      embeddedFileSystem = embeddedFileSystem,
    ).withCache(
      cache
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
        applicationName
      )
    ) {
      write(embeddedManifest.manifestBytes)
    }
  }

  suspend fun success(applicationName: String, seed: String): String {
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
      "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString
    )
    zipline = loader.loadOnce(applicationName, manifestUrl).zipline
    return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
      " loaded\n"
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
      LoaderTestFixtures.createJs(seed), "$seed.js"
    )
    httpClient.filePathToByteString = mapOf(
      "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString
    )
    zipline = loader.loadOnce(applicationName, manifestUrl).zipline
    return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
      " loaded\n"
    )
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
      .writeUtf8(" ".repeat(MANIFEST_MAX_SIZE - loadedManifest.manifestBytes.size + 1))
      .write(loadedManifest.manifestBytes)
      .readByteString()
    httpClient.filePathToByteString = mapOf(
      manifestUrl to tooLargeManifest,
      "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString,
    )
    zipline = loader.loadOnce(applicationName, manifestUrl).zipline
    return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
      " loaded\n"
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
        byteCount = loadedManifest.manifestBytes.size - 1, // Drop trailing '}'.
      )
      .readByteString()
    httpClient.filePathToByteString = mapOf(
      manifestUrl to malformedManifest,
      "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString
    )
    zipline = loader.loadOnce(applicationName, manifestUrl).zipline
    return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
      " loaded\n"
    )
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
    zipline = loader.loadOnce(applicationName, manifestUrl).zipline
    return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
      " loaded\n"
    )
  }

  suspend fun failureCodeLoadFails(applicationName: String): String {
    val seed = "broken"
    val ziplineFileByteString = testFixtures.createZiplineFile(
      LoaderTestFixtures.createFailureJs(
        seed
      ), "$seed.js"
    )
    val loadedManifest = LoaderTestFixtures.createRelativeManifest(
      seed,
      ziplineFileByteString.sha256(),
      includeUnknownFieldInJson,
    )

    val manifestUrl = "$baseUrl/$applicationName/${getApplicationManifestFileName(applicationName)}"

    httpClient.filePathToByteString = mapOf(
      manifestUrl to loadedManifest.manifestBytes,
      "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString
    )
    zipline = loader.loadOnce(applicationName, manifestUrl).zipline
    return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
      " loaded\n"
    )
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
      "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString
    )
    zipline = loader.loadOnce(applicationName, manifestUrl) {
      val loadedSeed = (it.quickJs.evaluate("globalThis.log", "assert.js") as String)
        .removeSuffix(" loaded\n")
      if (loadedSeed == seed) throw IllegalArgumentException("Zipline code run failed")
    }.zipline
    return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String)
      .removeSuffix(" loaded\n")
  }
}
