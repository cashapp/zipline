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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class QuickJsCompileTest {
  private var quickJs = QuickJs.create()

  @AfterTest fun tearDown() {
    quickJs.close()
  }

  @Test fun helloWorld() {
    val code = quickJs.compile("'hello, world!'.toUpperCase();", "myFile.js")
    assertNotEquals(0, code.size)

    quickJs.close()
    quickJs = QuickJs.create()

    val hello = quickJs.execute(code)
    assertEquals("HELLO, WORLD!", hello)
  }

  @Test fun badCode() {
    val t = assertFailsWith<QuickJsException> {
      quickJs.compile("@#%(*W#(UF(E", "myFile.js")
    }
    assertThat(t.message!!).startsWith("unexpected token in expression: '@'")
  }

  @Test fun multipleParts() {
    val code = quickJs.compile("myFunction();", "myFileA.js")
    assertNotEquals(0, code.size)

    val functionDef =
        quickJs.compile("function myFunction() { return 'this is the answer'; }", "myFileB.js")
    assertNotEquals(0, functionDef.size)

    quickJs.close()
    quickJs = QuickJs.create()

    val t = assertFailsWith<QuickJsException> {
      quickJs.execute(code)
    }
    assertThat(t.message!!).startsWith("'myFunction' is not defined")

    assertNull(quickJs.execute(functionDef))
    assertEquals("this is the answer", quickJs.execute(code))
  }
}
