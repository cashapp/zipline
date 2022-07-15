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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * Signature Payload
 * -----------------
 *
 * We’d like to embed the signature in the manifest itself. But we can’t sign the manifest if it
 * contains the signature; that’s circular. Instead we create a file with equivalent content and
 * sign that.
 *
 * To create a signature payload:
 *
 *  1. Create a JSON document that is a copy of the manifest. This operates on the JSON model and
 *     not the decoded model to ensure that unknown fields are covered by signatures.
 *
 *  2. For each element in `modules`:
 *
 *      * Change the `url` property to `""` (empty string). This is necessary because the `url`
 *        attribute is a relative URL, and the may be rewritten by both serving tools and the
 *        client-side downloader. This is safe because the ultimate URL of the module is
 *        insignificant; we rely on checksum verification to confirm we got the right resource.
 *
 *  3. For each element in `signatures`:
 *
 *     *  Change the string value to `""` (empty string). This is necessary because we don’t know
 *        the signature value yet when we are signing the file.
 *
 *  4. Encode this new document as JSON with no unnecessary whitespace.
 *
 * The UTF-8 bytes of the JSON are the input to the signing function.
 *
 * Note that this mechanism replaces values with `""` rather than omitting them completely. This way
 * the signature payload covers which fields were present, and the order of the field declarations.
 *
 * Example
 * -------
 *
 * ```
 * {
 *   "moduleId": "./sample-app.js",
 *   "prepareFunction": "com.squareup.cash.prepareSample",
 *   "modules": {
 *     "./kotlin_kotlin.js": {
 *       "url": "kotlin_kotlin.zipline",
 *       "sha256": "44e6b8c5ad603fca0f38e2d45fbd610ced3315fa96756fb4df361a2a7fa6edb7"
 *     },
 *     "./sample-app.js": {
 *       "url": "cash-sample-app.zipline",
 *       "sha256": "e460f2bd8787f06f2db09aa9dd4e75de3d12961e1df1f77da16ff323007abad1",
 *       "dependsOnIds": [
 *         "./kotlin_kotlin.js"
 *       ]
 *     }
 *   },
 *   "signatures": {
 *     "kochiku20220705": "304502204b9ea1b30b065afcc38ce238a58a63c7ea341a37",
 *   }
 * }
 * ```
 *
 * Steps 1–3 produce this intermediate JSON:
 *
 * ```
 * {
 *   "moduleId": "./sample-app.js",
 *   "prepareFunction": "com.squareup.cash.prepareSample",
 *   "modules": {
 *     "./kotlin_kotlin.js": {
 *       "url": "",
 *       "sha256": "44e6b8c5ad603fca0f38e2d45fbd610ced3315fa96756fb4df361a2a7fa6edb7"
 *     },
 *     "./sample-app.js": {
 *       "url": "",
 *       "sha256": "e460f2bd8787f06f2db09aa9dd4e75de3d12961e1df1f77da16ff323007abad1",
 *       "dependsOnIds": [
 *         "./kotlin_kotlin.js"
 *       ]
 *     }
 *   },
 *   "signatures": {
 *     "kochiku20220705": "",
 *   }
 * }
 * ```
 *
 * Before signing we must perform step 4 (strip whitespace) and encode as UTF-8.
 *
 * Note that if the manifest contains fields unknown to the signer, these fields must be copied to
 * the signature payload.
 */
internal fun signaturePayload(manifest: JsonElement): JsonElement {
  val newContent = manifest.jsonObject.toMutableMap()

  val modules = newContent["modules"]
  if (modules != null) {
    val newModules = mutableMapOf<String, JsonElement>()
    for ((key, module) in modules.jsonObject) {
      val newModule = module.jsonObject.toMutableMap()
      if (newModule.containsKey("url")) {
        newModule["url"] = JsonPrimitive("")
      }
      newModules[key] = JsonObject(newModule)
    }
    newContent["modules"] = JsonObject(newModules)
  }

  val signatures = newContent["signatures"]
  if (signatures != null) {
    val newSignatures = mutableMapOf<String, JsonElement>()
    for (key in signatures.jsonObject.keys) {
      newSignatures[key] = JsonPrimitive("")
    }
    newContent["signatures"] = JsonObject(newSignatures)
  }

  return JsonObject(newContent)
}

/**
 * For better performance, we sign the SHA-256 hash of the signature payload, rather than the
 * signature payload itself.
 */
internal fun signaturePayloadSha256(manifestJson: String): ByteString {
  val signaturePayload = signaturePayload(manifestJson)
  return signaturePayload.encodeUtf8().sha256()
}

internal fun signaturePayload(manifestJson: String): String {
  val jsonElement = Json.parseToJsonElement(manifestJson)
  val signaturePayload = signaturePayload(jsonElement)
  return Json.encodeToString(JsonElement.serializer(), signaturePayload)
}
