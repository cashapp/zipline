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
package app.cash.zipline

import app.cash.zipline.testing.loadTestingJs
import app.cash.zipline.testing.singleThreadCoroutineDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class SetTimeoutTest {
  private val dispatcher = singleThreadCoroutineDispatcher("SetTimeoutTest")
  private val zipline = Zipline.create(dispatcher)

  @BeforeTest fun setUp(): Unit = runBlocking(dispatcher) {
    zipline.loadTestingJs()
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.initZipline()")
  }

  @AfterTest fun tearDown() = runBlocking(dispatcher) {
    zipline.close()
  }

  @Test fun happyPath() = runBlocking(dispatcher) {
    zipline.quickJs.evaluate(
      """
      var greeting = 'hello';

      var sayGoodbye = function() {
        greeting = 'goodbye';
      };

      setTimeout(sayGoodbye, 100);
      """,
    )

    assertEquals("hello", zipline.quickJs.evaluate("greeting"))

    eventually(duration = 2.seconds, interval = 100.milliseconds) {
      assertEquals("goodbye", zipline.quickJs.evaluate("greeting"))
    }
  }

  @Test fun ziplineCloseSilentlyCancelsQueuedTasks(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate(
      """
      var doNothing = function() {
      };

      setTimeout(doNothing, 100);
      """,
    )

    zipline.close()
    delay(200L)
  }

  private suspend inline fun eventually(
    duration: Duration,
    interval: Duration,
    assertCode: () -> Unit,
  ) {
    val maxIterations = (duration.inWholeMilliseconds / interval.inWholeMilliseconds).toInt().coerceAtLeast(1)
    var lastException: Throwable? = null

    repeat(maxIterations) {
      try {
        assertCode()
        return // Success, exit
      } catch (e: AssertionError) {
        lastException = e // Try again
      }
      delay(interval)
    }

    throw lastException ?: throw IllegalStateException("How could we reach here without an exception?")
  }
}
