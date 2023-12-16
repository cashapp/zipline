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

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
class LoadOrFallbackTest {
  private val tester = LoaderTester()

  @BeforeTest
  fun setUp() {
    tester.beforeTest()
  }

  @AfterTest
  fun tearDown() {
    tester.afterTest()
  }

  @Test
  fun preferNetworkWhenThatWorks() = runBlocking {
    assertEquals("apple", tester.success("red", "apple", DefaultFreshnessCheckerNotFresh))
  }

  @Test
  fun fallBackToPreviousNetworkWhenSomethingFails() = runBlocking {
    assertEquals("apple", tester.success("red", "apple", DefaultFreshnessCheckerNotFresh))
    assertEquals("apple", tester.failureManifestFetchFails("red"))
  }

  @Test
  fun fallBackBecauseManifestFetchFails() = runBlocking {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureManifestFetchFails("red"))
  }

  @Test
  fun fallBackBecauseCodeFetchFails() = runBlocking {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureCodeFetchFails("red"))
  }

  @Test
  fun fallBackBecauseCodeLoadFails() = runBlocking {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureCodeLoadFails("red"))
  }

  @Test
  fun fallBackBecauseCodeRunFails() = runBlocking {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureCodeRunFails("red"))
  }

  @Test
  fun successfulNetworkUpdatesFallback() = runBlocking {
    assertEquals("apple", tester.success("red", "apple", DefaultFreshnessCheckerNotFresh))
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("firetruck", tester.success("red", "firetruck", DefaultFreshnessCheckerNotFresh))
    assertEquals("firetruck", tester.failureManifestFetchFails("red"))
  }

  @Test
  fun eachApplicationHasItsOwnLastWorkingNetwork() = runBlocking {
    assertEquals("apple", tester.success("red", "apple", DefaultFreshnessCheckerNotFresh))
    assertEquals("sky", tester.success("blue", "sky", DefaultFreshnessCheckerNotFresh))
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("sky", tester.failureManifestFetchFails("blue"))
  }

  @Test
  fun eachApplicationHasItsOwnEmbedded() = runBlocking {
    tester.seedEmbedded("red", "apple")
    tester.seedEmbedded("blue", "sky")
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("sky", tester.failureManifestFetchFails("blue"))
  }

  @Test
  fun anyLastWorkingNetworkNotPruned() = runBlocking {
    assertEquals("apple", tester.success("red", "apple", DefaultFreshnessCheckerNotFresh))
    assertEquals("sky", tester.success("blue", "sky", DefaultFreshnessCheckerNotFresh))
    assertEquals(0, tester.prune())
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("sky", tester.failureManifestFetchFails("blue"))
  }

  @Test
  fun successfulNetworkMakesPreviousNetworkPrunable() = runBlocking {
    assertEquals("apple", tester.success("red", "apple", DefaultFreshnessCheckerNotFresh))
    assertEquals(
      2,
      tester.countFiles {
        assertEquals("firetruck", tester.success("red", "firetruck", DefaultFreshnessCheckerNotFresh))
      },
    )
  }

  @Test
  fun loadFailureFromNetworkIsNotPrunable() = runBlocking {
    assertEquals("apple", tester.success("red", "apple", DefaultFreshnessCheckerNotFresh))
    //assertEquals("apple", tester.success("red", "apple", DefaultFreshnessCheckerNotFresh))
    assertEquals(
      0,
      tester.countFiles {
        assertEquals("apple", tester.failureCodeLoadFails("red"))
      },
    )
    assertEquals(0, tester.prune())
  }

  @Test
  fun manifestContainsUnknownField() = runBlocking {
    tester.includeUnknownFieldInJson = true
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureManifestFetchFails("red"))
    assertEquals("firetruck", tester.failureCodeRunFails("red"))
    assertEquals("firetruck", tester.failureCodeLoadFails("red"))
    assertEquals("firetruck", tester.success("red", "firetruck", DefaultFreshnessCheckerNotFresh))
    assertEquals("firetruck", tester.failureCodeFetchFails("red"))
  }

  @Test
  fun fallBackBecauseManifestIsTooLarge() = runBlocking {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureManifestTooLarge("red"))
  }

  @Test
  fun fallBackBecauseManifestIsMalformedJson() = runBlocking {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureManifestMalformedJson("red"))
  }

  @Test
  fun freshAtMillisForSubsequentNetworkCalls() = runBlocking {
    tester.nowMillis = 1_000L
    val load1 = tester.load(applicationName = "red", seed = "apple", freshnessChecker = DefaultFreshnessCheckerNotFresh).single() as LoadResult.Success
    assertEquals(1_000L, load1.freshAtEpochMs)
    assertNull(load1.manifest.freshAtEpochMs) // Network manifests don't track this.

    tester.nowMillis = 2_000L
    val load2 = tester.load(applicationName = "red", seed = "apple", freshnessChecker = DefaultFreshnessCheckerNotFresh).single() as LoadResult.Success
    assertEquals(2_000L, load2.freshAtEpochMs)
    assertNull(load2.manifest.freshAtEpochMs) // Network manifests don't track this.
  }
}
