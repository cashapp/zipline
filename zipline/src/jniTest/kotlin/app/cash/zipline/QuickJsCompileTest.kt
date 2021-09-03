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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickJsCompileTest {
  private var quickJs = QuickJs.create()

  @After fun tearDown() {
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
    val t = assertThrows<QuickJsException> {
      quickJs.compile("@#%(*W#(UF(E", "myFile.js")
    }
    assertEquals("unexpected token in expression: '@'", t.message)
  }

  @Test fun exceptionsInScriptIncludeStackTrace() {
    val code = quickJs.compile("""
      |f1();
      |
      |function f1() {
      |  f2();
      |}
      |
      |function f2() {
      |  nope();
      |}
      |""".trimMargin(), "C:\\Documents\\myFile.js")
    val t = assertThrows<QuickJsException> {
      quickJs.execute(code)
    }
    assertEquals("'nope' is not defined", t.message)
    assertEquals("JavaScript.f2(C:\\Documents\\myFile.js:8)", t.stackTrace[0].toString())
    assertEquals("JavaScript.f1(C:\\Documents\\myFile.js:4)", t.stackTrace[1].toString())
    assertEquals("JavaScript.<eval>(C:\\Documents\\myFile.js:1)", t.stackTrace[2].toString())
    assertEquals("app.cash.zipline.QuickJs.execute(Native Method)", t.stackTrace[3].toString())
  }

  @Test fun multipleParts() {
    val code = quickJs.compile("myFunction();", "myFileA.js")
    assertNotEquals(0, code.size)

    val functionDef =
        quickJs.compile("function myFunction() { return 'this is the answer'; }", "myFileB.js")
    assertNotEquals(0, functionDef.size)

    quickJs.close()
    quickJs = QuickJs.create()

    val t = assertThrows<QuickJsException> {
      quickJs.execute(code)
    }
    assertEquals("'myFunction' is not defined", t.message)

    assertNull(quickJs.execute(functionDef))
    assertEquals("this is the answer", quickJs.execute(code))
  }

  internal interface TestInterface {
    val value: String?
  }

  @Test fun withAGetProxy() {
    val proxyDef = quickJs.compile("var value = { getValue: function() { return '8675309'; } };",
        "myObject.js")
    assertNotEquals(0, proxyDef.size)

    quickJs.close()
    quickJs = QuickJs.create()

    val t = assertThrows<IllegalArgumentException> {
      quickJs.get("value", QuickJsGetTest.TestInterface::class.java)
    }
    assertEquals("A global JavaScript object called value was not found", t.message)

    quickJs.execute(proxyDef)
    val proxy = quickJs.get("value", QuickJsGetTest.TestInterface::class.java)
    assertEquals("8675309", proxy.value)
  }

  @Test fun withASetProxy() {
    val code = quickJs.compile("value.getValue();", "myFile.js")
    assertNotEquals(0, code.size)

    quickJs.close()
    quickJs = QuickJs.create()

    val t = assertThrows<QuickJsException> {
      quickJs.execute(code)
    }
    assertEquals("'value' is not defined", t.message)

    quickJs.set("value", QuickJsSetTest.TestInterface::class.java,
        QuickJsSetTest.TestInterface { "8675309" })
    assertEquals("8675309", quickJs.execute(code))
  }
}
