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
import app.cash.zipline.internal.decodeFromStringFast
import app.cash.zipline.internal.encodeToStringFast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

@PublishedApi
internal fun <T> flowSerializer(itemSerializer: KSerializer<T>): KSerializer<Flow<T>> {
  return FlowSerializer(itemSerializer)
}

// Zipline can only bridge interfaces, not implementations, so split this in two.
internal interface FlowZiplineService : ZiplineService {
  suspend fun collectJson(collector: FlowZiplineCollector)
}

internal interface FlowZiplineCollector : ZiplineService {
  suspend fun emit(value: String)
}

private val json = Json {
  useArrayPolymorphism = true
}

internal class FlowSerializer<T>(
  private val itemSerializer: KSerializer<T>,
) : KSerializer<Flow<T>> {
  override val descriptor = PrimitiveSerialDescriptor("Flow", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Flow<T>) {
    val service = value.toZiplineService()
    encoder.encodeSerializableValue(
      ContextualSerializer(PassByReference::class),
      SendByReference(service, ziplineServiceAdapter()),
    )
  }

  private fun Flow<T>.toZiplineService(): FlowZiplineService {
    return object : FlowZiplineService {
      override suspend fun collectJson(collector: FlowZiplineCollector) {
        try {
          this@toZiplineService.collect {
            val value = json.encodeToStringFast(itemSerializer, it)
            collector.emit(value)
          }
        } finally {
          collector.close()
        }
      }
    }
  }

  override fun deserialize(decoder: Decoder): Flow<T> {
    val reference = decoder.decodeSerializableValue(
      ContextualSerializer(PassByReference::class),
    ) as ReceiveByReference
    val service = reference.take<FlowZiplineService>(ziplineServiceAdapter())
    return service.toFlow()
  }

  private fun FlowZiplineService.toFlow(): Flow<T> {
    return channelFlow {
      try {
        val collector = object : FlowZiplineCollector {
          override suspend fun emit(value: String) {
            val item = json.decodeFromStringFast(itemSerializer, value)
            this@channelFlow.send(item)
          }
        }
        this@toFlow.collectJson(collector)
        this@channelFlow.close()
      } finally {
        this@toFlow.close()
      }
    }
  }
}
