/*
 * Copyright (C) 2022 Block, Inc.
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
package app.cash.zipline.loader.internal

import app.cash.zipline.Zipline
import app.cash.zipline.loader.internal.tink.subtle.Field25519
import app.cash.zipline.loader.internal.tink.subtle.KeyPair
import app.cash.zipline.loader.internal.tink.subtle.newKeyPairFromSeed
import java.security.SecureRandom
import okio.ByteString.Companion.toByteString

internal actual fun Zipline.multiplatformLoadJsModule(bytecode: ByteArray, id: String) =
  loadJsModule(bytecode, id)

/** Returns a new `<publicKey / privateKey>` KeyPair. */
fun generateEd25519KeyPair(): KeyPair {
  val secretSeed = ByteArray(Field25519.FIELD_LEN)
  secureRandom().nextBytes(secretSeed)

  return newKeyPairFromSeed(secretSeed.toByteString())
}

actual val ecdsa: SignatureAlgorithm = Ecdsa(secureRandom())

internal fun secureRandom(): SecureRandom {
  return SecureRandom().also { it.nextLong() } // Force seeding.
}

fun SignatureAlgorithmId.generateKeyPair(): KeyPair {
  return when (this) {
    SignatureAlgorithmId.Ed25519 -> generateEd25519KeyPair()
    SignatureAlgorithmId.Ecdsa -> Ecdsa(secureRandom()).generateP256KeyPair()
  }
}
