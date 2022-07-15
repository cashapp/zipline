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

import app.cash.zipline.loader.internal.tink.subtle.Ed25519.verify
import okio.ByteString

/**
 * Ed25519 verifying.
 *
 * # Usage
 *
 * ```
 * // get the publicKey from the other party.
 * val verifier = new Ed25519Verify(publicKey)
 * if (!verifier.verify(signature, message)) {
 *   // Signature didn't verify.
 * }
 * ```
 *
 * @since 1.1.0
 */
internal class Ed25519Verify(
  private val publicKey: ByteString,
) {
  init {
    require(publicKey.size == PUBLIC_KEY_LEN) {
      "Given public key's length is not $PUBLIC_KEY_LEN."
    }
  }

  fun verify(signature: ByteString, data: ByteString): Boolean {
    if (signature.size != SIGNATURE_LEN) {
      return false
    }
    return verify(data, signature, publicKey)
  }

  companion object {
    const val PUBLIC_KEY_LEN = Field25519.FIELD_LEN
    const val SIGNATURE_LEN = Field25519.FIELD_LEN * 2
  }
}
