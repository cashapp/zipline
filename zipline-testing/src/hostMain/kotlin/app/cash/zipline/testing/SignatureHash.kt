/*
 * Copyright (C) 2023 Cash App
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
package app.cash.zipline.testing

import okio.ByteString.Companion.encodeUtf8

/**
 * Returns an 8-character base64-encoded SHA-256 hash.
 *
 * The hash function, length, and base64 are chosen to avoid collisions for most interface
 * definitions (usually less than 100 functions), while minimizing encoded length.
 *
 * Note that no hashing occurs at runtime. Instead, function signatures are hashed in the Zipline
 * Kotlin compiler plugin and the outputs of those hashes are inlined in the generated code.
 *
 * The probability of a collision in a service declaring 1,000 functions is 0.000,000,002. This
 * is not a security mechanism and an attacker seeking to create a collision could name their
 * functions to do so.
 *
 * Don't change how this works! Doing so will break upgrades for programs compiled with different
 * identifiers.
 */
fun String.signatureHash(): String = encodeUtf8().sha256().substring(0, 6).base64() // In base64, 6 bytes takes 8 chars.
