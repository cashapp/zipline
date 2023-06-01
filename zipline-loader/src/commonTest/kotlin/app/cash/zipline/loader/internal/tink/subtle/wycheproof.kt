// Copyright 2019 Google Inc.
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

package app.cash.zipline.loader.internal.tink.subtle

import app.cash.zipline.testing.systemFileSystem
import app.cash.zipline.testing.ziplineRoot
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okio.Path

@Serializable
class WycheproofTestJson(
  val algorithm: String,
  val generatorVersion: String,
  val numberOfTests: Int,
  val header: List<String>,
  val notes: Map<String, String>,
  val schema: String,
  val testGroups: List<TestGroup>,
)

@Serializable
class TestGroup(
  val jwk: Jwk? = null,
  val key: Key,
  val keyDer: String,
  val keyPem: String,
  val type: String,
  val tests: List<TestCase>,
)

@Serializable
class TestCase(
  val tcId: Int,
  val comment: String,
  val msg: String,
  val sig: String,
  val result: String,
)

@Serializable
class Jwk(
  val crv: String,
  val d: String,
  val kid: String,
  val kty: String,
  val x: String,
)

@Serializable
class Key(
  val curve: String,
  val keySize: Int,
  val type: String,
  val pk: String? = null,
  val sk: String? = null,
  val uncompressed: String? = null,
  val wx: String? = null,
  val wy: String? = null,
)

private val wycheproofDir = ziplineRoot / "zipline-loader/src/commonTest/resources/wycheproof/"

fun loadEddsaTestJson(): WycheproofTestJson {
  return loadWycheproofTestJson(wycheproofDir / "eddsa_test.json")
}

fun loadEcdsaP256TestJson(): WycheproofTestJson {
  return loadWycheproofTestJson(wycheproofDir / "ecdsa_secp256r1_sha256_test.json")
}

fun loadWycheproofTestJson(path: Path): WycheproofTestJson {
  val wycheproofTestJson = systemFileSystem.read(path) {
    readUtf8()
  }

  val json = Json {
    ignoreUnknownKeys = true
  }

  return json.decodeFromString(wycheproofTestJson)
}
