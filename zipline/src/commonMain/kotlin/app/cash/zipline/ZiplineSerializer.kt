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

import app.cash.zipline.internal.bridge.Endpoint
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

/**
 * This is a special [KSerializer] that delegates to an appropriate user-provided serializer in
 * [Zipline.serializersModule]. It is itself serializable, which is potentially useful when building
 * generic interfaces like [Flow].
 *
 * Note that when encoded to bytes this emits only a placeholder value ‘--’. The decoded value is
 * always the [KSerializer] for the target type 'T' instead of the encoded data.
 */
interface ZiplineSerializer<T>: KSerializer<T>

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T> ZiplineSerializer(): ZiplineSerializer<T> {
  val typeOfT = typeOf<T>()
  return DeferredSerializer(typeOfT)
}

/** This [KSerializer] can't decode anything until it's used with an [Endpoint]. */
@PublishedApi
internal class DeferredSerializer<T>(
  val type: KType
): ZiplineSerializer<T> {
  private var delegate: KSerializer<T>? = null

  internal fun connect(endpoint: Endpoint) {
    delegate = endpoint.serializersModule.serializer(type) as KSerializer<T>
  }

  override val descriptor: SerialDescriptor
    get() {
      val delegate = this.delegate ?: throw IllegalStateException("not connected")
      return delegate.descriptor
    }

  override fun deserialize(decoder: Decoder): T {
    val delegate = this.delegate ?: throw IllegalStateException("not connected")
    return delegate.deserialize(decoder)
  }

  override fun serialize(encoder: Encoder, value: T) {
    val delegate = this.delegate ?: throw IllegalStateException("not connected")
    delegate.serialize(encoder, value)
  }
}

/** This delegates to a [KSerializer] from an [Endpoint]. */
internal class ConnectedSerializer<T>(
  private val delegate: KSerializer<T>
): ZiplineSerializer<T>, KSerializer<T> by delegate

/** Yo dog. This encodes a [DeferredSerializer] and decodes as a [ConnectedSerializer]. */
internal class ZiplineSerializerSerializer<T : Any>(
  private val endpoint: Endpoint,
  private val delegate: KSerializer<T>,
) : KSerializer<ZiplineSerializer<T>> {
  override val descriptor = PrimitiveSerialDescriptor("ZiplineSerializer", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: ZiplineSerializer<T>) {
    if (value is DeferredSerializer<*>) {
      value.connect(endpoint)
      encoder.encodeString("--")
    } else {
      error("serializing an connected serializer is not implemented")
    }
  }

  override fun deserialize(decoder: Decoder): ZiplineSerializer<T> {
    decoder.decodeString() // Discard '--'.
    return ConnectedSerializer(delegate)
  }
}
