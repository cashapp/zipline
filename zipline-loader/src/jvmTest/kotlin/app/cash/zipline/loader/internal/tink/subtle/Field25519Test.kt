// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////
package app.cash.zipline.loader.internal.tink.subtle

import java.math.BigInteger
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tink's Unit tests for [Field25519].
 *
 * TODO(quannguyen): Add more tests. Note that several functions assume that the inputs are in
 * reduced forms, so testing them don't guarantee that its uses are safe. There may be integer
 * overflow when they aren't used correctly.
 */
class Field25519Test {
  var x = arrayOfNulls<BigInteger>(NUM_BASIC_TESTS)
  var y = arrayOfNulls<BigInteger>(NUM_BASIC_TESTS)

  @Before
  fun setUp() {
    for (i in 0 until NUM_BASIC_TESTS) {
      x[i] = BigInteger(Field25519.FIELD_LEN * 8, rand).mod(P)
      y[i] = BigInteger(Field25519.FIELD_LEN * 8, rand).mod(P)
    }
  }

  @Test
  fun testBasicSum() {
    for (i in 0 until NUM_BASIC_TESTS) {
      val expectedResult = x[i]!!.add(y[i]).mod(P)
      val xBytes = toLittleEndian(x[i])
      val yBytes = toLittleEndian(y[i])
      val output = LongArray(Field25519.LIMB_CNT * 2 + 1)
      Field25519.sum(output, Field25519.expand(xBytes), Field25519.expand(yBytes))
      Field25519.reduceCoefficients(output)
      val result = BigInteger(reverse(Field25519.contract(output)))
      assertEquals("Sum x[i] + y[i]: " + x[i] + "+" + y[i], expectedResult, result)
    }
  }

  @Test
  fun testBasicSub() {
    for (i in 0 until NUM_BASIC_TESTS) {
      val expectedResult = x[i]!!.subtract(y[i]).mod(P)
      val xBytes = toLittleEndian(x[i])
      val yBytes = toLittleEndian(y[i])
      val output = LongArray(Field25519.LIMB_CNT * 2 + 1)
      Field25519.sub(output, Field25519.expand(xBytes), Field25519.expand(yBytes))
      Field25519.reduceCoefficients(output)
      val result = BigInteger(reverse(Field25519.contract(output)))
      assertEquals(
        "Subtraction x[i] - y[i]: " + x[i] + "-" + y[i],
        expectedResult,
        result,
      )
    }
  }

  @Test
  fun testBasicProduct() {
    for (i in 0 until NUM_BASIC_TESTS) {
      val expectedResult = x[i]!!.multiply(y[i]).mod(P)
      val xBytes = toLittleEndian(x[i])
      val yBytes = toLittleEndian(y[i])
      val output = LongArray(Field25519.LIMB_CNT * 2 + 1)
      Field25519.product(output, Field25519.expand(xBytes), Field25519.expand(yBytes))
      Field25519.reduceSizeByModularReduction(output)
      Field25519.reduceCoefficients(output)
      val result = BigInteger(reverse(Field25519.contract(output)))
      assertEquals("Product x[i] * y[i]: " + x[i] + "*" + y[i], expectedResult, result)
    }
  }

  @Test
  fun testBasicMult() {
    for (i in 0 until NUM_BASIC_TESTS) {
      val expectedResult = x[i]!!.multiply(y[i]).mod(P)
      val xBytes = toLittleEndian(x[i])
      val yBytes = toLittleEndian(y[i])
      val output = LongArray(Field25519.LIMB_CNT * 2 + 1)
      Field25519.mult(output, Field25519.expand(xBytes), Field25519.expand(yBytes))
      val result = BigInteger(reverse(Field25519.contract(output)))
      assertEquals(
        "Multiplication x[i] * y[i]: " + x[i] + "*" + y[i],
        expectedResult,
        result,
      )
    }
  }

  @Test
  fun testBasicScalarProduct() {
    val scalar: Long = 121665
    for (i in 0 until NUM_BASIC_TESTS) {
      val expectedResult = x[i]!!.multiply(BigInteger.valueOf(scalar)).mod(P)
      val xBytes = toLittleEndian(x[i])
      val output = LongArray(Field25519.LIMB_CNT * 2 + 1)
      Field25519.scalarProduct(output, Field25519.expand(xBytes), scalar)
      Field25519.reduceSizeByModularReduction(output)
      Field25519.reduceCoefficients(output)
      val result = BigInteger(reverse(Field25519.contract(output)))
      assertEquals(
        "Scalar product x[i] * 10 " + x[i] + "*" + 10,
        expectedResult,
        result,
      )
    }
  }

  @Test
  fun testBasicSquare() {
    for (i in 0 until NUM_BASIC_TESTS) {
      val expectedResult = x[i]!!.multiply(x[i]).mod(P)
      val xBytes = toLittleEndian(x[i])
      val output = LongArray(Field25519.LIMB_CNT * 2 + 1)
      Field25519.square(output, Field25519.expand(xBytes))
      Field25519.reduceSizeByModularReduction(output)
      Field25519.reduceCoefficients(output)
      val result = BigInteger(reverse(Field25519.contract(output)))
      assertEquals("Square x[i] * x[i]: " + x[i] + "*" + x[i], expectedResult, result)
    }
  }

  @Test
  fun testBasicInverse() {
    for (i in 0 until NUM_BASIC_TESTS) {
      val expectedResult = x[i]!!.modInverse(P)
      val xBytes = toLittleEndian(x[i])
      val output = LongArray(Field25519.LIMB_CNT * 2 + 1)
      Field25519.inverse(output, Field25519.expand(xBytes))
      val result = BigInteger(reverse(Field25519.contract(output)))
      assertEquals("Inverse: x[i]^(-1) mod P: " + x[i], expectedResult, result)
    }
  }

  @Test
  fun testContractExpand() {
    for (i in 0 until NUM_BASIC_TESTS) {
      val xBytes = toLittleEndian(x[i])
      val result = Field25519.contract(Field25519.expand(xBytes))
      assertArrayEquals(xBytes, result)
    }
  }

  private fun toLittleEndian(n: BigInteger?): ByteArray {
    val b = ByteArray(32)
    val nBytes = n!!.toByteArray()
    System.arraycopy(nBytes, 0, b, 32 - nBytes.size, nBytes.size)
    for (i in 0 until b.size / 2) {
      val t = b[i]
      b[i] = b[b.size - i - 1]
      b[b.size - i - 1] = t
    }
    return b
  }

  private fun reverse(x: ByteArray): ByteArray {
    val r = ByteArray(x.size)
    for (i in x.indices) {
      r[i] = x[x.size - i - 1]
    }
    return r
  }

  companion object {
    /**
     * The idea of basic tests is simple. We generate random numbers, make computations with
     * Field25519 and compare the results with Java BigInteger.
     */
    private const val NUM_BASIC_TESTS = 1024
    private val rand = SecureRandom()
    private val P = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))
  }
}
