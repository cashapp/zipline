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
package app.cash.zipline.internal

import app.cash.zipline.ZiplineManifest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import okio.ByteString
import okio.ByteString.Companion.decodeHex

// TODO: promote to a new okio-serializers module
internal object ByteStringAsHexSerializer : KSerializer<ByteString> {
  override val descriptor = PrimitiveSerialDescriptor("ByteString", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: ByteString) {
    encoder.encodeString(value.hex())
  }

  override fun deserialize(decoder: Decoder): ByteString {
    return decoder.decodeString().decodeHex()
  }
}

internal val jsonForManifest = Json {
  // For backwards-compatibility, allow new fields to be introduced.
  ignoreUnknownKeys = true

  // Because new releases may change default values, it's best to encode them.
  encodeDefaults = true
}

/**
 * Confirm the manifest is of a reasonable size before proceeding to operate on it. 10 KiB is a
 * typical size for our test applications. 640K ought to be enough for anybody.
 */
internal const val MANIFEST_MAX_SIZE = 640 * 1024

internal fun JsonElement.decodeToManifest(): ZiplineManifest = jsonForManifest.decodeFromJsonElement(this)
