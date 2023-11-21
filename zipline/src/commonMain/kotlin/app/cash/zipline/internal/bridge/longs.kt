/*
 * Copyright (C) 2023 Square, Inc.
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
package app.cash.zipline.internal.bridge

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long

/** Put longs in strings if they can't be represented exactly as doubles. */
internal object LongSerializer : KSerializer<Long> {
  override val descriptor = PrimitiveSerialDescriptor(
    "LongSerializer",
    PrimitiveKind.LONG,
  )

  override fun deserialize(decoder: Decoder): Long {
    return when (val jsonElement = (decoder as JsonDecoder).decodeJsonElement()) {
      is JsonArray, is JsonObject, JsonNull -> throw SerializationException("expected a Long")
      is JsonPrimitive -> jsonElement.long
    }
  }

  override fun serialize(encoder: Encoder, value: Long) {
    if (value in MIN_SAFE_INTEGER..MAX_SAFE_INTEGER) {
      encoder.encodeLong(value)
    } else {
      encoder.encodeString(value.toString())
    }
  }

  // All integers in this range can be expressed as a double. Outside of it they can't.
  private const val MIN_SAFE_INTEGER = -9007199254740991L // -(2^53 – 1)
  private const val MAX_SAFE_INTEGER = 9007199254740991L // 2^53 – 1
}
