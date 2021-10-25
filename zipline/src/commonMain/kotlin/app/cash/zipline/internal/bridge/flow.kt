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

import app.cash.zipline.ZiplineReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

@PublishedApi
internal class FlowSerializer<T>(
  private val referenceSerializer: KSerializer<ZiplineReference<*>>,
  private val itemSerializer: KSerializer<T>,
): KSerializer<Flow<T>> {
  override val descriptor get() = referenceSerializer.descriptor

  override fun serialize(encoder: Encoder, value: Flow<T>) {
    val bridge: JsonFlowBridge = SerializingJsonFlowBridge(value, itemSerializer)
    val reference = ZiplineReference(bridge)
    referenceSerializer.serialize(encoder, reference)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun deserialize(decoder: Decoder): Flow<T> {
    @Suppress("UNCHECKED_CAST")
    val bridgeReference =
      referenceSerializer.deserialize(decoder) as ZiplineReference<JsonFlowBridge>

    return channelFlow {
      val bridge = bridgeReference.get()
      try {
        val collector: FlowCollector<String> = object : FlowCollector<String> {
          override suspend fun emit(value: String) {
            val item = Json.decodeFromString(itemSerializer, value)
            send(item)
          }
        }
        val collectorReference = ZiplineReference(collector)
        bridge.collect(collectorReference)
        close()
      } finally {
        bridgeReference.close()
      }
    }
  }
}

private interface JsonFlowBridge {
  suspend fun collect(collectorReference: ZiplineReference<FlowCollector<String>>)
}

private class SerializingJsonFlowBridge<T>(
  private val flow: Flow<T>,
  private val serializer: KSerializer<T>,
) : JsonFlowBridge {
  override suspend fun collect(collectorReference: ZiplineReference<FlowCollector<String>>) {
    try {
      val collector = collectorReference.get()
      flow.collect {
        val value = Json.encodeToString(serializer, it)
        collector.emit(value)
      }
    } finally {
      collectorReference.close()
    }
  }
}
