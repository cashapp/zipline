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
package com.google.crypto.tink.subtle

import com.google.crypto.tink.subtle.Field25519.expand
import java.math.BigInteger

/** Constants used in [Ed25519].  */
internal object Ed25519Constants {
  // d = -121665 / 121666 mod 2^255-19
  val D: LongArray

  // 2d
  val D2: LongArray

  // 2^((p-1)/4) mod p where p = 2^255-19
  val SQRTM1: LongArray

  /**
   * Base point for the Edwards twisted curve = (x, 4/5) and its exponentiations. B_TABLE[i][j] =
   * (j+1)*256^i*B for i in [0, 32) and j in [0, 8). Base point B = B_TABLE[0][0]
   *
   * See `Ed25519ConstantsGenerator`.
   */
  val B_TABLE: List<List<Ed25519.CachedXYT>>
  val B2: List<Ed25519.CachedXYT>
  private val P_BI =
    BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))
  private val D_BI =
    BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P_BI)).mod(P_BI)
  private val D2_BI = BigInteger.valueOf(2).multiply(D_BI).mod(P_BI)
  private val SQRTM1_BI =
    BigInteger.valueOf(2).modPow(P_BI.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4)), P_BI)

  private class Point(
    val x: BigInteger,
    val y: BigInteger,
  )

  private fun recoverX(y: BigInteger): BigInteger {
    // x^2 = (y^2 - 1) / (d * y^2 + 1) mod 2^255-19
    val xx = y.pow(2)
      .subtract(BigInteger.ONE)
      .multiply(D_BI.multiply(y.pow(2)).add(BigInteger.ONE).modInverse(P_BI))
    var x = xx.modPow(P_BI.add(BigInteger.valueOf(3)).divide(BigInteger.valueOf(8)), P_BI)
    if (x.pow(2).subtract(xx).mod(P_BI) != BigInteger.ZERO) {
      x = x.multiply(SQRTM1_BI).mod(P_BI)
    }
    if (x.testBit(0)) {
      x = P_BI.subtract(x)
    }
    return x
  }

  private fun edwards(a: Point, b: Point): Point {
    val xxyy = D_BI.multiply(
      a.x.multiply(b.x).multiply(a.y).multiply(b.y)
    ).mod(P_BI)
    return Point(
      x = a.x.multiply(b.y).add(b.x.multiply(a.y))
        .multiply(BigInteger.ONE.add(xxyy).modInverse(P_BI))
        .mod(P_BI),
      y = a.y.multiply(b.y).add(a.x.multiply(b.x))
        .multiply(BigInteger.ONE.subtract(xxyy).modInverse(P_BI))
        .mod(P_BI),
    )
  }

  private fun toLittleEndian(n: BigInteger): ByteArray {
    val b = ByteArray(32)
    val nBytes = n.toByteArray()
    System.arraycopy(nBytes, 0, b, 32 - nBytes.size, nBytes.size)
    for (i in 0 until b.size / 2) {
      val t = b[i]
      b[i] = b[b.size - i - 1]
      b[b.size - i - 1] = t
    }
    return b
  }

  private fun getCachedXYT(p: Point): Ed25519.CachedXYT {
    return Ed25519.CachedXYT(
      expand(toLittleEndian(p.y.add(p.x).mod(P_BI))),
      expand(toLittleEndian(p.y.subtract(p.x).mod(P_BI))),
      expand(toLittleEndian(D2_BI.multiply(p.x).multiply(p.y).mod(P_BI)))
    )
  }

  init {
    val y = BigInteger.valueOf(4).multiply(BigInteger.valueOf(5).modInverse(P_BI)).mod(P_BI)
    val b = Point(
      x = recoverX(y),
      y = y,
    )

    D = expand(toLittleEndian(D_BI))
    D2 = expand(toLittleEndian(D2_BI))
    SQRTM1 = expand(toLittleEndian(SQRTM1_BI))

    var bi: Point = b
    B_TABLE = mutableListOf()
    for (i in 0..31) {
      val list = mutableListOf<Ed25519.CachedXYT>()
      B_TABLE += list
      var bij: Point = bi
      for (j in 0..7) {
        list += getCachedXYT(bij)
        bij = edwards(bij, bi)
      }
      for (j in 0..7) {
        bi = edwards(bi, bi)
      }
    }
    bi = b
    val b2 = edwards(b, b)
    B2 = mutableListOf()
    for (i in 0..7) {
      B2 += getCachedXYT(bi)
      bi = edwards(bi, b2)
    }
  }
}
