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
import app.cash.zipline.internal.bridge.InboundCall
import app.cash.zipline.internal.bridge.InboundCallHandler
import app.cash.zipline.internal.bridge.OutboundBridge
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter
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
    internal object ManualAdapter : ZiplineServiceAdapter<SampleService>() {
      override val serialName: String = "SampleService"

      override fun inboundCallHandler(
        service: SampleService,
        context: InboundBridge.Context
      ): InboundCallHandler = GeneratedInboundCallHandler(service, context)

      override fun outboundService(
        context: OutboundBridge.Context
      ): SampleService = GeneratedOutboundService(context)

      private class GeneratedInboundCallHandler(
        private val service: SampleService,
        override val context: InboundBridge.Context,
      ) : InboundCallHandler {
        private val requestSerializer = context.serializersModule.serializer<SampleRequest>()
        private val responseSerializer = context.serializersModule.serializer<SampleResponse>()

        override fun call(inboundCall: InboundCall): Array<String> {
          return when (inboundCall.funName) {
            "ping" -> {
              inboundCall.result(
                responseSerializer,
                service.ping(
                  inboundCall.parameter(requestSerializer)
                )
              )
            }
            "close" -> {
              inboundCall.result(
                Unit.serializer(),
                service.close()
              )
            }
            else -> {
              inboundCall.unexpectedFunction()
            }
          }
        }

        override suspend fun callSuspending(inboundCall: InboundCall): Array<String> {
          return when (inboundCall.funName) {
            else -> {
              inboundCall.unexpectedFunction()
            }
          }
        }
      }

      private class GeneratedOutboundService(
        private val context: OutboundBridge.Context
      ) : SampleService {
        private val requestSerializer = context.serializersModule.serializer<SampleRequest>()
        private val responseSerializer = context.serializersModule.serializer<SampleResponse>()

        override fun ping(request: SampleRequest): SampleResponse {
          val call = context.newCall("ping", 1)
          call.parameter(requestSerializer, request)
          return call.invoke(responseSerializer)
        }

        override fun close() {
          val call = context.newCall("close", 0)
          return call.invoke(Unit.serializer())
        }
      }
    }
  }
}
