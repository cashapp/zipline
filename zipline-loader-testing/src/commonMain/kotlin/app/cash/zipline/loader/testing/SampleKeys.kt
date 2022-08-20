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
  // Ed25519 keys.
  val key1Public = "9d91c44b8566071900928188c84e028ab0c9ac83292d7334c177a43a41347a1a".decodeHex()
  val key1Private = "ae4737d95df505eac2424000559d072d91db00192756b265a9792007d743cdf7".decodeHex()
  val key2Public = "3a746afed460db40ab6976307736539be17a4e4d7bbd8ba4ffb797ba799c98af".decodeHex()
  val key2Private = "6207b6f19c9d7dfa8af31ed5d97891112a877b43b6d8c0f5f1086b170037ba32".decodeHex()
  val key3Public = "6caa9a465cc893cf5b5f8eeafa0e041e907c94ceae6f42b8fa07bed1b1c7b161".decodeHex()
  val key3Private = "e8808e10fe275a9aa8370fab885a1cbb4cc747efbdf77406ebe08155085e6b56".decodeHex()

  // Ecdsa P-256 keys.
  val key4Public = "04d662bf2ed11a3017e2ec9b4955f91cd975a21b47030e687e1c88ac24ebea0a4c9d12ef2ebed97be89a689d5c68e2308e155bd8030a7a961121b5b12184ddedd9".decodeHex()
  val key4Private = "3041020100301306072a8648ce3d020106082a8648ce3d030107042730250201010420bbb00b5fc89bdea4258b701b97e0fe875f374fad8b9c909278e7a548c2eb06d4".decodeHex()
  val key5Public = "04bee3640f35b3f879c27eca70af4b7f0e4d133f948074fc4fad9b38df0f6c5732e02ffff73c15ccfa69c145f3a772367c20c94a5e85251521e5bb806dce3ce9a0".decodeHex()
  val key5Private = "3041020100301306072a8648ce3d020106082a8648ce3d0301070427302502010104207308a8de095066989550c7346752d008a004d8be6d0eec26a51f203fa76c9198".decodeHex()

  // Sample signatures. Note that ECDSA signatures are not deterministic.
  val key4HelloWorldsignatureA = "304502210092dd21ecde1d3ece8c0d503a1b0406bdc2538b1b568ec6fb4e4e1d11960debaf022019d85d6c98ac0e4c76e38f6334c560b75836e28492dcbcbac877aaccd723d3af".decodeHex()
  val key4HelloWorldsignatureB = "304402205455347bb54aeba3875e68f2a7925aaad7b2cea254f16934a7d23fcb3a30d96002200d0df2a28515cee6289abd8555042d6b00a0b46a00103228e6156e6d0828e73b".decodeHex()
}
