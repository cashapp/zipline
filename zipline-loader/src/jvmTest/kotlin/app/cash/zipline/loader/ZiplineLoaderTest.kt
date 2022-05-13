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
import app.cash.zipline.loader.ZiplineLoader.Companion.APPLICATION_MANIFEST_FILE_NAME_SUFFIX
import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.alphaUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.bravoUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createDownloadZiplineLoader
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createProductionZiplineLoader
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.manifestUrl
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
class ZiplineLoaderTest {
  private val httpClient = FakeZiplineHttpClient()
  private val dispatcher = TestCoroutineDispatcher()
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val cacheSize = 1024 * 1024
  private var nowMillis = 1_000L

  private val cacheFileSystem = FakeFileSystem()
  private val embeddedDir = "/zipline".toPath()
  private val embeddedFileSystem = FakeFileSystem()
  private lateinit var quickJs: QuickJs
  private lateinit var testFixtures: LoaderTestFixtures
  private lateinit var loader: ZiplineLoader

  @Before
  fun setUp() {
    Database.Schema.create(driver)
    quickJs = QuickJs.create()
    testFixtures = LoaderTestFixtures(quickJs)
    loader = createProductionZiplineLoader(
      dispatcher = dispatcher,,,
      httpClient = httpClient,
      embeddedDir = embeddedDir,
      embeddedFileSystem = embeddedFileSystem,
      cacheDbDriver = driver,
      cacheDir = "/zipline/cache".toPath(),
      cacheFileSystem = cacheFileSystem,
      cacheMaxSizeInBytes = cacheSize
    ) { nowMillis }
  }

  @After
  fun tearDown() {
    quickJs.close()
    driver.close()
  }

