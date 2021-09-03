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

import java.util.Arrays
import java.util.Date
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("UNCHECKED_CAST")
class QuickJsSetTest {
  private val quickJs = QuickJs.create()

  @After fun tearDown() {
    quickJs.close()
  }

  @Test fun setNonInterface() {
    val t = assertThrows<UnsupportedOperationException> {
      quickJs.set("s", String::class.java, "foo")
    }
    assertEquals("Only interfaces can be bound. Received: class java.lang.String", t.message)
  }

  fun interface TestInterface {
    fun getValue(): String
  }

  @Test fun callMethodOnJavaObject() {
    quickJs.set("value", TestInterface::class.java, TestInterface { "8675309" })
    assertEquals("8675309", quickJs.evaluate("value.getValue();"))
  }

  @Test fun callMissingMethodOnJavaObjectFails() {
    quickJs.set("value", TestInterface::class.java, TestInterface { throw AssertionError() })
    val t = assertThrows<QuickJsException> {
      quickJs.evaluate("value.increment()")
    }
    assertEquals("not a function", t.message)
  }

  @Test fun setSameNameTwiceFails() {
    quickJs.set("value", TestInterface::class.java, TestInterface { "foo" })
    val t = assertThrows<IllegalArgumentException> {
      quickJs.set("value", TestInterface::class.java, TestInterface { throw AssertionError() })
    }
    assertEquals("A global object called value already exists", t.message)
  }

  @Test fun exceptionsFromJavaWithUnifiedStackTrace() {
    @Suppress("ObjectLiteralToLambda") // Avoid lambda name in stacktrace.
    val boundObject = object : TestInterface {
      override fun getValue(): String {
        throw UnsupportedOperationException("Cannot getValue")
      }
    }
    quickJs.set("value", TestInterface::class.java, boundObject)

    val t = assertThrows<UnsupportedOperationException> {
      quickJs.evaluate("""
        |f1();
        |
        |function f1() {
        |  f2();
        |}
        |
        |function f2() {
        |  value.getValue();
        |}
        |""".trimMargin(), "test.js")
    }
    assertEquals("Cannot getValue", t.message)

    val stackTrace = t.stackTrace

    // Top entry is what threw - the TestInterface implementation.
    assertEquals(boundObject.javaClass.name, stackTrace[0].className)
    assertEquals("getValue", stackTrace[0].methodName)

    // The next four entries are JavaScript
    assertEquals("JavaScript.getValue(native)", stackTrace[1].toString())
    assertEquals("JavaScript.f2(test.js:8)", stackTrace[2].toString())
    assertEquals("JavaScript.f1(test.js:4)", stackTrace[3].toString())
    assertEquals("JavaScript.<eval>(test.js:1)", stackTrace[4].toString())

    // Then one or two native JniQuickJs.evaluate methods, followed by JniQuickJs.evaluate in Java.
    var i = 5
    assertEquals("app.cash.zipline.QuickJs", stackTrace[i].className)
    assertEquals("evaluate", stackTrace[i].methodName)
    assertTrue(stackTrace[i].isNativeMethod)
    while (stackTrace[i].methodName == "evaluate") {
      i++
    }
    assertFalse(stackTrace[i - 1].isNativeMethod)

    // Then this test method.
    assertEquals(QuickJsSetTest::class.java.name, stackTrace[i].className)
    assertEquals("exceptionsFromJavaWithUnifiedStackTrace", stackTrace[i].methodName)
  }

  fun interface TestInterfaceArgs {
    fun foo(a: String?, b: String?, c: String?): String?
  }

  @Test fun callMethodOnJavaObjectThrowsJavaException() {
    quickJs.set("value", TestInterface::class.java,
        TestInterface { throw UnsupportedOperationException("This is an error message.") })
    val t = assertThrows<UnsupportedOperationException> {
      quickJs.evaluate("value.getValue()")
    }
    assertEquals("This is an error message.", t.message)
  }

  @Test fun callMethodWithArgsOnJavaObject() {
    quickJs.set("value", TestInterfaceArgs::class.java,
        TestInterfaceArgs { a, b, c -> if (a != null) a + b + c else null })

    assertEquals("This is a test", quickJs.evaluate("value.foo('This', ' is a ', 'test')"))
    assertNull(quickJs.evaluate("value.foo(null, null, null)"))

    val t1 = assertThrows<QuickJsException> {
      quickJs.evaluate("value.foo('This')")
    }
    assertEquals("Wrong number of arguments", t1.message)

    val t2 = assertThrows<QuickJsException> {
      quickJs.evaluate("value.foo('This', ' is ', 'too ', 'many ', 'arguments')")
    }
    assertEquals("Wrong number of arguments", t2.message)

    val t3 = assertThrows<IllegalArgumentException> {
      quickJs.evaluate("value.foo('1', '2', 3)")
    }
    assertEquals("Cannot convert value 3 to String", t3.message)
  }

