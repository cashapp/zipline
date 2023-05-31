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

import assertk.assertThat
import assertk.assertions.startsWith
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuickJsInterruptTest {
  private val quickJs = QuickJs.create()

  @BeforeTest fun setUp() {
    quickJs.evaluate(
      """
      |var fib = function(a) {
      |  if (a < 2) return 1;
      |  return fib(a - 1) + fib(a - 2);
      |}
      """.trimMargin(),
    )

    // Set a huge max stack because of the heavy recursion of fib().
    quickJs.maxStackSize *= 5
  }

  @AfterTest fun tearDown() {
    quickJs.close()
  }

  @Test fun interruptedAfterEnoughComputations() {
    val counter = InterruptCounter()
    quickJs.interruptHandler = counter

    // Discovered experimentally, fib(17) does enough computations trigger exactly 1 interruption.
    // This program completes in ~1 ms on a 2.4 GHz Intel i9 MacBook Pro.
    val result = quickJs.evaluate("""fib(17)""")
    assertEquals(1, counter.count)
    assertEquals(2584, result)
  }

  @Test fun multipleInterruptions() {
    val counter = InterruptCounter()
    quickJs.interruptHandler = counter

    // Discovered experimentally, fib(20) does enough computations trigger exactly 4 interruptions.
    // This program completes in ~5 ms on a 2.4 GHz Intel i9 MacBook Pro.
    val result = quickJs.evaluate("""fib(20)""")
    assertEquals(4, counter.count)
    assertEquals(10946, result)
  }

  @Test fun interruptionReturnsTrueToTerminateWork() {
    quickJs.interruptHandler = object : InterruptHandler {
      override fun poll(): Boolean {
        return true
      }
    }

    val e = assertFailsWith<QuickJsException> {
      quickJs.evaluate("""fib(20)""")
    }
    assertThat(e.message!!).startsWith("interrupted")
  }

  @Test fun removeInterruptHandler() {
    val counter = InterruptCounter()
    quickJs.interruptHandler = counter

    assertEquals(10946, quickJs.evaluate("""fib(20)"""))
    assertEquals(4, counter.count)

    quickJs.interruptHandler = null

    assertEquals(10946, quickJs.evaluate("""fib(20)"""))
    assertEquals(4, counter.count) // Still 4.
  }

  /**
   * It's possible an interrupt handler may run JavaScript to query global state before returning.
   * Confirm that such work can't itself be interrupted.
   */
  @Test fun interruptionAreNotReentrant() {
    var count = 0
    quickJs.interruptHandler = object : InterruptHandler {
      override fun poll(): Boolean {
        count++
        assertEquals(10946, quickJs.evaluate("""fib(20)"""))
        return false
      }
    }

    assertEquals(2584, quickJs.evaluate("""fib(17)"""))
    assertEquals(1, count)
  }

  class InterruptCounter : InterruptHandler {
    var count = 0
    override fun poll(): Boolean {
      count++
      return false
    }
  }
}
