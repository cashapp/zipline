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
internal abstract class ZiplineServiceAdapter<T : ZiplineService> {
  abstract val serialName: String

  abstract fun inboundCallHandler(
    service: T,
    context: InboundBridge.Context
  ): InboundCallHandler

  abstract fun outboundService(
    context: OutboundBridge.Context
  ): T

  fun serializer(endpoint: Endpoint) : KSerializer<T> {
    return object : KSerializer<T> {
      override val descriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

      override fun serialize(encoder: Encoder, value: T) {
        val name = endpoint.generateName()
        endpoint.set(name, value, this@ZiplineServiceAdapter)
        encoder.encodeString(name)
      }

      override fun deserialize(decoder: Decoder): T {
        val name = decoder.decodeString()
        return outboundService(endpoint.newOutboundContext(name))
      }
    }
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
