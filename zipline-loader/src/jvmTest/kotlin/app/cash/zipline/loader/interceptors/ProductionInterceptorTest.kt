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

package app.cash.zipline.loader.interceptors

import app.cash.zipline.QuickJs
import app.cash.zipline.Zipline
import app.cash.zipline.loader.FakeZiplineHttpClient
import app.cash.zipline.loader.TestFixturesJvm
import app.cash.zipline.loader.ZiplineCache
import app.cash.zipline.loader.ZiplineFile.Companion.toZiplineFile
import app.cash.zipline.loader.ZiplineModuleLoader
import app.cash.zipline.loader.createZiplineCache
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.TestCoroutineDispatcher
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test

class ProductionInterceptorTest {
  private val httpClient = FakeZiplineHttpClient()
  private val dispatcher = TestCoroutineDispatcher()
  private val cacheDbDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val cacheMaxSizeInBytes = 100 * 1024 * 1024
  private val cacheDirectory = "/zipline/cache".toPath()
  private var nowMillis = 1_000L

  private var concurrentDownloadsSemaphore = Semaphore(3)
  private val zipline = Zipline.create(dispatcher)

  private lateinit var cache: ZiplineCache

  private lateinit var fileSystem: FileSystem
  private val downloadDir = "/zipline/downloads".toPath()
  private lateinit var embeddedFileSystem: FileSystem
  private val embeddedDirectory = "/zipline".toPath()
  private lateinit var quickJs: QuickJs
  private lateinit var testFixturesJvm: TestFixturesJvm
  private lateinit var moduleLoader: ZiplineModuleLoader

  @Before
  fun setUp() {
    quickJs = QuickJs.create()
    testFixturesJvm = TestFixturesJvm(quickJs)
    fileSystem = FakeFileSystem()
    embeddedFileSystem = FakeFileSystem()
    cache = createZiplineCache(
      driver = cacheDbDriver,
      fileSystem = fileSystem,
      directory = cacheDirectory,
      maxSizeInBytes = cacheMaxSizeInBytes.toLong(),
      nowMs = { nowMillis }
    )
    moduleLoader = ZiplineModuleLoader.createDownloadOnly(
      dispatcher = dispatcher,
      httpClient = httpClient,
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      downloadDir = downloadDir,
      downloadFileSystem = fileSystem,
    )
  }

  @After
  fun tearDown() {
    quickJs.close()
    cacheDbDriver.close()
  }

  @Test
  fun getFromEmbeddedFileSystemNoNetworkCall(): Unit = runBlocking {
    embeddedFileSystem.createDirectories(embeddedDirectory)
    val alphaSha256 = testFixturesJvm.alphaSha256
    embeddedFileSystem.write(embeddedDirectory / testFixturesJvm.alphaSha256Hex) {
      write(testFixturesJvm.alphaByteString)
    }

    httpClient.filePathToByteString = mapOf()

    moduleLoader.load(manifest)

    val ziplineFile = strategy.getZiplineFile(
      id = "alpha",
      sha256 = alphaSha256,
      url = alphaFilePath
    )

    assertEquals(alphaBytecode(quickJs).toZiplineFile(), ziplineFile)
  }

  @Test
  fun getFromWarmCacheNoNetworkCall(): Unit = runBlocking {
    val alphaSha256 = alphaBytecode(quickJs).sha256()
    cache.getOrPut(alphaSha256) {
      alphaBytecode(quickJs)
    }

    httpClient.filePathToByteString = mapOf()

    val ziplineFile = strategy.getZiplineFile(
      id = "alpha",
      sha256 = alphaBytecode(quickJs).sha256(),
      url = alphaFilePath
    )

    assertEquals(alphaBytecode(quickJs).toZiplineFile(), ziplineFile)
  }

  @Test
  fun getFromNetworkPutInCache(): Unit = runBlocking {
    val alphaSha256 = alphaBytecode(quickJs).sha256()
    httpClient.filePathToByteString = mapOf(
      alphaFilePath to alphaBytecode(quickJs),
    )

    val ziplineFile = strategy.getZiplineFile(
      id = "alpha",
      sha256 = alphaBytecode(quickJs).sha256(),
      url = alphaFilePath
    )

    assertEquals(alphaBytecode(quickJs).toZiplineFile(), ziplineFile)

    val ziplineFileFromCache = cache.getOrPut(alphaSha256) {
      "fake".encodeUtf8()
    }
    assertEquals(alphaBytecode(quickJs), ziplineFileFromCache)
  }

  @Test
  fun processFileLoadsIntoZipline(): Unit = runBlocking(dispatcher) {
    val alphaByteString = alphaBytecode(quickJs)
    val ziplineFile = alphaByteString.toZiplineFile()
    strategy.processFile(ziplineFile, "alpha", alphaByteString.sha256())
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |""".trimMargin()
    )
  }
}
