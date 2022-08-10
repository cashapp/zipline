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

import app.cash.zipline.Zipline
import app.cash.zipline.loader.internal.cache.ZiplineCache
import app.cash.zipline.loader.internal.fetcher.LoadedManifest
import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.alphaUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.bravoUrl
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path

class ProductionFetcherReceiverTest {
  private val tester = LoaderTester()

  private lateinit var loader: ZiplineLoader
  private lateinit var cache: ZiplineCache
  private lateinit var embeddedFileSystem: FileSystem
  private lateinit var embeddedDir: Path
  private val testFixtures = LoaderTestFixtures()

  private lateinit var zipline: Zipline

  @BeforeTest
  fun setUp() {
    tester.beforeTest()
    loader = tester.loader
    cache = tester.cache
    embeddedFileSystem = tester.embeddedFileSystem
    embeddedDir = tester.embeddedDir
  }

  @AfterTest
  fun tearDown() {
    tester.afterTest()
    loader.close()
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

    tester.httpClient.filePathToByteString = mapOf()

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

    tester.httpClient.filePathToByteString = mapOf()

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

    tester.httpClient.filePathToByteString = mapOf(
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

  private suspend fun ZiplineLoader.loadOrFail(
    applicationName: String,
    manifest: ZiplineManifest,
    initializer: (Zipline) -> Unit = {},
  ): Zipline {
    return loadFromManifest(
      applicationName = applicationName,
      loadedManifest = LoadedManifest(ByteString.EMPTY, manifest, 1L),
      initializer = initializer,
    )
  }
}
