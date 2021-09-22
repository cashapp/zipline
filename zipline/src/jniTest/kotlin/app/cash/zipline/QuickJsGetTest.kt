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
package app.cash.zipline

import java.util.Date
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test

class QuickJsGetTest {
  private val quickJs = QuickJs.create()

  @After fun tearDown() {
    quickJs.close()
  }

  @Test fun getNonInterface() {
    val t = assertThrows<UnsupportedOperationException> {
      quickJs.get("s", String::class)
    }
    assertEquals("Only interfaces can be proxied. Received: class java.lang.String", t.message)
  }

  internal interface TestInterface {
    val value: String
  }

  @Test fun get() {
    quickJs.evaluate("var value = { getValue: function() { return '8675309'; } };")
    val proxy = quickJs.get("value", TestInterface::class)
    assertEquals("8675309", proxy.value)
  }

  @Test fun getMissingObjectThrows() {
    val t = assertThrows<IllegalArgumentException> {
      quickJs.get("DoesNotExist", TestInterface::class)
    }
    assertEquals("A global JavaScript object called DoesNotExist was not found", t.message)
  }

  @Test fun getMissingMethodThrows() {
    quickJs.evaluate("var value = { getOtherValue: function() { return '8675309'; } };")
    val t = assertThrows<QuickJsException> {
      quickJs.get("value", TestInterface::class)
    }
    assertEquals("JavaScript global value has no method called getValue", t.message)
  }

  @Test fun getMethodNotCallableThrows() {
    quickJs.evaluate("var value = { getValue: '8675309' };")
    val t = assertThrows<QuickJsException> {
      quickJs.get("value", TestInterface::class)
    }
    assertEquals("JavaScript property value.getValue not callable", t.message)
  }

  @Test fun proxyCalledAfterEngineClosed() {
    quickJs.evaluate("var value = { getValue: function() { return '8675309'; } };")
    val proxy = quickJs.get("value", TestInterface::class)

    // Close the context - proxy can no longer be used.
    quickJs.close()

    val t = assertThrows<NullPointerException> {
      proxy.value
    }
    assertEquals("Null QuickJs context - did you close your QuickJs?", t.message)
  }

  @Test fun proxyCallThrows() {
    quickJs.evaluate("var value = { getValue: function() { throw 'nope'; } };")
    val proxy = quickJs.get("value", TestInterface::class)
    val t = assertThrows<QuickJsException> {
      proxy.value
    }
    assertEquals("nope", t.message)
  }

  @Test fun proxyCallThrowsIncludeStacktrace() {
    quickJs.evaluate("function nop() { return 1; }")
    quickJs.evaluate("var value = { getValue: function() { return nope(); } };", "test.js")
    val proxy = quickJs.get("value", TestInterface::class)
    val t = assertThrows<QuickJsException> {
      proxy.value
    }
    assertEquals("JavaScript.getValue(test.js)", t.stackTrace[0].toString())
    assertEquals("app.cash.zipline.QuickJs.call(Native Method)", t.stackTrace[1].toString())
  }

  @Ignore("TODO: track JsMethodProxies.")
  @Test fun replaceProxiedObjectProxyReferencesOld() {
    quickJs.evaluate("var value = { getValue: function() { return '8675309'; } };")
    val proxy = quickJs.get("value", TestInterface::class)

    // Now replace the proxied object with a new global.
    quickJs.evaluate("value = { getValue: function() { return '7471111'; } };")
    val t = assertThrows<QuickJsException> {
      // Calls to the old object fail.
      proxy.value
    }
    assertEquals("JavaScript object value has been garbage collected", t.message)

    // We can create a new proxy to the new object and call it.
    val proxy2 = quickJs.get("value", TestInterface::class)
    assertNotEquals(proxy, proxy2)
    assertEquals("7471111", proxy2.value)
  }

  @Test fun replaceProxiedMethodReferencesNew() {
    quickJs.evaluate("var value = { getValue: function() { return '8675309'; } };")
    val proxy = quickJs.get("value", TestInterface::class)
    quickJs.evaluate("value.getValue = function() { return '7471111'; };")
    assertEquals("7471111", proxy.value)
  }

  @Test fun getNonObjectThrows() {
    quickJs.evaluate("var value = 2;")
    val t = assertThrows<IllegalArgumentException> {
      quickJs.get("value", TestInterface::class)
    }
    assertEquals("JavaScript global called value is not an object", t.message)
  }

  @Test fun getSameObjectTwice() {
    quickJs.evaluate("var value = { getValue: function() { return '8675309'; } };")
    val proxy1 = quickJs.get("value", TestInterface::class)
    val proxy2 = quickJs.get("value", TestInterface::class)
    assertNotEquals(proxy1, proxy2)
    assertEquals(proxy1.value, proxy2.value)
  }