  interface TestInterfaceVoids {
    fun func()
    fun getResult(): String
  }

  @Test fun callVoidJavaMethod() {
    quickJs.set("value", TestInterfaceVoids::class.java, object : TestInterfaceVoids {
      private var result = "not called"
      override fun getResult(): String = result
      override fun func() {
        result = "called"
      }
    })
    assertEquals("not called", quickJs.evaluate("value.getResult()"))
    assertNull(quickJs.evaluate("value.func()"))
    assertEquals("called", quickJs.evaluate("value.getResult()"))
  }

  interface TestPrimitiveTypes {
    fun b(b: Boolean): Boolean
    fun i(i: Int): Int
    fun d(d: Double): Double
  }

  // Verify that primitive types can be used as both arguments and return values from Java methods.
  @Test fun callJavaMethodWithPrimitiveTypes() {
    quickJs.set("value", TestPrimitiveTypes::class.java, object : TestPrimitiveTypes {
      override fun b(b: Boolean): Boolean = !b
      override fun i(i: Int): Int = i * i
      override fun d(d: Double): Double = d / 2.0
    })

    // TODO: add an evaluate interface that supports other types.
    assertEquals("true", quickJs.evaluate("value.b(false).toString()"))
    assertEquals("16", quickJs.evaluate("value.i(4).toString()"))
    assertEquals("3.14159", quickJs.evaluate("value.d(6.28318).toString()"))
  }

  fun interface TestMultipleArgTypes {
    fun print(b: Boolean, i: Int, d: Double): String?
  }

  // Double check that arguments of different types are processed in the correct order from the
  // QuickJs stack.
  @Test fun callJavaMethodWithAllArgTypes() {
    quickJs.set("printer", TestMultipleArgTypes::class.java, TestMultipleArgTypes { b, i, d ->
      String.format("boolean: %s, int: %s, double: %s", b, i, d)
    })
    assertEquals("boolean: true, int: 42, double: 2.718281828459",
        quickJs.evaluate("printer.print(true, 42, 2.718281828459)"))
  }

  interface TestBoxedPrimitiveArgTypes {
    fun b(b: Boolean?): Boolean?
    fun i(i: Int?): Int?
    fun d(d: Double?): Double?
  }

  @Test fun callJavaMethodWithBoxedPrimitiveTypes() {
    quickJs.set("value", TestBoxedPrimitiveArgTypes::class.java,
        object : TestBoxedPrimitiveArgTypes {
          override fun b(b: Boolean?): Boolean? = if (b != null) !b else null
          override fun i(i: Int?): Int? = if (i != null) i * i else null
          override fun d(d: Double?): Double? = if (d != null) d / 2.0 else null
        })

    // TODO: add an evaluate interface that supports other types.
    assertEquals("true", quickJs.evaluate("value.b(false).toString()"))
    assertEquals("16", quickJs.evaluate("value.i(4).toString()"))
    assertEquals("3.14159", quickJs.evaluate("value.d(6.28318).toString()"))
    assertNull(quickJs.evaluate("value.b(null)"))
    assertNull(quickJs.evaluate("value.i(null)"))
    assertNull(quickJs.evaluate("value.d(null)"))
  }

  fun interface UnsupportedReturnType {
    fun get(): Date?
  }

  @Test fun setUnsupportedReturnType() {
    val t = assertThrows<IllegalArgumentException> {
      quickJs.set("value", UnsupportedReturnType::class.java, UnsupportedReturnType { null })
    }
    assertEquals("Unsupported Java type java.util.Date", t.message)
  }

  fun interface UnsupportedArgumentType {
    fun set(d: Date?)
  }

  @Test fun setUnsupportedArgumentType() {
    val t = assertThrows<IllegalArgumentException> {
      quickJs.set("value", UnsupportedArgumentType::class.java, UnsupportedArgumentType { })
    }
    assertEquals("Unsupported Java type java.util.Date", t.message)
  }

  interface OverloadedMethod {
    fun foo(i: Int)
    fun foo(d: Double)
  }

  @Test fun setOverloadedMethod() {
    val t = assertThrows<UnsupportedOperationException> {
      quickJs.set("value", OverloadedMethod::class.java, object : OverloadedMethod {
        override fun foo(i: Int) {}
        override fun foo(d: Double) {}
      })
    }
    assertEquals("foo is overloaded in " + OverloadedMethod::class.java.toString(), t.message)
  }

