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

class ManifestSigner private constructor(
  private val signers: Map<String, Signer>,
) {
  init {
    require(signers.isNotEmpty()) {
      "signer requires at least one private key"
    }
  }

  /** Returns a copy of [manifest] that is signed with all private keys held by this signer. */
  fun sign(manifest: ZiplineManifest): ZiplineManifest {
    // Sign with each signing key.
    val signaturePayload = manifest.signaturePayload
    val signatures = signers.mapValues { (_, signer) ->
      val signatureBytes = signer.algorithm.sign(signaturePayload, signer.privateKey)
      return@mapValues signatureBytes.hex()
    }

    // Return the updated manifest!
    return manifest.copy(signatures = signatures)
  }

  class Builder {
    private val signers = mutableMapOf<String, Signer>()

    /** Adds an EdDSA Ed25519 public key that will be used to sign manifests. */
    fun addEd25519(
      name: String,
      privateKey: ByteString,
    ) = add(SignatureAlgorithmId.Ed25519, name, privateKey)

    /** Adds an ECDSA P-256 public key that will be used to sign manifests. */
    fun addEcdsaP256(
      name: String,
      privateKey: ByteString,
    ) = add(SignatureAlgorithmId.EcdsaP256, name, privateKey)

    fun add(
      algorithm: SignatureAlgorithmId,
      name: String,
      privateKey: ByteString,
    ) = apply {
      signers[name] = Signer(algorithm.get(), privateKey)
    }

    fun build() = ManifestSigner(signers.toMap())
  }

  private class Signer(
    val algorithm: SignatureAlgorithm,
    val privateKey: ByteString,
  )
}