  @Ignore("TODO: track JsMethodProxies.")
  @Test fun proxyCalledAfterObjectGarbageCollected() {
    quickJs.evaluate("var value = { getValue: function() { return '8675309'; } };")
    val proxy = quickJs.get("value", TestInterface::class)
    quickJs.evaluate("delete value;")
    val t = assertThrows<QuickJsException> {
      proxy.value
    }
    assertEquals("JavaScript object value has been garbage collected", t.message)
  }

  internal interface UnsupportedArgumentType {
    fun set(d: Date?)
  }

  @Test fun proxyUnsupportedArgumentType() {
    quickJs.evaluate("var value = { set: function(d) { return d.toString(); } };")
    val t = assertThrows<IllegalArgumentException> {
      quickJs.get("value", UnsupportedArgumentType::class)
    }
    assertEquals("Unsupported Java type java.util.Date", t.message)
  }

  internal interface TestPrimitiveTypes {
    fun b(b: Boolean): Boolean
    fun d(i: Int, d: Double): Double
  }

  // Verify that primitive types can be used as arguments and return values from JavaScript methods.
  @Test fun proxyWithPrimitiveTypes() {
    quickJs.evaluate("""
      |var value = {
      |  b: function(v) { return !v; },
      |  d: function(i, v) { return v / i; }
      |};
      |""".trimMargin())
    val proxy = quickJs.get("value", TestPrimitiveTypes::class)
    assertEquals(false, proxy.b(true))
    assertEquals(3.14159, proxy.d(2, 6.28318), 0.001)
  }

  internal interface TestMultipleArgTypes {
    fun print(b: Boolean, i: Int, d: Double): String?
  }

  // Double check that arguments of different types are processed in the correct order to the stack.
  @Test fun proxyWithAllArgTypes() {
    quickJs.evaluate("""
      |var printer = {
      |  print: function(b, i, d) {
      |    return 'boolean: ' + b + ', int: ' + i + ', double: ' + d;
      |  }
      |};
      |""".trimMargin())
    val printer = quickJs.get("printer", TestMultipleArgTypes::class)
    assertEquals("boolean: true, int: 42, double: 2.718281828459", printer.print(true, 42, 2.718281828459))
  }

  internal interface TestBoxedPrimitiveArgTypes {
    fun b(b: Boolean?): Boolean?
    fun d(d: Double?): Double?
  }

  @Test fun proxyWithBoxedPrimitiveTypes() {
    quickJs.evaluate("""
      |var value = {
      |  b: function(v) { return v != null ? !v : null; },
      |  d: function(v) { return v != null ? v / 2.0 : null; }
      |};
      |""".trimMargin())
    val proxy = quickJs.get("value", TestBoxedPrimitiveArgTypes::class)
    assertEquals(true, proxy.b(false))
    assertEquals(3.14159, proxy.d(6.28318)!!, 0.001)
    assertNull(proxy.b(null))
    assertNull(proxy.d(null))
  }

  internal interface IntegerGetter {
    fun get(): Int
  }

  @Test fun proxyMethodCanReturnInteger() {
    quickJs.evaluate("""
      |var value = {
      |  get: function() { return 2; }
      |};
      |""".trimMargin())
    val getter = quickJs.get("value", IntegerGetter::class)
    assertEquals(2, getter.get())
  }

  internal interface PrinterFunction {
    // All JavaScript functions have a call() method. The first argument will be passed
    // as `this`.
    fun call(b: Boolean, i: Int, d: Double): String?
  }

  @Test fun proxyFunctionObject() {
    quickJs.evaluate("""
      |function printer(i, d) {
      |  return 'boolean: ' + this + ', int: ' + i + ', double: ' + d;
      |}
      |""".trimMargin())
    val printer = quickJs.get("printer", PrinterFunction::class)
    assertEquals("boolean: true, int: 42, double: 2.718281828459", printer.call(true, 42, 2.718281828459))
  }

  @Test fun proxyFunctionObjectReturnsWrongType() {
    quickJs.evaluate("""
      |function printer(i, d) {
      |  return d;
      |}
      |""".trimMargin())
    val printer = quickJs.get("printer", PrinterFunction::class)
    val t = assertThrows<IllegalArgumentException> {
      printer.call(true, 42, 2.718281828459)
    }
    assertEquals("Cannot convert value 2.718281828459 to String", t.message)
  }

  internal interface TestMultipleObjectArgs {
    fun print(b: Any?, i: Any?, d: Any?): Any?
  }

