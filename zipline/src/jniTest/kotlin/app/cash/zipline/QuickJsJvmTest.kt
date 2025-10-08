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

import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickJsJvmTest {
  private val quickjs = QuickJs.create()

  @After fun tearDown() {
    quickjs.close()
  }

  @Test fun exceptionsInScriptIncludeStackTrace() {
    val t = assertFailsWith<QuickJsException> {
      quickjs.evaluate(
        """
        |f1();
        |
        |function f1() {
        |  f2();
        |}
        |
        |function f2() {
        |  nope();
        |}
        |
        """.trimMargin(),
        "test.js",
      )
    }
    assertEquals("'nope' is not defined", t.message)
    assertEquals("JavaScript.f2(test.js:8)", t.stackTrace[0].toString())
    assertEquals("JavaScript.f1(test.js:4)", t.stackTrace[1].toString())
    assertEquals("JavaScript.<eval>(test.js:1)", t.stackTrace[2].toString())
    assertEquals("app.cash.zipline.QuickJs.execute(Native Method)", t.stackTrace[3].toString())
  }

  @Test fun dateNow() {
    val beforeMillis = System.currentTimeMillis().toDouble()
    Thread.sleep(100)
    val nowMillis = quickjs.evaluate("Date.now()") as Double
    Thread.sleep(100)
    val afterMillis = System.currentTimeMillis().toDouble()
    assertTrue(
      "$beforeMillis <= $nowMillis <= $afterMillis",
      nowMillis in beforeMillis..afterMillis,
    )
  }
}
