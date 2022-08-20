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
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * ECDSA using the P-256 (secp256r1) curve.
 *
 * We've got (at least) two choices for encoding public keys as bytes:
 *
 *  * Java's format. This is the ASN.1 of the `SubjectPublicKeyInfo` from X.509, encoded with the
 *    `X509EncodedKeySpec` class.
 *  * Apple's format. This is ANSI X9.62, encoded as 3 fixed-width values concatenated together.
 *
 * We use Apple's format because it's more compact, and because it's easy enough to support on both
 * platforms.
 *
 * Private keys are encoded using PKCS8.
 *
 * https://developer.apple.com/documentation/security/certificate_key_and_trust_services/keys/storing_keys_as_data
 */
class EcdsaP256(
  private val random: SecureRandom,
) : SignatureAlgorithm {

  /** Generate a key pair using the P-256 curve. */
  fun generateP256KeyPair(): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance("EC")
    keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"), random)
    val keyPair = keyPairGenerator.generateKeyPair()
    return KeyPair(
      publicKey = (keyPair.public as ECPublicKey).encodeAnsiX963(),
      privateKey = keyPair.private.encoded.toByteString(),
    )
  }

  fun sign(privateKey: PrivateKey, plaintext: ByteString): ByteString {
    val signer = Signature.getInstance("SHA256withECDSA")
    signer.initSign(privateKey, random)
    signer.update(plaintext.toByteArray())
    return signer.sign().toByteString()
  }

  override fun sign(message: ByteString, privateKey: ByteString): ByteString {
    return sign(decodePkcs8EcPrivateKey(privateKey), message)
  }

  fun verify(publicKey: PublicKey, signature: ByteString, data: ByteString): Boolean {
    val verifier = Signature.getInstance("SHA256withECDSA")
    verifier.initVerify(publicKey)
    verifier.update(data.toByteArray())
    return verifier.verify(signature.toByteArray())
  }

  override fun verify(message: ByteString, signature: ByteString, publicKey: ByteString): Boolean {
    return verify(publicKey.decodeAnsiX963(), signature, message)
  }
}

internal fun decodePkcs8EcPrivateKey(privateKey: ByteString): PrivateKey {
  val keyFactory = KeyFactory.getInstance("EC")
  val keySpec = PKCS8EncodedKeySpec(privateKey.toByteArray())
  return keyFactory.generatePrivate(keySpec)
}

/** ANSI X9.63 standard concatenates `0x04 || X || Y`. */
internal fun ECPublicKey.encodeAnsiX963(): ByteString {
  return Buffer()
    .writeByte(4)
    .write(w.affineX.toUnsignedFixedWidth(32))
    .write(w.affineY.toUnsignedFixedWidth(32))
    .readByteString()
}

internal fun ByteString.decodeAnsiX963(): ECPublicKey {
  val buffer = Buffer().write(this)
  require(4 == buffer.readByte().toInt())
  val x = buffer.readByteArray(32)
  val y = buffer.readByteArray(32)
  require(buffer.exhausted())
  val point = ECPoint(BigInteger(1, x), BigInteger(1, y))

  val keySpecParameters = AlgorithmParameters.getInstance("EC").apply {
    init(ECGenParameterSpec("secp256r1"))
  }
  val keySpec = ECPublicKeySpec(
    point,
    keySpecParameters.getParameterSpec(ECParameterSpec::class.java),
  )

  val keyFactory = KeyFactory.getInstance("EC")
  return keyFactory.generatePublic(keySpec) as ECPublicKey
}

internal fun BigInteger.toUnsignedFixedWidth(byteCount: Int): ByteArray {
  val result = ByteArray(byteCount)
  val signedBytes = toByteArray()
  val startIndex = maxOf(signedBytes.size - byteCount, 0)
  signedBytes.copyInto(
    destination = result,
    destinationOffset = byteCount - (signedBytes.size - startIndex),
    startIndex = startIndex,
  )
  return result
}
