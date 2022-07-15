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

import app.cash.zipline.loader.internal.signaturePayload
import app.cash.zipline.loader.internal.tink.subtle.Ed25519Verify
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8

/**
 * Confirms the manifest is cryptographically signed by a trusted key.
 */
class ManifestVerifier private constructor(
  private val trustedKeys: Map<String, Ed25519Verify>,
) {
  init {
    require(trustedKeys.isNotEmpty()) {
      "verifier requires at least one trusted key"
    }
  }

  /**
   * Returns normally if [manifest] is signed by a trusted key in [trustedKeys]. This will check the
   * first key in [ZiplineManifest.signatures] that is recognized.
   *
   * This throws an exception if no trusted signature is found, or if a signature doesn't verify.
   */
  fun verify(manifestBytes: ByteString, manifest: ZiplineManifest) {
    val signaturePayload = signaturePayload(manifestBytes.utf8())
    val signaturePayloadBytes = signaturePayload.encodeUtf8()

    for ((keyName, signature) in manifest.signatures) {
      val trustedKey = trustedKeys[keyName] ?: continue

      check(trustedKey.verify(signature.decodeHex(), signaturePayloadBytes)) {
        "manifest signature for key $keyName did not verify!"
      }

      return // Success!
    }

    throw IllegalStateException(
      """
      |no keys in the manifest were recognized for signature verification!
      |  trusted keys: ${trustedKeys.keys}
      |  manifest keys: ${manifest.signatures.keys}
      """.trimMargin()
    )
  }

  class Builder {
    private val trustedKeys = mutableMapOf<String, Ed25519Verify>()

    /** Adds an EdDSA Ed25519 public key that will be used to verify manifests. */
    fun addEd25519(
      name: String,
      trustedKey: ByteString,
    ) = apply {
      trustedKeys[name] = Ed25519Verify(trustedKey)
    }

    fun build()= ManifestVerifier(trustedKeys.toMap())
  }
}