  @Test fun callProxyMultipleObjectArgs() {
    quickJs.evaluate("""
      |var printer = {
      |  print: function(b, i, d) {
      |    return 'boolean: ' + b + ', int: ' + i + ', double: ' + d;
      |  }
      |};
      |""".trimMargin())
    val printer = quickJs.get("printer", TestMultipleObjectArgs::class)
    assertEquals("boolean: true, int: 42, double: 2.718281828459", printer.print(true, 42, 2.718281828459))
  }

  @Test fun passUnsupportedTypeAsObjectFails() {
    quickJs.evaluate("""
      |var printer = {
      |  print: function(b, i, d) {
      |    return 'boolean: ' + b + ', int: ' + i + ', double: ' + d;
      |  }
      |};
      |""".trimMargin())
    val printer = quickJs.get("printer", TestMultipleObjectArgs::class)
    val t = assertThrows<IllegalArgumentException> {
      printer.print(true, 42, Date())
    }
    assertEquals("Unsupported Java type java.util.Date", t.message)
  }

  internal interface TestVarArgs {
    fun call(separator: String?, vararg args: Any?): String?
  }

  @Test fun callVarArgMethod() {
    quickJs.evaluate("""
      |function joiner() {
      |  var result = '';
      |  for (i = 0; i < arguments.length; i++) {
      |    if (i > 0) result = result + this;
      |    result = result + arguments[i].toString();
      |  }
      |  return result.toString();
      |}
      |""".trimMargin())
    val joiner = quickJs.get("joiner", TestVarArgs::class)
    assertEquals("", joiner.call("-"))
    assertEquals("1 + 2 + three", joiner.call(" + ", 1, 2, "three"))
    val t = assertThrows<IllegalArgumentException> {
      joiner.call(", ", "Test", Date(), 1.0)
    }
    assertEquals("Unsupported Java type java.util.Date", t.message)
  }

  internal interface Summer {
    fun sumDoubles(vararg args: Double): Double
    fun sumIntegers(vararg args: Int): Double
    fun countTrues(vararg args: Boolean): Double
  }

  @Test fun callVarArgPrimitiveMethod() {
    quickJs.evaluate("""
      |var summer = {
      |  sum: function() {
      |    var result = 0;
      |    for (i = 0; i < arguments.length; i++) {
      |      result = result + arguments[i];
      |    }
      |    return result;  },
      |  sumDoubles: function() { return this.sum.apply(this, arguments); },
      |  sumIntegers: function() { return this.sum.apply(this, arguments); },
      |  countTrues: function() { return this.sum.apply(this, arguments); }
      |};
      |""".trimMargin())
    val summer = quickJs.get("summer", Summer::class)
    assertEquals(0.0, summer.sumDoubles(), 0.001)
    assertEquals(10.0, summer.sumDoubles(1.0, 2.0, 3.0, 4.0), 0.001)
    assertEquals(0.0, summer.sumIntegers(), 0.001)
    assertEquals(10.0, summer.sumIntegers(1, 2, 3, 4), 0.001)
    assertEquals(0.0, summer.countTrues(), 0.001)
    assertEquals(3.0, summer.countTrues(true, false, true, true), 0.001)
  }

  internal interface ObjectSorter {
    fun sort(args: Array<Any?>?): Array<Any>?
  }

  @Test fun marshalArrayWithManyElements() {
    quickJs.evaluate(SORTER_FUNCTOR)
    val sorter = quickJs.get("Sorter", ObjectSorter::class)
    assertEquals(100000, sorter.sort(arrayOfNulls<Any?>(100000))!!.size)
  }

  @Test fun arraysOfObjects() {
    quickJs.evaluate(SORTER_FUNCTOR)
    val sorter = quickJs.get("Sorter", ObjectSorter::class)
    assertNull(sorter.sort(null))
    val original = arrayOf<Any?>(2, 4, 3, 1)
    val sorted = sorter.sort(original)
    assertArrayEquals(arrayOf<Any>(1, 2, 3, 4), sorted)
    assertNotSame(sorted, original)
    assertArrayEquals(arrayOf("a", "b", "d", null), sorter.sort(arrayOf("b", "d", null, "a")))
  }

  internal interface StringSorter {
    fun sort(args: Array<String?>?): Array<String?>?
  }

