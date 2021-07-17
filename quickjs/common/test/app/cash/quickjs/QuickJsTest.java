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
package app.cash.quickjs;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

public final class QuickJsTest {
  private QuickJs quickjs;

  @Before public void setUp() {
    quickjs = QuickJs.create();
  }

  @After public void tearDown() {
    quickjs.close();
  }

  @Test public void helloWorld() {
    String hello = (String) quickjs.evaluate("'hello, world!'.toUpperCase();");
    assertThat(hello).isEqualTo("HELLO, WORLD!");
  }

  @Test public void exceptionsInScriptThrowInJava() {
    try {
      quickjs.evaluate("nope();");
      fail();
    } catch (QuickJsException e) {
      assertThat(e).hasMessageThat().isEqualTo("'nope' is not defined");
    }
  }

  @Test public void returnTypes() {
    assertThat(quickjs.evaluate("\"test\";")).isEqualTo("test");

    assertThat(quickjs.evaluate("true;")).isEqualTo(true);
    assertThat(quickjs.evaluate("false;")).isEqualTo(false);

    assertThat(quickjs.evaluate("1;")).isEqualTo(1);
    assertThat(quickjs.evaluate("1.123;")).isEqualTo(1.123);

    assertThat(quickjs.evaluate("undefined;")).isNull();
    assertThat(quickjs.evaluate("null;")).isNull();
  }

  @Test public void exceptionsInScriptIncludeStackTrace() {
    try {
      quickjs.evaluate("\n"
          + "f1();\n"           // Line 2.
          + "\n"
          + "function f1() {\n"
          + "  f2();\n"         // Line 5.
          + "}\n"
          + "\n"
          + "\n"
          + "function f2() {\n"
          + "  nope();\n"       // Line 10.
          + "}\n", "test.js");
      fail();
    } catch (QuickJsException e) {
      assertThat(e).hasMessageThat().isEqualTo("'nope' is not defined");
      assertThat(e.getStackTrace()[0].toString()).isEqualTo("JavaScript.f2(test.js:10)");
      assertThat(e.getStackTrace()[1].toString()).isEqualTo("JavaScript.f1(test.js:5)");
      assertThat(e.getStackTrace()[2].toString()).isEqualTo("JavaScript.<eval>(test.js:2)");
      assertThat(e.getStackTrace()[3].toString())
          .isEqualTo("app.cash.quickjs.QuickJs.evaluate(Native Method)");
    }
  }

  @Ignore("https://github.com/cashapp/quickjs-java/issues/214")
  @Test public void emojiRoundTrip() {
    assertThat(quickjs.evaluate("\"\uD83D\uDC1D️\""))
        .isEqualTo("\uD83D\uDC1D️️");
  }
}
