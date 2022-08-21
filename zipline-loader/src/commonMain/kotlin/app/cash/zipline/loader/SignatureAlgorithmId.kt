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

enum class SignatureAlgorithmId {
  /**
   * Edwards-curve Digital Signature Algorithm (EdDSA) using the Ed25519 curve.
   *
   * Both public and private keys are 32 bytes. Signatures are 64 bytes.
   *
   * https://en.wikipedia.org/wiki/EdDSA
   */
  Ed25519,

  /**
   * ECDSA using the P-256 (secp256r1) curve.
   *
   * We've got (at least) two choices for encoding public keys as bytes:
   *
   *  * Java's format. This is the ASN.1 of the `SubjectPublicKeyInfo` from X.509, encoded with the
   *    `X509EncodedKeySpec` class.
   *  * Apple's format. This is ANSI X9.62, encoded as 3 fixed-width values concatenated together,
   *    `0x04 || X || Y`. The encoded key is always 1 + 32 + 32 = 65 bytes.
   *
   * We use Apple's format because it's more compact, and because it's easy enough to support on
   * both platforms.
   *
   * Private keys are encoded using PKCS8.
   *
   * https://en.wikipedia.org/wiki/Elliptic_Curve_Digital_Signature_Algorithm
   * https://developer.apple.com/documentation/security/certificate_key_and_trust_services/keys/storing_keys_as_data
   */
  EcdsaP256,
}
