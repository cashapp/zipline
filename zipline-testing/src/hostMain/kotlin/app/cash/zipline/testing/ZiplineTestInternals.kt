/*
 * Copyright (C) 2023 Cash App
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
@file:Suppress(
  // Access :zipline internals
  "CANNOT_OVERRIDE_INVISIBLE_MEMBER",
  "EXPOSED_FUNCTION_RETURN_TYPE",
  "EXPOSED_PARAMETER_TYPE",
  "EXPOSED_SUPER_CLASS",
  "INVISIBLE_MEMBER",
  "INVISIBLE_REFERENCE",
)

package app.cash.zipline.testing

import app.cash.zipline.ZiplineFunction
import app.cash.zipline.ZiplineScope
import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.bridge.Endpoint
import app.cash.zipline.internal.bridge.OutboundCallHandler
import app.cash.zipline.internal.bridge.OutboundService
import app.cash.zipline.internal.bridge.ReturningZiplineFunction
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.typeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * Suppress visibility warnings to directly call internal functions annotated [PublishedApi].
 *
 * Note that when overriding functions from invisible members, this class uses the base generic type
 * because that interferes the least with our suppressed warnings.
 */
object ZiplineTestInternals {
  private val echoResponseKt = typeOf<EchoResponse>()
  private val echoRequestKt = typeOf<EchoRequest>()
  private val stringKt = typeOf<String>()
  private val listOfStringKt = typeOf<List<String>>()

  fun newEndpointPair(): Pair<Endpoint, Endpoint> {
    val scope = CoroutineScope(EmptyCoroutineContext)
    return newEndpointPair(scope, kotlinBuiltInSerializersModule)
  }

  /** Simulate generated code for outbound calls.  */
  fun takeEchoClient(endpoint: Endpoint, name: String): EchoService {
    return endpoint.take(name, ZiplineScope(), EchoServiceAdapter())
  }

  /** Simulate generated code for inbound calls.  */
  fun bindEchoService(endpoint: Endpoint, name: String, echoService: EchoService) {
    endpoint.bind(name, echoService, EchoServiceAdapter())
  }

  /** Simulate generated code for outbound calls.  */
  fun takeGenericEchoService(endpoint: Endpoint, name: String): GenericEchoService<String> {
    return endpoint.take(name, ZiplineScope(), GenericEchoServiceAdapter())
  }

  /** Simulate generated code for inbound calls.  */
  fun bindGenericEchoService(
    endpoint: Endpoint,
    name: String,
    echoService: GenericEchoService<String>,
  ) {
    endpoint.bind(name, echoService, GenericEchoServiceAdapter())
  }

  /** Simulate generated code for inbound calls.  */
  fun bindEchoZiplineService(
    endpoint: Endpoint,
    name: String,
    service: EchoZiplineService,
  ) {
    endpoint.bind(name, service, EchoZiplineServiceAdapter())
  }

  /** Simulate generated code for outbound calls.  */
  fun takeEchoZiplineService(endpoint: Endpoint, name: String): EchoZiplineService {
    return endpoint.take(name, ZiplineScope(), EchoZiplineServiceAdapter())
  }

  /** Simulate a generated subclass of ZiplineServiceAdapter.  */
  class EchoServiceAdapter : ZiplineServiceAdapter<EchoService>() {
    val simpleName: String
      get() = "EchoService"
    val serialName: String
      get() = "app.cash.zipline.kotlin.EchoService"
    val serializers: List<KSerializer<*>>
      get() = emptyList()

    override fun ziplineFunctions(
      serializersModule: SerializersModule,
    ): List<ZiplineFunction<ZiplineService>> {
      val requestSerializer = serializersModule.serializer(echoRequestKt) as KSerializer<*>
      val responseSerializer = serializersModule.serializer(echoResponseKt) as KSerializer<*>
      val name =
        "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
      return listOf<ZiplineFunction<ZiplineService>>(
        object : ReturningZiplineFunction<ZiplineService>(
          name.signatureHash(),
          name,
          listOf(requestSerializer),
          responseSerializer,
        ) {
          override fun call(service: ZiplineService, args: List<*>): Any {
            return (service as EchoService).echo(args[0] as EchoRequest)
          }
        },
      )
    }

