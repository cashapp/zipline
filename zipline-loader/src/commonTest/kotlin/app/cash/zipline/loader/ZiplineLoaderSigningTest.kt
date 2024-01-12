/*
 * Copyright (C) 2022 Block, Inc.
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

import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.ALPHA_URL
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.BRAVO_URL
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.MANIFEST_URL
import app.cash.zipline.loader.testing.SampleKeys
import app.cash.zipline.testing.LoggingEventListener
import assertk.assertThat
import assertk.assertions.isLessThan
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.encodeUtf8

/**
 * This test just confirms we actually use the [ManifestVerifier] when it is configured. It assumes
 * all the interesting edge cases in that class are tested independently.
 */
@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
class ZiplineLoaderSigningTest {
  private val eventListener = LoggingEventListener()
  private val tester = LoaderTester(
    eventListenerFactory = eventListener,
    manifestVerifier = ManifestVerifier.Builder()
      .addEd25519("key1", SampleKeys.key1Public)
      .build(),
  )

  private val testFixtures = LoaderTestFixtures()

  @BeforeTest
  fun setUp() {
    tester.beforeTest()
  }

  @AfterTest
  fun tearDown() {
    tester.afterTest()
  }

  @Test
  fun signatureVerifiesAndChecksumsMatch(): Unit = runBlocking {
    val signer = ManifestSigner.Builder()
      .addEd25519("key1", SampleKeys.key1Private)
      .build()
    val manifest = signer.sign(testFixtures.manifest)

    tester.httpClient.filePathToByteString = mapOf(
      MANIFEST_URL to manifest.encodeJson().encodeUtf8(),
      ALPHA_URL to testFixtures.alphaByteString,
      BRAVO_URL to testFixtures.bravoByteString,
    )
    val zipline = (tester.loader.loadOnce("test", DefaultFreshnessCheckerNotFresh, MANIFEST_URL) as LoadResult.Success).zipline
    zipline.close()

    val events = eventListener.takeAll()
    assertContains(events, "manifestVerified test key1")
    assertContains(events, "manifestReady test")

    // The manifestReady event should follow the manifestVerified event.
    assertThat(events.indexOf("manifestVerified test key1"))
      .isLessThan(events.indexOf("manifestReady test"))
  }

  /**
   * Note that checksum verification is essential for signing to be effective. This is because we
   * sign only the manifest and not the .zipline files it includes.
   */
  @Test
  fun checksumDoesNotMatch(): Unit = runBlocking {
    val signer = ManifestSigner.Builder()
      .addEd25519("key1", SampleKeys.key1Private)
      .build()
    val manifestWithBadChecksum = testFixtures.manifest.copy(
      modules = testFixtures.manifest.modules.mapValues { (key, value) ->
        when (key) {
          "bravo" -> value.copy(sha256 = "wrong content for SHA-256".encodeUtf8().sha256())
          else -> value
        }
      },
    )
    val manifest = signer.sign(manifestWithBadChecksum)

    tester.httpClient.filePathToByteString = mapOf(
      MANIFEST_URL to manifest.encodeJson().encodeUtf8(),
      ALPHA_URL to testFixtures.alphaByteString,
      BRAVO_URL to testFixtures.alphaByteString,
    )
    val result = tester.loader.loadOnce("test", DefaultFreshnessCheckerNotFresh, MANIFEST_URL)
    assertTrue(result is LoadResult.Failure)
    assertTrue(result.exception is IllegalStateException)
    assertEquals("checksum mismatch for bravo", result.exception.message)
    val exception = eventListener.takeException()
    assertTrue(exception is IllegalStateException)
    assertEquals("checksum mismatch for bravo", exception.message)
  }

  @Test
  fun signatureDoesNotVerify(): Unit = runBlocking {
    val signer = ManifestSigner.Builder()
      .addEd25519("key1", SampleKeys.key2Private) // Wrong bytes for this key!
      .build()
    val manifest = signer.sign(testFixtures.manifest)

    tester.httpClient.filePathToByteString = mapOf(
      MANIFEST_URL to manifest.encodeJson().encodeUtf8(),
      ALPHA_URL to testFixtures.alphaByteString,
      BRAVO_URL to testFixtures.bravoByteString,
    )
    val loadResult = tester.loader.loadOnce("test", DefaultFreshnessCheckerNotFresh, MANIFEST_URL)
    assertEquals(
      "manifest signature for key key1 did not verify!",
      (loadResult as LoadResult.Failure).exception.message,
    )
    val listenerException = eventListener.takeException()
    assertTrue(listenerException is IllegalStateException)
    assertEquals(
      "manifest signature for key key1 did not verify!",
      listenerException.message,
    )
  }
}
