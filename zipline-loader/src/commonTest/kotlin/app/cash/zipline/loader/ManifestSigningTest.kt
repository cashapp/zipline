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
package app.cash.zipline.loader

import app.cash.zipline.ZiplineManifest
import app.cash.zipline.loader.testing.SampleKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okio.ByteString.Companion.encodeUtf8

class ManifestSigningTest {
  private val signer1 = ManifestSigner.Builder()
    .addEd25519("key1", SampleKeys.key1Private)
    .build()

  private val verifier1 = ManifestVerifier.Builder()
    .addEd25519("key1", SampleKeys.key1Public)
    .build()

  private val signer2 = ManifestSigner.Builder()
    .addEd25519("key2", SampleKeys.key2Private)
    .build()

  private val verifier2 = ManifestVerifier.Builder()
    .addEd25519("key2", SampleKeys.key2Public)
    .build()

  private val verifier3 = ManifestVerifier.Builder()
    .addEd25519("key3", SampleKeys.key3Public)
    .build()

  private val signer4 = ManifestSigner.Builder()
    .addEcdsaP256("key4", SampleKeys.key4Private)
    .build()

  private val verifier4 = ManifestVerifier.Builder()
    .addEcdsaP256("key4", SampleKeys.key4Public)
    .build()

  private val verifier5 = ManifestVerifier.Builder()
    .addEcdsaP256("key5", SampleKeys.key5Public)
    .build()

  private val signer12 = ManifestSigner.Builder()
    .addEd25519("key1", SampleKeys.key1Private)
    .addEd25519("key2", SampleKeys.key2Private)
    .build()

  private val signer14 = ManifestSigner.Builder()
    .addEd25519("key1", SampleKeys.key1Private)
    .addEcdsaP256("key4", SampleKeys.key4Private)
    .build()

  private val verifier12 = ManifestVerifier.Builder()
    .addEd25519("key1", SampleKeys.key1Public)
    .addEd25519("key2", SampleKeys.key2Public)
    .build()

  private val verifier14 = ManifestVerifier.Builder()
    .addEd25519("key1", SampleKeys.key1Public)
    .addEcdsaP256("key4", SampleKeys.key4Public)
    .build()

  private val verifier23 = ManifestVerifier.Builder()
    .addEd25519("key2", SampleKeys.key2Public)
    .addEd25519("key3", SampleKeys.key3Public)
    .build()

  private val verifier25 = ManifestVerifier.Builder()
    .addEd25519("key2", SampleKeys.key2Public)
    .addEcdsaP256("key5", SampleKeys.key5Public)
    .build()

  private val manifest = ZiplineManifest.create(
    modules = mapOf(
      "bravo" to ZiplineManifest.Module(
        url = "/bravo.zipline",
        sha256 = "abc123".encodeUtf8(),
      ),
    ),
  )

  @Test
  fun happyPathEd25519() {
    val signedManifest = signer1.sign(manifest)
    assertEquals("key1", verifier1.verify(signedManifest))
  }

  @Test
  fun happyPathEcdsa() {
    if (!canSignEcdsaP256()) return

    val signedManifest = signer4.sign(manifest)
    assertEquals("key4", verifier4.verify(signedManifest))
  }

  @Test
  fun keyRotation() {
    // In the beginning we have a single key.
    val manifestA = signer1.sign(manifest)
    assertEquals("key1", verifier1.verify(manifestA))

    // Next introduce a second key, but don't verify with it yet.
    val manifestB = signer12.sign(manifest)
    assertEquals("key1", verifier1.verify(manifestB))

    // Next start verifying with the new key.
    assertEquals("key1", verifier12.verify(manifestB))

    // Now that key2 is always available, clients don't need to trust key1.
    assertEquals("key2", verifier2.verify(manifestB))

    // Finally, we stop signing with the original key.
    val manifestC = signer2.sign(manifest)
    assertFailsWith<IllegalStateException> {
      verifier1.verify(manifestC) // Old clients stop working!
    }
    assertEquals("key2", verifier12.verify(manifestC))
    assertEquals("key2", verifier2.verify(manifestC))
  }

  @Test
  fun failsDueToTrustedKeyNotFound() {
    val signedManifest = signer1.sign(manifest)
    assertFailsWith<IllegalStateException> {
      verifier2.verify(signedManifest)
    }
  }

  @Test
  fun failsDueToSignatureMismatch() {
    val verifier1WithWrongKey = ManifestVerifier.Builder()
      .addEd25519("key1", SampleKeys.key2Public)
      .build()
    val signedManifest = signer1.sign(manifest)
    assertFailsWith<IllegalStateException> {
      verifier1WithWrongKey.verify(signedManifest)
    }
  }

  @Test
  fun manifestWithMultipleSignatures() {
    val signedManifest = signer12.sign(manifest)
    verifier1.verify(signedManifest)
    verifier2.verify(signedManifest)
    assertFailsWith<IllegalStateException> {
      verifier3.verify(signedManifest)
    }
  }

  @Test
  fun manifestWithMultipleSignaturesWithDifferentAlgorithms() {
    if (!canSignEcdsaP256()) return

    val signedManifest = signer14.sign(manifest)
    verifier1.verify(signedManifest)
    verifier4.verify(signedManifest)
    assertFailsWith<IllegalStateException> {
      verifier3.verify(signedManifest)
    }
    assertFailsWith<IllegalStateException> {
      verifier5.verify(signedManifest)
    }
  }

  @Test
  fun verifierWithMultipleKeys() {
    val signedManifest = signer1.sign(manifest)
    verifier12.verify(signedManifest)
    assertFailsWith<IllegalStateException> {
      verifier23.verify(signedManifest)
    }
  }

  @Test
  fun verifierWithMultipleKeysWithDifferentAlgorithms() {
    val signedManifest = signer1.sign(manifest)
    verifier14.verify(signedManifest)
    assertFailsWith<IllegalStateException> {
      verifier25.verify(signedManifest)
    }
  }

  @Test
  fun verifiesOnFirstMatchingKeyOnly() {
    val signedManifest = signer12.sign(manifest)

    // This verifier would fail if we were verifying key2 only because it has a signature mismatch
    // there. This is caused by using the wrong bytes for key2.
    val verifier12WithWrongKey2 = ManifestVerifier.Builder()
      .addEd25519("key1", SampleKeys.key1Public)
      .addEd25519("key2", SampleKeys.key3Public)
      .build()

    assertEquals("key1", verifier12WithWrongKey2.verify(signedManifest))
  }

  @Test
  fun verifyFailsOnFirstMatchingKey() {
    val signedManifest = signer12.sign(manifest)

    // This verifier should fail because key1 has the wrong bytes, and that's the first key we
    // attempt.
    val verifier12WithWrongKey1 = ManifestVerifier.Builder()
      .addEd25519("key1", SampleKeys.key3Public)
      .addEd25519("key2", SampleKeys.key2Public)
      .build()

    assertFailsWith<IllegalStateException> {
      verifier12WithWrongKey1.verify(signedManifest)
    }
  }

  /**
   * We normally expect to be verifying bytes, since that's the only reliable way to guarantee that
   * unknown fields are preserved. For tests, we can encode directly.
   */
  private fun ManifestVerifier.verify(manifest: ZiplineManifest): String? {
    val manifestBytes = manifest.encodeJson().encodeUtf8()
    return verify(manifestBytes, manifest)
  }
}
