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
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.SignatureException
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.EllipticCurve
import java.security.spec.PKCS8EncodedKeySpec
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString

internal class EcdsaP256(
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
  val keySpec = ECPublicKeySpec(point, secp256r1ParamSpec)
  val keyFactory = KeyFactory.getInstance("EC")
  return keyFactory.generatePublic(keySpec) as ECPublicKey
}

/**
 * The Elliptic Curve parameters for the named curve "secp256r1" (also known as P-256 and
 * prime256v1).
 *
 * On Android API 26+ this curve is available via a built-in API:
 *
 * ```
 * AlgorithmParameters.getInstance("EC")
 *   .apply { init(ECGenParameterSpec("secp256r1")) }
 *   .getParameterSpec(ECParameterSpec::class.java)
 * ```
 *
 * https://developer.android.com/reference/java/security/AlgorithmParameters
 * https://datatracker.ietf.org/doc/html/rfc6090
 */
private val secp256r1ParamSpec: ECParameterSpec by lazy {
  ECParameterSpec(
    EllipticCurve(
      ECFieldFp(
        BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
      ),
      BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
      BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
    ),
    ECPoint(
      BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
      BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
    ),
    BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16),
    1,
  )
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
