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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/** A flow reference is a [Flow] that can be transmitted over a Zipline call. */
@Serializable
class FlowReference<T> @PublishedApi internal constructor(
  @Contextual private val referenceFlowReference: ZiplineReference<ReferenceFlow>
) {
  private fun getJsonFlow(): Flow<String> {
    return channelFlow {
      val referenceFlow = referenceFlowReference.get()
      try {
        val collector: FlowCollector<String> = object : FlowCollector<String> {
          override suspend fun emit(value: String) {
            this@channelFlow.send(value)
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

  fun get(serializer: KSerializer<T>): Flow<T> {
    return getJsonFlow()
      .map { Json.decodeFromString(serializer, it) }
  }
}

fun <T> Flow<T>.asFlowReference(serializer: KSerializer<T>): FlowReference<T> {
  val flowOfStrings = map { Json.encodeToString(serializer, it) }
  val referenceFlow = RealReferenceFlow(flowOfStrings, EmptySerializersModule)
  val ziplineReference = ZiplineReference<ReferenceFlow>(referenceFlow)
  return FlowReference(ziplineReference)
}

// Zipline can only bridge interfaces, not implementations, so split this in two.
internal interface ReferenceFlow {
  suspend fun collectJson(collectorReference: ZiplineReference<FlowCollector<String>>)
}

@PublishedApi
internal class RealReferenceFlow(
  private val flow: Flow<String>,
  private val serializersModule: SerializersModule,
) : ReferenceFlow {
  override suspend fun collectJson(collectorReference: ZiplineReference<FlowCollector<String>>) {
    try {
      val collector = collectorReference.get()
      collector.emitAll(flow)
    } finally {
      collectorReference.close()
    }
  }
}
