/*
 * Copyright (C) 2021 Square, Inc.
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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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
