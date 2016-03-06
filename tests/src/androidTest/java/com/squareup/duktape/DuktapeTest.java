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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.TimeZone;

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

  @Test public void bindSameNameTwiceFails() {
    duktape.bind("value", TestInterface.class, new TestInterface() {
      @Override public String getValue() {
        return "foo";
      }
    });
    try {
      duktape.bind("value", TestInterface.class, new TestInterface() {
        @Override public String getValue() {
          return "bar";
        }
      });
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("A global object called value already exists");
    }
  }

  @Test public void exceptionsFromJavaWithUnifiedStackTrace() {
    TestInterface boundObject = new TestInterface() {
      @Override public String getValue() {
        throw new UnsupportedOperationException("Cannot getValue");
      }
    };
    duktape.bind("value", TestInterface.class, boundObject);
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
          + "  value.getValue();\n"       // Line 10.
          + "}\n", "test.js");
      fail();
    } catch (UnsupportedOperationException e) {
      assertThat(e).hasMessage("Cannot getValue");

      StackTraceElement[] stackTrace = e.getStackTrace();

      // Top entry is what threw - the TestInterface implementation.
      assertThat(stackTrace[0].getClassName()).isEqualTo(boundObject.getClass().getName());
      assertThat(stackTrace[0].getMethodName()).isEqualTo("getValue");

      // The next three entries are JavaScript
      assertThat(stackTrace[1]).isEqualTo(new StackTraceElement("JavaScript", "f2", "test.js", 10));
      assertThat(stackTrace[2]).isEqualTo(new StackTraceElement("JavaScript", "f1", "test.js", 5));
      assertThat(stackTrace[3]).isEqualTo(new StackTraceElement("JavaScript", "eval", "test.js", 2));

      // Then the native Duktape.evaluate method.
      assertThat(stackTrace[4].getClassName()).isEqualTo(Duktape.class.getName());
      assertThat(stackTrace[4].getMethodName()).isEqualTo("evaluate");
      assertThat(stackTrace[4].isNativeMethod()).isTrue();

      // Then the Java method.
      assertThat(stackTrace[5].getClassName()).isEqualTo(Duktape.class.getName());
      assertThat(stackTrace[5].getMethodName()).isEqualTo("evaluate");
      assertThat(stackTrace[5].isNativeMethod()).isFalse();

      // Then this test method.
      assertThat(stackTrace[6].getClassName()).isEqualTo(DuktapeTest.class.getName());
      assertThat(stackTrace[6].getMethodName())
          .isEqualTo("exceptionsFromJavaWithUnifiedStackTrace");
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
        return a != null ? a + b + c : null;
      }
    });

    assertThat(duktape.evaluate("value.foo('This', ' is a ', 'test')")).isEqualTo("This is a test");
    assertThat(duktape.evaluate("value.foo(null, null, null)")).isNull();

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

  interface TestPrimitiveTypes {
    boolean b(boolean b);
    int i(int i);
    double d(double d);
  }

  // Verify that primitive types can be used as both arguments and return values from Java methods.
  @Test public void callJavaMethodWithPrimitiveTypes() {
    duktape.bind("value", TestPrimitiveTypes.class, new TestPrimitiveTypes() {
      @Override public boolean b(boolean b) {
        return !b;
      }
      @Override public int i(int i) {
        return i * i;
      }
      @Override public double d(double d) {
        return d / 2.0;
      }
    });

    // TODO: add an evaluate interface that supports other types.
    assertThat(duktape.evaluate("value.b(false).toString()")).isEqualTo("true");
    assertThat(duktape.evaluate("value.i(4).toString()")).isEqualTo("16");
    assertThat(duktape.evaluate("value.d(6.28318).toString()")).isEqualTo("3.14159");
  }

  interface TestMultipleArgTypes {
    String print(boolean b, int i, double d);
  }

  // Double check that arguments of different types are processed in the correct order from the
  // Duktape stack.
  @Test public void callJavaMethodWithAllArgTypes() {
    duktape.bind("printer", TestMultipleArgTypes.class, new TestMultipleArgTypes() {
      @Override public String print(boolean b, int i, double d) {
        return String.format("boolean: %s, int: %s, double: %s", b, i, d);
      }
    });
    assertThat(duktape.evaluate("printer.print(true, 42, 2.718281828459)"))
        .isEqualTo("boolean: true, int: 42, double: 2.718281828459");
  }

  interface TestBoxedPrimitiveArgTypes {
    Boolean b(Boolean b);
    Integer i(Integer i);
    Double d(Double d);
  }

  @Test public void callJavaMethodWithBoxedPrimitiveTypes() {
    duktape.bind("value", TestBoxedPrimitiveArgTypes.class, new TestBoxedPrimitiveArgTypes() {
      @Override public Boolean b(Boolean b) {
        return b != null ? !b : null;
      }
      @Override public Integer i(Integer i) {
        return i != null ? i * i : null;
      }
      @Override public Double d(Double d) {
        return d != null ? d / 2.0 : null;
      }
    });

    // TODO: add an evaluate interface that supports other types.
    assertThat(duktape.evaluate("value.b(false).toString()")).isEqualTo("true");
    assertThat(duktape.evaluate("value.i(4).toString()")).isEqualTo("16");
    assertThat(duktape.evaluate("value.d(6.28318).toString()")).isEqualTo("3.14159");

    assertThat(duktape.evaluate("value.b(null)")).isNull();
    assertThat(duktape.evaluate("value.i(null)")).isNull();
    assertThat(duktape.evaluate("value.d(null)")).isNull();
  }

  interface UnsupportedReturnType {
    Date get();
  }

  @Test public void bindUnsupportedReturnType() {
    try {
      duktape.bind("value", UnsupportedReturnType.class, new UnsupportedReturnType() {
        @Override public Date get() {
          return null;
        }
      });
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage(
          "In bound method \"value.get\": Unsupported return type class java.util.Date");
    }
  }

  interface UnsupportedArgumentType {
    void set(Date d);
  }

  @Test public void bindUnsupportedArgumentType() {
    try {
      duktape.bind("value", UnsupportedArgumentType.class, new UnsupportedArgumentType() {
        @Override public void set(Date d) {
        }
      });
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage(
          "In bound method \"value.set\": Unsupported parameter type class java.util.Date");
    }
  }

  interface OverloadedMethod {
    void foo(int i);
    void foo(double d);
  }

  @Test public void bindOverloadedMethod() {
    try {
      duktape.bind("value", OverloadedMethod.class, new OverloadedMethod() {
        @Override public void foo(int i) {
        }
        @Override public void foo(double d) {
        }
      });
      fail();
    } catch (UnsupportedOperationException expected) {
      assertThat(expected).hasMessage("foo is overloaded in " + OverloadedMethod.class.toString());
    }
  }

  interface ExtendedInterface extends TestInterface {
  }

  @Test public void bindExtendedInterface() {
    try {
      duktape.bind("value", ExtendedInterface.class, new ExtendedInterface() {
        @Override public String getValue() {
          return "nope";
        }
      });
      fail();
    } catch (UnsupportedOperationException expected) {
      assertThat(expected)
          .hasMessage(ExtendedInterface.class.toString() + " must not extend other interfaces");
    }
  }

  @Test public void bindFailureLeavesDuktapeConsistent() {
    duktape.bind("value", TestInterface.class, new TestInterface() {
      @Override public String getValue() {
        return "8675309";
      }
    });
    duktape.evaluate("var localVar = 42;");

    try {
      duktape.bind("illegal", UnsupportedArgumentType.class, new UnsupportedArgumentType() {
        @Override public void set(Date d) {
        }
      });
      fail();
    } catch (IllegalArgumentException expected) {
    }

    // The state of our Duktape context is still valid, containing what was there before.
    assertThat(duktape.evaluate("value.getValue();")).isEqualTo("8675309");
    assertThat(duktape.evaluate("localVar.toString();")).isEqualTo("42");
  }
}