  fun interface ExtendedInterface : TestInterface

  @Test fun setExtendedInterface() {
    val t = assertThrows<UnsupportedOperationException> {
      quickJs.set("value", ExtendedInterface::class.java, ExtendedInterface { "nope" })
    }
    assertEquals(ExtendedInterface::class.java.toString() + " must not extend other interfaces",
        t.message)
  }

  @Test fun setFailureLeavesQuickJsConsistent() {
    quickJs.set("value", TestInterface::class.java, TestInterface { "8675309" })
    quickJs.evaluate("var localVar = 42;")
    assertThrows<IllegalArgumentException> {
      quickJs.set("illegal", UnsupportedArgumentType::class.java, UnsupportedArgumentType { })
    }

    // The state of our QuickJs context is still valid, containing what was there before.
    assertEquals("8675309", quickJs.evaluate("value.getValue();"))
    assertEquals("42", quickJs.evaluate("localVar.toString();"))
  }

  fun interface TestMultipleObjectArgs {
    fun print(b: Any?, i: Any?, d: Any?): Any?
  }

  // Double check that arguments of different types are processed in the correct order from the
  // QuickJs stack.
  @Test fun callJavaMethodObjectArgs() {
    quickJs.set("printer", TestMultipleObjectArgs::class.java, TestMultipleObjectArgs { b, i, d ->
      String.format("boolean: %s, int: %s, double: %s", b, i, d)
    })
    assertEquals("boolean: true, int: 42, double: 2.718281828459",
        quickJs.evaluate("printer.print(true, 42, 2.718281828459)"))
  }

  @Test fun passUnsupportedTypeAsObjectFails() {
    quickJs.set("printer", TestMultipleObjectArgs::class.java, TestMultipleObjectArgs { b, i, d ->
      String.format("boolean: %s, int: %s, double: %s", b, i, d)
    })
    val t = assertThrows<QuickJsException> {
      quickJs.evaluate("printer.print(true, 42, new Date())")
    }
    assertTrue(t.message!!.startsWith("Cannot marshal "))
  }

  fun interface TestVarArgs {
    fun format(format: String?, vararg args: Any?): String?
  }

  @Test fun callVarArgMethod() {
    quickJs.set("formatter", TestVarArgs::class.java,
        TestVarArgs { format, args -> String.format(format!!, *args) })
    assertEquals("okay", quickJs.evaluate("formatter.format('okay')"))
    assertEquals("1999-12-31 23:59:59.999 - FATAL: failure", quickJs.evaluate(
        "formatter.format('%s - %s: %s', '1999-12-31 23:59:59.999', 'FATAL', 'failure');"))
    assertTrue(quickJs.evaluate("formatter.format('%s %s', 'three', [1, 2, 3])")
        .toString()
        .startsWith("three [Ljava.lang.Object;"))
  }

  interface Summer {
    fun sumIntegers(vararg args: Int): Int
    fun sumDoubles(vararg args: Double): Double
    fun countTrues(vararg args: Boolean): Int
  }

  @Test fun callVarArgPrimitiveMethod() {
    quickJs.set("Summer", Summer::class.java, object : Summer {
      override fun sumIntegers(vararg args: Int): Int {
        var v = 0
        for (arg in args) {
          v += arg
        }
        return v
      }

      override fun sumDoubles(vararg args: Double): Double {
        var v = 0.0
        for (arg in args) {
          v += arg
        }
        return v
      }

      override fun countTrues(vararg args: Boolean): Int {
        var v = 0
        for (arg in args) {
          v += if (arg) 1 else 0
        }
        return v
      }
    })

    assertEquals(0, quickJs.evaluate("Summer.sumIntegers()"))
    assertEquals(10, quickJs.evaluate("Summer.sumIntegers(1, 2, 3, 4)"))
    val t1 = assertThrows<IllegalArgumentException> {
      quickJs.evaluate("Summer.sumIntegers(1, 2, 'three', 4)")
    }
    assertEquals("Cannot convert value three to int", t1.message)

    assertEquals(0, quickJs.evaluate("Summer.sumDoubles()"))
    assertEquals(10, quickJs.evaluate("Summer.sumDoubles(0.5, 2.5, 3, 4)"))
    val t2 = assertThrows<IllegalArgumentException> {
      quickJs.evaluate("Summer.sumDoubles(1, 2, 'three', 4)")
    }
    assertEquals("Cannot convert value three to double", t2.message)

    assertEquals(0, quickJs.evaluate("Summer.countTrues()"))
    assertEquals(3, quickJs.evaluate("Summer.countTrues(true, true, false, true)"))
    val t3 = assertThrows<IllegalArgumentException> {
      quickJs.evaluate("Summer.countTrues(true, false, 'ninja', true)")
    }
    assertEquals("Cannot convert value ninja to boolean", t3.message)
  }

