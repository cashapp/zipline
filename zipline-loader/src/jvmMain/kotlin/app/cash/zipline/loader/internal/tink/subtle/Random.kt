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

import java.security.SecureRandom
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * A simple wrapper of [SecureRandom].
 *
 * @since 1.0.0
 */
internal object Random {
  private val localRandom: ThreadLocal<SecureRandom> = object : ThreadLocal<SecureRandom>() {
    override fun initialValue(): SecureRandom = newDefaultSecureRandom()
  }

  private fun newDefaultSecureRandom(): SecureRandom {
    val result = SecureRandom()
    result.nextLong() // force seeding
    return result
  }

  /** Returns a random byte array of size [size]. */
  fun randBytes(size: Int): ByteString {
    val rand = ByteArray(size)
    localRandom.get().nextBytes(rand)
    return rand.toByteString()
  }
}
