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
import app.cash.zipline.internal.bridge.ReturningZiplineFunction
import app.cash.zipline.internal.bridge.SuspendCallback
import app.cash.zipline.internal.bridge.SuspendingZiplineFunction
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter
import app.cash.zipline.internal.bridge.requireContextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

@Serializable
data class SampleValVarRequest(
  val message: String
)

@Serializable
data class SampleValVarResponse(
  val message: String
)

/**
 * Note that this interface is unused. It only serves as a sample for what the code generators
 * should consume and produce.
 */
interface SampleValVarService<T> : ZiplineService {
  var count: Int // could be T or List<T>

  // --> this is syntactic sugar for getCount() + setCount(int)

  fun ping(request: SampleValVarRequest): SampleValVarResponse

  suspend fun reduce(request: List<T>): T

  companion object {
    /** This function's body is what callers use to create a properly-typed adapter. */
    internal inline fun <reified T> manualAdapter(): ManualAdapter<T> {
      return ManualAdapter<T>(
        listOf(serializer<T>())
      )
    }

    /**
     * We expect this manually-written adapter to be consistent with the generated one. This exists
     * mostly to model what the expected generated code should look like when making changes to
     * `AdapterGenerator`.
     */
    internal class ManualAdapter<TX>(
      override val serializers: List<KSerializer<*>>,
    ) : ZiplineServiceAdapter<SampleValVarService<TX>>() {
      override val serialName: String = "SampleValVarService"

      class ZiplineFunction0<TF>(
        argSerializers: List<KSerializer<*>>,
        resultSerializer: KSerializer<*>,
      ) : ReturningZiplineFunction<SampleValVarService<TF>>(
        "fun ping(app.cash.zipline.SampleValVarRequest): app.cash.zipline.SampleValVarResponse",
        argSerializers,
        resultSerializer,
      ) {
        override fun call(service: SampleValVarService<TF>, args: List<*>): Any? =
          service.ping(args[0] as SampleValVarRequest)
      }


      class ZiplineFunction3<TF>(
        argSerializers: List<KSerializer<*>>,
        resultSerializer: KSerializer<*>,
      ) : ReturningZiplineFunction<SampleValVarService<TF>>(
        "val count:Int",
        argSerializers,
        resultSerializer,
      ) {
        override fun call(service: SampleValVarService<TF>, args: List<*>): Any? =
          service.count
      }

      class ZiplineFunction4<TF>(
        argSerializers: List<KSerializer<*>>,
        resultSerializer: KSerializer<*>,
      ) : ReturningZiplineFunction<SampleValVarService<TF>>(
        "var count:Int",
        argSerializers,
        resultSerializer,
      ) {
        override fun call(service: SampleValVarService<TF>, args: List<*>): Any? {
          service.count = args[0] as Int
          return Unit
        }
      }

      class ZiplineFunction1<TF>(
        argSerializers: List<KSerializer<*>>,
        resultSerializer: KSerializer<*>,
      ) : SuspendingZiplineFunction<SampleValVarService<TF>>(
        "suspend fun reduce(List<T>): T",
        argSerializers,
        resultSerializer,
      ) {
        override suspend fun callSuspending(service: SampleValVarService<TF>, args: List<*>) =
          service.reduce(args[0] as List<TF>)
      }

      class ZiplineFunction2<TF>(
        argSerializers: List<KSerializer<*>>,
        resultSerializer: KSerializer<*>,
      ) : ReturningZiplineFunction<SampleValVarService<TF>>(
        "fun close(): kotlin.Unit",
        argSerializers,
        resultSerializer,
      ) {
        override fun call(service: SampleValVarService<TF>, args: List<*>): Any? = service.close()
      }

      override fun ziplineFunctions(
        serializersModule: SerializersModule,
      ): List<ZiplineFunction<SampleValVarService<TX>>> {
        val serializers = serializers
        val sampleValVarRequestKSerializer = serializersModule.serializer<SampleValVarRequest>()
        val sampleValVarResponseKSerializer = serializersModule.serializer<SampleValVarResponse>()
        val listOfTSerializer = serializersModule.requireContextual<List<TX>>(
          List::class,
          listOf(serializers[0]),
        )
        val intSerializer = serializersModule.serializer<Int>()
        val unitSerializer = serializersModule.serializer<Unit>()
        val suspendCallbackTSerializer = ziplineServiceSerializer<SuspendCallback<TX>>(
          SuspendCallback::class,
          listOf(serializers[0]),
        )
        return listOf(
          ZiplineFunction0(listOf(sampleValVarRequestKSerializer), sampleValVarResponseKSerializer),
          ZiplineFunction1(listOf(listOfTSerializer), suspendCallbackTSerializer),
          ZiplineFunction2(listOf(), unitSerializer),
          ZiplineFunction3(listOf(), intSerializer),
          ZiplineFunction4(listOf(intSerializer), unitSerializer),
        )
      }

      override fun outboundService(
        callHandler: OutboundCallHandler
      ): SampleValVarService<TX> = GeneratedOutboundService(callHandler)

      private class GeneratedOutboundService<TS>(
        private val callHandler: OutboundCallHandler
      ) : SampleValVarService<TS> {

        override var count: Int
          get() {
            val callHandler = callHandler
            return callHandler.call(this, 3) as Int
          }
          set(value) {
            val callHandler = callHandler
            return callHandler.call(this, 4, value) as Unit
          }

        override fun ping(request: SampleValVarRequest): SampleValVarResponse {
          val callHandler = callHandler
          return callHandler.call(this, 0, request) as SampleValVarResponse
        }

        override suspend fun reduce(request: List<TS>): TS {
          val callHandler = callHandler
          return callHandler.callSuspending(this, 1, request) as TS
        }

        override fun close() {
          val callHandler = callHandler
          callHandler.closed = true
          return callHandler.call(this, 2) as Unit
        }
      }
    }
  }
}
