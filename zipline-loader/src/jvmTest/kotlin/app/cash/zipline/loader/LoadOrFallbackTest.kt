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

import app.cash.zipline.QuickJs
import app.cash.zipline.Zipline
import app.cash.zipline.loader.ZiplineLoader.Companion.getApplicationManifestFileName
import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createFailureJs
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createJs
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createProductionZiplineLoader
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createZiplineFile
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import org.junit.Test

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
class LoadOrFallbackTest {
  private lateinit var tester: LoadOrFallbackTester
  private val dispatcher = TestCoroutineDispatcher()

  @Before
  fun setUp() {
    tester = LoadOrFallbackTester()
  }

  @Test
  fun preferNetworkWhenThatWorks(): Unit = runBlocking(dispatcher) {
    assertEquals("apple", tester.success("red", "apple"))
  }

  @Test
  fun fallBackToPreviousNetworkWhenSomethingFails(): Unit = runBlocking(dispatcher) {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("apple", tester.failureManifestFetchFails("red"))
  }

  @Test
  fun fallBackBecauseManifestFetchFails(): Unit = runBlocking(dispatcher) {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureManifestFetchFails("red"))
  }

  @Test
  fun fallBackBecauseCodeFetchFails(): Unit = runBlocking(dispatcher) {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureCodeFetchFails("red"))
  }

  @Test
  fun fallBackBecauseCodeRunFails(): Unit = runBlocking(dispatcher) {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureCodeRunFails("red"))
  }

  @Test
  fun successfulNetworkUpdatesFallback(): Unit = runBlocking(dispatcher) {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("firetruck", tester.success("red", "firetruck"))
    assertEquals("firetruck", tester.failureManifestFetchFails("red"))
  }

  @Test
  fun eachApplicationHasItsOwnLastWorkingNetwork(): Unit = runBlocking(dispatcher) {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("sky", tester.success("blue", "sky"))
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("sky", tester.failureManifestFetchFails("blue"))
  }

  @Test
  fun eachApplicationHasItsOwnEmbedded(): Unit = runBlocking(dispatcher) {
    tester.seedEmbedded("red", "apple")
    tester.seedEmbedded("blue", "sky")
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("sky", tester.failureManifestFetchFails("blue"))
  }

  @Test
  fun anyLastWorkingNetworkNotPruned(): Unit = runBlocking(dispatcher) {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("sky", tester.success("blue", "sky"))
    assertEquals(0, tester.pruneEverythingWeCanPrune())
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("sky", tester.failureManifestFetchFails("blue"))
  }

  @Test
  fun successfulNetworkMakesPreviousNetworkPrunable(): Unit = runBlocking(dispatcher) {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("firetruck", tester.success("red", "firetruck"))
    assertEquals(1, tester.pruneEverythingWeCanPrune())
  }

  @Test
  fun successAfterFailureMakesFailurePrunable(): Unit = runBlocking(dispatcher) {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("apple", tester.failureCodeRunFails("red"))
    assertEquals(1, tester.pruneEverythingWeCanPrune())
  }

  @OptIn(ExperimentalSerializationApi::class)
  class LoadOrFallbackTester {
    private val httpClient = FakeZiplineHttpClient()
    private val dispatcher = TestCoroutineDispatcher()
    private val cacheDbDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val cacheMaxSizeInBytes = 100 * 1024 * 1024
    private val cacheDirectory = "/zipline/cache".toPath()
    private var nowMillis = 1_000L

    private var zipline: Zipline = Zipline.create(dispatcher)
    private var fileSystem: FileSystem = FakeFileSystem()
    private var database: Database = createDatabase(cacheDbDriver)

    private var embeddedFileSystem: FileSystem = FakeFileSystem()
    private val embeddedDir = "/zipline".toPath()
    private var quickJs = QuickJs.create()
    private var testFixtures = LoaderTestFixtures(quickJs)

    private val baseUrl = "https://example.com/files"

    private lateinit var cache: ZiplineCache
    private lateinit var loader: ZiplineLoader

    init {
      Database.Schema.create(cacheDbDriver)
      cache = openZiplineCacheForTesting(
        database = database,
        directory = cacheDirectory,
        fileSystem = fileSystem,
        maxSizeInBytes = cacheMaxSizeInBytes.toLong(),
        nowMs = { nowMillis },
      )
      loader = createProductionZiplineLoader(
        dispatcher = dispatcher,
        httpClient = httpClient,
        embeddedDir = embeddedDir,
        embeddedFileSystem = embeddedFileSystem,
        cache = cache,
      )
    }

    private fun createRelativeManifest(
      seed: String
    ) = ZiplineManifest.create(
      modules = mapOf(
        seed to ZiplineModule(
          url = "$seed.zipline",
          sha256 = seed.encodeUtf8().sha256(),
        )
      )
    )

    fun seedEmbedded(applicationId: String, seed: String) {
      embeddedFileSystem.createDirectories(embeddedDir / applicationId)
      embeddedFileSystem.write(embeddedDir / applicationId / seed) {
        write(createZiplineFile(quickJs, createJs(seed), "$seed.js"))
      }
    }

    suspend fun success(applicationId: String, seed: String): String {
      val manifest = createRelativeManifest(seed)
      val manifestJsonString = Json.encodeToString(ZiplineManifest.serializer(), manifest)

      val manifestUrl = "$baseUrl/$applicationId/${getApplicationManifestFileName(applicationId)}"
      httpClient.filePathToByteString = mapOf(
        manifestUrl to manifestJsonString.encodeUtf8(),
        "$baseUrl/$applicationId/$seed.zipline" to createZiplineFile(
          quickJs, createJs(seed), "$seed.js"
        )
      )
      zipline = loader.loadOrFallBack(applicationId, EmptySerializersModule, manifestUrl)
      return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
        " loaded\n"
      )
    }

    fun pruneEverythingWeCanPrune(): Int {
      // TODO()
      return 1
    }

    suspend fun failureManifestFetchFails(applicationId: String): String {
      val seed = "fail"
      val manifestUrl = "$baseUrl/$applicationId/${getApplicationManifestFileName(applicationId)}"
      httpClient.filePathToByteString = mapOf(
        "$baseUrl/$applicationId/$seed.zipline" to createZiplineFile(
          quickJs, createJs(seed), "$seed.js"
        )
      )
      zipline = loader.loadOrFallBack(applicationId, EmptySerializersModule, manifestUrl)
      return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
        " loaded\n"
      )
    }

    suspend fun failureCodeFetchFails(applicationId: String): String {
      val seed = "fail"
      val manifest = createRelativeManifest(seed)
      val manifestJsonString = Json.encodeToString(ZiplineManifest.serializer(), manifest)

      val manifestUrl = "$baseUrl/$applicationId/${getApplicationManifestFileName(applicationId)}"

      httpClient.filePathToByteString = mapOf(
        manifestUrl to manifestJsonString.encodeUtf8(),
      )
      zipline = loader.loadOrFallBack(applicationId, EmptySerializersModule, manifestUrl)
      return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
        " loaded\n"
      )
    }

    suspend fun failureCodeRunFails(applicationId: String): String {
      val seed = "fail"
      val manifest = createRelativeManifest(seed)
      val manifestJsonString = Json.encodeToString(ZiplineManifest.serializer(), manifest)

      val manifestUrl = "$baseUrl/$applicationId/${getApplicationManifestFileName(applicationId)}"

      httpClient.filePathToByteString = mapOf(
        manifestUrl to manifestJsonString.encodeUtf8(),
        "$baseUrl/$applicationId/$seed.zipline" to createZiplineFile(
          quickJs, createFailureJs(seed), "$seed.js"
        )
      )
      zipline = loader.loadOrFallBack(applicationId, EmptySerializersModule, manifestUrl)
      return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
        " loaded\n"
      )
    }
  }
}
