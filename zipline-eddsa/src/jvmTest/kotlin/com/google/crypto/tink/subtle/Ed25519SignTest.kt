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
package com.google.crypto.tink.subtle

import java.security.GeneralSecurityException
import java.util.TreeSet
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tink's unit tests for [Ed25519Sign].
 */
class Ed25519SignTest {
  @Test
  fun testSigningOneKeyWithMultipleMessages() {
    val keyPair = Ed25519Sign.KeyPair.newKeyPair()
    val signer = Ed25519Sign(keyPair.privateKey)
    val verifier = Ed25519Verify(keyPair.publicKey)
    for (i in 0..99) {
      val msg = Random.randBytes(20)
      val sig = signer.sign(msg)
      try {
        verifier.verify(sig, msg)
      } catch (ex: GeneralSecurityException) {
        fail(
          """
          |Message: ${msg.toByteString().hex()}
          |Signature: ${sig.toByteString().hex()}
          |PrivateKey: ${keyPair.privateKey.toByteString().hex()}
          |PublicKey: ${keyPair.publicKey.toByteString().hex()}
          """.trimMargin(),
        )
      }
    }
  }

  @Test
  fun testSigningOneKeyWithTheSameMessage() {
    val keyPair = Ed25519Sign.KeyPair.newKeyPair()
    val signer = Ed25519Sign(keyPair.privateKey)
    val verifier = Ed25519Verify(keyPair.publicKey)
    val msg = Random.randBytes(20)
    val allSignatures = TreeSet<String>()
    for (i in 0..99) {
      val sig = signer.sign(msg)
      allSignatures.add(ByteString.of(*sig).hex())
      try {
        verifier.verify(sig, msg)
      } catch (ex: GeneralSecurityException) {
        fail(
          """
          |Message: ${msg.toByteString().hex()}
          |Signature: ${sig.toByteString().hex()}
          |PrivateKey: ${keyPair.privateKey.toByteString().hex()}
          |PublicKey: ${keyPair.publicKey.toByteString().hex()}
          """.trimMargin(),
        )
      }
    }
    // Ed25519 is deterministic, expect a unique signature for the same message.
    assertEquals(1, allSignatures.size.toLong())
  }

  @Test
  fun testSignWithPrivateKeyLengthDifferentFrom32Byte() {
    assertThrows(IllegalArgumentException::class.java) {
      Ed25519Sign(ByteArray(31))
    }
    assertThrows(IllegalArgumentException::class.java) {
      Ed25519Sign(ByteArray(33))
    }
  }

  @Test
  fun testSigningWithMultipleRandomKeysAndMessages() {
    for (i in 0..99) {
      val keyPair = Ed25519Sign.KeyPair.newKeyPair()
      val signer = Ed25519Sign(keyPair.privateKey)
      val verifier = Ed25519Verify(keyPair.publicKey)
      val msg = Random.randBytes(20)
      val sig = signer.sign(msg)
      try {
        verifier.verify(sig, msg)
      } catch (ex: GeneralSecurityException) {
        fail(
          """
          |Message: ${msg.toByteString().hex()}
          |Signature: ${sig.toByteString().hex()}
          |PrivateKey: ${keyPair.privateKey.toByteString().hex()}
          |PublicKey: ${keyPair.publicKey.toByteString().hex()}
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
      val privateKey = key.sk.decodeHex().toByteArray()
      val tests = group.tests
      for (testcase in tests) {
        val tcId = "testcase ${testcase.tcId} (${testcase.comment})"
        val msg = testcase.msg.decodeHex().toByteArray()
        val sig = testcase.sig.decodeHex().toByteArray()
        val result = testcase.result
        if (result == "invalid") {
          continue
        }
        val signer = Ed25519Sign(privateKey)
        val computedSig = signer.sign(msg)
        assertArrayEquals(tcId, sig, computedSig)
      }
    }
    assertEquals(0, errors.toLong())
  }

  @Test
  fun testKeyPairFromSeedTooShort() {
    val keyMaterial = Random.randBytes(10)
    assertThrows(IllegalArgumentException::class.java) {
      Ed25519Sign.KeyPair.newKeyPairFromSeed(keyMaterial)
    }
  }
}
