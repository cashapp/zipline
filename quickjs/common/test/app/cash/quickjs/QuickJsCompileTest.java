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
package app.cash.quickjs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

public final class QuickJsCompileTest {
  private QuickJs quickjs;

  @Before public void setUp() {
    quickjs = QuickJs.create();
  }

  @After public void tearDown() {
    quickjs.close();
  }

  @Test public void helloWorld() {
    byte[] code = quickjs.compile("'hello, world!'.toUpperCase();", "myFile.js");
    assertThat(code.length).isGreaterThan(0);

    quickjs.close();
    quickjs = QuickJs.create();

    Object hello = quickjs.execute(code);
    assertThat(hello).isEqualTo("HELLO, WORLD!");
  }

  @Test public void badCode() {
    try {
      quickjs.compile("@#%(*W#(UF(E", "myFile.js");
      fail();
    } catch (QuickJsException e) {
      assertThat(e.getMessage()).contains("unexpected token in expression");
    }
  }

  @Test public void exceptionsInScriptIncludeStackTrace() {
    byte[] code = quickjs.compile("\n"
        + "f1();\n"           // Line 2.
        + "\n"
        + "function f1() {\n"
        + "  f2();\n"         // Line 5.
        + "}\n"
        + "\n"
        + "\n"
        + "function f2() {\n"
        + "  nope();\n"       // Line 10.
        + "}\n", "C:\\Documents\\myFile.js");
    try {
      quickjs.execute(code);
      fail();
    } catch (QuickJsException e) {
      assertThat(e).hasMessageThat().isEqualTo("'nope' is not defined");
      assertThat(e.getStackTrace()[0].toString()).isEqualTo("JavaScript.f2(C:\\Documents\\myFile.js:10)");
      assertThat(e.getStackTrace()[1].toString()).isEqualTo("JavaScript.f1(C:\\Documents\\myFile.js:5)");
      assertThat(e.getStackTrace()[2].toString()).isEqualTo("JavaScript.<eval>(C:\\Documents\\myFile.js:2)");
      assertThat(e.getStackTrace()[3].toString())
          .isEqualTo("app.cash.quickjs.QuickJs.execute(Native Method)");
    }
  }

  @Test public void multipleParts() {
    byte[] code = quickjs.compile("myFunction();", "myFileA.js");
    assertThat(code.length).isGreaterThan(0);
    byte[] functionDef = quickjs.compile("function myFunction() { return 'this is the answer'; }", "myFileB.js");
    assertThat(functionDef.length).isGreaterThan(0);

    quickjs.close();
    quickjs = QuickJs.create();

    try {
      quickjs.execute(code);
      fail();
    } catch (QuickJsException e) {
      assertThat(e.getMessage()).isEqualTo("'myFunction' is not defined");
    }

    assertThat(quickjs.execute(functionDef)).isNull();
    assertThat(quickjs.execute(code)).isEqualTo("this is the answer");
  }

  interface TestInterface {
    String getValue();
  }

  @Test public void withAGetProxy() {
    byte[] proxyDef = quickjs.compile("var value = { getValue: function() { return '8675309'; } };", "myObject.js");
    assertThat(proxyDef.length).isGreaterThan(0);

    quickjs.close();
    quickjs = QuickJs.create();

    try {
      quickjs.get("value", QuickJsGetTest.TestInterface.class);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).isEqualTo("A global JavaScript object called value was not found");
    }

    quickjs.execute(proxyDef);
    QuickJsGetTest.TestInterface proxy = quickjs.get("value", QuickJsGetTest.TestInterface.class);
    assertThat(proxy.getValue()).isEqualTo("8675309");
  }

  @Test public void withASetProxy() {
    byte[] code = quickjs.compile("value.getValue();", "myFile.js");
    assertThat(code.length).isGreaterThan(0);

    quickjs.close();
    quickjs = QuickJs.create();

    try {
      quickjs.execute(code);
      fail();
    } catch (QuickJsException e) {
      assertThat(e.getMessage()).isEqualTo("'value' is not defined");
    }

    quickjs.set("value", QuickJsSetTest.TestInterface.class, () -> "8675309");
    assertThat(quickjs.execute(code)).isEqualTo("8675309");
  }
}
