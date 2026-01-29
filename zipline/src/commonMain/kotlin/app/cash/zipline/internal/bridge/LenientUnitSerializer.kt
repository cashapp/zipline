/*
 * Copyright (C) 2025 Cash App
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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * An alternative to `Unit.serializer()` that ignores the type of its input value and always encodes
 * either `{}` if the input is non-null, and `null` if the input is null.
 *
 * This works around a bug in the Kotlin compiler where functions that should return `Unit` may
 * instead return other values. See https://github.com/cashapp/zipline/issues/1719
 */
@PublishedApi
internal object LenientUnitSerializer : KSerializer<Any?> {
  private val delegateSerializer = Unit.serializer()

  override val descriptor = SerialDescriptor("LenientUnit", delegateSerializer.descriptor)

  override fun serialize(encoder: Encoder, value: Any?) {
    if (value != null) {
      encoder.encodeSerializableValue(delegateSerializer, Unit)
    } else {
      encoder.encodeNull()
    }
  }

  override fun deserialize(decoder: Decoder): Any? {
    if (decoder.decodeNotNullMark()) {
      decoder.decodeSerializableValue(delegateSerializer)
      return Unit
    } else {
      decoder.decodeNull()
      return null
    }
  }
}
