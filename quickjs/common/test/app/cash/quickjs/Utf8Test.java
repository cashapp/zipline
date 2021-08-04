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

/**
 * This test attempts to transmit strings through each of our supported mechanisms and confirms that
 * each doesn't mangle content. We've had bugs where non-ASCII characters weren't encoded properly.
 */
public final class Utf8Test {
  private QuickJs quickjs;

  @Before public void setUp() {
    quickjs = QuickJs.create();
  }

  @After public void tearDown() {
    quickjs.close();
  }

  interface Formatter {
    String format(String message);
  }

  @Test public void nonAsciiInInputAndOutput() {
    assertThat(quickjs.evaluate("var s = \"a\uD83D\uDC1Dcdefg\"; '(' + s + ', ' + s + ')';"))
        .isEqualTo("(a\uD83D\uDC1Dcdefg, a\uD83D\uDC1Dcdefg)");
  }

  @Test public void nonAsciiInFileName() {
    try {
      quickjs.evaluate("\n"
          + "f1();\n"           // Line 2.
          + "\n"
          + "function f1() {\n"
          + "  nope();\n"       // Line 5.
          + "}\n", "a\uD83D\uDC1Dcdefg.js");
      quickjs.evaluate("formatter.format();");
      fail();
    } catch (QuickJsException e) {
      assertThat(e.getStackTrace()[0].toString())
          .isEqualTo("JavaScript.f1(a\uD83D\uDC1Dcdefg.js:5)");
    }
  }

  @Test public void nonAsciiInProxyInputAndOutput() {
    quickjs.evaluate("var formatter = {\n"
        + "  format: function(message) {\n"
        + "    return '(' + message + ', ' + message + ')';\n"
        + "  }\n"
        + "};\n");
    Formatter formatter = quickjs.get("formatter", Formatter.class);
    assertThat(formatter.format("a\uD83D\uDC1Dcdefg"))
        .isEqualTo("(a\uD83D\uDC1Dcdefg, a\uD83D\uDC1Dcdefg)");
  }

  @Test public void nonAsciiInBoundObjectInputAndOutput() {
    quickjs.set("formatter", Formatter.class, new Formatter() {
      @Override public String format(String message) {
        return "(" + message + ", " + message + ")";
      }
    });
    assertThat(quickjs.evaluate("formatter.format('a\uD83D\uDC1Dcdefg');"))
        .isEqualTo("(a\uD83D\uDC1Dcdefg, a\uD83D\uDC1Dcdefg)");
  }

  @Test public void nonAsciiInExceptionThrownInJs() {
    quickjs.evaluate("var formatter = {\n"
        + "  format: function(message) {\n"
        + "    throw 'a\uD83D\uDC1Dcdefg';\n"
        + "  }\n"
        + "};\n");
    Formatter formatter = quickjs.get("formatter", Formatter.class);
    try {
      formatter.format("");
      fail();
    } catch (QuickJsException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("a\uD83D\uDC1Dcdefg");
    }
  }

  @Test public void nonAsciiInExceptionThrownInJava() {
    quickjs.set("formatter", Formatter.class, new Formatter() {
      @Override public String format(String message) {
        throw new RuntimeException("a\uD83D\uDC1Dcdefg");
      }
    });
    try {
      quickjs.evaluate("formatter.format('');");
      fail();
    } catch (RuntimeException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("a\uD83D\uDC1Dcdefg");
    }
  }

  @Test public void nonAsciiInProxyResult() {
    quickjs.evaluate("var formatter = {\n"
        + "  format: function(message) {\n"
        + "    return 'a\uD83D\uDC1Dcdefg';\n"
        + "  }\n"
        + "};\n");
    Formatter formatter = quickjs.get("formatter", Formatter.class);
    assertThat(formatter.format(""))
        .isEqualTo("a\uD83D\uDC1Dcdefg");
  }
}
