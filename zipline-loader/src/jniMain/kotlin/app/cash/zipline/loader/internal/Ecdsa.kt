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

import app.cash.zipline.loader.internal.tink.subtle.KeyPair
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import okio.ByteString
import okio.ByteString.Companion.toByteString

class Ecdsa(
  private val random: SecureRandom,
) : SignatureAlgorithm {

  /** Generate a key pair using the P-256 curve. */
  fun generateP256KeyPair(): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance("EC")
    keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"), random)
    val keyPair = keyPairGenerator.generateKeyPair()
    return KeyPair(
      publicKey = keyPair.public.encoded.toByteString(),
      privateKey = keyPair.private.encoded.toByteString(),
    )
  }

  fun decodePublicKey(publicKey: ByteString): PublicKey {
    val keyFactory = KeyFactory.getInstance("EC")
    val keySpec = X509EncodedKeySpec(publicKey.toByteArray())
    return keyFactory.generatePublic(keySpec)
  }

  fun decodePrivateKey(privateKey: ByteString): PrivateKey {
    val keyFactory = KeyFactory.getInstance("EC")
    val keySpec = PKCS8EncodedKeySpec(privateKey.toByteArray())
    return keyFactory.generatePrivate(keySpec)
  }

  fun sign(privateKey: PrivateKey, plaintext: ByteString): ByteString {
    val signer = Signature.getInstance("SHA256withECDSA")
    signer.initSign(privateKey, random)
    signer.update(plaintext.toByteArray())
    return signer.sign().toByteString()
  }

  override fun sign(message: ByteString, privateKey: ByteString): ByteString {
    return sign(decodePrivateKey(privateKey), message)
  }

  fun verify(publicKey: PublicKey, signature: ByteString, data: ByteString): Boolean {
    val verifier = Signature.getInstance("SHA256withECDSA")
    verifier.initVerify(publicKey)
    verifier.update(data.toByteArray())
    return verifier.verify(signature.toByteArray())
  }

  override fun verify(message: ByteString, signature: ByteString, publicKey: ByteString): Boolean {
    return verify(decodePublicKey(publicKey), signature, message)
  }
}
