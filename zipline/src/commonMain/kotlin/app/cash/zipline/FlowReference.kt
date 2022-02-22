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

import app.cash.zipline.internal.bridge.PassByReference
import app.cash.zipline.internal.bridge.ReceiveByReference
import app.cash.zipline.internal.bridge.SendByReference
import app.cash.zipline.internal.bridge.ziplineServiceAdapter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/** A flow reference is a [Flow] that can be transmitted over a Zipline call. */
class FlowReference<T> @PublishedApi internal constructor(
  internal val flowZiplineService: FlowZiplineService,
  internal val itemSerializer: KSerializer<T>,
) {
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun getDecodingFlow(flowZiplineService: FlowZiplineService): Flow<T> {
    return channelFlow {
      try {
        val collector = object : ZiplineFlowCollector {
          override suspend fun emit(value: String) {
            val item = json.decodeFromString(itemSerializer, value)
            this@channelFlow.send(item)
          }
        }
        flowZiplineService.collectJson(collector)
        this@channelFlow.close()
      } finally {
        flowZiplineService.close()
      }
    }
  }

  fun take(): Flow<T> {
    return when (flowZiplineService) {
      is RealFlowZiplineService<*> -> {
        // If it's a RealReferenceFlow, then the instance didn't pass through Zipline. Don't attempt
        // serialization both because it's unnecessary, and because the serializer isn't connected.
        flowZiplineService.flow as Flow<T>
      }
      else -> getDecodingFlow(flowZiplineService)
    }
  }
}

fun <T> Flow<T>.asFlowReference(): FlowReference<T> {
  val serializer = DeferredSerializer<T>()
  val referenceFlow = RealFlowZiplineService(this, serializer)
  return FlowReference(referenceFlow, serializer)
}

// Zipline can only bridge interfaces, not implementations, so split this in two.
internal interface FlowZiplineService : ZiplineService {
  suspend fun collectJson(collector: ZiplineFlowCollector)
}

private class RealFlowZiplineService<T>(
  val flow: Flow<T>,
  private val serializer: KSerializer<T>,
) : FlowZiplineService {
  override suspend fun collectJson(collector: ZiplineFlowCollector) {
    try {
      flow.collect {
        val value = json.encodeToString(serializer, it)
        collector.emit(value)
      }
    } finally {
      collector.close()
    }
  }
}

internal class FlowReferenceSerializer<T>(
  private val itemSerializer: KSerializer<T>,
) : KSerializer<FlowReference<T>> {
  override val descriptor = PrimitiveSerialDescriptor("FlowReference", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: FlowReference<T>) {
    val itemSerializer = value.itemSerializer
    if (itemSerializer is DeferredSerializer<T>) {
      itemSerializer.delegate = this.itemSerializer
    }
    encoder.encodeSerializableValue(
      ContextualSerializer(PassByReference::class),
      SendByReference(value.flowZiplineService, ziplineServiceAdapter()),
    )
  }

  override fun deserialize(decoder: Decoder): FlowReference<T> {
    val reference = decoder.decodeSerializableValue(
      ContextualSerializer(PassByReference::class),
    ) as ReceiveByReference
    val service = reference.take<FlowZiplineService>(ziplineServiceAdapter())
    return FlowReference(service, itemSerializer)
  }
}

internal interface ZiplineFlowCollector : ZiplineService {
  suspend fun emit(value: String)
}

/** This [KSerializer] can't decode anything until [delegate] is set. */
private class DeferredSerializer<T>: KSerializer<T> {
  var delegate: KSerializer<T>? = null

  override val descriptor: SerialDescriptor
    get() {
      val delegate = this.delegate ?: throw IllegalStateException("no delegate set")
      return delegate.descriptor
    }

  override fun deserialize(decoder: Decoder): T {
    val delegate = this.delegate ?: throw IllegalStateException("no delegate set")
    return delegate.deserialize(decoder)
  }

  override fun serialize(encoder: Encoder, value: T) {
    val delegate = this.delegate ?: throw IllegalStateException("not connected")
    delegate.serialize(encoder, value)
  }
}

private val json = Json {
  useArrayPolymorphism = true
}
