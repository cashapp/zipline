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

import app.cash.zipline.internal.bridge.InboundBridge
import app.cash.zipline.internal.bridge.InboundCallHandler2
import app.cash.zipline.internal.bridge.OutboundBridge
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer

@Serializable
data class SampleRequest(
  val message: String
)

@Serializable
data class SampleResponse(
  val message: String
)

/**
 * Note that this interface is unused. It only serves as a sample for what the code generators
 * should consume and produce.
 */
interface SampleService : ZiplineService {
  fun ping(request: SampleRequest): SampleResponse

  companion object {
    /**
     * We expect this manually-written adapter to be consistent with the generated one. This exists
     * mostly to model what the expected generated code should look like when making changes to
     * `AdapterGenerator`.
     */
    internal object ManualAdapter
      : ZiplineServiceAdapter<SampleService>(), KSerializer<SampleService> {
      override val serialName: String = "SampleService"

      override fun inboundCallHandlers(
        service: SampleService,
        context: InboundBridge.Context
      ): Map<String, InboundCallHandler2> {
        val sampleRequestSerializer = context.serializersModule.serializer<SampleRequest>()
        val sampleResponseSerializer = context.serializersModule.serializer<SampleResponse>()
        val unitSerializer = Unit.serializer()
        val result = mutableMapOf<String, InboundCallHandler2>()
        result["fun close(): kotlin.Unit"] =
          InboundCallHandler0(listOf(), unitSerializer, service)
        result["fun ping(app.cash.zipline.SampleRequest): app.cash.zipline.SampleResponse"] =
          InboundCallHandler1(listOf(sampleRequestSerializer), sampleResponseSerializer, service)
        return result
      }

      class InboundCallHandler0(
        argSerializers: List<KSerializer<*>>,
        resultSerializer: KSerializer<*>,
        val service: SampleService,
      ) : InboundCallHandler2(argSerializers, resultSerializer) {
        override fun call(args: List<*>): Any? = service.close()
      }

      class InboundCallHandler1(
        argSerializers: List<KSerializer<*>>,
        resultSerializer: KSerializer<*>,
        val service: SampleService,
      ) : InboundCallHandler2(argSerializers, resultSerializer) {
        override fun call(args: List<*>): Any? = service.ping(args[0] as SampleRequest)
      }

      override fun outboundService(
        context: OutboundBridge.Context
      ): SampleService = GeneratedOutboundService(context)

      private class GeneratedOutboundService(
        private val context: OutboundBridge.Context
      ) : SampleService {
        private val requestSerializer = context.serializersModule.serializer<SampleRequest>()
        private val responseSerializer = context.serializersModule.serializer<SampleResponse>()

        override fun ping(request: SampleRequest): SampleResponse {
          val call = context.newCall("fun ping(app.cash.zipline.SampleRequest): app.cash.zipline.SampleResponse", 1)
          call.parameter(requestSerializer, request)
          return call.invoke(this, responseSerializer)
        }

        override fun close() {
          this.context.closed = true
          val call = context.newCall("fun close(): kotlin.Unit", 0)
          return call.invoke(this, Unit.serializer())
        }
      }
    }
  }
}
