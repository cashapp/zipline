/*
 * Copyright (C) 2016 Square, Inc.
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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class DuktapeGetTest {
  private Duktape duktape;

  @Before public void setUp() {
    duktape = Duktape.create();
  }

  @After public void tearDown() {
    duktape.close();
  }

  @Test public void getNonInterface() {
    try {
      duktape.get("s", String.class);
      fail();
    } catch (UnsupportedOperationException expected) {
      assertThat(expected)
          .hasMessage("Only interfaces can be proxied. Received: class java.lang.String");
    }
  }

  interface TestInterface {
    String getValue();
  }

  @Test public void get() {
    duktape.evaluate("var value = { getValue: function() { return '8675309'; } };");
    TestInterface proxy = duktape.get("value", TestInterface.class);
    String v = proxy.getValue();
    assertThat(v).isEqualTo("8675309");
  }

  @Test public void getMissingObjectThrows() {
    try {
      duktape.get("DoesNotExist", TestInterface.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessage("A global JavaScript object called DoesNotExist was not found");
    }
  }

  @Test public void getMissingMethodThrows() {
    duktape.evaluate("var value = { getOtherValue: function() { return '8675309'; } };");
    try {
      duktape.get("value", TestInterface.class);
      fail();
    } catch (DuktapeException expected) {
      assertThat(expected).hasMessage("JavaScript global value has no method called getValue");
    }
  }

  @Test public void getMethodNotCallableThrows() {
    duktape.evaluate("var value = { getValue: '8675309' };");

    try {
      duktape.get("value", TestInterface.class);
      fail();
    } catch (DuktapeException expected) {
      assertThat(expected).hasMessage("JavaScript property value.getValue not callable");
    }
  }

  @Test public void proxyCalledAfterDuktapeClosed() {
    duktape.evaluate("var value = { getValue: function() { return '8675309'; } };");
    TestInterface proxy = duktape.get("value", TestInterface.class);

    // Close the context - proxy can no longer be used.
    duktape.close();

    try {
      proxy.getValue();
      fail();
    } catch (NullPointerException expected) {
      assertThat(expected).hasMessage("Null Duktape context - did you close your Duktape?");
    }
  }

  @Test public void proxyCallThrows() {
    duktape.evaluate("var value = { getValue: function() { throw 'nope'; } };");
    TestInterface proxy = duktape.get("value", TestInterface.class);

    try {
      proxy.getValue();
      fail();
    } catch (DuktapeException expected) {
      assertThat(expected).hasMessage("nope");
    }
  }

  @Test public void replaceProxiedObjectProxyReferencesOld() {
    duktape.evaluate("var value = { getValue: function() { return '8675309'; } };");

    TestInterface proxy = duktape.get("value", TestInterface.class);

    // Now replace the proxied object with a new global.
    duktape.evaluate("value = { getValue: function() { return '7471111'; } };");

    try {
      // Calls to the old object fail.
      proxy.getValue();
      fail();
    } catch (DuktapeException expected) {
      assertThat(expected).hasMessage("JavaScript object value has been garbage collected");
    }

    // We can create a new proxy to the new object and call it.
    TestInterface proxy2 = duktape.get("value", TestInterface.class);
    assertThat(proxy).isNotEqualTo(proxy2);
    assertThat(proxy2.getValue()).isEqualTo("7471111");
  }

  @Test public void replaceProxiedMethodReferencesNew() {
    duktape.evaluate("var value = { getValue: function() { return '8675309'; } };");

    TestInterface proxy = duktape.get("value", TestInterface.class);
    duktape.evaluate("value.getValue = function() { return '7471111'; };");

    String v = proxy.getValue();
    assertThat(v).isEqualTo("7471111");
  }

  @Test public void getNonObjectThrows() {
    duktape.evaluate("var value = 2;");

    try {
      duktape.get("value", TestInterface.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("JavaScript global called value is not an object");
    }
  }

  @Test public void getSameObjectTwice() {
    duktape.evaluate("var value = { getValue: function() { return '8675309'; } };");

    TestInterface proxy1 = duktape.get("value", TestInterface.class);
    TestInterface proxy2 = duktape.get("value", TestInterface.class);
    assertThat(proxy1).isNotEqualTo(proxy2);
    assertThat(proxy1.getValue()).isEqualTo(proxy2.getValue());
  }

  @Test public void proxyCalledAfterObjectGarbageCollected() {
    duktape.evaluate("var value = { getValue: function() { return '8675309'; } };");

    TestInterface proxy = duktape.get("value", TestInterface.class);
    duktape.evaluate("delete value;");

    try {
      proxy.getValue();
      fail();
    } catch (DuktapeException expected) {
      assertThat(expected).hasMessage("JavaScript object value has been garbage collected");
    }
  }

  interface UnsupportedArgumentType {
    void set(Date d);
  }

  @Test public void proxyUnsupportedArgumentType() {
    duktape.evaluate("var value = { set: function(d) { return d.toString(); } };");

    try {
      duktape.get("value", UnsupportedArgumentType.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage(
          "In proxied method \"value.set\": Unsupported Java type class java.util.Date");
    }
  }

  interface TestPrimitiveTypes {
    boolean b(boolean b);
    int i(int i);
    double d(double d);
  }

  // Verify that primitive types can be used as arguments and return values from JavaScript methods.
  @Test public void proxyWithPrimitiveTypes() {
    duktape.evaluate("var value = {\n" +
        "  b: function(v) { return !v; },\n" +
        "  i: function(v) { return v * v; },\n" +
        "  d: function(v) { return v / 2.0; }\n" +
        "};");

    TestPrimitiveTypes proxy = duktape.get("value", TestPrimitiveTypes.class);
    assertThat(proxy.b(true)).isEqualTo(false);
    assertThat(proxy.i(4)).isEqualTo(16);
    assertThat(proxy.d(6.28318)).isWithin(0.0001).of(3.14159);
  }

  interface TestMultipleArgTypes {
    String print(boolean b, int i, double d);
  }

  // Double check that arguments of different types are processed in the correct order to the
  // Duktape stack.
  @Test public void proxyWithAllArgTypes() {
    duktape.evaluate("var printer = {\n" +
        "  print: function(b, i, d) {\n" +
        "    return 'boolean: ' + b + ', int: ' + i + ', double: ' + d;\n" +
        "  }\n" +
        "};");
    TestMultipleArgTypes printer = duktape.get("printer", TestMultipleArgTypes.class);
    assertThat(printer.print(true, 42, 2.718281828459))
        .isEqualTo("boolean: true, int: 42, double: 2.718281828459");
  }

  interface TestBoxedPrimitiveArgTypes {
    Boolean b(Boolean b);
    Integer i(Integer i);
    Double d(Double d);
  }

  @Test public void proxyWithBoxedPrimitiveTypes() {
    duktape.evaluate("var value = {\n" +
        "  b: function(v) { return v != null ? !v : null; },\n" +
        "  i: function(v) { return v != null ? v * v : null; },\n" +
        "  d: function(v) { return v != null ? v / 2.0 : null; }\n" +
        "};");

    TestBoxedPrimitiveArgTypes proxy = duktape.get("value", TestBoxedPrimitiveArgTypes.class);
    assertThat(proxy.b(false)).isEqualTo(true);
    assertThat(proxy.i(4)).isEqualTo(16);
    assertThat(proxy.d(6.28318)).isWithin(0.0001).of(3.14159);

    assertThat(proxy.b(null)).isNull();
    assertThat(proxy.i(null)).isNull();
    assertThat(proxy.d(null)).isNull();
  }

  interface PrinterFunction {
    // All JavaScript functions have a call() method. The first argument will be passed
    // as `this`.
    String call(boolean b, int i, double d);
  }

  @Test public void proxyFunctionObject() {
    duktape.evaluate(""
        + "function printer(i, d) {\n"
        + "  return 'boolean: ' + this + ', int: ' + i + ', double: ' + d;\n"
        + "}\n");
    PrinterFunction printer = duktape.get("printer", PrinterFunction.class);
    assertThat(printer.call(true, 42, 2.718281828459))
        .isEqualTo("boolean: true, int: 42, double: 2.718281828459");
  }
}