  fun interface ObjectSorter {
    fun sort(args: Array<Any>?): Array<Any>?
  }

  @Test fun arraysOfObjects() {
    quickJs.set("Sorter", ObjectSorter::class.java, object : ObjectSorter {
      override fun sort(args: Array<Any>?): Array<Any>? {
        if (args == null) return null
        Arrays.sort(args, Comparator<Any?> { lhs, rhs ->
          if (lhs == null) return@Comparator -1
          if (rhs == null) 1 else (lhs as Comparable<Any?>).compareTo(rhs)
        })
        return args
      }
    })
    assertNull(quickJs.evaluate("Sorter.sort(null)"))
    assertArrayEquals(quickJs.evaluate("Sorter.sort([2, 4, 3, 1])") as Array<Any?>?,
        arrayOf(1, 2, 3, 4))
    assertArrayEquals(
        quickJs.evaluate("Sorter.sort(['b', 'd', null, 'a'])") as Array<Any?>?,
        arrayOf(null, "a", "b", "d"))

    val original = TimeZone.getDefault()
    val t = assertThrows<QuickJsException> {
      TimeZone.setDefault(TimeZone.getTimeZone("GMT+0:00"))
      quickJs.evaluate("Sorter.sort([ 1, 2, 3, new Date(0) ])")
    }
    TimeZone.setDefault(original)
    assertTrue(t.message!!.startsWith("Cannot marshal "))
  }

  fun interface StringSorter {
    fun sort(args: Array<String>): Array<String>
  }

  @Test fun arraysOfStrings() {
    quickJs.set("Sorter", StringSorter::class.java, StringSorter { args ->
      Arrays.sort(args)
      args
    })

    assertArrayEquals(
        quickJs.evaluate("Sorter.sort(['b', 'd', 'c', 'a'])") as Array<Any?>?,
        arrayOf("a", "b", "c", "d"))

    val t = assertThrows<IllegalArgumentException> {
      quickJs.evaluate("Sorter.sort(['b', 'd', 3, 'a'])")
    }
    assertEquals("Cannot convert value 3 to String", t.message)
  }

  interface DoubleSorter {
    fun sort(args: DoubleArray): DoubleArray
    fun sortNullsFirst(args: Array<Double>): Array<Double>
  }

  @Test fun arraysOfDoubles() {
    quickJs.set("Sorter", DoubleSorter::class.java, object : DoubleSorter {
      override fun sort(args: DoubleArray): DoubleArray {
        Arrays.sort(args)
        return args
      }

      override fun sortNullsFirst(args: Array<Double>): Array<Double> {
        Arrays.sort(args, java.util.Comparator { lhs, rhs ->
          if (lhs == null) return@Comparator -1
          if (rhs == null) 1 else lhs.compareTo(rhs)
        })
        return args
      }
    })

    assertArrayEquals(quickJs.evaluate("Sorter.sort([2.9, 2.3, 3, 1])") as Array<Any?>?,
        arrayOf(1, 2.3, 2.9, 3))

    val t = assertThrows<IllegalArgumentException> {
      quickJs.evaluate("Sorter.sort([2.3, 4, null, 1])")
    }
    assertEquals("Cannot convert value null to double", t.message)

    assertArrayEquals(
        quickJs.evaluate("Sorter.sortNullsFirst([2.9, null, 3, 1])") as Array<Any?>?,
        arrayOf(null, 1, 2.9, 3))
  }

  interface IntSorter {
    fun sort(args: IntArray): IntArray
    fun sortNullsFirst(args: Array<Int>): Array<Int>
  }

