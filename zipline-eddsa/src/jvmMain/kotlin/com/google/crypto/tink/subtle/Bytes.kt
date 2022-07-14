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

package com.google.crypto.tink.subtle;

import java.security.GeneralSecurityException;

/**
 * Helper methods that deal with byte arrays.
 *
 * @since 1.0.0
 */
public final class Bytes {
  /**
   * Best effort fix-timing array comparison.
   *
   * @return true if two arrays are equal.
   */
  public static boolean equal(final byte[] x, final byte[] y) {
    if (x == null || y == null) {
      return false;
    }
    if (x.length != y.length) {
      return false;
    }
    int res = 0;
    for (int i = 0; i < x.length; i++) {
      res |= x[i] ^ y[i];
    }
    return res == 0;
  }

  /**
   * Returns the concatenation of the input arrays in a single array. For example, {@code concat(new
   * byte[] {a, b}, new byte[] {}, new byte[] {c}} returns the array {@code {a, b, c}}.
   *
   * @return a single array containing all the values from the source arrays, in order
   */
  public static byte[] concat(byte[]... chunks) throws GeneralSecurityException {
    int length = 0;
    for (byte[] chunk : chunks) {
      if (length > Integer.MAX_VALUE - chunk.length) {
        throw new GeneralSecurityException("exceeded size limit");
      }
      length += chunk.length;
    }
    byte[] res = new byte[length];
    int pos = 0;
    for (byte[] chunk : chunks) {
      System.arraycopy(chunk, 0, res, pos, chunk.length);
      pos += chunk.length;
    }
    return res;
  }
}
