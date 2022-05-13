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

import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
class LoadOrFallbackTest {
  val tester = LoadOrFallbackTester()

  @Test
  fun preferNetworkWhenThatWorks() {
    assertEquals("apple", tester.success("red", "apple"))
  }

  @Test
  fun fallBackToPreviousNetworkWhenSomethingFails() {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("apple", tester.failureManifestFetchFails("red"))
  }

  @Test
  fun fallBackBecauseManifestFetchFails() {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureManifestFetchFails("red"))
  }

  @Test
  fun fallBackBecauseCodeFetchFails() {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureCodeFetchFails("red"))
  }

  @Test
  fun fallBackBecauseCodeRunFails() {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureCodeRunFails("red"))
  }

  @Test
  fun successfulNetworkUpdatesFallback() {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("firetruck", tester.success("red", "firetruck"))
    assertEquals("firetruck", tester.failureManifestFetchFails("red"))
  }

  @Test
  fun eachApplicationHasItsOwnLastWorkingNetwork() {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("sky", tester.success("blue", "sky"))
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("sky", tester.failureManifestFetchFails("blue"))
  }

  @Test
  fun eachApplicationHasItsOwnEmbedded() {
    tester.seedEmbedded("red", "apple")
    tester.seedEmbedded("blue", "sky")
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("sky", tester.failureManifestFetchFails("blue"))
  }

  @Test
  fun anyLastWorkingNetworkNotPruned() {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("sky", tester.success("blue", "sky"))
    assertEquals(0, tester.pruneEverythingWeCanPrune())
    assertEquals("apple", tester.failureManifestFetchFails("red"))
    assertEquals("sky", tester.failureManifestFetchFails("blue"))
  }

  @Test
  fun successfulNetworkMakesPreviousNetworkPrunable() {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("firetruck", tester.success("red", "firetruck"))
    assertEquals(1, tester.pruneEverythingWeCanPrune())
  }

  @Test
  fun successAfterFailureMakesFailurePrunable() {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals("apple", tester.failureCodeRunFails("red"))
    assertEquals(1, tester.pruneEverythingWeCanPrune())
  }

  class LoadOrFallbackTester {
    fun seedEmbedded(applicationId: String, result: String) {

    }
    fun success(applicationId: String, result: String): String {

    }
    fun pruneEverythingWeCanPrune(): Int {

    }
    fun failureManifestFetchFails(applicationId: String): String {

    }
    fun failureCodeFetchFails(applicationId: String): String {

    }
    fun failureCodeRunFails(applicationId: String): String {

    }
  }
}