  @Test fun arraysOfInts() {
    quickJs.set("Sorter", IntSorter::class.java, object : IntSorter {
      override fun sort(args: IntArray): IntArray {
        Arrays.sort(args)
        return args
      }

      override fun sortNullsFirst(args: Array<Int>): Array<Int> {
        Arrays.sort(args, java.util.Comparator { lhs, rhs ->
          if (lhs == null) return@Comparator -1
          if (rhs == null) 1 else lhs.compareTo(rhs)
        })
        return args
      }
    })
    assertArrayEquals(quickJs.evaluate("Sorter.sort([2, 4, 3, 1])") as Array<Any?>?,
        arrayOf(1, 2, 3, 4))

    val t = assertThrows<IllegalArgumentException> {
      quickJs.evaluate("Sorter.sort([2, 4, null, 1])")
    }
    assertEquals("Cannot convert value null to int", t.message)

    assertArrayEquals(quickJs.evaluate("Sorter.sort([2, 4, 3, 1])") as Array<Any?>?,
        arrayOf(1, 2, 3, 4))

    assertArrayEquals(
        quickJs.evaluate("Sorter.sortNullsFirst([2, null, 3, 1])") as Array<Any?>?,
        arrayOf(null, 1, 2, 3))
  }

  interface BoolSorter {
    fun sort(args: BooleanArray): BooleanArray
    fun sortNullsFirst(args: Array<Boolean>): Array<Boolean>
  }

  @Test fun arraysOfBooleans() {
    quickJs.set("Sorter", BoolSorter::class.java, object : BoolSorter {
      override fun sort(args: BooleanArray): BooleanArray {
        var count = 0
        for (arg in args) {
          if (arg) count++
        }
        val result = BooleanArray(args.size)
        for (i in args.size - 1 downTo count) {
          result[i] = true
        }
        return result
      }

      override fun sortNullsFirst(args: Array<Boolean>): Array<Boolean> {
        Arrays.sort(args, java.util.Comparator { lhs, rhs ->
          if (lhs == null) return@Comparator -1
          if (rhs == null) 1 else lhs.compareTo(rhs)
        })
        return args
      }
    })
    assertArrayEquals(
        quickJs.evaluate("Sorter.sort([ true, false, true, false ])") as Array<Any?>,
        arrayOf<Any>(false, false, true, true))

    val t = assertThrows<IllegalArgumentException> {
      quickJs.evaluate("Sorter.sort([false, true, null, false])")
    }
    assertEquals("Cannot convert value null to boolean", t.message)

    assertArrayEquals(
        quickJs.evaluate("Sorter.sortNullsFirst([true, false, true, null])") as Array<Any?>,
        arrayOf(null, false, true, true))
  }

  @Test fun lotsOfLocalTemps() {
    quickJs.set("foo", TestInterfaceArgs::class.java,
        TestInterfaceArgs { a, b, c -> a + b + c })
    val result = quickJs.evaluate("""
      |var len = 0;
      |for (var i = 0; i < 100000; i++) {
      |  var s = foo.foo('a', 'b', 'c');
      |  len += s.length;
      |}
      |len;
      |""".trimMargin())
    assertEquals(300000, result)
  }

  // https://github.com/square/duktape-android/issues/95
  interface HugeInterface {
    fun method01(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method02(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method03(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method04(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method05(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method06(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method07(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method08(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method09(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method10(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method11(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method12(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method13(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method14(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method15(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method16(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method17(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method18(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method19(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method20(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method21(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method22(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method23(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method24(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method25(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method26(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method27(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method28(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method29(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method30(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method31(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method32(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method33(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method34(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method35(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method36(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method37(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method38(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method39(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method40(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method41(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method42(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method43(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method44(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method45(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method46(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method47(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method48(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method49(
      a: String?,
      b: String?,
      c: String?,
      d: String?,
      e: String?,
      f: String?,
      g: String?,
      h: String?
    ): String?

    fun method50(
      a: String,
      b: String,
      c: String,
      d: String,
      e: String,
      f: String,
      g: String,
      h: String
    ): String
  }

  @Test fun lotsOfInterfaceMethodsAndArgs() {
    quickJs.set("foo", HugeInterface::class.java, object : HugeInterface {
      override fun method01(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method02(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method03(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method04(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method05(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method06(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method07(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method08(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method09(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method10(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method11(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method12(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method13(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method14(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method15(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method16(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method17(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method18(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method19(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method20(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method21(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method22(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method23(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method24(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method25(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method26(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method27(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method28(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method29(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method30(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method31(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method32(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method33(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method34(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method35(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method36(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method37(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method38(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method39(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method40(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method41(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method42(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method43(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method44(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method45(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method46(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method47(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method48(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method49(
        a: String?, b: String?, c: String?, d: String?, e: String?, f: String?, g: String?,
        h: String?
      ): String? {
        return null
      }

      override fun method50(
        a: String, b: String, c: String, d: String, e: String, f: String, g: String,
        h: String
      ): String {
        return "method50$a$b$c$d$e$f$g$h"
      }
    })
    val result = quickJs.evaluate("foo.method50('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h')\n")
    assertEquals("method50abcdefgh", result)
  }
}
