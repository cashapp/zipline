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

import app.cash.zipline.testing.Formatter
import app.cash.zipline.testing.isJni
import app.cash.zipline.testing.loadTestingJs
import assertk.assertThat
import assertk.assertions.contains
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * This test attempts to transmit strings through each of our supported mechanisms and confirms that
 * each doesn't mangle content. We've had bugs where non-ASCII characters weren't encoded properly.
 */
class Utf8Test {
  @OptIn(ExperimentalCoroutinesApi::class)
  private val dispatcher = UnconfinedTestDispatcher()
  private val zipline = Zipline.create(dispatcher)
  private val quickjs = zipline.quickJs

  @BeforeTest
  fun setUp() = runBlocking(dispatcher) {
    zipline.loadTestingJs()
  }

  @AfterTest
  fun tearDown() = runBlocking(dispatcher) {
    zipline.close()
  }

  @Test
  fun nonAsciiInInputAndOutput() = runBlocking(dispatcher) {
    assertEquals(
      "(a\uD83D\uDC1Dcdefg, a\uD83D\uDC1Dcdefg)",
      quickjs.evaluate("var s = \"a\uD83D\uDC1Dcdefg\"; '(' + s + ', ' + s + ')';"),
    )
  }

  @Test
  fun nonAsciiInFileName() = runBlocking(dispatcher) {
    val t = assertFailsWith<QuickJsException> {
      quickjs.evaluate(
        """
        |f1();
        |
        |function f1() {
        |  nope();
        |}
        |
        """.trimMargin(),
        "a\uD83D\uDC1Dcdefg.js",
      )
      quickjs.evaluate("formatter.format();")
    }
    val expectedSubstring = when {
      isJni -> "JavaScript.f1(a\uD83D\uDC1Dcdefg.js:4)"
      else -> "at f1 (a\uD83D\uDC1Dcdefg.js:4)"
    }
    assertThat(t.stackTraceToString()).contains(expectedSubstring)
  }

  @Test
  fun nonAsciiInboundCalls() = runBlocking(dispatcher) {
    quickjs.evaluate("testing.app.cash.zipline.testing.prepareNonAsciiInputAndOutput()")
    val formatter = zipline.take<Formatter>("formatter")
    assertEquals("(a\uD83D\uDC1Dcdefg, a\uD83D\uDC1Dcdefg)", formatter.format("a\uD83D\uDC1Dcdefg"))
  }

  @Test
  fun nonAsciiOutboundCalls() = runBlocking(dispatcher) {
    zipline.bind<Formatter>(
      "formatter",
      object : Formatter {
        override fun format(message: String): String {
          return "($message, $message)"
        }
      },
    )
    assertEquals(
      "(a\uD83D\uDC1Dcdefg, a\uD83D\uDC1Dcdefg)",
      quickjs.evaluate("testing.app.cash.zipline.testing.callFormatter('a\uD83D\uDC1Dcdefg');"),
    )
  }

  @Test
  fun nonAsciiInExceptionThrownInJs() = runBlocking(dispatcher) {
    quickjs.evaluate("testing.app.cash.zipline.testing.prepareNonAsciiThrower()")
    val formatter = zipline.take<Formatter>("formatter")
    val t = assertFailsWith<Exception> {
      formatter.format("")
    }
    assertThat(t.message!!).contains("a\uD83D\uDC1Dcdefg")
  }

  @Test
  fun nonAsciiInExceptionThrownInJava() = runBlocking(dispatcher) {
    zipline.bind<Formatter>(
      "formatter",
      object : Formatter {
        override fun format(message: String): String {
          throw RuntimeException("a\uD83D\uDC1Dcdefg")
        }
      },
    )
    val t = assertFailsWith<RuntimeException> {
      quickjs.evaluate("testing.app.cash.zipline.testing.callFormatter('');")
    }
    assertThat(t.message!!).contains("RuntimeException: a\uD83D\uDC1Dcdefg")
  }
}
