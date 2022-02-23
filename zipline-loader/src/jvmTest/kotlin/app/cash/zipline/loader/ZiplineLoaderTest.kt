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
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.FileSystem
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

  private lateinit var fileSystem: FileSystem
  private lateinit var resourceFileSystem: FileSystem
  private val resourceDirectory = "/zipline".toPath()
  private lateinit var quickJs: QuickJs

  @BeforeTest
  fun setUp() {
    quickJs = QuickJs.create()
    fileSystem = FakeFileSystem()
    resourceFileSystem = FakeFileSystem()
    loader = ZiplineLoader(
      dispatcher = dispatcher,
      httpClient = httpClient,
      fileSystem = fileSystem,
      resourceFileSystem = resourceFileSystem,
      resourceDirectory = resourceDirectory,
      cacheDbDriver = driver,
      cacheDirectory = "/zipline/cache".toPath(),
      nowMs = { nowMillis },
      cacheMaxSizeInBytes = cacheSize
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

  // TODO check resources for manifest too, not only network
  @Test
  fun loaderUsesResourcesBeforeCache(): Unit = runBlocking(dispatcher) {
    // seed the resources FS with zipline files
    resourceFileSystem.createDirectories(resourceDirectory)
    resourceFileSystem.write(resourceDirectory / alphaBytecode(quickJs).sha256()) {
      write(alphaBytecode(quickJs))
    }
    resourceFileSystem.write(resourceDirectory / bravoBytecode(quickJs).sha256()) {
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

  companion object {
    private val alphaJs = """
      |globalThis.log = globalThis.log || "";
      |globalThis.log += "alpha loaded\n"
      |""".trimMargin()

    private fun alphaBytecode(quickJs: QuickJs) = ziplineFile(quickJs, alphaJs, "alpha.js")
    private const val alphaFilePath = "/alpha.zipline"

    private val bravoJs = """
      |globalThis.log = globalThis.log || "";
      |globalThis.log += "bravo loaded\n"
      |""".trimMargin()

    private fun bravoBytecode(quickJs: QuickJs) = ziplineFile(quickJs, bravoJs, "bravo.js")
    private const val bravoFilePath = "/bravo.zipline"

    private const val manifestPath = "/manifest.zipline.json"
    private fun manifest(quickJs: QuickJs) = ZiplineManifest.create(
      modules = mapOf(
        "bravo" to ZiplineModule(
          url = bravoFilePath,
          sha256 = bravoBytecode(quickJs).sha256(),
          dependsOnIds = listOf("alpha"),
        ),
        "alpha" to ZiplineModule(
          url = alphaFilePath,
          sha256 = alphaBytecode(quickJs).sha256(),
          dependsOnIds = listOf(),
        ),
      )
    )

    private fun ziplineFile(quickJs: QuickJs, javaScript: String, fileName: String): ByteString {
      val ziplineFile = ZiplineFile(
        CURRENT_ZIPLINE_VERSION,
        quickJs.compile(javaScript, fileName).toByteString()
      )

      val buffer = Buffer()
      ziplineFile.writeTo(buffer)
      return buffer.readByteString()
    }
  }
}
