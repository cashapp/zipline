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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/** A flow reference is a [Flow] that can be transmitted over a Zipline call. */
class FlowReference<T> @PublishedApi internal constructor(
  internal val referenceFlowReference: ZiplineReference<ReferenceFlow>,
  internal val itemSerializer: KSerializer<T>,
) {
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun getDecodingFlow(referenceFlow: ReferenceFlow): Flow<T> {
    return channelFlow {
      try {
        val collector: ZiplineFlowCollector = object : ZiplineFlowCollector {
          override suspend fun emit(value: String) {
            val item = Json.decodeFromString(itemSerializer, value)
            this@channelFlow.send(item)
          }
        }
        val collectorReference = ZiplineReference(collector)
        referenceFlow.collectJson(collectorReference)
        this@channelFlow.close()
      } finally {
        referenceFlowReference.close()
      }
    }
  }

  fun get(): Flow<T> {
    return when (val referenceFlow = referenceFlowReference.get()) {
      is RealReferenceFlow<*> -> {
        // If it's a RealReferenceFlow, then the instance didn't pass through Zipline. Don't attempt
        // serialization both because it's unnecessary, and because the serializer isn't connected.
        referenceFlow.flow as Flow<T>
      }
      else -> getDecodingFlow(referenceFlow)
    }
  }
}

fun <T> Flow<T>.asFlowReference(): FlowReference<T> {
  val serializer = DeferredSerializer<T>()
  val referenceFlow = RealReferenceFlow(this, serializer)
  val ziplineReference = ZiplineReference<ReferenceFlow>(referenceFlow)
  return FlowReference(ziplineReference, serializer)
}

// Zipline can only bridge interfaces, not implementations, so split this in two.
internal interface ReferenceFlow : ZiplineService {
  suspend fun collectJson(collectorReference: ZiplineReference<ZiplineFlowCollector>)
}

private class RealReferenceFlow<T>(
  val flow: Flow<T>,
  private val serializer: KSerializer<T>,
) : ReferenceFlow {
  override suspend fun collectJson(
    collectorReference: ZiplineReference<ZiplineFlowCollector>
  ) {
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

internal class FlowReferenceSerializer<T>(
  private val ziplineReferenceSerializer: KSerializer<ZiplineReference<ReferenceFlow>>,
  private val itemSerializer: KSerializer<T>,
) : KSerializer<FlowReference<T>> {
  override val descriptor get() = ziplineReferenceSerializer.descriptor

  override fun deserialize(decoder: Decoder): FlowReference<T> {
    @Suppress("UNCHECKED_CAST")
    val ziplineReference = ziplineReferenceSerializer.deserialize(decoder)
    return FlowReference(ziplineReference, itemSerializer)
  }

  override fun serialize(encoder: Encoder, value: FlowReference<T>) {
    val itemSerializer = value.itemSerializer
    if (itemSerializer is DeferredSerializer<T>) {
      itemSerializer.delegate = this.itemSerializer
    }
    ziplineReferenceSerializer.serialize(encoder, value.referenceFlowReference)
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
