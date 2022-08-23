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

import app.cash.zipline.Zipline
import app.cash.zipline.loader.SignatureAlgorithmId
import app.cash.zipline.loader.internal.tink.subtle.Ed25519

// TODO: drop this once we adopt Kotlin Hierarchical Multiplatform projects
internal expect fun Zipline.multiplatformLoadJsModule(bytecode: ByteArray, id: String)

const val MANIFEST_FILE_NAME = "manifest.zipline.json"
fun getApplicationManifestFileName(applicationName: String) =
  "$applicationName.$MANIFEST_FILE_NAME"

/** ECDSA P-256. https://en.wikipedia.org/wiki/Elliptic_Curve_Digital_Signature_Algorithm */
expect val ecdsaP256: SignatureAlgorithm

fun SignatureAlgorithmId.get(): SignatureAlgorithm {
  return when (this) {
    SignatureAlgorithmId.Ed25519 -> Ed25519
    SignatureAlgorithmId.EcdsaP256 -> ecdsaP256
  }
}

/** Returns the URL of [link] relative to [baseUrl]. */
expect fun resolveUrl(baseUrl: String, link: String): String
