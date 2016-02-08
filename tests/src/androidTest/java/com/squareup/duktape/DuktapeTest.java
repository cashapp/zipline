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
package com.squareup.duktape;

import android.support.test.runner.AndroidJUnit4;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public final class DuktapeTest {
  private Duktape duktape;

  @Before public void setUp() {
    duktape = Duktape.create();
  }

  @After public void tearDown() {
    duktape.close();
  }

  @Test public void helloWorld() {
    String hello = duktape.evaluate("'hello, world!'.toUpperCase();");
    assertThat(hello).isEqualTo("HELLO, WORLD!");
  }

  @Test public void exceptionsInScriptThrowInJava() {
    try {
      duktape.evaluate("nope();");
      fail();
    } catch (DuktapeException e) {
      assertThat(e).hasMessage("ReferenceError: identifier 'nope' undefined");
    }
  }

  @Test public void exceptionsInScriptIncludeStackTrace() {
    try {
      duktape.evaluate("\n"
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
    } catch (DuktapeException e) {
      assertThat(e).hasMessage("ReferenceError: identifier 'nope' undefined");
      assertThat(e.getStackTrace()).asList().containsAllOf(
              new StackTraceElement("JavaScript", "eval", "test.js", 2),
              new StackTraceElement("JavaScript", "f1", "test.js", 5),
              new StackTraceElement("JavaScript", "f2", "test.js", 10));
    }
  }

  @Test public void dateTimezoneOffset() {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("GMT+2:00"));
      String date = duktape.evaluate("new Date(0).toString();");
      assertThat(date).isEqualTo("1970-01-01 02:00:00.000+02:00");
      String offset = duktape.evaluate("new Date(0).getTimezoneOffset().toString();");
      assertThat(offset).isEqualTo("-120");
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test public void bindNonInterface() {
    try {
      duktape.bind("s", String.class, "foo");
      fail();
    } catch (UnsupportedOperationException expected) {
      assertThat(expected)
          .hasMessage("Only interfaces can be bound. Received: class java.lang.String");
    }
  }

  interface TestInterface {
    String getValue();
  }

  @Test public void callMethodOnJavaObject() {
    duktape.bind("value", TestInterface.class, new TestInterface() {
      @Override
      public String getValue() {
        return "8675309";
      }
    });
    assertThat(duktape.evaluate("value.getValue();")).isEqualTo("8675309");
  }

  @Test public void callMissingMethodOnJavaObjectFails() {
    duktape.bind("value", TestInterface.class, new TestInterface() {
      @Override public String getValue() {
        return "bar";
      }
    });
    try {
      duktape.evaluate("value.increment()");
      fail();
    } catch (DuktapeException expected) {
      assertThat(expected).hasMessage("TypeError: not callable");
    }
  }

  interface TestInterfaceArgs {
    String foo(String a, String b, String c);
  }

  @Test public void callMethodOnJavaObjectThrowsJavaException() {
    duktape.bind("value", TestInterface.class, new TestInterface() {
      @Override public String getValue() {
        throw new UnsupportedOperationException("This is an error message.");
      }
    });
    try {
      duktape.evaluate("value.getValue()");
      fail();
    } catch (UnsupportedOperationException expected) {
      assertThat(expected).hasMessage("This is an error message.");
    }
  }

  @Test public void callMethodWithArgsOnJavaObject() {
    duktape.bind("value", TestInterfaceArgs.class, new TestInterfaceArgs() {
      @Override public String foo(String a, String b, String c) {
        return a + b + c;
      }
    });

    assertThat(duktape.evaluate("value.foo('This', ' is a ', 'test')")).isEqualTo("This is a test");

    try {
      duktape.evaluate("value.foo('This')");
      fail();
    } catch (DuktapeException expected) {
      assertThat(expected.getMessage()).isEqualTo("Error: wrong number of arguments");
    }
    try {
      duktape.evaluate("value.foo('This', ' is ', 'too ', 'many ', 'arguments')");
      fail();
    } catch (DuktapeException expected) {
      assertThat(expected.getMessage()).isEqualTo("Error: wrong number of arguments");
    }
    try {
      duktape.evaluate("value.foo('1', '2', 3)");
      fail();
    } catch (DuktapeException expected) {
      assertThat(expected.getMessage()).isEqualTo("TypeError: not string");
    }
  }

  interface TestInterfaceVoids {
    void func();
    String getResult();
  }

  @Test public void callVoidJavaMethod() {
    duktape.bind("value", TestInterfaceVoids.class, new TestInterfaceVoids() {
      String result = "not called";
      @Override public void func() {
        result = "called";
      }
      @Override public String getResult() {
        return result;
      }
    });

    assertThat(duktape.evaluate("value.getResult()")).isEqualTo("not called");
    assertThat(duktape.evaluate("value.func()")).isNull();
    assertThat(duktape.evaluate("value.getResult()")).isEqualTo("called");
  }
}
