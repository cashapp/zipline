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
import java.util.TreeSet;
import okio.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static com.google.crypto.tink.subtle.WycheproofKt.loadEddsaTestJson;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link Ed25519Sign}.
 *
 */
@RunWith(JUnit4.class)
public final class Ed25519SignTest {

  public String hexEncode(byte[] bytes) {
    return ByteString.of(bytes).hex();
  }

  @Test
  public void testSigningOneKeyWithMultipleMessages() throws Exception {
    Ed25519Sign.KeyPair keyPair = Ed25519Sign.KeyPair.newKeyPair();
    Ed25519Sign signer = new Ed25519Sign(keyPair.getPrivateKey());
    Ed25519Verify verifier = new Ed25519Verify(keyPair.getPublicKey());
    for (int i = 0; i < 100; i++) {
      byte[] msg = Random.randBytes(20);
      byte[] sig = signer.sign(msg);
      try {
        verifier.verify(sig, msg);
      } catch (GeneralSecurityException ex) {
        fail(
            String.format(
                "\n\nMessage: %s\nSignature: %s\nPrivateKey: %s\nPublicKey: %s\n",
                hexEncode(msg),
                hexEncode(sig),
                hexEncode(keyPair.getPrivateKey()),
                hexEncode(keyPair.getPublicKey())));
      }
    }
  }

  @Test
  public void testSigningOneKeyWithTheSameMessage() throws Exception {
    Ed25519Sign.KeyPair keyPair = Ed25519Sign.KeyPair.newKeyPair();
    Ed25519Sign signer = new Ed25519Sign(keyPair.getPrivateKey());
    Ed25519Verify verifier = new Ed25519Verify(keyPair.getPublicKey());
    byte[] msg = Random.randBytes(20);
    TreeSet<String> allSignatures = new TreeSet<String>();
    for (int i = 0; i < 100; i++) {
      byte[] sig = signer.sign(msg);
      allSignatures.add(hexEncode(sig));
      try {
        verifier.verify(sig, msg);
      } catch (GeneralSecurityException ex) {
        fail(
            String.format(
                "\n\nMessage: %s\nSignature: %s\nPrivateKey: %s\nPublicKey: %s\n",
                hexEncode(msg),
                hexEncode(sig),
                hexEncode(keyPair.getPrivateKey()),
                hexEncode(keyPair.getPublicKey())));
      }
    }
    // Ed25519 is deterministic, expect a unique signature for the same message.
    assertEquals(1, allSignatures.size());
  }

  @Test
  public void testSignWithPrivateKeyLengthDifferentFrom32Byte() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          Ed25519Sign unused = new Ed25519Sign(new byte[31]);
        });
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          Ed25519Sign unused = new Ed25519Sign(new byte[33]);
        });
  }

  @Test
  public void testSigningWithMultipleRandomKeysAndMessages() throws Exception {
    for (int i = 0; i < 100; i++) {
      Ed25519Sign.KeyPair keyPair = Ed25519Sign.KeyPair.newKeyPair();
      Ed25519Sign signer = new Ed25519Sign(keyPair.getPrivateKey());
      Ed25519Verify verifier = new Ed25519Verify(keyPair.getPublicKey());
      byte[] msg = Random.randBytes(20);
      byte[] sig = signer.sign(msg);
      try {
        verifier.verify(sig, msg);
      } catch (GeneralSecurityException ex) {
        fail(
            String.format(
                "\n\nMessage: %s\nSignature: %s\nPrivateKey: %s\nPublicKey: %s\n",
                hexEncode(msg),
                hexEncode(sig),
                hexEncode(keyPair.getPrivateKey()),
                hexEncode(keyPair.getPublicKey())));
      }
    }
  }

  @Test
  public void testSigningWithWycheproofVectors() throws Exception {
    int errors = 0;
    List<TestGroup> testGroups = loadEddsaTestJson().getTestGroups();
    for (TestGroup group : testGroups) {
      Key key = group.getKey();
      byte[] privateKey = ByteString.decodeHex(key.getSk()).toByteArray();
      List<TestCase> tests = group.getTests();
      for (TestCase testcase : tests) {
        String tcId = String.format("testcase %d (%s)", testcase.getTcId(), testcase.getComment());
        byte[] msg = ByteString.decodeHex(testcase.getMsg()).toByteArray();
        byte[] sig = ByteString.decodeHex(testcase.getSig()).toByteArray();
        String result = testcase.getResult();
        if (result.equals("invalid")) {
          continue;
        }
        Ed25519Sign signer = new Ed25519Sign(privateKey);
        byte[] computedSig = signer.sign(msg);
        assertArrayEquals(tcId, sig, computedSig);
      }
    }
    assertEquals(0, errors);
  }

  @Test
  public void testKeyPairFromSeedTooShort() throws Exception {
    byte[] keyMaterial = Random.randBytes(10);
    assertThrows(
        IllegalArgumentException.class, () -> Ed25519Sign.KeyPair.newKeyPairFromSeed(keyMaterial));
  }
}
