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
import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.ALPHA_URL
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.BRAVO_URL
import app.cash.zipline.testing.systemFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okio.FileSystem

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadOnlyFetcherReceiverTest {
  private val testFixtures = LoaderTestFixtures()

  private val dispatcher = UnconfinedTestDispatcher()
  private val httpClient = FakeZiplineHttpClient()
  private val loader = testZiplineLoader(
    dispatcher = dispatcher,
    httpClient = httpClient,
    nowEpochMs = { 1 },
    eventListenerFactory = EventListenerNoneFactory,
  )

  private val fileSystem = systemFileSystem
  private val downloadDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "okio-${randomToken().hex()}"

  @Test
  fun getFileFromNetworkSaveToFs() = runBlocking {
    httpClient.filePathToByteString = mapOf(
      ALPHA_URL to testFixtures.alphaByteString,
      BRAVO_URL to testFixtures.bravoByteString,
    )

    loader.download(
      "test",
      EventListener.NONE,
      fileSystem,
      downloadDir,
      testFixtures.embeddedLoadedManifest,
    )

    assertTrue(fileSystem.exists(downloadDir / testFixtures.alphaSha256Hex))
    assertEquals(
      testFixtures.alphaByteString,
      fileSystem.read(downloadDir / testFixtures.alphaSha256Hex) { readByteString() },
    )
    assertTrue(fileSystem.exists(downloadDir / testFixtures.bravoSha256Hex))
    assertEquals(
      testFixtures.bravoByteString,
      fileSystem.read(downloadDir / testFixtures.bravoSha256Hex) { readByteString() },
    )
  }
}
