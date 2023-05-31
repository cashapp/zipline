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
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class QuickJsTest {
  private val quickJs = QuickJs.create()

  @AfterTest
  fun tearDown() {
    quickJs.close()
  }

  @Test
  fun version() {
    assertNotEquals("", QuickJs.version)
  }

  @Test fun helloWorld() {
    val hello = quickJs.evaluate("'hello, world!'.toUpperCase();") as String?
    assertEquals("HELLO, WORLD!", hello)
  }

  @Test fun exceptionsInScriptThrowInKotlin() {
    val t = assertFailsWith<QuickJsException> {
      quickJs.evaluate("nope();")
    }
    assertThat(t.message!!).startsWith("'nope' is not defined")
  }

  @Test fun returnTypes() {
    assertEquals("test", quickJs.evaluate("\"test\";"))
    assertEquals(true, quickJs.evaluate("true;"))
    assertEquals(false, quickJs.evaluate("false;"))
    assertEquals(1, quickJs.evaluate("1;"))
    assertEquals(1.123, quickJs.evaluate("1.123;"))

    assertNull(quickJs.evaluate("undefined;"))
    assertNull(quickJs.evaluate("null;"))

    assertContentEquals(
      arrayOf("test", true, false, 1, 1.123, null, null),
      quickJs.evaluate("""["test", true, false, 1, 1.123, undefined, null];""") as Array<Any?>,
    )
  }
}
