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
package app.cash.zipline.internal.bridge

import app.cash.zipline.ZiplineService
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Adapts [ZiplineService] implementations to receive incoming and send outgoing calls. Most
 * implementations are generated.
 */
@PublishedApi
internal abstract class ZiplineServiceAdapter<T : ZiplineService> : KSerializer<T> {
  abstract val serialName: String

  override val descriptor
    get() = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

  abstract fun inboundCallHandler(
    service: T,
    context: InboundBridge.Context
  ): InboundCallHandler

  abstract fun outboundService(
    context: OutboundBridge.Context
  ): T

  override fun serialize(encoder: Encoder, value: T) {
    encoder.encodeSerializableValue(
      ContextualSerializer(PassByReference::class),
      SendByReference(value, this),
    )
  }

  override fun deserialize(decoder: Decoder): T {
    val reference = decoder.decodeSerializableValue(
      ContextualSerializer(PassByReference::class),
    ) as ReceiveByReference
    return reference.take(this)
  }
}

@PublishedApi
internal fun <T : ZiplineService> ziplineServiceAdapter() : ZiplineServiceAdapter<T> {
  error("unexpected call to ziplineServiceAdapter(): is the Zipline plugin configured?")
}

@PublishedApi
internal fun <T : ZiplineService> ziplineServiceAdapter(
  ziplineServiceAdapter: ZiplineServiceAdapter<T>
) : ZiplineServiceAdapter<T> {
  return ziplineServiceAdapter
}
