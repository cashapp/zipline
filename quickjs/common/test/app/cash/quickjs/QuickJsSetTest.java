/*
 * Copyright (C) 2019 Square, Inc.
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

import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

public final class QuickJsSetTest {
  private QuickJs quickJs;

  @Before public void setUp() {
    quickJs = QuickJs.create();
  }

  @After public void tearDown() {
    quickJs.close();
  }

  @Test public void setNonInterface() {
    try {
      quickJs.set("s", String.class, "foo");
      fail();
    } catch (UnsupportedOperationException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Only interfaces can be bound. Received: class java.lang.String");
    }
  }

  interface TestInterface {
    String getValue();
  }

  @Test public void callMethodOnJavaObject() {
    quickJs.set("value", TestInterface.class, new TestInterface() {
      @Override
      public String getValue() {
        return "8675309";
      }
    });
    assertThat(quickJs.evaluate("value.getValue();")).isEqualTo("8675309");
  }

  @Test public void callMissingMethodOnJavaObjectFails() {
    quickJs.set("value", TestInterface.class, new TestInterface() {
      @Override
      public String getValue() {
        throw new AssertionError();
      }
    });
    try {
      quickJs.evaluate("value.increment()");
      fail();
    } catch (QuickJsException expected) {
      assertThat(expected).hasMessageThat().contains("not a function");
    }
  }

  @Test public void setSameNameTwiceFails() {
    quickJs.set("value", TestInterface.class, new TestInterface() {
      @Override public String getValue() {
        return "foo";
      }
    });
    try {
      quickJs.set("value", TestInterface.class, new TestInterface() {
        @Override
        public String getValue() {
          throw new AssertionError();
        }
      });
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("A global object called value already exists");
    }
  }

  @Test public void exceptionsFromJavaWithUnifiedStackTrace() {
    TestInterface boundObject = new TestInterface() {
      @Override public String getValue() {
        throw new UnsupportedOperationException("Cannot getValue");
      }
    };
    quickJs.set("value", TestInterface.class, boundObject);
    try {
      quickJs.evaluate("\n"
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
      assertThat(e).hasMessageThat().isEqualTo("Cannot getValue");

      StackTraceElement[] stackTrace = e.getStackTrace();

      // Top entry is what threw - the TestInterface implementation.
      assertThat(stackTrace[0].getClassName()).isEqualTo(boundObject.getClass().getName());
      assertThat(stackTrace[0].getMethodName()).isEqualTo("getValue");

      // The next four entries are JavaScript
      assertThat(stackTrace[1].toString()).isEqualTo("JavaScript.getValue(native)");
      assertThat(stackTrace[2].toString()).isEqualTo("JavaScript.f2(test.js:10)");
      assertThat(stackTrace[3].toString()).isEqualTo("JavaScript.f1(test.js:5)");
      assertThat(stackTrace[4].toString()).isEqualTo("JavaScript.<eval>(test.js:2)");

      // Then one or two native QuickJs.evaluate methods, followed by QuickJs.evaluate in Java.
      int i = 5;
      assertThat(stackTrace[i].getClassName()).isEqualTo(QuickJs.class.getName());
      assertThat(stackTrace[i].getMethodName()).isEqualTo("evaluate");
      assertThat(stackTrace[i].isNativeMethod()).isTrue();
      while (stackTrace[i].getMethodName().equals("evaluate")) {
        i++;
      }
      assertThat(stackTrace[i - 1].isNativeMethod()).isFalse();

      // Then this test method.
      assertThat(stackTrace[i].getClassName()).isEqualTo(QuickJsSetTest.class.getName());
      assertThat(stackTrace[i].getMethodName())
          .isEqualTo("exceptionsFromJavaWithUnifiedStackTrace");
    }
  }

  interface TestInterfaceArgs {
    String foo(String a, String b, String c);
  }

  @Test public void callMethodOnJavaObjectThrowsJavaException() {
    quickJs.set("value", TestInterface.class, new TestInterface() {
      @Override public String getValue() {
        throw new UnsupportedOperationException("This is an error message.");
      }
    });
    try {
      quickJs.evaluate("value.getValue()");
      fail();
    } catch (UnsupportedOperationException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("This is an error message.");
    }
  }

  @Test public void callMethodWithArgsOnJavaObject() {
    quickJs.set("value", TestInterfaceArgs.class, new TestInterfaceArgs() {
      @Override public String foo(String a, String b, String c) {
        return a != null ? a + b + c : null;
      }
    });

    assertThat(quickJs.evaluate("value.foo('This', ' is a ', 'test')")).isEqualTo("This is a test");
    assertThat(quickJs.evaluate("value.foo(null, null, null)")).isNull();

    try {
      quickJs.evaluate("value.foo('This')");
      fail();
    } catch (QuickJsException expected) {
      assertThat(expected.getMessage()).isEqualTo("Wrong number of arguments");
    }
    try {
      quickJs.evaluate("value.foo('This', ' is ', 'too ', 'many ', 'arguments')");
      fail();
    } catch (QuickJsException expected) {
      assertThat(expected.getMessage()).isEqualTo("Wrong number of arguments");
    }
    try {
      quickJs.evaluate("value.foo('1', '2', 3)");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).startsWith("Cannot convert value 3 to String");
    }
  }

  interface TestInterfaceVoids {
    void func();

    String getResult();
  }

  @Test public void callVoidJavaMethod() {
    quickJs.set("value", TestInterfaceVoids.class, new TestInterfaceVoids() {
      String result = "not called";

      @Override public void func() {
        result = "called";
      }

      @Override public String getResult() {
        return result;
      }
    });

    assertThat(quickJs.evaluate("value.getResult()")).isEqualTo("not called");
    assertThat(quickJs.evaluate("value.func()")).isNull();
    assertThat(quickJs.evaluate("value.getResult()")).isEqualTo("called");
  }

  interface TestPrimitiveTypes {
    boolean b(boolean b);

    int i(int i);

    double d(double d);
  }

  // Verify that primitive types can be used as both arguments and return values from Java methods.
  @Test public void callJavaMethodWithPrimitiveTypes() {
    quickJs.set("value", TestPrimitiveTypes.class, new TestPrimitiveTypes() {
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
    assertThat(quickJs.evaluate("value.b(false).toString()")).isEqualTo("true");
    assertThat(quickJs.evaluate("value.i(4).toString()")).isEqualTo("16");
    assertThat(quickJs.evaluate("value.d(6.28318).toString()")).isEqualTo("3.14159");
  }

  interface TestMultipleArgTypes {
    String print(boolean b, int i, double d);
  }

  // Double check that arguments of different types are processed in the correct order from the
  // QuickJs stack.
  @Test public void callJavaMethodWithAllArgTypes() {
    quickJs.set("printer", TestMultipleArgTypes.class, new TestMultipleArgTypes() {
      @Override public String print(boolean b, int i, double d) {
        return String.format("boolean: %s, int: %s, double: %s", b, i, d);
      }
    });
    assertThat(quickJs.evaluate("printer.print(true, 42, 2.718281828459)"))
        .isEqualTo("boolean: true, int: 42, double: 2.718281828459");
  }

  interface TestBoxedPrimitiveArgTypes {
    Boolean b(Boolean b);

    Integer i(Integer i);

    Double d(Double d);
  }

  @Test public void callJavaMethodWithBoxedPrimitiveTypes() {
    quickJs.set("value", TestBoxedPrimitiveArgTypes.class, new TestBoxedPrimitiveArgTypes() {
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
    assertThat(quickJs.evaluate("value.b(false).toString()")).isEqualTo("true");
    assertThat(quickJs.evaluate("value.i(4).toString()")).isEqualTo("16");
    assertThat(quickJs.evaluate("value.d(6.28318).toString()")).isEqualTo("3.14159");

    assertThat(quickJs.evaluate("value.b(null)")).isNull();
    assertThat(quickJs.evaluate("value.i(null)")).isNull();
    assertThat(quickJs.evaluate("value.d(null)")).isNull();
  }

  interface UnsupportedReturnType {
    Date get();
  }

  @Test public void setUnsupportedReturnType() {
    try {
      quickJs.set("value", UnsupportedReturnType.class, new UnsupportedReturnType() {
        @Override public Date get() {
          return null;
        }
      });
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("Unsupported Java type java.util.Date");
    }
  }

  interface UnsupportedArgumentType {
    void set(Date d);
  }

  @Test public void setUnsupportedArgumentType() {
    try {
      quickJs.set("value", UnsupportedArgumentType.class, new UnsupportedArgumentType() {
        @Override public void set(Date d) {
        }
      });
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("Unsupported Java type java.util.Date");
    }
  }

  interface OverloadedMethod {
    void foo(int i);

    void foo(double d);
  }

  @Test public void setOverloadedMethod() {
    try {
      quickJs.set("value", OverloadedMethod.class, new OverloadedMethod() {
        @Override public void foo(int i) {
        }

        @Override public void foo(double d) {
        }
      });
      fail();
    } catch (UnsupportedOperationException expected) {
      assertThat(expected)
          .hasMessageThat().isEqualTo("foo is overloaded in " + OverloadedMethod.class.toString());
    }
  }

  interface ExtendedInterface extends TestInterface {
  }

  @Test public void setExtendedInterface() {
    try {
      quickJs.set("value", ExtendedInterface.class, new ExtendedInterface() {
        @Override public String getValue() {
          return "nope";
        }
      });
      fail();
    } catch (UnsupportedOperationException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo(ExtendedInterface.class.toString() + " must not extend other interfaces");
    }
  }

  @Test public void setFailureLeavesQuickJsConsistent() {
    quickJs.set("value", TestInterface.class, new TestInterface() {
      @Override public String getValue() {
        return "8675309";
      }
    });
    quickJs.evaluate("var localVar = 42;");

    try {
      quickJs.set("illegal", UnsupportedArgumentType.class, new UnsupportedArgumentType() {
        @Override public void set(Date d) {
        }
      });
      fail();
    } catch (IllegalArgumentException expected) {
    }

    // The state of our QuickJs context is still valid, containing what was there before.
    assertThat(quickJs.evaluate("value.getValue();")).isEqualTo("8675309");
    assertThat(quickJs.evaluate("localVar.toString();")).isEqualTo("42");
  }

  interface TestMultipleObjectArgs {
    Object print(Object b, Object i, Object d);
  }

  // Double check that arguments of different types are processed in the correct order from the
  // QuickJs stack.
  @Test public void callJavaMethodObjectArgs() {
    quickJs.set("printer", TestMultipleObjectArgs.class, new TestMultipleObjectArgs() {
      @Override public Object print(Object b, Object i, Object d) {
        return String.format("boolean: %s, int: %s, double: %s", b, i, d);
      }
    });
    assertThat(quickJs.evaluate("printer.print(true, 42, 2.718281828459)"))
        .isEqualTo("boolean: true, int: 42, double: 2.718281828459");
  }

  @Test public void passUnsupportedTypeAsObjectFails() {
    quickJs.set("printer", TestMultipleObjectArgs.class, new TestMultipleObjectArgs() {
      @Override public Object print(Object b, Object i, Object d) {
        return String.format("boolean: %s, int: %s, double: %s", b, i, d);
      }
    });
    try {
      quickJs.evaluate("printer.print(true, 42, new Date())");
      fail();
    } catch (QuickJsException expected) {
      assertThat(expected.getMessage()).contains("Cannot marshal");
    }
  }

  interface TestVarArgs {
    String format(String format, Object... args);
  }

  @Test public void callVarArgMethod() {
    quickJs.set("formatter", TestVarArgs.class, new TestVarArgs() {
      @Override public String format(String format, Object... args) {
        return String.format(format, args);
      }
    });

    assertThat(quickJs.evaluate("formatter.format('okay')")).isEqualTo("okay");
    assertThat(quickJs.evaluate(""
        + "formatter.format('%s - %s: %s', '1999-12-31 23:59:59.999', 'FATAL', 'failure');"))
        .isEqualTo("1999-12-31 23:59:59.999 - FATAL: failure");
    assertThat(quickJs.evaluate("formatter.format('%s %s', 'three', [1, 2, 3])").toString())
        .startsWith("three [Ljava.lang.Object;");
  }

  interface Summer {
    int sumIntegers(int... args);

    double sumDoubles(double... args);

    int countTrues(boolean... args);
  }

  @Test public void callVarArgPrimitiveMethod() {
    quickJs.set("Summer", Summer.class, new Summer() {
      @Override public int sumIntegers(int... args) {
        int v = 0;
        for (int arg : args) {
          v += arg;
        }
        return v;
      }

      @Override public double sumDoubles(double... args) {
        double v = 0;
        for (double arg : args) {
          v += arg;
        }
        return v;
      }

      @Override public int countTrues(boolean... args) {
        int v = 0;
        for (boolean arg : args) {
          v += arg ? 1 : 0;
        }
        return v;
      }
    });

    assertThat(quickJs.evaluate("Summer.sumIntegers()")).isEqualTo(0);
    assertThat(quickJs.evaluate("Summer.sumIntegers(1, 2, 3, 4)")).isEqualTo(10);
    try {
      quickJs.evaluate("Summer.sumIntegers(1, 2, 'three', 4)");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Cannot convert value three to int");
    }

    assertThat(quickJs.evaluate("Summer.sumDoubles()")).isEqualTo(0);
    assertThat(quickJs.evaluate("Summer.sumDoubles(0.5, 2.5, 3, 4)")).isEqualTo(10);
    try {
      quickJs.evaluate("Summer.sumDoubles(1, 2, 'three', 4)");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Cannot convert value three to double");
    }

    assertThat(quickJs.evaluate("Summer.countTrues()")).isEqualTo(0);
    assertThat(quickJs.evaluate("Summer.countTrues(true, true, false, true)")).isEqualTo(3);
    try {
      quickJs.evaluate("Summer.countTrues(true, false, 'ninja', true)");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Cannot convert value ninja to boolean");
    }
  }

  interface ObjectSorter {
    Object[] sort(Object[] args);
  }

  @Test public void arraysOfObjects() {
    quickJs.set("Sorter", ObjectSorter.class, new ObjectSorter() {
      @Override
      public Object[] sort(Object[] args) {
        if (args == null) return null;
        Arrays.sort(args, new Comparator<Object>() {
          @Override
          public int compare(Object lhs, Object rhs) {
            if (lhs == null) return -1;
            if (rhs == null) return 1;
            return ((Comparable<Object>) lhs).compareTo(rhs);
          }
        });
        return args;
      }
    });

    assertThat(quickJs.evaluate("Sorter.sort(null)")).isNull();
    assertArrayEquals((Object[]) quickJs.evaluate("Sorter.sort([2, 4, 3, 1])"),
        new Object[] { 1, 2, 3, 4 });

    assertArrayEquals((Object[]) quickJs.evaluate("Sorter.sort(['b', 'd', null, 'a'])"),
        new String[] { null, "a", "b", "d" });

    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("GMT+0:00"));
      quickJs.evaluate("Sorter.sort([ 1, 2, 3, new Date(0) ])");
      fail();
    } catch (QuickJsException expected) {
      assertThat(expected)
          .hasMessageThat()
          .contains("Cannot marshal");
    } finally {
      TimeZone.setDefault(original);
    }
  }

  interface StringSorter {
    String[] sort(String[] args);
  }

  @Test public void arraysOfStrings() {
    quickJs.set("Sorter", StringSorter.class, new StringSorter() {
      @Override public String[] sort(String[] args) {
        Arrays.sort(args);
        return args;
      }
    });

    assertArrayEquals((Object[]) quickJs.evaluate("Sorter.sort(['b', 'd', 'c', 'a'])"),
        new String[] { "a", "b", "c", "d" });

    try {
      quickJs.evaluate("Sorter.sort(['b', 'd', 3, 'a'])");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Cannot convert value 3 to String");
    }
  }

  interface DoubleSorter {
    double[] sort(double[] args);

    Double[] sortNullsFirst(Double[] args);
  }

  @Test public void arraysOfDoubles() {
    quickJs.set("Sorter", DoubleSorter.class, new DoubleSorter() {
      @Override public double[] sort(double[] args) {
        Arrays.sort(args);
        return args;
      }

      @Override public Double[] sortNullsFirst(Double[] args) {
        Arrays.sort(args, new Comparator<Double>() {
          @Override public int compare(Double lhs, Double rhs) {
            if (lhs == null) return -1;
            if (rhs == null) return 1;
            return lhs.compareTo(rhs);
          }
        });
        return args;
      }
    });

    assertArrayEquals((Object[]) quickJs.evaluate("Sorter.sort([2.9, 2.3, 3, 1])"),
        new Object[] { 1, 2.3, 2.9, 3 });

    try {
      quickJs.evaluate("Sorter.sort([2.3, 4, null, 1])");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Cannot convert value null to double");
    }

    assertArrayEquals((Object[]) quickJs.evaluate("Sorter.sortNullsFirst([2.9, null, 3, 1])"),
        new Object[] { null, 1, 2.9, 3 });
  }

  interface IntSorter {
    int[] sort(int[] args);

    Integer[] sortNullsFirst(Integer[] args);
  }

  @Test public void arraysOfInts() {
    quickJs.set("Sorter", IntSorter.class, new IntSorter() {
      @Override public int[] sort(int[] args) {
        Arrays.sort(args);
        return args;
      }

      @Override public Integer[] sortNullsFirst(Integer[] args) {
        Arrays.sort(args, new Comparator<Integer>() {
          @Override public int compare(Integer lhs, Integer rhs) {
            if (lhs == null) return -1;
            if (rhs == null) return 1;
            return lhs.compareTo(rhs);
          }
        });
        return args;
      }
    });

    assertArrayEquals((Object[]) quickJs.evaluate("Sorter.sort([2, 4, 3, 1])"),
        new Object[] { 1, 2, 3, 4 });

    try {
      quickJs.evaluate("Sorter.sort([2, 4, null, 1])");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Cannot convert value null to int");
    }

    assertArrayEquals((Object[]) quickJs.evaluate("Sorter.sort([2, 4, 3, 1])"),
        new Object[] { 1, 2, 3, 4 });

    assertArrayEquals((Object[]) quickJs.evaluate("Sorter.sortNullsFirst([2, null, 3, 1])"),
        new Object[] { null, 1, 2, 3 });
  }

  interface BoolSorter {
    boolean[] sort(boolean[] args);

    Boolean[] sortNullsFirst(Boolean[] args);
  }

  @Test public void arraysOfBooleans() {
    quickJs.set("Sorter", BoolSorter.class, new BoolSorter() {
      @Override public boolean[] sort(boolean[] args) {
        int count = 0;
        for (boolean arg : args) {
          if (arg) count++;
        }
        boolean[] result = new boolean[args.length];
        for (int i = args.length - 1; i >= count; i--) {
          result[i] = true;
        }
        return result;
      }

      @Override public Boolean[] sortNullsFirst(Boolean[] args) {
        Arrays.sort(args, new Comparator<Boolean>() {
          @Override public int compare(Boolean lhs, Boolean rhs) {
            if (lhs == null) return -1;
            if (rhs == null) return 1;
            return lhs.compareTo(rhs);
          }
        });
        return args;
      }
    });

    assertArrayEquals((Object[]) quickJs.evaluate("Sorter.sort([ true, false, true, false ])"),
        new Object[] { false, false, true, true });

    try {
      quickJs.evaluate("Sorter.sort([false, true, null, false])");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Cannot convert value null to boolean");
    }

    assertArrayEquals(
        (Object[]) quickJs.evaluate("Sorter.sortNullsFirst([true, false, true, null])"),
        new Object[] { null, false, true, true });
  }

  @Test public void lotsOfLocalTemps() {
    quickJs.set("foo", TestInterfaceArgs.class, new TestInterfaceArgs() {
      @Override public String foo(String a, String b, String c) {
        return a + b + c;
      }
    });

    Object result = quickJs.evaluate(""
        + "var len = 0;\n"
        + "for (var i = 0; i < 100000; i++) {\n"
        + "  var s = foo.foo('a', 'b', 'c');\n"
        + "  len += s.length;\n"
        + "}\n"
        + "len;\n");
    assertThat(result).isEqualTo(300000);
  }

  // https://github.com/square/duktape-android/issues/95
  interface HugeInterface {
    String method01(String a, String b, String c, String d, String e, String f, String g, String h);

    String method02(String a, String b, String c, String d, String e, String f, String g, String h);

    String method03(String a, String b, String c, String d, String e, String f, String g, String h);

    String method04(String a, String b, String c, String d, String e, String f, String g, String h);

    String method05(String a, String b, String c, String d, String e, String f, String g, String h);

    String method06(String a, String b, String c, String d, String e, String f, String g, String h);

    String method07(String a, String b, String c, String d, String e, String f, String g, String h);

    String method08(String a, String b, String c, String d, String e, String f, String g, String h);

    String method09(String a, String b, String c, String d, String e, String f, String g, String h);

    String method10(String a, String b, String c, String d, String e, String f, String g, String h);

    String method11(String a, String b, String c, String d, String e, String f, String g, String h);

    String method12(String a, String b, String c, String d, String e, String f, String g, String h);

    String method13(String a, String b, String c, String d, String e, String f, String g, String h);

    String method14(String a, String b, String c, String d, String e, String f, String g, String h);

    String method15(String a, String b, String c, String d, String e, String f, String g, String h);

    String method16(String a, String b, String c, String d, String e, String f, String g, String h);

    String method17(String a, String b, String c, String d, String e, String f, String g, String h);

    String method18(String a, String b, String c, String d, String e, String f, String g, String h);

    String method19(String a, String b, String c, String d, String e, String f, String g, String h);

    String method20(String a, String b, String c, String d, String e, String f, String g, String h);

    String method21(String a, String b, String c, String d, String e, String f, String g, String h);

    String method22(String a, String b, String c, String d, String e, String f, String g, String h);

    String method23(String a, String b, String c, String d, String e, String f, String g, String h);

    String method24(String a, String b, String c, String d, String e, String f, String g, String h);

    String method25(String a, String b, String c, String d, String e, String f, String g, String h);

    String method26(String a, String b, String c, String d, String e, String f, String g, String h);

    String method27(String a, String b, String c, String d, String e, String f, String g, String h);

    String method28(String a, String b, String c, String d, String e, String f, String g, String h);

    String method29(String a, String b, String c, String d, String e, String f, String g, String h);

    String method30(String a, String b, String c, String d, String e, String f, String g, String h);

    String method31(String a, String b, String c, String d, String e, String f, String g, String h);

    String method32(String a, String b, String c, String d, String e, String f, String g, String h);

    String method33(String a, String b, String c, String d, String e, String f, String g, String h);

    String method34(String a, String b, String c, String d, String e, String f, String g, String h);

    String method35(String a, String b, String c, String d, String e, String f, String g, String h);

    String method36(String a, String b, String c, String d, String e, String f, String g, String h);

    String method37(String a, String b, String c, String d, String e, String f, String g, String h);

    String method38(String a, String b, String c, String d, String e, String f, String g, String h);

    String method39(String a, String b, String c, String d, String e, String f, String g, String h);

    String method40(String a, String b, String c, String d, String e, String f, String g, String h);

    String method41(String a, String b, String c, String d, String e, String f, String g, String h);

    String method42(String a, String b, String c, String d, String e, String f, String g, String h);

    String method43(String a, String b, String c, String d, String e, String f, String g, String h);

    String method44(String a, String b, String c, String d, String e, String f, String g, String h);

    String method45(String a, String b, String c, String d, String e, String f, String g, String h);

    String method46(String a, String b, String c, String d, String e, String f, String g, String h);

    String method47(String a, String b, String c, String d, String e, String f, String g, String h);

    String method48(String a, String b, String c, String d, String e, String f, String g, String h);

    String method49(String a, String b, String c, String d, String e, String f, String g, String h);

    String method50(String a, String b, String c, String d, String e, String f, String g, String h);
  }

  @Test public void lotsOfInterfaceMethodsAndArgs() {
    quickJs.set("foo", HugeInterface.class, new HugeInterface() {
      @Override
      public String method01(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method02(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method03(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method04(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method05(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method06(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method07(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method08(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method09(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method10(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method11(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method12(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method13(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method14(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method15(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method16(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method17(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method18(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method19(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method20(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method21(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method22(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method23(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method24(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method25(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method26(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method27(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method28(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method29(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method30(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method31(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method32(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method33(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method34(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method35(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method36(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method37(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method38(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method39(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method40(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method41(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method42(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method43(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method44(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method45(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method46(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method47(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method48(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method49(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return null;
      }

      @Override
      public String method50(String a, String b, String c, String d, String e, String f, String g,
          String h) {
        return "method50" + a + b + c + d + e + f + g + h;
      }
    });

    Object result = quickJs.evaluate("foo.method50('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h')\n");
    assertThat(result).isEqualTo("method50abcdefgh");
  }
}
