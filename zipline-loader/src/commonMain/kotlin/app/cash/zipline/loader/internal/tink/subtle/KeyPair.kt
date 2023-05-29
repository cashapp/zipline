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

import okio.ByteString

/** Defines the KeyPair consisting of a private key and its corresponding public key.  */
internal class KeyPair internal constructor(
  val publicKey: ByteString,
  val privateKey: ByteString,
)

/** Returns a new `<publicKey / privateKey>` KeyPair generated from a seed. */
internal fun newKeyPairFromSeed(secretSeed: ByteString): KeyPair {
  require(secretSeed.size == Field25519.FIELD_LEN) {
    "Given secret seed length is not ${Field25519.FIELD_LEN}"
  }
  val publicKey = Ed25519.scalarMultWithBaseToBytes(Ed25519.getHashedScalar(secretSeed))
  return KeyPair(publicKey, secretSeed)
}
