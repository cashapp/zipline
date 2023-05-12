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

import app.cash.zipline.loader.testing.SampleKeys
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

/**
 * There are more tests for this class in `EcdsaP256Test`.
 */
class EcdsaP256JniTest {
  @Test
  fun signAndVerify() {
    val data = "hello world".encodeUtf8()
    val signature = ecdsaP256.sign(data, SampleKeys.key4Private)

    // Valid signature verifies.
    assertTrue(ecdsaP256.verify(data, signature, SampleKeys.key4Public))

    // If the data changes, it doesn't verify.
    assertFalse(ecdsaP256.verify("hello World".encodeUtf8(), signature, SampleKeys.key4Public))

    // If the key changes, it doesn't verify.
    assertFalse(ecdsaP256.verify(data, signature, SampleKeys.key5Public))
  }

  @Test
  fun toUnsignedFixedWidth() {
    // Pads out to the full width.
    assertEquals(
      "00000000".decodeHex(),
      BigInteger("0").toUnsignedFixedWidth(4).toByteString(),
    )
    assertEquals(
      "00000001".decodeHex(),
      BigInteger("1").toUnsignedFixedWidth(4).toByteString(),
    )
    assertEquals(
      "000000ff".decodeHex(),
      BigInteger("255").toUnsignedFixedWidth(4).toByteString(),
    )

    // Leading sign '0' bit is dropped.
    assertEquals(
      "7fffffff".decodeHex(),
      BigInteger("2147483647").toUnsignedFixedWidth(4).toByteString(),
    )
    assertEquals(
      "ffffffff".decodeHex(),
      BigInteger("4294967295").toUnsignedFixedWidth(4).toByteString(),
    )
  }

  /** Sample data from Wycheproof, `ecdsa_secp256r1_sha256_test.json`. */
  @Test
  fun encodeAndDecodeAnsiX963() {
    val derBytes = "3059301306072a8648ce3d020106082a8648ce3d030107034200042927b10512bae3eddcfe467828128bad2903269919f7086069c8c4df6c732838c7787964eaac00e5921fb1498a60f4606766b3d9685001558d1a974e7341513e".decodeHex()
    val ansiX963Bytes = "042927b10512bae3eddcfe467828128bad2903269919f7086069c8c4df6c732838c7787964eaac00e5921fb1498a60f4606766b3d9685001558d1a974e7341513e".decodeHex()
    val decodedDerPublicKey = decodeEcPublicKey(derBytes)
    assertEquals(
      ansiX963Bytes,
      decodedDerPublicKey.encodeAnsiX963(),
    )
    assertEquals(
      decodedDerPublicKey,
      ansiX963Bytes.decodeAnsiX963(),
    )
  }

  private fun decodeEcPublicKey(publicKey: ByteString): ECPublicKey {
    val keyFactory = KeyFactory.getInstance("EC")
    val keySpec = X509EncodedKeySpec(publicKey.toByteArray())
    return keyFactory.generatePublic(keySpec) as ECPublicKey
  }
}