  @Test fun arraysOfStrings() {
    quickJs.evaluate(SORTER_FUNCTOR)
    val sorter = quickJs.get("Sorter", StringSorter::class)
    assertArrayEquals(arrayOf("a", "b", "c", "d"), sorter.sort(arrayOf("b", "d", "c", "a")))
    assertArrayEquals(arrayOf("a", "b", "d", null), sorter.sort(arrayOf("b", "d", null, "a")))

    // Replace the sorter with something broken.
    quickJs.evaluate("""Sorter.sort = function(v) {  return [ 'a', 'b', 3, 'd' ]; };""")
    val t = assertThrows<IllegalArgumentException> {
      sorter.sort(arrayOf("b", "d", "c", "a"))
    }
    assertEquals("Cannot convert value 3 to String", t.message)
  }

  internal interface DoubleSorter {
    fun sort(args: DoubleArray?): DoubleArray?
  }

  @Test fun arraysOfDoubles() {
    quickJs.evaluate(SORTER_FUNCTOR)
    val sorter = quickJs.get("Sorter", DoubleSorter::class)
    assertArrayEquals(doubleArrayOf(1.0, 2.3, 2.9, 3.0),
        sorter.sort(doubleArrayOf(2.9, 2.3, 3.0, 1.0)), 0.0)

    // Replace the sorter with something broken.
    quickJs.evaluate("""Sorter.sort = function(v) {  return [ 1, 2, null, 4 ]; };""")
    val t = assertThrows<IllegalArgumentException> {
      sorter.sort(DoubleArray(0))
    }
    assertEquals("Cannot convert value null to double", t.message)
  }

  // Note, we return double[] and Double[] since we can't return ints from JavaScript.
  internal interface IntSorter {
    fun sort(args: IntArray?): DoubleArray?
    fun sortNullable(args: Array<Int?>?): Array<Double?>?
  }

  @Test fun arraysOfInts() {
    quickJs.evaluate(SORTER_FUNCTOR)
    val sorter = quickJs.get("Sorter", IntSorter::class)
    assertArrayEquals(doubleArrayOf(1.0, 2.0, 3.0, 4.0), sorter.sort(intArrayOf(2, 4, 3, 1)), 0.0)
    assertArrayEquals(arrayOf(1.0, 2.0, 3.0, null), sorter.sortNullable(arrayOf(2, null, 3, 1)))

    // Replace the sorter with something broken.
    quickJs.evaluate("""Sorter.sort = function(v) {  return [ 1, 2, null, 4 ]; };""")
    val t = assertThrows<IllegalArgumentException> {
      sorter.sort(IntArray(0))
    }
    assertEquals("Cannot convert value null to double", t.message)
  }

  internal interface BoolSorter {
    fun sort(args: BooleanArray?): BooleanArray?
    fun sortNullable(args: Array<Boolean?>?): Array<Boolean?>?
  }

  @Test fun arraysOfBooleans() {
    quickJs.evaluate(SORTER_FUNCTOR)
    val sorter = quickJs.get("Sorter", BoolSorter::class)
    assertArrayEquals(booleanArrayOf(false, false, true, true), sorter.sort(booleanArrayOf(true, false, true, false)))
    assertArrayEquals(arrayOf(false, null, true, true), sorter.sortNullable(arrayOf(null, true, false, true)))

    // Replace the sorter with something broken.
    quickJs.evaluate("""Sorter.sort = function(v) {  return [ false, false, null, true ]; };""")
    val t = assertThrows<IllegalArgumentException> {
      sorter.sort(BooleanArray(0))
    }
    assertEquals("Cannot convert value null to boolean", t.message)
  }

  internal interface MatrixTransposer {
    fun call(o: Any?, matrix: Array<Array<Double?>>?): Array<Array<Double>>
  }

  @Test fun twoDimensionalArrays() {
    quickJs.evaluate("""
      |function transpose(matrix) {
      |  return matrix[0].map(function(col, i) {
      |      return matrix.map(function(row) {
      |        return row[i];
      |      })
      |    });
      |};
      |""".trimMargin())
    val transposer = quickJs.get("transpose", MatrixTransposer::class)
    val matrix = Array(2) { arrayOfNulls<Double?>(2) }
    matrix[0][0] = 1.0
    matrix[0][1] = 2.0
    matrix[1][0] = 3.0
    matrix[1][1] = 4.0
    val expected = Array(2) { arrayOfNulls<Double?>(2) }
    expected[0][0] = 1.0
    expected[1][0] = 2.0
    expected[0][1] = 3.0
    expected[1][1] = 4.0
    val result = transposer.call(null, matrix)
    assertArrayEquals(expected, result)
  }

  companion object {
    private const val SORTER_FUNCTOR = """
      var Sorter = {
        sort: function(v) {
          if (v) {
            v.sort();
          }
          return v;
        },
        sortNullable: function(v) { return this.sort(v); }
      };
      """
  }
}
