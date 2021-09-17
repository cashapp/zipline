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
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import okio.use

/** A flow reference is a [Flow] that can be transmitted over a Zipline call. */
@Serializable
class FlowReference<T> @PublishedApi internal constructor(
  @Contextual private val referenceFlowReference: ZiplineReference<ReferenceFlow>
) {
  private fun getJsonFlow(): Flow<String> {
    return channelFlow {
      val referenceFlow = referenceFlowReference.get(EmptySerializersModule)
      referenceFlowReference.use {
        val collector: FlowCollector<String> = object : FlowCollector<String> {
          override suspend fun emit(value: String) {
            println("COLLECTING EMITTED $value")
            this@channelFlow.send(value)
          }
        }
        val collectorReference = ZiplineReference(EmptySerializersModule, collector)
        println("COLLECTING ALL THE JSONS")
        referenceFlow.collectJson(collectorReference)
        println("DONE COLLECTING ALL THE JSONS (should not happen)")
        this@channelFlow.close()
      }
    }
  }

  fun get(serializer: KSerializer<T>): Flow<T> {
    return getJsonFlow().transform {
      emit(Json.decodeFromString(serializer, it))
    }
  }
}

fun <T> Flow<T>.toFlowReference(serializer: KSerializer<T>): FlowReference<T> {
  val flowOfStrings = transform {
    val json = Json.encodeToString(serializer, it)
    println("EMITTING ENCODED $json")
    emit(json)
  }
  val referenceFlow = RealReferenceFlow(flowOfStrings, EmptySerializersModule)
  val ziplineReference = ZiplineReference<ReferenceFlow>(EmptySerializersModule, referenceFlow)
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
    collectorReference.use {
      val collector = collectorReference.get(serializersModule)
      collector.emitAll(flow)
    }
  }
}
