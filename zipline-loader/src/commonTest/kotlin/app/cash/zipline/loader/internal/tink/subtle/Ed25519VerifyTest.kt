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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString

/**
 * Tink's unit tests for [Ed25519Verify].
 */
class Ed25519VerifyTest {
  @Test
  fun testVerificationWithPublicKeyLengthDifferentFrom32Byte() {
    assertFailsWith<IllegalArgumentException> {
      Ed25519Verify(ByteArray(31).toByteString())
    }
    assertFailsWith<IllegalArgumentException> {
      Ed25519Verify(ByteArray(33).toByteString())
    }
  }

  @Test
  fun testVerificationWithWycheproofVectors() {
    var errors = 0
    val testGroups = loadEddsaTestJson().testGroups
    for (group in testGroups) {
      val key = group.key
      val publicKey = key.pk!!.decodeHex()
      val tests = group.tests
      for (testcase in tests) {
        val tcId = "testcase ${testcase.tcId} (${testcase.comment})"
        val msg = testcase.msg.decodeHex()
        val sig = testcase.sig.decodeHex()
        val result = testcase.result
        val verifier = Ed25519Verify(publicKey)
        if (verifier.verify(sig, msg)) {
          if (result == "invalid") {
            println("FAIL $tcId: accepting invalid signature")
            errors++
          }
        } else {
          if (result == "valid") {
            println("FAIL $tcId: rejecting valid signature")
            errors++
          }
        }
      }
    }
    assertEquals(0, errors.toLong())
  }
}