    override fun outboundService(callHandler: OutboundCallHandler): ZiplineService {
      return GeneratedOutboundService(callHandler)
    }

    private class GeneratedOutboundService(
      val callHandler: OutboundCallHandler,
    ) : EchoService, OutboundService {

      override fun echo(request: EchoRequest): EchoResponse {
        return callHandler.call(this, 0, request) as EchoResponse
      }
    }
  }

  /** Simulate a generated subclass of ZiplineServiceAdapter.  */
  class GenericEchoServiceAdapter : ZiplineServiceAdapter<GenericEchoService<String>>() {
    val simpleName: String
      get() = "GenericEchoService"
    val serialName: String
      get() = "app.cash.zipline.kotlin.GenericEchoService<kotlin.String>"
    val serializers: List<KSerializer<*>>
      get() = emptyList()

    override fun ziplineFunctions(
      serializersModule: SerializersModule,
    ): List<ZiplineFunction<ZiplineService>> {
      val requestSerializer = serializersModule.serializer(stringKt) as KSerializer<*>
      val responseSerializer = serializersModule.serializer(listOfStringKt) as KSerializer<*>
      val name = "fun genericEcho(T): kotlin.collections.List<T>"
      return listOf<ZiplineFunction<ZiplineService>>(
        object : ReturningZiplineFunction<ZiplineService>(
          name.signatureHash(),
          name,
          listOf(requestSerializer),
          responseSerializer,
        ) {
          override fun call(service: ZiplineService, args: List<*>): Any {
            return (service as GenericEchoService<String>).genericEcho(args[0] as String)
          }
        },
      )
    }

    override fun outboundService(callHandler: OutboundCallHandler): ZiplineService {
      return GeneratedOutboundService(callHandler)
    }

    private class GeneratedOutboundService(val callHandler: OutboundCallHandler) :
      GenericEchoService<String>, OutboundService {

      override fun genericEcho(request: String): List<String> {
        return callHandler.call(this, 0, request) as List<String>
      }
    }
  }

  /** Simulate a generated subclass of ZiplineServiceAdapter.  */
  class EchoZiplineServiceAdapter : ZiplineServiceAdapter<EchoZiplineService>() {
    val simpleName: String
      get() = "EchoService"
    val serialName: String
      get() = "app.cash.zipline.kotlin.EchoZiplineService"
    val serializers: List<KSerializer<*>>
      get() = emptyList()

    override fun ziplineFunctions(
      serializersModule: SerializersModule,
    ): List<ZiplineFunction<ZiplineService>> {
      val requestSerializer = serializersModule.serializer(echoRequestKt) as KSerializer<*>
      val responseSerializer = serializersModule.serializer(echoResponseKt) as KSerializer<*>
      val name =
        "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
      return listOf<ZiplineFunction<ZiplineService>>(
        object : ReturningZiplineFunction<ZiplineService>(
          name.signatureHash(),
          name,
          listOf(requestSerializer),
          responseSerializer,
        ) {
          override fun call(service: ZiplineService, args: List<*>): Any {
            return (service as EchoZiplineService).echo(args[0] as EchoRequest)
          }
        },
      )
    }

    override fun outboundService(callHandler: OutboundCallHandler): ZiplineService {
      return GeneratedOutboundService(callHandler)
    }

    private class GeneratedOutboundService(val callHandler: OutboundCallHandler) :
      EchoZiplineService, OutboundService {

      override fun echo(request: EchoRequest): EchoResponse {
        return callHandler.call(this, 0, request) as EchoResponse
      }
    }
  }
}
