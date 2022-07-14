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

package com.google.crypto.tink.subtle;

import java.security.GeneralSecurityException;
import java.util.List;
import okio.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static com.google.crypto.tink.subtle.WycheproofKt.loadEddsaTestJson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Unit tests for {@link Ed25519Verify}.
 *
 */
@RunWith(JUnit4.class)
public final class Ed25519VerifyTest {
  @Test
  public void testVerificationWithPublicKeyLengthDifferentFrom32Byte() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          Ed25519Verify unused = new Ed25519Verify(new byte[31]);
        });
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          Ed25519Verify unused = new Ed25519Verify(new byte[33]);
        });
  }

  @Test
  public void testVerificationWithWycheproofVectors() throws Exception {
    int errors = 0;
    List<TestGroup> testGroups = loadEddsaTestJson().getTestGroups();
    for (TestGroup group : testGroups) {
      Key key = group.getKey();
      byte[] publicKey = ByteString.decodeHex(key.getPk()).toByteArray();
      List<TestCase> tests = group.getTests();
      for (TestCase testcase : tests) {
        String tcId = String.format("testcase %d (%s)", testcase.getTcId(), testcase.getComment());
        byte[] msg = ByteString.decodeHex(testcase.getMsg()).toByteArray();
        byte[] sig = ByteString.decodeHex(testcase.getSig()).toByteArray();
        String result = testcase.getResult();
        Ed25519Verify verifier = new Ed25519Verify(publicKey);
        try {
          verifier.verify(sig, msg);
          if (result.equals("invalid")) {
            System.out.printf("FAIL %s: accepting invalid signature%n", tcId);
            errors++;
          }
        } catch (GeneralSecurityException ex) {
          if (result.equals("valid")) {
            System.out.printf("FAIL %s: rejecting valid signature, exception: %s%n", tcId, ex);
            errors++;
          }
        }
      }
    }
    assertEquals(0, errors);
  }
}
