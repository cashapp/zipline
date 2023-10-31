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
package app.cash.zipline.loader.internal.fetcher

import app.cash.zipline.EventListener
import app.cash.zipline.loader.testing.LoaderTestFixtures
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

class FetcherTest {
  private var concurrentDownloadsSemaphore = Semaphore(3)

  private lateinit var testFixtures: LoaderTestFixtures

  private var alphaFetcherIds = mutableListOf<String>()
  private var bravoFetcherIds = mutableListOf<String>()
  private lateinit var bravoByteString: ByteString

  private val fetcherAlpha = object : Fetcher {
    override suspend fun fetch(
      applicationName: String,
      eventListener: EventListener,
      id: String,
      sha256: ByteString,
      nowEpochMs: Long,
      baseUrl: String?,
      url: String,
    ): ByteString? {
      alphaFetcherIds.add(id)
      return null
    }
  }

  private val fetcherBravo = object : Fetcher {
    override suspend fun fetch(
      applicationName: String,
      eventListener: EventListener,
      id: String,
      sha256: ByteString,
      nowEpochMs: Long,
      baseUrl: String?,
      url: String,
    ): ByteString? {
      bravoFetcherIds.add(id)
      return bravoByteString
    }
  }

  @BeforeTest
  fun setUp() {
    testFixtures = LoaderTestFixtures()
    bravoByteString = "test".encodeUtf8()
  }

  @Test
  fun fetcherRunsInOrder() = runBlocking {
    val fetchers = listOf(fetcherAlpha, fetcherBravo)
    val actualByteString = fetchers.fetch(
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      applicationName = "foxtrot",
      eventListener = EventListener.NONE,
      id = "alpha",
      sha256 = "alpha".encodeUtf8().sha256(),
      nowEpochMs = 1_000L,
      baseUrl = null,
      url = "alpha",
    )
    fetchers.fetch(
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      applicationName = "foxtrot",
      eventListener = EventListener.NONE,
      id = "bravo",
      sha256 = "bravo".encodeUtf8().sha256(),
      nowEpochMs = 1_000L,
      baseUrl = null,
      url = "bravo",
    )
    assertEquals(bravoByteString, actualByteString)

    // Both fetchers have been called, which means that alpha ran first and returned null.
    // Receiver ran and alpha ran before bravo.
    assertEquals("alpha", alphaFetcherIds.first())
    assertEquals("bravo", alphaFetcherIds.last())

    assertEquals("alpha", bravoFetcherIds.first())
    assertEquals("bravo", bravoFetcherIds.last())
  }
}
