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
import java.security.SignatureException
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString

class EcdsaP256(
  private val random: SecureRandom,
) : SignatureAlgorithm {

  /** Generate a key pair using the P-256 curve. */
  fun generateKeyPair(): KeyPair {
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
    val ecPublicKey = publicKey.decodeAnsiX963()
    return try {
      verify(ecPublicKey, signature, message)
    } catch (e: SignatureException) {
      false // Malformed signature.
    }
  }
}

internal fun decodePkcs8EcPrivateKey(privateKey: ByteString): PrivateKey {
  val keyFactory = KeyFactory.getInstance("EC")
  val keySpec = PKCS8EncodedKeySpec(privateKey.toByteArray())
  return keyFactory.generatePrivate(keySpec)
}

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

/**
 * Assuming [this] is a non-negative integer, encode it as a big-endian byte array of length
 * [byteCount]. This function assumes the encoded value will fit.
 *
 * This makes two adjustments to the result of [BigInteger.toByteArray]:
 *
 *  * It is padded at the start if that value doesn't need [byteCount] bytes.
 *  * It drops a possible leading 0 byte. Because `BigInteger` is signed its encoding always
 *    includes a leading sign bit, which will always be zero. An unsigned 32-bit integer may require
 *    33 bits to encode!
 */
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