  @Test
  fun happyPath(): Unit = runBlocking(dispatcher) {
    httpClient.filePathToByteString = mapOf(
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    val zipline = Zipline.create(dispatcher)
    loader.load(zipline, testFixtures.manifest)
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
  }

  @Test
  fun loadManifestFromUrl(): Unit = runBlocking(dispatcher) {
    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestWithRelativeUrlsByteString,
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    val zipline = Zipline.create(dispatcher)
    loader.load(zipline, manifestUrl)
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
  }

  @Test
  fun loaderUsesCache(): Unit = runBlocking(dispatcher) {
    // load, no cache hit, download
    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestWithRelativeUrlsByteString,
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    val ziplineColdCache = Zipline.create(dispatcher)
    loader.load(ziplineColdCache, manifestUrl)
    assertEquals(
      ziplineColdCache.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )

    // load, cache hit, no download
    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestWithRelativeUrlsByteString,
      // Note no actual alpha/bravo files are available on the network
    )
    val ziplineWarmedCache = Zipline.create(dispatcher)
    loader.load(ziplineWarmedCache, manifestUrl)
    assertEquals(
      ziplineWarmedCache.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
  }

  @Test
  fun loaderUsesResourcesBeforeCacheButManifestOverNetwork(): Unit = runBlocking(dispatcher) {
    // seed the resources FS with zipline files
    embeddedFileSystem.createDirectories(embeddedDir)
    embeddedFileSystem.write(embeddedDir / testFixtures.alphaSha256Hex) {
      write(testFixtures.alphaByteString)
    }
    embeddedFileSystem.write(embeddedDir / testFixtures.bravoSha256Hex) {
      write(testFixtures.bravoByteString)
    }

    // load, resources hit, no download via cache
    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestByteString,
      // Note no actual alpha/bravo files are available on the cache / network
    )
    val ziplineWarmedCache = Zipline.create(dispatcher)
    loader.load(ziplineWarmedCache, manifestUrl)
    assertEquals(
      ziplineWarmedCache.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
  }

  @Test
  fun loaderUsesResourcesForPrebuiltManifestWhenNetworkOffline(): Unit = runBlocking(dispatcher) {
    // seed the embedded FS with zipline manifest and files
    embeddedFileSystem.createDirectories(embeddedDir)
    embeddedFileSystem.write(embeddedDir / APPLICATION_MANIFEST_FILE_NAME_SUFFIX) {
      write(testFixtures.manifestByteString)
    }
    embeddedFileSystem.write(embeddedDir / testFixtures.alphaSha256Hex) {
      write(testFixtures.alphaByteString)
    }
    embeddedFileSystem.write(embeddedDir / testFixtures.bravoSha256Hex) {
      write(testFixtures.bravoByteString)
    }

    // load, resources hit, no download via cache
    httpClient.filePathToByteString = mapOf(
      // Note no actual manifest/alpha/bravo files are available on the cache / network
    )
    val ziplineWarmedCache = Zipline.create(dispatcher)
    loader.load(ziplineWarmedCache, manifestUrl)
    assertEquals(
      ziplineWarmedCache.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
  }

  @Test
  fun downloadToDirectoryThenLoadFromAsEmbedded(): Unit = runBlocking(dispatcher) {
    val downloadDir = "/downloads/latest".toPath()
    val downloadFileSystem = cacheFileSystem
    loader = createDownloadZiplineLoader(
      dispatcher = dispatcher,,,
      httpClient = httpClient,
    )

    assertFalse(downloadFileSystem.exists(downloadDir / APPLICATION_MANIFEST_FILE_NAME_SUFFIX))
    assertFalse(downloadFileSystem.exists(downloadDir / testFixtures.alphaSha256Hex))
    assertFalse(downloadFileSystem.exists(downloadDir / testFixtures.bravoSha256Hex))

    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestWithRelativeUrlsByteString,
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    loader.download(downloadDir, downloadFileSystem, manifestUrl)

    assertEquals(
      testFixtures.manifestByteString,
      downloadFileSystem.read(downloadDir / APPLICATION_MANIFEST_FILE_NAME_SUFFIX) { readByteString() })
    assertTrue(downloadFileSystem.exists(downloadDir / testFixtures.alphaSha256Hex))
    assertEquals(
      testFixtures.alphaByteString,
      downloadFileSystem.read(downloadDir / testFixtures.alphaSha256Hex) { readByteString() })
    assertTrue(downloadFileSystem.exists(downloadDir / testFixtures.bravoSha256Hex))
    assertEquals(
      testFixtures.bravoByteString,
      downloadFileSystem.read(downloadDir / testFixtures.bravoSha256Hex) { readByteString() })

    // Load into Zipline
    val zipline = Zipline.create(dispatcher)
    loader = createProductionZiplineLoader(
      dispatcher = dispatcher,,,
      httpClient = httpClient,
      embeddedDir = downloadDir,
      embeddedFileSystem = downloadFileSystem,
      cacheDbDriver = driver,
      cacheDir = "/zipline/cache".toPath(),
      cacheFileSystem = cacheFileSystem,
      cacheMaxSizeInBytes = cacheSize
    ) { nowMillis }
    loader.load(zipline, testFixtures.manifest)
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
  }

  @Test
  fun downloadToDirectory(): Unit = runBlocking(dispatcher) {
    val fileSystem = FakeFileSystem()
    val downloadDir = "/zipline/download".toPath()

    assertFalse(fileSystem.exists(downloadDir / APPLICATION_MANIFEST_FILE_NAME_SUFFIX))
    assertFalse(fileSystem.exists(downloadDir / testFixtures.alphaSha256Hex))
    assertFalse(fileSystem.exists(downloadDir / testFixtures.bravoSha256Hex))

    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestByteString,
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    loader = createDownloadZiplineLoader(dispatcher,,, httpClient)
    loader.download(downloadDir, fileSystem, testFixtures.manifest)

    // check that files have been downloaded to downloadDir as expected
    assertTrue(fileSystem.exists(downloadDir / APPLICATION_MANIFEST_FILE_NAME_SUFFIX))
    assertEquals(
      testFixtures.manifestByteString,
      fileSystem.read(downloadDir / APPLICATION_MANIFEST_FILE_NAME_SUFFIX) { readByteString() })
    assertTrue(fileSystem.exists(downloadDir / testFixtures.alphaSha256Hex))
    assertEquals(
      testFixtures.alphaByteString,
      fileSystem.read(downloadDir / testFixtures.alphaSha256Hex) { readByteString() })
    assertTrue(fileSystem.exists(downloadDir / testFixtures.bravoSha256Hex))
    assertEquals(
      testFixtures.bravoByteString,
      fileSystem.read(downloadDir / testFixtures.bravoSha256Hex) { readByteString() })
  }
}
