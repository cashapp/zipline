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

import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

public final class QuickJsGetTest {
  private QuickJs quickJs;

  @Before public void setUp() {
    quickJs = QuickJs.create();
  }

  @After public void tearDown() {
    quickJs.close();
  }

  @Test public void getNonInterface() {
    try {
      quickJs.get("s", String.class);
      fail();
    } catch (UnsupportedOperationException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Only interfaces can be proxied. Received: class java.lang.String");
    }
  }

  interface TestInterface {
    String getValue();
  }

  @Test public void get() {
    quickJs.evaluate("var value = { getValue: function() { return '8675309'; } };");
    TestInterface proxy = quickJs.get("value", TestInterface.class);
    String v = proxy.getValue();
    assertThat(v).isEqualTo("8675309");
  }

  @Test public void getMissingObjectThrows() {
    try {
      quickJs.get("DoesNotExist", TestInterface.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("A global JavaScript object called DoesNotExist was not found");
    }
  }

  @Test public void getMissingMethodThrows() {
    quickJs.evaluate("var value = { getOtherValue: function() { return '8675309'; } };");
    try {
      quickJs.get("value", TestInterface.class);
      fail();
    } catch (QuickJsException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("JavaScript global value has no method called getValue");
    }
  }

  @Test public void getMethodNotCallableThrows() {
    quickJs.evaluate("var value = { getValue: '8675309' };");

    try {
      quickJs.get("value", TestInterface.class);
      fail();
    } catch (QuickJsException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("JavaScript property value.getValue not callable");
    }
  }

  @Test public void proxyCalledAfterEngineClosed() {
    quickJs.evaluate("var value = { getValue: function() { return '8675309'; } };");
    TestInterface proxy = quickJs.get("value", TestInterface.class);

    // Close the context - proxy can no longer be used.
    quickJs.close();

    try {
      proxy.getValue();
      fail();
    } catch (NullPointerException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Null QuickJs context - did you close your QuickJs?");
    }
  }

  @Test public void proxyCallThrows() {
    quickJs.evaluate("var value = { getValue: function() { throw 'nope'; } };");
    TestInterface proxy = quickJs.get("value", TestInterface.class);

    try {
      proxy.getValue();
      fail();
    } catch (QuickJsException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("nope");
    }
  }

  @Test public void proxyCallThrowsIncludeStacktrace() {
    quickJs.evaluate("function nop() { return 1; }");
    quickJs.evaluate("var value = { getValue: function() { return nope(); } };", "test.js");
    TestInterface proxy = quickJs.get("value", TestInterface.class);

    try {
      proxy.getValue();
      fail();
    } catch (QuickJsException expected) {
      assertThat(expected.getStackTrace()[0].toString()).isEqualTo("JavaScript.getValue(test.js)");
      assertThat(expected.getStackTrace()[1].toString())
          .isEqualTo("app.cash.quickjs.QuickJs.call(Native Method)");
    }
  }

  @Ignore("TODO: track JsMethodProxies.")
  @Test public void replaceProxiedObjectProxyReferencesOld() {
    quickJs.evaluate("var value = { getValue: function() { return '8675309'; } };");

    TestInterface proxy = quickJs.get("value", TestInterface.class);

    // Now replace the proxied object with a new global.
    quickJs.evaluate("value = { getValue: function() { return '7471111'; } };");

    try {
      // Calls to the old object fail.
      proxy.getValue();
      fail();
    } catch (QuickJsException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("JavaScript object value has been garbage collected");
    }

    // We can create a new proxy to the new object and call it.
    TestInterface proxy2 = quickJs.get("value", TestInterface.class);
    assertThat(proxy).isNotEqualTo(proxy2);
    assertThat(proxy2.getValue()).isEqualTo("7471111");
  }

  @Test public void replaceProxiedMethodReferencesNew() {
    quickJs.evaluate("var value = { getValue: function() { return '8675309'; } };");

    TestInterface proxy = quickJs.get("value", TestInterface.class);
    quickJs.evaluate("value.getValue = function() { return '7471111'; };");

    String v = proxy.getValue();
    assertThat(v).isEqualTo("7471111");
  }

  @Test public void getNonObjectThrows() {
    quickJs.evaluate("var value = 2;");

    try {
      quickJs.get("value", TestInterface.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("JavaScript global called value is not an object");
    }
  }

  @Test public void getSameObjectTwice() {
    quickJs.evaluate("var value = { getValue: function() { return '8675309'; } };");

    TestInterface proxy1 = quickJs.get("value", TestInterface.class);
    TestInterface proxy2 = quickJs.get("value", TestInterface.class);
    assertThat(proxy1).isNotEqualTo(proxy2);
    assertThat(proxy1.getValue()).isEqualTo(proxy2.getValue());
  }

  @Ignore("TODO: track JsMethodProxies.")
  @Test public void proxyCalledAfterObjectGarbageCollected() {
    quickJs.evaluate("var value = { getValue: function() { return '8675309'; } };");

    TestInterface proxy = quickJs.get("value", TestInterface.class);
    quickJs.evaluate("delete value;");

    try {
      proxy.getValue();
      fail();
    } catch (QuickJsException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("JavaScript object value has been garbage collected");
    }
  }

  interface UnsupportedArgumentType {
    void set(Date d);
  }

  @Test public void proxyUnsupportedArgumentType() {
    quickJs.evaluate("var value = { set: function(d) { return d.toString(); } };");

    try {
      quickJs.get("value", UnsupportedArgumentType.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("Unsupported Java type java.util.Date");
    }
  }

  interface TestPrimitiveTypes {
    boolean b(boolean b);

    double d(int i, double d);
  }

  // Verify that primitive types can be used as arguments and return values from JavaScript methods.
  @Test public void proxyWithPrimitiveTypes() {
    quickJs.evaluate("var value = {\n" +
        "  b: function(v) { return !v; },\n" +
        "  d: function(i, v) { return v / i; }\n" +
        "};");

    TestPrimitiveTypes proxy = quickJs.get("value", TestPrimitiveTypes.class);
    assertThat(proxy.b(true)).isEqualTo(false);
    assertThat(proxy.d(2, 6.28318)).isEqualTo(3.14159);
  }

  interface TestMultipleArgTypes {
    String print(boolean b, int i, double d);
  }

  // Double check that arguments of different types are processed in the correct order to the
  // stack.
  @Test public void proxyWithAllArgTypes() {
    quickJs.evaluate("var printer = {\n" +
        "  print: function(b, i, d) {\n" +
        "    return 'boolean: ' + b + ', int: ' + i + ', double: ' + d;\n" +
        "  }\n" +
        "};");
    TestMultipleArgTypes printer = quickJs.get("printer", TestMultipleArgTypes.class);
    assertThat(printer.print(true, 42, 2.718281828459))
        .isEqualTo("boolean: true, int: 42, double: 2.718281828459");
  }

  interface TestBoxedPrimitiveArgTypes {
    Boolean b(Boolean b);

    Double d(Double d);
  }

  @Test public void proxyWithBoxedPrimitiveTypes() {
    quickJs.evaluate("var value = {\n" +
        "  b: function(v) { return v != null ? !v : null; },\n" +
        "  d: function(v) { return v != null ? v / 2.0 : null; }\n" +
        "};");

    TestBoxedPrimitiveArgTypes proxy = quickJs.get("value", TestBoxedPrimitiveArgTypes.class);
    assertThat(proxy.b(false)).isEqualTo(true);
    assertThat(proxy.d(6.28318)).isEqualTo(3.14159);

    assertThat(proxy.b(null)).isNull();
    assertThat(proxy.d(null)).isNull();
  }

  interface IntegerGetter {
    int get();
  }

  @Test public void proxyMethodCanReturnInteger() {
    quickJs.evaluate("var value = {\n" +
        "  get: function() { return 2; }\n" +
        "};");

    IntegerGetter getter = quickJs.get("value", IntegerGetter.class);
    assertThat(getter.get()).isEqualTo(2);
  }

  interface PrinterFunction {
    // All JavaScript functions have a call() method. The first argument will be passed
    // as `this`.
    String call(boolean b, int i, double d);
  }

  @Test public void proxyFunctionObject() {
    quickJs.evaluate(""
        + "function printer(i, d) {\n"
        + "  return 'boolean: ' + this + ', int: ' + i + ', double: ' + d;\n"
        + "}\n");
    PrinterFunction printer = quickJs.get("printer", PrinterFunction.class);
    assertThat(printer.call(true, 42, 2.718281828459))
        .isEqualTo("boolean: true, int: 42, double: 2.718281828459");
  }

  @Test public void proxyFunctionObjectReturnsWrongType() {
    quickJs.evaluate(""
        + "function printer(i, d) {\n"
        + "  return d;\n"
        + "}\n");
    PrinterFunction printer = quickJs.get("printer", PrinterFunction.class);
    try {
      printer.call(true, 42, 2.718281828459);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Cannot convert value 2.718281828459 to String");
    }
  }

  interface TestMultipleObjectArgs {
    Object print(Object b, Object i, Object d);
  }

  @Test public void callProxyMultipleObjectArgs() {
    quickJs.evaluate("var printer = {\n" +
        "  print: function(b, i, d) {\n" +
        "    return 'boolean: ' + b + ', int: ' + i + ', double: ' + d;\n" +
        "  }\n" +
        "};");
    TestMultipleObjectArgs printer = quickJs.get("printer", TestMultipleObjectArgs.class);
    assertThat(printer.print(true, 42, 2.718281828459))
        .isEqualTo("boolean: true, int: 42, double: 2.718281828459");
  }

  @Test public void passUnsupportedTypeAsObjectFails() {
    quickJs.evaluate("var printer = {\n" +
        "  print: function(b, i, d) {\n" +
        "    return 'boolean: ' + b + ', int: ' + i + ', double: ' + d;\n" +
        "  }\n" +
        "};");
    TestMultipleObjectArgs printer = quickJs.get("printer", TestMultipleObjectArgs.class);
    try {
      printer.print(true, 42, new Date());
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat().isEqualTo("Unsupported Java type java.util.Date");
    }
  }

  interface TestVarArgs {
    String call(String separator, Object... args);
  }

  @Test public void callVarArgMethod() {
    quickJs.evaluate(""
        + "function joiner() {\n"
        + "  var result = '';\n"
        + "  for (i = 0; i < arguments.length; i++) {\n"
        + "    if (i > 0) result = result + this;\n"
        + "    result = result + arguments[i].toString();\n"
        + "  }\n"
        + "  return result.toString();\n"
        + "}");
    TestVarArgs joiner = quickJs.get("joiner", TestVarArgs.class);
    assertThat(joiner.call("-")).isEqualTo("");
    assertThat(joiner.call(" + ", 1, 2, "three")).isEqualTo("1 + 2 + three");

    try {
      joiner.call(", ", "Test", new Date(), 1.0);
      fail();
    } catch (Exception expected) {
      assertThat(expected)
          .hasMessageThat().isEqualTo("Unsupported Java type java.util.Date");
    }
  }

  interface Summer {
    double sumDoubles(double... args);

    double sumIntegers(int... args);

    double countTrues(boolean... args);
  }

  @Test public void callVarArgPrimitiveMethod() {
    quickJs.evaluate(""
        + "var summer = {\n"
        + "  sum: function() {\n"
        + "    var result = 0;\n"
        + "    for (i = 0; i < arguments.length; i++) {\n"
        + "      result = result + arguments[i];\n"
        + "    }\n"
        + "    return result;"
        + "  },\n"
        + "  sumDoubles: function() { return this.sum.apply(this, arguments); },\n"
        + "  sumIntegers: function() { return this.sum.apply(this, arguments); },\n"
        + "  countTrues: function() { return this.sum.apply(this, arguments); }\n"
        + "};");
    Summer summer = quickJs.get("summer", Summer.class);
    assertThat(summer.sumDoubles()).isEqualTo(0.0);
    assertThat(summer.sumDoubles(1, 2, 3, 4)).isEqualTo(10.0);

    assertThat(summer.sumIntegers()).isEqualTo(0.0);
    assertThat(summer.sumIntegers(1, 2, 3, 4)).isEqualTo(10.0);

    assertThat(summer.countTrues()).isEqualTo(0.0);
    assertThat(summer.countTrues(true, false, true, true)).isEqualTo(3.0);
  }

  interface ObjectSorter {
    Object[] sort(Object[] args);
  }

  private static final String SORTER_FUNCTOR = ""
      + "var Sorter = {\n"
      + "  sort: function(v) {"
      + "    if (v) {\n"
      + "      v.sort();\n"
      + "    }\n"
      + "    return v;\n"
      + "  },\n"
      + "  sortNullable: function(v) { return this.sort(v); }\n"
      + "};";

  @Test public void marshalArrayWithManyElements() {
    quickJs.evaluate(SORTER_FUNCTOR);

    ObjectSorter sorter = quickJs.get("Sorter", ObjectSorter.class);

    int length = 100000;
    assertThat(sorter.sort(new Object[length])).hasLength(length);
  }

  @Test public void arraysOfObjects() {
    quickJs.evaluate(SORTER_FUNCTOR);

    ObjectSorter sorter = quickJs.get("Sorter", ObjectSorter.class);

    assertThat(sorter.sort(null)).isNull();

    Object[] original = new Object[] { 2, 4, 3, 1 };
    Object[] sorted = sorter.sort(original);
    assertArrayEquals(sorted, new Object[] { 1, 2, 3, 4 });
    assertThat(original).isNotSameInstanceAs(sorted);

    assertArrayEquals(sorter.sort(new Object[] { "b", "d", null, "a" }),
        new String[] { "a", "b", "d", null });
  }

  interface StringSorter {
    String[] sort(String[] args);
  }

  @Test public void arraysOfStrings() {
    quickJs.evaluate(SORTER_FUNCTOR);

    StringSorter sorter = quickJs.get("Sorter", StringSorter.class);

    assertArrayEquals(sorter.sort(new String[] { "b", "d", "c", "a" }),
        new String[] { "a", "b", "c", "d" });

    assertArrayEquals(sorter.sort(new String[] { "b", "d", null, "a" }),
        new String[] { "a", "b", "d", null });

    // Replace the sorter with something broken.
    quickJs.evaluate(""
        + "Sorter.sort = function(v) {"
        + "  return [ 'a', 'b', 3, 'd' ];\n"
        + "};");
    try {
      sorter.sort(new String[] { "b", "d", "c", "a" });
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat().isEqualTo("Cannot convert value 3 to String");
    }
  }

  interface DoubleSorter {
    double[] sort(double[] args);
  }

  @Test public void arraysOfDoubles() {
    quickJs.evaluate(SORTER_FUNCTOR);

    DoubleSorter sorter = quickJs.get("Sorter", DoubleSorter.class);

    assertArrayEquals(sorter.sort(new double[] { 2.9, 2.3, 3, 1 }),
        new double[] { 1.0, 2.3, 2.9, 3.0 }, 0.0);

    // Replace the sorter with something broken.
    quickJs.evaluate(""
        + "Sorter.sort = function(v) {"
        + "  return [ 1, 2, null, 4 ];\n"
        + "};");
    try {
      sorter.sort(new double[0]);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat().isEqualTo("Cannot convert value null to double");
    }
  }

  // Note, we return double[] and Double[] since we can't return ints from JavaScript.
  interface IntSorter {
    double[] sort(int[] args);

    Double[] sortNullable(Integer[] args);
  }

  @Test public void arraysOfInts() {
    quickJs.evaluate(SORTER_FUNCTOR);

    IntSorter sorter = quickJs.get("Sorter", IntSorter.class);

    assertArrayEquals(sorter.sort(new int[] { 2, 4, 3, 1 }),
        new double[] { 1.0, 2.0, 3.0, 4.0 }, 0.0);

    assertArrayEquals(sorter.sortNullable(new Integer[] { 2, null, 3, 1 }),
        new Double[] { 1.0, 2.0, 3.0, null });

    // Replace the sorter with something broken.
    quickJs.evaluate(""
        + "Sorter.sort = function(v) {"
        + "  return [ 1, 2, null, 4 ];\n"
        + "};");
    try {
      sorter.sort(new int[0]);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat().isEqualTo("Cannot convert value null to double");
    }
  }

  interface BoolSorter {
    boolean[] sort(boolean[] args);

    Boolean[] sortNullable(Boolean[] args);
  }

  @Test public void arraysOfBooleans() {
    quickJs.evaluate(SORTER_FUNCTOR);

    BoolSorter sorter = quickJs.get("Sorter", BoolSorter.class);

    assertArrayEquals(sorter.sort(new boolean[] { true, false, true, false }),
        new boolean[] { false, false, true, true });

    assertArrayEquals(sorter.sortNullable(new Boolean[] { null, true, false, true }),
        new Boolean[] { false, null, true, true });

    // Replace the sorter with something broken.
    quickJs.evaluate(""
        + "Sorter.sort = function(v) {"
        + "  return [ false, false, null, true ];\n"
        + "};");
    try {
      sorter.sort(new boolean[0]);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat().isEqualTo("Cannot convert value null to boolean");
    }
  }

  interface MatrixTransposer {
    Double[][] call(Object o, Double[][] matrix);
  }

  @Test public void twoDimensionalArrays() {
    quickJs.evaluate(""
        + "function transpose(matrix) {\n"
        + "  return matrix[0].map(function(col, i) {\n"
        + "      return matrix.map(function(row) {\n"
        + "        return row[i];\n"
        + "      })\n"
        + "    });\n"
        + "};\n");

    MatrixTransposer transposer = quickJs.get("transpose", MatrixTransposer.class);

    Double[][] matrix = new Double[2][2];
    matrix[0][0] = 1.0;
    matrix[0][1] = 2.0;
    matrix[1][0] = 3.0;
    matrix[1][1] = 4.0;

    Double[][] expected = new Double[2][2];
    expected[0][0] = 1.0;
    expected[1][0] = 2.0;
    expected[0][1] = 3.0;
    expected[1][1] = 4.0;

    Double[][] result = transposer.call(null, matrix);
    assertArrayEquals(expected, result);
  }
}
