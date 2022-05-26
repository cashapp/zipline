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

import app.cash.zipline.QuickJs
import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.alphaUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.bravoUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createDownloadZiplineLoader
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test

class DownloadOnlyFetcherReceiverTest {
  private val httpClient = FakeZiplineHttpClient()
  private val dispatcher = TestCoroutineDispatcher()
  private val cacheDbDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private lateinit var fileSystem: FileSystem
  private val downloadDir = "/zipline/downloads".toPath()
  private lateinit var quickJs: QuickJs
  private lateinit var testFixtures: LoaderTestFixtures

  private lateinit var loader: ZiplineLoader

  @Before
  fun setUp() {
    quickJs = QuickJs.create()
    testFixtures = LoaderTestFixtures(quickJs)
    fileSystem = FakeFileSystem()
    loader = createDownloadZiplineLoader(
      dispatcher = dispatcher,
      httpClient = httpClient,
    )
  }

  @After
  fun tearDown() {
    quickJs.close()
    cacheDbDriver.close()
  }

  @Test
  fun getFileFromNetworkSaveToFs() = runBlocking {
    httpClient.filePathToByteString = mapOf(
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString,
    )

    loader.download("test", downloadDir, fileSystem, testFixtures.manifest)

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
