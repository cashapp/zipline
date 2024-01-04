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
import app.cash.zipline.loader.internal.SignatureAlgorithm
import app.cash.zipline.loader.internal.get
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8

/**
 * Confirms the manifest is cryptographically signed by a trusted key.
 */
class ManifestVerifier private constructor(
  private val doSignatureChecks: Boolean,
  private val verifiers: Map<String, Verifier>,
) {
  init {
    require(!doSignatureChecks || verifiers.isNotEmpty()) {
      "verifier requires at least one trusted key"
    }
  }

  /**
   * Returns normally if [manifest] is signed by a trusted key in [verifiers]. This will check the
   * first key in [ZiplineManifest.signatures] that is recognized.
   *
   * @return the key that was verified, which is also the first key that was recognized. This
   *   will only return null if this is the special [NO_SIGNATURE_CHECKS] verifier.
   * @throws IllegalStateException if no trusted signature is found, or if a signature doesn't
   *   verify.
   */
  @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
  fun verify(manifestBytes: ByteString, manifest: ZiplineManifest): String? {
    if (!doSignatureChecks) return null

    val signaturePayload = app.cash.zipline.internal.signaturePayload(manifestBytes.utf8())
    val signaturePayloadBytes = signaturePayload.encodeUtf8()

    for ((keyName, signature) in manifest.signatures) {
      val verifier = verifiers[keyName] ?: continue

      check(
        verifier.algorithm.verify(
          message = signaturePayloadBytes,
          signature = signature.decodeHex(),
          publicKey = verifier.trustedKey,
        ),
      ) {
        "manifest signature for key $keyName did not verify!"
      }

      return keyName // Success!
    }

    throw IllegalStateException(
      """
      |no keys in the manifest were recognized for signature verification!
      |  trusted keys: ${verifiers.keys}
      |  manifest keys: ${manifest.signatures.keys}
      """.trimMargin(),
    )
  }

  class Builder {
    private val verifiers = mutableMapOf<String, Verifier>()

    /** Adds an EdDSA Ed25519 public key that will be used to verify manifests. */
    fun addEd25519(
      name: String,
      trustedKey: ByteString,
    ) = add(SignatureAlgorithmId.Ed25519, name, trustedKey)

    /** Adds an ECDSA P-256 public key that will be used to verify manifests. */
    fun addEcdsaP256(
      name: String,
      trustedKey: ByteString,
    ) = add(SignatureAlgorithmId.EcdsaP256, name, trustedKey)

    fun add(
      algorithm: SignatureAlgorithmId,
      name: String,
      trustedKey: ByteString,
    ) = apply {
      verifiers[name] = Verifier(algorithm.get(), trustedKey)
    }

    fun build() = ManifestVerifier(
      doSignatureChecks = true,
      verifiers = verifiers.toMap(),
    )
  }

  private class Verifier(
    val algorithm: SignatureAlgorithm,
    val trustedKey: ByteString,
  )

  companion object {
    /**
     * A special instance of [ManifestVerifier] that doesn't do any signature checks. Use this in
     * development and tests to skip code signing.
     */
    val NO_SIGNATURE_CHECKS = ManifestVerifier(
      doSignatureChecks = false,
      verifiers = mapOf(),
    )
  }
}
