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
import app.cash.zipline.ziplineServiceSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.KSerializer
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

/**
 * This serializes [Flow] instances (not directly serializable) by converting them to
 * [FlowZiplineService] instances which can be serialized by [ziplineServiceSerializer] to
 * pass-by-reference.
 */
internal class FlowSerializer<T>(
  private val itemSerializer: KSerializer<T>,
) : KSerializer<Flow<T>> {
  private val delegateSerializer = ziplineServiceSerializer<FlowZiplineService>()
  override val descriptor = delegateSerializer.descriptor

  override fun serialize(encoder: Encoder, value: Flow<T>) {
    val service = value.toZiplineService()
    return encoder.encodeSerializableValue(delegateSerializer, service)
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
    val service = decoder.decodeSerializableValue(delegateSerializer)
    return service.toFlow()
  }

  private fun FlowZiplineService.toFlow(): Flow<T> {
    return channelFlow {
      invokeOnClose {
        this@toFlow.close()
      }
      this@toFlow.collectJson(object : FlowZiplineCollector {
        override suspend fun emit(value: String) {
          val item = json.decodeFromStringFast(itemSerializer, value)
          this@channelFlow.send(item)
        }
      })
    }
  }
}
