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

import app.cash.zipline.loader.generateKeyPairForTest
import app.cash.zipline.loader.randomByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString

/**
 * Tink's unit tests for [Ed25519Sign].
 */
class Ed25519SignTest {
  @Test
  fun testSigningOneKeyWithMultipleMessages() {
    val keyPair = generateKeyPairForTest()
    val signer = Ed25519Sign(keyPair.privateKey)
    val verifier = Ed25519Verify(keyPair.publicKey)
    for (i in 0..99) {
      val msg = randomByteString(20)
      val sig = signer.sign(msg)
      if (!verifier.verify(sig, msg)) {
        fail(
          """
          |Message: ${msg.hex()}
          |Signature: ${sig.hex()}
          |PrivateKey: ${keyPair.privateKey.hex()}
          |PublicKey: ${keyPair.publicKey.hex()}
          """.trimMargin(),
        )
      }
    }
  }

  @Test
  fun testSigningOneKeyWithTheSameMessage() {
    val keyPair = generateKeyPairForTest()
    val signer = Ed25519Sign(keyPair.privateKey)
    val verifier = Ed25519Verify(keyPair.publicKey)
    val msg = randomByteString(20)
    val allSignatures = mutableSetOf<String>()
    for (i in 0..99) {
      val sig = signer.sign(msg)
      allSignatures.add(sig.hex())
      if (!verifier.verify(sig, msg)) {
        fail(
          """
          |Message: ${msg.hex()}
          |Signature: ${sig.hex()}
          |PrivateKey: ${keyPair.privateKey.hex()}
          |PublicKey: ${keyPair.publicKey.hex()}
          """.trimMargin(),
        )
      }
    }
    // Ed25519 is deterministic, expect a unique signature for the same message.
    assertEquals(1, allSignatures.size.toLong())
  }

  @Test
  fun testSignWithPrivateKeyLengthDifferentFrom32Byte() {
    assertFailsWith<IllegalArgumentException> {
      Ed25519Sign(ByteArray(31).toByteString())
    }
    assertFailsWith<IllegalArgumentException> {
      Ed25519Sign(ByteArray(33).toByteString())
    }
  }

  @Test
  fun testSigningWithMultipleRandomKeysAndMessages() {
    for (i in 0..99) {
      val keyPair = generateKeyPairForTest()
      val signer = Ed25519Sign(keyPair.privateKey)
      val verifier = Ed25519Verify(keyPair.publicKey)
      val msg = randomByteString(20)
      val sig = signer.sign(msg)
      if (!verifier.verify(sig, msg)) {
        fail(
          """
          |Message: ${msg.hex()}
          |Signature: ${sig.hex()}
          |PrivateKey: ${keyPair.privateKey.hex()}
          |PublicKey: ${keyPair.publicKey.hex()}
          """.trimMargin(),
        )
      }
    }
  }

  @Test
  fun testSigningWithWycheproofVectors() {
    val errors = 0
    val testGroups = loadEddsaTestJson().testGroups
    for (group in testGroups) {
      val key = group.key
      val privateKey = key.sk!!.decodeHex()
      val tests = group.tests
      for (testcase in tests) {
        val tcId = "testcase ${testcase.tcId} (${testcase.comment})"
        val msg = testcase.msg.decodeHex()
        val sig = testcase.sig.decodeHex()
        val result = testcase.result
        if (result == "invalid") {
          continue
        }
        val signer = Ed25519Sign(privateKey)
        val computedSig = signer.sign(msg)
        assertEquals(sig, computedSig, tcId)
      }
    }
    assertEquals(0, errors.toLong())
  }

  @Test
  fun testKeyPairFromSeedTooShort() {
    val keyMaterial = randomByteString(10)
    assertFailsWith<IllegalArgumentException> {
      newKeyPairFromSeed(keyMaterial)
    }
  }
}
