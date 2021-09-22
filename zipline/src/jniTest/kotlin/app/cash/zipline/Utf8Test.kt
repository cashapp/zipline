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

import app.cash.zipline.Utf8Test.Formatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * This test attempts to transmit strings through each of our supported mechanisms and confirms that
 * each doesn't mangle content. We've had bugs where non-ASCII characters weren't encoded properly.
 */
class Utf8Test {
  private val quickjs = QuickJs.create()

  @After fun tearDown() {
    quickjs.close()
  }

  fun interface Formatter {
    fun format(message: String): String?
  }

  @Test fun nonAsciiInInputAndOutput() {
    assertEquals("(a\uD83D\uDC1Dcdefg, a\uD83D\uDC1Dcdefg)",
        quickjs.evaluate("var s = \"a\uD83D\uDC1Dcdefg\"; '(' + s + ', ' + s + ')';"))
  }

  @Test fun nonAsciiInFileName() {
    val t = assertThrows<QuickJsException> {
      quickjs.evaluate("""
        |f1();
        |
        |function f1() {
        |  nope();
        |}
        |""".trimMargin(), "a\uD83D\uDC1Dcdefg.js")
      quickjs.evaluate("formatter.format();")
    }
    assertEquals("JavaScript.f1(a\uD83D\uDC1Dcdefg.js:4)", t.stackTrace[0].toString())
  }

  @Test fun nonAsciiInProxyInputAndOutput() {
    quickjs.evaluate("""
      |var formatter = {
      |  format: function(message) {
      |    return '(' + message + ', ' + message + ')';
      |  }
      |};
      |""".trimMargin())
    val formatter = quickjs.get("formatter", Formatter::class)
    assertEquals("(a\uD83D\uDC1Dcdefg, a\uD83D\uDC1Dcdefg)", formatter.format("a\uD83D\uDC1Dcdefg"))
  }

  @Test fun nonAsciiInBoundObjectInputAndOutput() {
    quickjs.set("formatter", Formatter::class,
        Formatter { message -> "($message, $message)" })
    assertEquals("(a\uD83D\uDC1Dcdefg, a\uD83D\uDC1Dcdefg)",
        quickjs.evaluate("formatter.format('a\uD83D\uDC1Dcdefg');"))
  }

  @Test fun nonAsciiInExceptionThrownInJs() {
    quickjs.evaluate("""
      |var formatter = {
      |  format: function(message) {
      |    throw 'a🐝cdefg';
      |  }
      |};
      |""".trimMargin())
    val formatter = quickjs.get("formatter", Formatter::class)
    val t = assertThrows<QuickJsException> {
      formatter.format("")
    }
    assertEquals("a\uD83D\uDC1Dcdefg", t.message)
  }

  @Test fun nonAsciiInExceptionThrownInJava() {
    quickjs.set("formatter", Formatter::class,
        Formatter { throw RuntimeException("a\uD83D\uDC1Dcdefg") })
    val t = assertThrows<RuntimeException> {
      quickjs.evaluate("formatter.format('');")
    }
    assertEquals("a\uD83D\uDC1Dcdefg", t.message)
  }

  @Test fun nonAsciiInProxyResult() {
    quickjs.evaluate("""
      |var formatter = {
      |  format: function(message) {
      |    return 'a🐝cdefg';
      |  }
      |};
      |""".trimMargin())
    val formatter = quickjs.get("formatter", Formatter::class)
    assertEquals("a\uD83D\uDC1Dcdefg", formatter.format(""))
  }
}
