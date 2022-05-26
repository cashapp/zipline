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
import app.cash.zipline.loader.fetcher.Fetcher
import app.cash.zipline.loader.fetcher.fetch
import app.cash.zipline.loader.testing.LoaderTestFixtures
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.junit.After
import org.junit.Before
import org.junit.Test

class FetcherTest {
  private var concurrentDownloadsSemaphore = Semaphore(3)

  private lateinit var quickJs: QuickJs
  private lateinit var testFixtures: LoaderTestFixtures

  private var alphaFetcherIds: MutableList<String> = mutableListOf()
  private var bravoFetcherIds: MutableList<String> = mutableListOf()
  private lateinit var bravoByteString: ByteString

  private val fetcherAlpha = object : Fetcher {
    override suspend fun fetch(
      applicationName: String,
      id: String,
      sha256: ByteString,
      url: String,
    ): ByteString? {
      alphaFetcherIds.add(id)
      return null
    }

    override suspend fun fetchManifest(
      applicationName: String,
      id: String,
      url: String?,
    ): ZiplineManifest? {
      error("unexpected call")
    }

    override suspend fun pin(applicationName: String, manifest: ZiplineManifest) {
      error("unexpected call")
    }

    override suspend fun unpin(applicationName: String, manifest: ZiplineManifest) {
      error("unexpected call")
    }
  }

  private val fetcherBravo = object : Fetcher {
    override suspend fun fetch(
      applicationName: String,
      id: String,
      sha256: ByteString,
      url: String,
    ): ByteString? {
      bravoFetcherIds.add(id)
      return bravoByteString
    }

    override suspend fun fetchManifest(
      applicationName: String,
      id: String,
      url: String?,
    ): ZiplineManifest? {
      error("unexpected call")
    }

    override suspend fun pin(applicationName: String, manifest: ZiplineManifest) {
      error("unexpected call")
    }

    override suspend fun unpin(applicationName: String, manifest: ZiplineManifest) {
      error("unexpected call")
    }
  }

  private var alphaReceiverIds: MutableList<String> = mutableListOf()

  @Before
  fun setUp() {
    alphaFetcherIds.clear()
    bravoFetcherIds.clear()
    alphaReceiverIds.clear()
    quickJs = QuickJs.create()
    testFixtures = LoaderTestFixtures(quickJs)
    bravoByteString = "test".encodeUtf8()
  }

  @After
  fun tearDown() {
    quickJs.close()
  }

  @Test
  fun fetcherRunsInOrder() = runBlocking {
    val fetchers = listOf(fetcherAlpha, fetcherBravo)
    val actualByteString = fetchers.fetch(
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      applicationName = "foxtrot",
      id = "alpha",
      sha256 = "alpha".encodeUtf8().sha256(),
      url = "alpha",
    )
    fetchers.fetch(
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      applicationName = "foxtrot",
      id = "bravo",
      sha256 = "bravo".encodeUtf8().sha256(),
      url = "bravo",
    )
    assertEquals(bravoByteString, actualByteString)

    // Both fetchers have been called, which means that alpha ran first and returned null
    // Receiver ran and alpha ran before bravo
    assertEquals("alpha", alphaFetcherIds.first())
    assertEquals("bravo", alphaFetcherIds.last())

    assertEquals("alpha", bravoFetcherIds.first())
    assertEquals("bravo", bravoFetcherIds.last())
  }
}
