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
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createRelativeManifest
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

  @Before
  fun setUp() {
    tester = LoadOrFallbackTester()
  }

  @Test
  fun preferNetworkWhenThatWorks() = runBlocking {
    assertEquals("apple", tester.success("red", "apple"))
  }

  @Test
  fun fallBackToPreviousNetworkWhenSomethingFails() = runBlocking {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("apple", tester.failureManifestFetchFails("red"))
  }

  @Test
  fun fallBackBecauseManifestFetchFails() = runBlocking {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureManifestFetchFails("red"))
  }

  @Test
  fun fallBackBecauseCodeFetchFails() = runBlocking {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureCodeFetchFails("red"))
  }

  @Test
  fun fallBackBecauseCodeRunFails() = runBlocking {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureCodeRunFails("red"))
  }

  @Test
  fun successfulNetworkUpdatesFallback() = runBlocking {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("firetruck", tester.success("red", "firetruck"))
    assertEquals("firetruck", tester.failureManifestFetchFails("red"))
  }

  @Test
  fun eachApplicationHasItsOwnLastWorkingNetwork() = runBlocking {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("sky", tester.success("blue", "sky"))
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("sky", tester.failureManifestFetchFails("blue"))
  }

  @Test
  fun eachApplicationHasItsOwnEmbedded() = runBlocking {
    tester.seedEmbedded("red", "apple")
    tester.seedEmbedded("blue", "sky")
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("sky", tester.failureManifestFetchFails("blue"))
  }

  @Test
  fun anyLastWorkingNetworkNotPruned() = runBlocking {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("sky", tester.success("blue", "sky"))
    assertEquals(0, tester.countPrunedFiles())
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("sky", tester.failureManifestFetchFails("blue"))
  }

  @Test
  fun successfulNetworkMakesPreviousNetworkPrunable() = runBlocking {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals(2, tester.countPrunedFiles {
      assertEquals("firetruck", tester.success("red", "firetruck"))
    })
  }

  @Test
  fun successAfterFailureMakesFailurePrunable() = runBlocking {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals(2, tester.countPrunedFiles {
      assertEquals("apple", tester.failureCodeRunFails("red"))
    })
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

    fun seedEmbedded(applicationName: String, seed: String) {
      embeddedFileSystem.createDirectories(embeddedDir)
      val ziplineFileByteString = testFixtures.createZiplineFile(createJs(seed), "$seed.js")
      val sha256 = ziplineFileByteString.sha256()
      val manifest = createRelativeManifest(seed, sha256)
      val manifestJsonString = Json.encodeToString(ZiplineManifest.serializer(), manifest)
      embeddedFileSystem.write(embeddedDir / sha256.hex()) {
        write(ziplineFileByteString)
      }
      embeddedFileSystem.write(embeddedDir / getApplicationManifestFileName(applicationName)) {
        write(manifestJsonString.encodeUtf8())
      }
    }

    suspend fun success(applicationName: String, seed: String): String {
      val manifestUrl =
        "$baseUrl/$applicationName/${getApplicationManifestFileName(applicationName)}"
      val ziplineFileByteString = testFixtures.createZiplineFile(createJs(seed), "$seed.js")
      val manifest = createRelativeManifest(seed, ziplineFileByteString.sha256())
      val manifestJsonString = Json.encodeToString(ZiplineManifest.serializer(), manifest)
      httpClient.filePathToByteString = mapOf(
        manifestUrl to manifestJsonString.encodeUtf8(),
        "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString
      )
      zipline = loader.loadOrFallBack(applicationName, EmptySerializersModule, manifestUrl)
      return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
        " loaded\n"
      )
    }

    fun countPrunedFiles(): Int = cache.countPrunedFiles()

    suspend fun countPrunedFiles(block: suspend () -> Unit): Int {
      val before = cache.countFiles()
      block()
      val after = cache.countFiles()
      return after - before
    }

    suspend fun failureManifestFetchFails(applicationName: String): String {
      val seed = "fail"
      val manifestUrl =
        "$baseUrl/$applicationName/${getApplicationManifestFileName(applicationName)}"
      val ziplineFileByteString = testFixtures.createZiplineFile(
        createJs(seed), "$seed.js"
      )
      httpClient.filePathToByteString = mapOf(
        "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString
      )
      zipline = loader.loadOrFallBack(applicationName, EmptySerializersModule, manifestUrl)
      return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
        " loaded\n"
      )
    }

    suspend fun failureCodeFetchFails(applicationName: String): String {
      val seed = "fail"
      val ziplineFileByteString = testFixtures.createZiplineFile(createJs(seed), "$seed.js")
      val manifest = createRelativeManifest(seed, ziplineFileByteString.sha256())
      val manifestJsonString = Json.encodeToString(ZiplineManifest.serializer(), manifest)

      val manifestUrl =
        "$baseUrl/$applicationName/${getApplicationManifestFileName(applicationName)}"

      httpClient.filePathToByteString = mapOf(
        manifestUrl to manifestJsonString.encodeUtf8(),
      )
      zipline = loader.loadOrFallBack(applicationName, EmptySerializersModule, manifestUrl)
      return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
        " loaded\n"
      )
    }

    suspend fun failureCodeRunFails(applicationName: String): String {
      val seed = "fail"
      val ziplineFileByteString = testFixtures.createZiplineFile(createFailureJs(seed), "$seed.js")
      val manifest = createRelativeManifest(seed, ziplineFileByteString.sha256())
      val manifestJsonString = Json.encodeToString(ZiplineManifest.serializer(), manifest)

      val manifestUrl =
        "$baseUrl/$applicationName/${getApplicationManifestFileName(applicationName)}"

      httpClient.filePathToByteString = mapOf(
        manifestUrl to manifestJsonString.encodeUtf8(),
        "$baseUrl/$applicationName/$seed.zipline" to ziplineFileByteString
      )
      zipline = loader.loadOrFallBack(applicationName, EmptySerializersModule, manifestUrl)
      return (zipline.quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(
        " loaded\n"
      )
    }
  }
}
