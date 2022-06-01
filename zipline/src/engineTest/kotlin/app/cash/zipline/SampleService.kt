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

import app.cash.zipline.internal.bridge.OutboundCallHandler
import app.cash.zipline.internal.bridge.ZiplineFunction
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
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
    /** This function's body is what callers use to create a properly-typed adapter. */
    internal inline fun <reified T> manualAdapter(): ManualAdapter {
      return ManualAdapter(
        listOf(
          typeOf<SampleRequest>(),
          typeOf<SampleResponse>(),
          typeOf<Unit>(),
        )
      )
    }

    /**
     * We expect this manually-written adapter to be consistent with the generated one. This exists
     * mostly to model what the expected generated code should look like when making changes to
     * `AdapterGenerator`.
     */
    internal class ManualAdapter(
      private val types: List<KType>
    ) : ZiplineServiceAdapter<SampleService>(), KSerializer<SampleService> {
      override val serialName: String = "SampleService"

      override fun ziplineFunctions(
        serializersModule: SerializersModule,
      ): List<ZiplineFunction<SampleService>> {
        val sampleRequestSerializer = serializersModule.serializer(types[0])
        val sampleResponseSerializer = serializersModule.serializer(types[1])
        val unitSerializer = serializersModule.serializer(types[2])
        return listOf(
          ZiplineFunction0(listOf(sampleRequestSerializer), sampleResponseSerializer),
          ZiplineFunction1(listOf(), unitSerializer),
        )
      }

      class ZiplineFunction0(
        argSerializers: List<KSerializer<*>>,
        resultSerializer: KSerializer<*>,
      ) : ZiplineFunction<SampleService>(
        "fun ping(app.cash.zipline.SampleRequest): app.cash.zipline.SampleResponse",
        argSerializers,
        resultSerializer,
      ) {
        override fun call(service: SampleService, args: List<*>): Any? =
          service.ping(args[0] as SampleRequest)
      }

      class ZiplineFunction1(
        argSerializers: List<KSerializer<*>>,
        resultSerializer: KSerializer<*>,
      ) : ZiplineFunction<SampleService>(
        "fun close(): kotlin.Unit",
        argSerializers,
        resultSerializer,
      ) {
        override fun call(service: SampleService, args: List<*>): Any? = service.close()
      }

      override fun outboundService(
        callHandler: OutboundCallHandler
      ): SampleService = GeneratedOutboundService(callHandler)

      private class GeneratedOutboundService(
        private val callHandler: OutboundCallHandler
      ) : SampleService {
        override fun ping(request: SampleRequest): SampleResponse {
          return callHandler.call(this, 0, request) as SampleResponse
        }

        override fun close() {
          callHandler.closed = true
          return callHandler.call(this, 1) as Unit
        }
      }
    }
  }
}
