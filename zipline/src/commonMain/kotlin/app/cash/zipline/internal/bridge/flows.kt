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
import app.cash.zipline.ziplineServiceSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Zipline can only bridge interfaces, not implementations, so split this in two.
@PublishedApi
internal interface FlowZiplineService<T> : ZiplineService {
  suspend fun collect(collector: FlowZiplineCollector<T>)
}

@PublishedApi
internal interface StateFlowZiplineService<T> : FlowZiplineService<T> {
  val value: T
}

@PublishedApi
internal interface FlowZiplineCollector<T> : ZiplineService {
  suspend fun emit(value: T)
}

/**
 * This serializes [Flow] instances (not directly serializable) by converting them to
 * [FlowZiplineService] instances which can be serialized by [ziplineServiceSerializer] to
 * pass-by-reference.
 */
@PublishedApi
internal class FlowSerializer<T>(
  private val delegateSerializer: KSerializer<FlowZiplineService<T>>,
) : KSerializer<Flow<T>> {
  override val descriptor = delegateSerializer.descriptor

  override fun serialize(encoder: Encoder, value: Flow<T>) {
    val service = value.toZiplineService()
    return encoder.encodeSerializableValue(delegateSerializer, service)
  }

  private fun Flow<T>.toZiplineService(): FlowZiplineService<T> {
    return object : FlowZiplineService<T> {
      override suspend fun collect(collector: FlowZiplineCollector<T>) {
        try {
          this@toZiplineService.collect(collector::emit)
        } finally {
          collector.close()
        }
      }

      override fun toString() = this@toZiplineService.toString()
    }
  }

  override fun deserialize(decoder: Decoder): Flow<T> {
    val service = decoder.decodeSerializableValue(delegateSerializer)
    return service.toFlow()
  }

  private fun FlowZiplineService<T>.toFlow(): Flow<T> {
    return channelFlow {
      this@toFlow.collect(object : FlowZiplineCollector<T> {
        override suspend fun emit(value: T) {
          this@channelFlow.send(value)
        }
      })
    }
  }

  override fun equals(other: Any?) = other is FlowSerializer<*> && other.delegateSerializer == delegateSerializer

  override fun hashCode() = delegateSerializer.hashCode()
}

/**
 * This serializes [StateFlow] instances (not directly serializable) by converting them to
 * [StateFlowZiplineService] instances which can be serialized by [ziplineServiceSerializer] to
 * pass-by-reference.
 */
@PublishedApi
internal class StateFlowSerializer<T>(
  private val delegateSerializer: KSerializer<StateFlowZiplineService<T>>,
) : KSerializer<StateFlow<T>> {
  override val descriptor = delegateSerializer.descriptor

  override fun serialize(encoder: Encoder, value: StateFlow<T>) {
    val service = value.toZiplineService()
    return encoder.encodeSerializableValue(delegateSerializer, service)
  }

  private fun StateFlow<T>.toZiplineService(): StateFlowZiplineService<T> {
    return object : StateFlowZiplineService<T> {
      override suspend fun collect(collector: FlowZiplineCollector<T>) {
        try {
          this@toZiplineService.collect(collector::emit)
        } finally {
          collector.close()
        }
      }

      override val value: T get() = this@toZiplineService.value

      override fun toString() = this@toZiplineService.toString()
    }
  }

  override fun deserialize(decoder: Decoder): StateFlow<T> {
    val service = decoder.decodeSerializableValue(delegateSerializer)
    return service.toStateFlow()
  }

  private fun StateFlowZiplineService<T>.toStateFlow(): StateFlow<T> {
    return object : StateFlow<T> {
      override suspend fun collect(collector: FlowCollector<T>): Nothing {
        channelFlow {
          this@toStateFlow.collect(object : FlowZiplineCollector<T> {
            override suspend fun emit(value: T) {
              this@channelFlow.send(value)
            }
          })
        }.collect(collector)
        throw AssertionError() // StateFlows never complete!
      }

      override val value: T get() = this@toStateFlow.value

      override val replayCache: List<T> get() = listOf(value)
    }
  }

  override fun equals(other: Any?) = other is StateFlowSerializer<*> && other.delegateSerializer == delegateSerializer

  override fun hashCode() = delegateSerializer.hashCode()
}
