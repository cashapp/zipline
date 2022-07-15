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

import okio.Buffer

/**
 * Helper methods that deal with byte arrays.
 *
 * @since 1.0.0
 */
object Bytes {
  /**
   * Best effort fix-timing array comparison.
   *
   * @return true if two arrays are equal.
   */
  @JvmStatic
  fun equal(x: ByteArray, y: ByteArray): Boolean {
    if (x.size != y.size) {
      return false
    }
    var res = 0
    for (i in x.indices) {
      res = res or (x[i].toInt() xor y[i].toInt())
    }
    return res == 0
  }

  /**
   * Returns the concatenation of the input arrays in a single array. For example, `concat(new
   * byte[] {a, b}, new byte[] {}, new byte[] {c}` returns the array `{a, b, c}`.
   *
   * @return a single array containing all the values from the source arrays, in order
   */
  @JvmStatic
  fun concat(vararg chunks: ByteArray): ByteArray {
    val buffer = Buffer()
    for (chunk in chunks) {
      buffer.write(chunk)
    }
    return buffer.readByteArray()
  }
}
