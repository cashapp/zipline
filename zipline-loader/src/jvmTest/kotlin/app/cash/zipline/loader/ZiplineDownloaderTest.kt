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
import app.cash.zipline.loader.blajhTestFixturesJvm.Companion.alphaFilePath
import app.cash.zipline.loader.blajhTestFixturesJvm.Companion.bravoFilePath
import app.cash.zipline.loader.blajhTestFixturesJvm.Companion.manifestPath
import app.cash.zipline.loader.ZiplineDownloader.Companion.PREBUILT_MANIFEST_FILE_NAME
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
class ZiplineDownloaderTest {
  private val dispatcher = TestCoroutineDispatcher()
  private val httpClient = FakeZiplineHttpClient()
  private val fileSystem = FakeFileSystem()
  private val downloadDir = "/zipline/download".toPath()
  private lateinit var quickJs: QuickJs
  private lateinit var testFixturesJvm: blajhTestFixturesJvm
  private lateinit var downloader: ZiplineDownloader

  private fun alphaBytecode(quickJs: QuickJs) = testFixturesJvm.alphaByteString
  private fun bravoBytecode(quickJs: QuickJs) = testFixturesJvm.bravoByteString
  private fun manifest(quickJs: QuickJs) = testFixturesJvm.manifest

  @Before
  fun setUp() {
    quickJs = QuickJs.create()
    testFixturesJvm = blajhTestFixturesJvm(quickJs)
    downloader = ZiplineDownloader(
      dispatcher = dispatcher,
      httpClient = httpClient,
      downloadDir = downloadDir,
      downloadFileSystem = fileSystem,
    )
  }

  @After
  fun tearDown() {
    quickJs.close()
  }

  @Test
  fun downloadToDirectory(): Unit = runBlocking(dispatcher) {
    assertFalse(fileSystem.exists(downloadDir / PREBUILT_MANIFEST_FILE_NAME))
    assertFalse(fileSystem.exists(downloadDir / testFixturesJvm.alphaSha256Hex))
    assertFalse(fileSystem.exists(downloadDir / testFixturesJvm.bravoSha256Hex))

    httpClient.filePathToByteString = mapOf(
      manifestPath to testFixturesJvm.manifestByteString,
      alphaFilePath to testFixturesJvm.alphaByteString,
      bravoFilePath to testFixturesJvm.bravoByteString
    )
    downloader.download(manifestPath)

    // check that files have been downloaded to downloadDir as expected
    assertTrue(fileSystem.exists(downloadDir / PREBUILT_MANIFEST_FILE_NAME))
    assertEquals(
      testFixturesJvm.manifestByteString,
      fileSystem.read(downloadDir / PREBUILT_MANIFEST_FILE_NAME) { readByteString() })
    assertTrue(fileSystem.exists(downloadDir / testFixturesJvm.alphaSha256Hex))
    assertEquals(
      testFixturesJvm.alphaByteString,
      fileSystem.read(downloadDir / testFixturesJvm.alphaSha256Hex) { readByteString() })
    assertTrue(fileSystem.exists(downloadDir / testFixturesJvm.bravoSha256Hex))
    assertEquals(
      testFixturesJvm.bravoByteString,
      fileSystem.read(downloadDir / testFixturesJvm.bravoSha256Hex) { readByteString() })
  }
}
