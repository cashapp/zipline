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

import app.cash.turbine.test
import app.cash.zipline.QuickJs
import app.cash.zipline.loader.ZiplineLoader.Companion.getApplicationManifestFileName
import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.alphaUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.bravoUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createDownloadZiplineLoader
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createJs
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createProductionZiplineLoader
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createRelativeManifest
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.manifestUrl
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalTime::class)
@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
class ZiplineLoaderTest {
  private val httpClient = FakeZiplineHttpClient()
  private val dispatcher = TestCoroutineDispatcher()
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val cacheSize = 1024L * 1024L
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
      dispatcher = dispatcher,
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
  fun happyPath() = runBlocking {
    httpClient.filePathToByteString = mapOf(
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    val zipline = loader.loadOrFail("test", testFixtures.manifest)
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
    zipline.close()
  }

  @Test
  fun loadManifestFromUrl() = runBlocking {
    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestWithRelativeUrlsByteString,
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    val zipline = loader.loadOrFail("test", manifestUrl)
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
    zipline.close()
  }

  @Test
  fun loaderUsesCache() = runBlocking {
    // load, no cache hit, download
    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestWithRelativeUrlsByteString,
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    val ziplineColdCache = loader.loadOrFail("test", manifestUrl)
    assertEquals(
      ziplineColdCache.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
    ziplineColdCache.close()

    // load, cache hit, no download
    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestWithRelativeUrlsByteString,
      // Note no actual alpha/bravo files are available on the network
    )
    val ziplineWarmedCache = loader.loadOrFail("test", manifestUrl)
    assertEquals(
      ziplineWarmedCache.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
    ziplineWarmedCache.close()
  }

  @Test
  fun loaderUsesResourcesBeforeCacheButManifestOverNetwork() = runBlocking {
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
    val zipline = loader.loadOrFail("test", manifestUrl)
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
    zipline.close()
  }

  @Test
  fun downloadToDirectoryThenLoadFromAsEmbedded() = runBlocking {
    val downloadDir = "/downloads/latest".toPath()
    val downloadFileSystem = cacheFileSystem
    loader = createDownloadZiplineLoader(
      dispatcher = dispatcher,
      httpClient = httpClient,
    )

    assertFalse(downloadFileSystem.exists(downloadDir / getApplicationManifestFileName("test")))
    assertFalse(downloadFileSystem.exists(downloadDir / testFixtures.alphaSha256Hex))
    assertFalse(downloadFileSystem.exists(downloadDir / testFixtures.bravoSha256Hex))

    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestWithRelativeUrlsByteString,
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    loader.download("test", downloadDir, downloadFileSystem, manifestUrl)

    assertEquals(
      testFixtures.manifestByteString,
      downloadFileSystem.read(downloadDir / getApplicationManifestFileName("test")) {
        readByteString()
      })
    assertTrue(downloadFileSystem.exists(downloadDir / testFixtures.alphaSha256Hex))
    assertEquals(
      testFixtures.alphaByteString,
      downloadFileSystem.read(downloadDir / testFixtures.alphaSha256Hex) { readByteString() })
    assertTrue(downloadFileSystem.exists(downloadDir / testFixtures.bravoSha256Hex))
    assertEquals(
      testFixtures.bravoByteString,
      downloadFileSystem.read(downloadDir / testFixtures.bravoSha256Hex) { readByteString() })

    // Load into Zipline.
    loader = createProductionZiplineLoader(
      dispatcher = dispatcher,
      httpClient = httpClient,
      embeddedDir = downloadDir,
      embeddedFileSystem = downloadFileSystem,
      cacheDbDriver = driver,
      cacheDir = "/zipline/cache".toPath(),
      cacheFileSystem = cacheFileSystem,
      cacheMaxSizeInBytes = cacheSize
    ) { nowMillis }
    val zipline = loader.loadOrFail("test", testFixtures.manifest)
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
    zipline.close()
  }

  @Test
  fun downloadToDirectory() = runBlocking {
    val fileSystem = FakeFileSystem()
    val downloadDir = "/zipline/download".toPath()

    assertFalse(fileSystem.exists(downloadDir / getApplicationManifestFileName("test")))
    assertFalse(fileSystem.exists(downloadDir / testFixtures.alphaSha256Hex))
    assertFalse(fileSystem.exists(downloadDir / testFixtures.bravoSha256Hex))

    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestByteString,
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    loader = createDownloadZiplineLoader(dispatcher, httpClient)
    loader.download("test", downloadDir, fileSystem, testFixtures.manifest)

    // check that files have been downloaded to downloadDir as expected
    assertTrue(fileSystem.exists(downloadDir / getApplicationManifestFileName("test")))
    assertEquals(
      testFixtures.manifestByteString,
      fileSystem.read(downloadDir / getApplicationManifestFileName("test")) { readByteString() })
    assertTrue(fileSystem.exists(downloadDir / testFixtures.alphaSha256Hex))
    assertEquals(
      testFixtures.alphaByteString,
      fileSystem.read(downloadDir / testFixtures.alphaSha256Hex) { readByteString() })
    assertTrue(fileSystem.exists(downloadDir / testFixtures.bravoSha256Hex))
    assertEquals(
      testFixtures.bravoByteString,
      fileSystem.read(downloadDir / testFixtures.bravoSha256Hex) { readByteString() })
  }

  @Test
  fun loadContinuously() = runBlocking {
    val baseUrl = "https://example.com/files"
    val applicationName = "red"
    val appleZiplineFileByteString = testFixtures.createZiplineFile(createJs("apple"), "apple.js")
    val appleManifest = createRelativeManifest("apple", appleZiplineFileByteString.sha256())
    val appleManifestUrl = "$baseUrl/apple/${getApplicationManifestFileName(applicationName)}"

    val firetruckZiplineFileByteString =
      testFixtures.createZiplineFile(createJs("firetruck"), "firetruck.js")
    val firetruckManifest =
      createRelativeManifest("firetruck", firetruckZiplineFileByteString.sha256())
    val firetruckManifestUrl =
      "$baseUrl/firetruck/${getApplicationManifestFileName(applicationName)}"

    httpClient.filePathToByteString = mapOf(
      appleManifestUrl to Json.encodeToString(ZiplineManifest.serializer(), appleManifest)
        .encodeUtf8(),
      "$baseUrl/apple/apple.zipline" to appleZiplineFileByteString,
      firetruckManifestUrl to Json.encodeToString(ZiplineManifest.serializer(), firetruckManifest)
        .encodeUtf8(),
      "$baseUrl/firetruck/firetruck.zipline" to firetruckZiplineFileByteString,
    )

    val manifestUrlFlow = flowOf(appleManifestUrl, firetruckManifestUrl)
    loader.loadContinuously(
      applicationName = "red",
      manifestUrlFlow = manifestUrlFlow,
      pollingInterval = 1000.toDuration(DurationUnit.MILLISECONDS),
    ) {}.test {
      assertEquals("apple", (awaitItem().quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(" loaded\n"))
      assertEquals("firetruck", (awaitItem().quickJs.evaluate("globalThis.log", "assert.js") as String).removeSuffix(" loaded\n"))
      cancel()
    }
  }
}
