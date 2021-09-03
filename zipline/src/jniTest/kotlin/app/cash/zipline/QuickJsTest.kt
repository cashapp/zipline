/*
 * Copyright (C) 2015 Square, Inc.
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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickJsTest {
  private val quickjs = QuickJs.create()

  @After fun tearDown() {
    quickjs.close()
  }

  @Test fun helloWorld() {
    val hello = quickjs.evaluate("'hello, world!'.toUpperCase();") as String?
    assertEquals("HELLO, WORLD!", hello)
  }

  @Test fun exceptionsInScriptThrowInJava() {
    val t = assertThrows<QuickJsException> {
      quickjs.evaluate("nope();")
    }
    assertEquals("'nope' is not defined", t.message)
  }

  @Test fun returnTypes() {
    assertEquals("test", quickjs.evaluate("\"test\";"))
    assertEquals(true, quickjs.evaluate("true;"))
    assertEquals(false, quickjs.evaluate("false;"))
    assertEquals(1, quickjs.evaluate("1;"))
    assertEquals(1.123, quickjs.evaluate("1.123;"))
    assertNull(quickjs.evaluate("undefined;"))
    assertNull(quickjs.evaluate("null;"))
  }

  @Test fun exceptionsInScriptIncludeStackTrace() {
    val t = assertThrows<QuickJsException> {
      quickjs.evaluate("""
        |f1();
        |
        |function f1() {
        |  f2();
        |}
        |
        |function f2() {
        |  nope();
        |}
        |""".trimMargin(), "test.js")
    }
    assertEquals("'nope' is not defined", t.message)
    assertEquals("JavaScript.f2(test.js:8)", t.stackTrace[0].toString())
    assertEquals("JavaScript.f1(test.js:4)", t.stackTrace[1].toString())
    assertEquals("JavaScript.<eval>(test.js:1)", t.stackTrace[2].toString())
    assertEquals("app.cash.zipline.QuickJs.evaluate(Native Method)", t.stackTrace[3].toString())
  }
}
