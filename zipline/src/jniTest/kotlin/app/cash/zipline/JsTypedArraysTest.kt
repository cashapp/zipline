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
package app.cash.zipline

import java.util.Arrays
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TODO: support conversions for more typed array types?
 */
class JsTypedArraysTest {
  private val quickJs = QuickJs.create()

  @After fun tearDown() {
    quickJs.close()
  }

  @Test fun uint8ArrayConvertedToByteArray() {
    val byteArray = quickJs.evaluate("""
      |var array = new Uint8Array(5);
      |array[0] = 86;
      |array[1] = 7;
      |array[2] = 53;
      |array[3] = 0;
      |array[4] = 9;
      |array;
      |""".trimMargin()) as ByteArray?
    assertArrayEquals(byteArrayOf(86, 7, 53, 0, 9), byteArray)
  }

  @Test fun uint8ArraySliceConvertedToByteArray() {
    val byteArray = quickJs.evaluate("""
      |var array = new Uint8Array(5);
      |array[0] = 86;
      |array[1] = 7;
      |array[2] = 53;
      |array[3] = 0;
      |array[4] = 9;
      |array.slice(1, 3);
      |""".trimMargin()) as ByteArray?
    assertArrayEquals(byteArrayOf(7, 53), byteArray)
  }

  @Test fun int8ArrayConvertedToByteArray() {
    val byteArray = quickJs.evaluate("""
      |var array = new Int8Array(5);
      |array[0] = 86;
      |array[1] = 7;
      |array[2] = 53;
      |array[3] = 0;
      |array[4] = 9;
      |array;
      |""".trimMargin()) as ByteArray?
    assertArrayEquals(byteArrayOf(86, 7, 53, 0, 9), byteArray)
  }

  @Test fun uint8ClampedArrayConvertedToByteArray() {
    val byteArray = quickJs.evaluate("""
      |var array = new Uint8ClampedArray(5);
      |array[0] = 86;
      |array[1] = 7;
      |array[2] = 53;
      |array[3] = 0;
      |array[4] = 9;
      |array;
      |""".trimMargin()) as ByteArray?
    assertArrayEquals(byteArrayOf(86, 7, 53, 0, 9), byteArray)
  }

  fun interface ByteArrayTransformer {
    fun transform(input: ByteArray?): ByteArray?
  }

  @Test fun byteArrayWithProxy() {
    quickJs.set("transformer", ByteArrayTransformer::class.java, ByteArrayTransformer { input ->
      requireNotNull(input)
      val result = ByteArray(input.size + 2)
      result[0] = -1
      result[1] = -2
      System.arraycopy(input, 0, result, 2, input.size)
      Arrays.sort(result)
      result
    })
    val byteArray = quickJs.evaluate("""
      |var array = new Uint8Array(5);
      |array[0] = 86;
      |array[1] = 7;
      |array[2] = 53;
      |array[3] = 0;
      |array[4] = 9;
      |transformer.transform(array);
      |""".trimMargin()) as ByteArray?
    assertArrayEquals(byteArrayOf(-2, -1, 0, 7, 9, 53, 86), byteArray)
  }

  @Test fun byteArrayWithProxyReturnsNull() {
    quickJs.set("transformer", ByteArrayTransformer::class.java, ByteArrayTransformer { null })
    val byteArray = quickJs.evaluate("""
      |var array = new Uint8Array(5);
      |array[0] = 86;
      |transformer.transform(array);
      |""".trimMargin()) as ByteArray?
    assertNull(byteArray)
  }

  @Test fun byteArrayWithProxyReceivesNull() {
    quickJs.set("transformer", ByteArrayTransformer::class.java,
        ByteArrayTransformer { byteArrayOf(86) })
    val byteArray = quickJs.evaluate("""
      |transformer.transform(null);
      |""".trimMargin()) as ByteArray?
    assertArrayEquals(byteArrayOf(86), byteArray)
  }

  @Test fun javaByteArrayConvertedToJsUint8Array() {
    quickJs.set("transformer", ByteArrayTransformer::class.java,
        ByteArrayTransformer { byteArrayOf(86) })
    val constructorName = quickJs.evaluate("""
      |var array = new Uint8Array(5);
      |array[0] = 86;
      |var transformed = transformer.transform(array);
      |transformed.constructor.name
      |""".trimMargin()) as String
    assertEquals("Uint8Array", constructorName)
  }

  @Test fun int16ArrayUnsupported() {
    val shortArray = quickJs.evaluate("""
      |var array = new Int16Array(1);
      |array[0] = 8675;
      |array;
      |""".trimMargin()) as ShortArray?
    assertNull(shortArray)
  }

  @Test fun uint16ArrayUnsupported() {
    val shortArray = quickJs.evaluate("""
      |var array = new Uint16Array(1);
      |array[0] = 8675;
      |array;
      """.trimMargin()) as ShortArray?
    assertNull(shortArray)
  }

  @Test fun int32ArrayUnsupported() {
    val intArray = quickJs.evaluate("""
      |var array = new Int32Array(1);
      |array[0] = 8675;
      |array;
      """.trimMargin()) as IntArray?
    assertNull(intArray)
  }

  @Test fun uint32ArrayUnsupported() {
    val intArray = quickJs.evaluate("""
      |var array = new Uint32Array(1);
      |array[0] = 8675;
      |array;
      """.trimMargin()) as IntArray?
    assertNull(intArray)
  }

  @Test fun float32ArrayUnsupported() {
    val floatArray = quickJs.evaluate("""
      |var array = new Float32Array(1);
      |array[0] = 867.5309;
      |array;
      """.trimMargin()) as FloatArray?
    assertNull(floatArray)
  }

  @Test fun float64ArrayUnsupported() {
    val doubleArray = quickJs.evaluate("""
      |var array = new Float64Array(1);
      |array[0] = 867.5309;
      |array;
      """.trimMargin()) as DoubleArray?
    assertNull(doubleArray)
  }
}
