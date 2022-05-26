/*
 * Copyright (C) 2022 Square, Inc.
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
import app.cash.zipline.QuickJs
import app.cash.zipline.Zipline
import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.alphaUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.bravoUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createProductionZiplineLoader
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test

class ProductionFetcherReceiverTest {
  private val httpClient = FakeZiplineHttpClient()
  private val dispatcher = TestCoroutineDispatcher()
  private val cacheDbDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val cacheMaxSizeInBytes = 100L * 1024L * 1024L
  private val cacheDirectory = "/zipline/cache".toPath()
  private var nowMillis = 1_000L

  private lateinit var zipline: Zipline
  private lateinit var cache: ZiplineCache

  private lateinit var fileSystem: FileSystem
  private lateinit var embeddedFileSystem: FileSystem
  private val embeddedDir = "/zipline".toPath()
  private lateinit var quickJs: QuickJs
  private lateinit var testFixtures: LoaderTestFixtures
  private lateinit var loader: ZiplineLoader

  @Before
  fun setUp() {
    Database.Schema.create(cacheDbDriver)
    quickJs = QuickJs.create()
    testFixtures = LoaderTestFixtures(quickJs)
    fileSystem = FakeFileSystem()
    embeddedFileSystem = FakeFileSystem()
    cache = createZiplineCache(
      eventListener = EventListener.NONE,
      driver = cacheDbDriver,
      fileSystem = fileSystem,
      directory = cacheDirectory,
      maxSizeInBytes = cacheMaxSizeInBytes,
      nowMs = { nowMillis }
    )
    loader = createProductionZiplineLoader(
      dispatcher = dispatcher,
      httpClient = httpClient,
      embeddedDir = embeddedDir,
      embeddedFileSystem = embeddedFileSystem,
      cache = cache,
    )
  }

  @After
  fun tearDown() {
    quickJs.close()
    cacheDbDriver.close()
  }

  @Test
  fun getFromEmbeddedFileSystemNoNetworkCall() = runBlocking {
    embeddedFileSystem.createDirectories(embeddedDir)
    embeddedFileSystem.write(embeddedDir / testFixtures.alphaSha256Hex) {
      write(testFixtures.alphaByteString)
    }
    embeddedFileSystem.write(embeddedDir / testFixtures.bravoSha256Hex) {
      write(testFixtures.bravoByteString)
    }

    httpClient.filePathToByteString = mapOf()

    zipline = loader.loadOrFail("test", testFixtures.manifest)

    assertEquals(
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin(),
      zipline.quickJs.evaluate("globalThis.log", "assert.js")
    )
  }

  @Test
  fun getFromWarmCacheNoNetworkCall() = runBlocking {
    cache.getOrPut("app1", testFixtures.alphaSha256) {
      testFixtures.alphaByteString
    }
    assertEquals(testFixtures.alphaByteString, cache.read(testFixtures.alphaSha256))
    cache.getOrPut("app1", testFixtures.bravoSha256) {
      testFixtures.bravoByteString
    }
    assertEquals(testFixtures.bravoByteString, cache.read(testFixtures.bravoSha256))

    httpClient.filePathToByteString = mapOf()

    zipline = loader.loadOrFail("test", testFixtures.manifest)

    assertEquals(
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin(),
      zipline.quickJs.evaluate("globalThis.log", "assert.js")
    )
  }

  @Test
  fun getFromNetworkPutInCache() = runBlocking {
    assertNull(cache.read(testFixtures.alphaSha256))
    assertNull(cache.read(testFixtures.bravoSha256))

    httpClient.filePathToByteString = mapOf(
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString,
    )

    zipline = loader.loadOrFail("test", testFixtures.manifest)

    assertEquals(
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin(),
      zipline.quickJs.evaluate("globalThis.log", "assert.js")
    )

    val ziplineFileFromCache = cache.getOrPut("app1", testFixtures.alphaSha256) {
      "fake".encodeUtf8()
    }
    assertEquals(testFixtures.alphaByteString, ziplineFileFromCache)
  }
}
