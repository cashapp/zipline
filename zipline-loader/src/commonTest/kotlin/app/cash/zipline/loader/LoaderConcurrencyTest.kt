/*
 * Copyright (C) 2024 Cash App
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
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class LoaderConcurrencyTest {
  private var currentDispatcher: CoroutineDispatcher? = null
  private var cacheHits = 0
  private val testDispatcher = StandardTestDispatcher()
  private val dispatcher = CurrentTrackingDispatcher(testDispatcher)
  private val cacheDispatcher = CurrentTrackingDispatcher(testDispatcher)

  private val tester = LoaderTester(
    eventListenerFactory = TestEventListener(),
    dispatcher = dispatcher,
    cacheDispatcher = cacheDispatcher,
  )

  @BeforeTest
  fun setUp() {
    tester.beforeTest()
  }

  @AfterTest
  fun tearDown() {
    tester.afterTest()
  }

  @Test
  fun cacheIsConfinedToCacheDispatcher() = runTest(testDispatcher) {
    val load1 = tester.load(
      applicationName = "red",
      seed = "apple",
      freshnessChecker = FakeFreshnessCheckerFresh,
    ).first()
    assertThat(load1).isInstanceOf<LoadResult.Success>()

    val load2 = tester.load(
      applicationName = "red",
      seed = "apple",
      freshnessChecker = FakeFreshnessCheckerFresh,
    ).first()
    assertThat(load2).isInstanceOf<LoadResult.Success>()

    assertThat(cacheHits).isEqualTo(1)
  }

  inner class CurrentTrackingDispatcher(
    private val delegate: CoroutineDispatcher,
  ) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
      delegate.dispatch(
        context,
        object : Runnable {
        override fun run() {
          currentDispatcher = this@CurrentTrackingDispatcher
          try {
            block.run()
          } finally {
            currentDispatcher = null
          }
        }
      },
      )
    }
  }

  inner class TestEventListener :
    EventListener(),
    EventListener.Factory {
    override fun create(applicationName: String, manifestUrl: String?) = this

    override fun cacheHit(applicationName: String, url: String, fileSize: Long) {
      assertThat(currentDispatcher).isSameInstanceAs(cacheDispatcher)
      cacheHits++
    }
  }
}
