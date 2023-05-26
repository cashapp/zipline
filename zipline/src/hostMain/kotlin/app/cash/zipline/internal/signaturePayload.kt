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
package app.cash.zipline.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Signature Payload
 * -----------------
 *
 * We’d like to embed the signature in the manifest itself. But we can’t sign the manifest if it
 * contains the signature; that’s circular. Instead, we create a file with equivalent content and
 * sign that.
 *
 * To create a signature payload:
 *
 *  1. Create a JSON document that is a copy of the manifest. This operates on the JSON model and
 *     not the decoded model to ensure that unknown fields are covered by signatures.
 *
 *  2. Remove the `unsigned` property.
 *
 *  3. Encode this new document as JSON with no unnecessary whitespace.
 *
 * The UTF-8 bytes of the JSON are the input to the signing function.
 *
 * Example
 * -------
 *
 * ```
 * {
 *   "unsigned": {
 *     "signatures": {
 *       "kochiku20220705": "304502204b9ea1b30b065afcc38ce238a58a63c7ea341a37",
 *     },
 *     "baseUrl": "https://example.com/cdn/59044aae339f8356/"
 *   }
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
 *   "moduleId": "./sample-app.js",
 *   "mainFunction": "com.squareup.cash.prepareSample"
 * }
 * ```
 *
 * Steps 1–2 produce this intermediate JSON:
 *
 * ```
 * {
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
 *   "moduleId": "./sample-app.js",
 *   "mainFunction": "com.squareup.cash.prepareSample"
 * }
 * ```
 *
 * Before signing, we must perform step 3 (strip whitespace) and encode as UTF-8.
 *
 * Note that if the manifest contains fields unknown to the signer, these fields must be copied to
 * the signature payload.
 */
internal fun signaturePayload(manifest: JsonElement): JsonElement {
  val newContent = manifest.jsonObject.toMutableMap()
  newContent.remove("unsigned")
  return JsonObject(newContent)
}

internal fun signaturePayload(manifestJson: String): String {
  val jsonElement = Json.parseToJsonElement(manifestJson)
  val signaturePayload = signaturePayload(jsonElement)
  return Json.encodeToString(JsonElement.serializer(), signaturePayload)
}
