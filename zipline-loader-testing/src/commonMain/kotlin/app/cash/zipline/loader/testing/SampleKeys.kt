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
package app.cash.zipline.loader.testing

import okio.ByteString.Companion.decodeHex

/** Keys generated with `KeyPair.newKeyPair()` (JVM-only). */
object SampleKeys {
  val key1Public = "9d91c44b8566071900928188c84e028ab0c9ac83292d7334c177a43a41347a1a".decodeHex()
  val key1Private = "ae4737d95df505eac2424000559d072d91db00192756b265a9792007d743cdf7".decodeHex()
  val key2Public = "3a746afed460db40ab6976307736539be17a4e4d7bbd8ba4ffb797ba799c98af".decodeHex()
  val key2Private = "6207b6f19c9d7dfa8af31ed5d97891112a877b43b6d8c0f5f1086b170037ba32".decodeHex()
  val key3Public = "6caa9a465cc893cf5b5f8eeafa0e041e907c94ceae6f42b8fa07bed1b1c7b161".decodeHex()
  val key3Private = "e8808e10fe275a9aa8370fab885a1cbb4cc747efbdf77406ebe08155085e6b56".decodeHex()
}
