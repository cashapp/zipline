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
package app.cash.zipline.testing

import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.bridge.InboundBridge
import app.cash.zipline.internal.bridge.InboundCall
import app.cash.zipline.internal.bridge.InboundCallHandler
import app.cash.zipline.internal.bridge.OutboundBridge
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer

interface EchoZiplineService : ZiplineService {
  fun echo(request: EchoRequest): EchoResponse

  // TODO: generate this:
  companion object {
    object Adapter : ZiplineServiceAdapter<EchoZiplineService>() {
      override val serialName: String = "EchoZiplineService"

      override fun inboundCallHandler(
        service: EchoZiplineService,
        context: InboundBridge.Context
      ): InboundCallHandler = GeneratedInboundCallHandler(service, context)

      override fun outboundService(
        context: OutboundBridge.Context
      ): EchoZiplineService = GeneratedOutboundService(context)

      private class GeneratedInboundCallHandler(
        private val service: EchoZiplineService,
        override val context: InboundBridge.Context,
      ) : InboundCallHandler {
        private val requestSerializer = context.serializersModule.serializer<EchoRequest>()
        private val responseSerializer = context.serializersModule.serializer<EchoResponse>()

        override fun call(inboundCall: InboundCall): Array<String> {
          return when (inboundCall.funName) {
            "echo" -> {
              inboundCall.result(
                responseSerializer,
                service.echo(
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
    }

    private class GeneratedOutboundService(
      private val context: OutboundBridge.Context
    ) : EchoZiplineService {
      private val requestSerializer = context.serializersModule.serializer<EchoRequest>()
      private val responseSerializer = context.serializersModule.serializer<EchoResponse>()

      override fun echo(request: EchoRequest): EchoResponse {
        val call = context.newCall("echo", 1)
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

