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
import app.cash.zipline.loader.ZiplineLoader.Companion.PREBUILT_MANIFEST_FILE_NAME
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
class ZiplineLoaderTest {
  private val httpClient = FakeZiplineHttpClient()
  private val dispatcher = TestCoroutineDispatcher()
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val cacheSize = 1024 * 1024
  private var nowMillis = 1_000L

  private lateinit var loader: ZiplineLoader

  private val cacheFileSystem = FakeFileSystem()
  private val embeddedDirectory = "/zipline".toPath()
  private val embeddedFileSystem = FakeFileSystem()
  private val quickJs = QuickJs.create()

  @BeforeTest
  fun setUp() {
    Database.Schema.create(driver)
    loader = ZiplineLoader(
      dispatcher = dispatcher,
      httpClient = httpClient,
      embeddedDirectory = embeddedDirectory,
      embeddedFileSystem = embeddedFileSystem,
      cacheDirectory = "/zipline/cache".toPath(),
      cacheFileSystem = cacheFileSystem,
      cacheDbDriver = driver,
      cacheMaxSizeInBytes = cacheSize,
      nowMs = { nowMillis },
    )
  }

  @AfterTest
  fun tearDown() {
    quickJs.close()
    driver.close()
  }

  @Test
  fun happyPath(): Unit = runBlocking(dispatcher) {
    httpClient.filePathToByteString = mapOf(
      alphaFilePath to alphaBytecode(quickJs),
      bravoFilePath to bravoBytecode(quickJs)
    )
    val zipline = Zipline.create(dispatcher)
    loader.load(zipline, manifest(quickJs))
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
      manifestPath to Json.encodeToString(manifest(quickJs)).encodeUtf8(),
      alphaFilePath to alphaBytecode(quickJs),
      bravoFilePath to bravoBytecode(quickJs)
    )
    val zipline = Zipline.create(dispatcher)
    loader.load(zipline, manifestPath)
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
      manifestPath to Json.encodeToString(manifest(quickJs)).encodeUtf8(),
      alphaFilePath to alphaBytecode(quickJs),
      bravoFilePath to bravoBytecode(quickJs)
    )
    val ziplineColdCache = Zipline.create(dispatcher)
    loader.load(ziplineColdCache, manifestPath)
    assertEquals(
      ziplineColdCache.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )

    // load, cache hit, no download
    httpClient.filePathToByteString = mapOf(
      manifestPath to Json.encodeToString(manifest(quickJs)).encodeUtf8(),
      // Note no actual alpha/bravo files are available on the network
    )
    val ziplineWarmedCache = Zipline.create(dispatcher)
    loader.load(ziplineWarmedCache, manifestPath)
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
    embeddedFileSystem.createDirectories(embeddedDirectory)
    embeddedFileSystem.write(embeddedDirectory / alphaBytecode(quickJs).sha256().hex()) {
      write(alphaBytecode(quickJs))
    }
    embeddedFileSystem.write(embeddedDirectory / bravoBytecode(quickJs).sha256().hex()) {
      write(bravoBytecode(quickJs))
    }

    // load, resources hit, no download via cache
    httpClient.filePathToByteString = mapOf(
      manifestPath to Json.encodeToString(manifest(quickJs)).encodeUtf8(),
      // Note no actual alpha/bravo files are available on the cache / network
    )
    val ziplineWarmedCache = Zipline.create(dispatcher)
    loader.load(ziplineWarmedCache, manifestPath)
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
    embeddedFileSystem.createDirectories(embeddedDirectory)
    embeddedFileSystem.write(embeddedDirectory / PREBUILT_MANIFEST_FILE_NAME) {
      write(Json.encodeToString(manifest(quickJs)).encodeUtf8())
    }
    embeddedFileSystem.write(embeddedDirectory / alphaBytecode(quickJs).sha256().hex()) {
      write(alphaBytecode(quickJs))
    }
    embeddedFileSystem.write(embeddedDirectory / bravoBytecode(quickJs).sha256().hex()) {
      write(bravoBytecode(quickJs))
    }

    // load, resources hit, no download via cache
    httpClient.filePathToByteString = mapOf(
      // Note no actual manifest/alpha/bravo files are available on the cache / network
    )
    val ziplineWarmedCache = Zipline.create(dispatcher)
    loader.load(ziplineWarmedCache, manifestPath)
    assertEquals(
      ziplineWarmedCache.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
  }

  @Test
  fun downloadToDirectoryThenLoadFromAsResources(): Unit = runBlocking(dispatcher) {
    val downloadDir = "/downloads/latest".toPath()
    val fileSystem = cacheFileSystem

    assertFalse(fileSystem.exists(downloadDir / PREBUILT_MANIFEST_FILE_NAME))
    assertFalse(fileSystem.exists(downloadDir / alphaBytecode(quickJs).sha256().hex()))
    assertFalse(fileSystem.exists(downloadDir / bravoBytecode(quickJs).sha256().hex()))

    httpClient.filePathToByteString = mapOf(
      manifestPath to Json.encodeToString(manifest(quickJs)).encodeUtf8(),
      alphaFilePath to alphaBytecode(quickJs),
      bravoFilePath to bravoBytecode(quickJs)
    )
    loader.download(manifestPath, downloadDir)

    // check that files have been downloaded to downloadDir as expected
    assertTrue(fileSystem.exists(downloadDir / PREBUILT_MANIFEST_FILE_NAME))
    assertEquals(Json.encodeToString(manifest(quickJs)).encodeUtf8(), fileSystem.read(downloadDir / PREBUILT_MANIFEST_FILE_NAME) { readByteString() })
    assertTrue(fileSystem.exists(downloadDir / alphaBytecode(quickJs).sha256().hex()))
    assertEquals(alphaBytecode(quickJs), fileSystem.read(downloadDir / alphaBytecode(quickJs).sha256().hex()) { readByteString() })
    assertTrue(fileSystem.exists(downloadDir / bravoBytecode(quickJs).sha256().hex()))
    assertEquals(bravoBytecode(quickJs), fileSystem.read(downloadDir / bravoBytecode(quickJs).sha256().hex()) { readByteString() })
  }
}
