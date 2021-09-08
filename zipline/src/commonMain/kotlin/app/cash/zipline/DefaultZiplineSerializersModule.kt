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
package app.cash.zipline

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

/**
 * All Zipline [SerializersModule] should include this to get serialization coverage of core types
 * like [Throwable].
 *
 * ```kotlin
 * val EchoSerializersModule: SerializersModule = SerializersModule {
 *   include(DefaultZiplineSerializersModule)
 * }
 * ```
 */
val DefaultZiplineSerializersModule: SerializersModule = SerializersModule {
  contextual(Throwable::class, ThrowableSerializer)
}

internal object ThrowableSerializer : KSerializer<Throwable> {
  override val descriptor: SerialDescriptor = ThrowableSurrogate.serializer().descriptor

  override fun serialize(encoder: Encoder, value: Throwable) {
    val surrogate = ThrowableSurrogate(value.toString())
    encoder.encodeSerializableValue(ThrowableSurrogate.serializer(), surrogate)
  }

  override fun deserialize(decoder: Decoder): Throwable {
    val surrogate = decoder.decodeSerializableValue(ThrowableSurrogate.serializer())
    return Exception(surrogate.message)
  }
}

@Serializable
internal class ThrowableSurrogate(
  val message: String
)
