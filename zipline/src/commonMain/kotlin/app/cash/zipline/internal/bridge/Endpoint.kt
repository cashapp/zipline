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

import app.cash.zipline.EventListener
import app.cash.zipline.ZiplineService
import kotlin.coroutines.Continuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * An outbound channel for delivering calls to the other platform, and an inbound channel for
 * receiving calls from the other platform.
 */
class Endpoint internal constructor(
  internal val scope: CoroutineScope,
  internal val userSerializersModule: SerializersModule,
  internal val eventListener: EventListener,
  internal val outboundChannel: CallChannel,
) {
  private val inboundServices = mutableMapOf<String, InboundService<*>>()
  private var nextId = 1

  internal val incompleteContinuations = mutableSetOf<Continuation<*>>()

  val serviceNames: Set<String>
    get() = inboundServices.keys.toSet()

  val clientNames: Set<String>
    get() = outboundChannel.serviceNamesArray().toSet()

  /** This uses both Zipline-provided serializers and user-provided serializers. */
  internal val json: Json = Json {
    useArrayPolymorphism = true
    serializersModule = SerializersModule {
      contextual(PassByReference::class, PassByReferenceSerializer(this@Endpoint))
      contextual(Throwable::class, ThrowableSerializer)
      include(userSerializersModule)
    }
  }

  internal val inboundChannel = object : CallChannel {
    override fun serviceNamesArray(): Array<String> {
      return serviceNames.toTypedArray()
    }

    override fun call(encodedArguments: Array<String>): Array<String> {
      val call = InboundCall(encodedArguments)
      val service = takeService(call.serviceName, call.funName)
      call.context = service.context

      return when {
        call.callbackName.isNotEmpty() -> service.callSuspending(call)
        else -> service.call(call)
      }
    }

    /**
     * Note that this removes the handler for calls to is [ZiplineService.close]. We remove before
     * dispatching so it'll always be removed even if the call stalls or throws.
     */
    private fun takeService(instanceName: String, funName: String): InboundService<*> {
      val result = when (funName) {
        "fun close(): kotlin.Unit" -> inboundServices.remove(instanceName)
        else -> inboundServices[instanceName]
      }
      return result ?: error("no handler for $instanceName")
    }

    override fun disconnect(instanceName: String): Boolean {
      return inboundServices.remove(instanceName) != null
    }
  }

  fun <T : ZiplineService> bind(name: String, instance: T) {
    error("unexpected call to Endpoint.bind: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> bind(
    name: String,
    service: T,
    adapter: ZiplineServiceAdapter<T>
  ) {
    eventListener.bindService(name, service)

    val context = newInboundContext(name, service)
    val ziplineFunctions = adapter.ziplineFunctions(context.serializersModule)
    val ziplineFunctionsMap = ziplineFunctions.associateBy { it.name }
    inboundServices[name] = InboundService(service, context, ziplineFunctionsMap)
  }

  fun <T : ZiplineService> take(name: String): T {
    error("unexpected call to Endpoint.take: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> take(name: String, adapter: ZiplineServiceAdapter<T>): T {
    // Detect leaked old services when creating new services.
    detectLeaks()

    val ziplineFunctions = adapter.ziplineFunctions(json.serializersModule)
    val outboundContext = newOutboundContext(name, ziplineFunctions)
    val result = adapter.outboundService(outboundContext)
    eventListener.takeService(name, result)
    trackLeaks(eventListener, name, outboundContext, result)
    return result
  }

  @PublishedApi
  internal fun remove(name: String): InboundService<*>? {
    return inboundServices.remove(name)
  }

  internal fun generateName(prefix: String): String {
    return "$prefix${nextId++}"
  }

  /** Derives the name of a [CancelCallback] from the name of a [SuspendCallback]. */
  internal fun cancelCallbackName(name: String): String {
    return "$name/cancel"
  }

  @PublishedApi
  internal fun newInboundContext(name: String, service: ZiplineService) =
    InboundBridge.Context(name, service, json, this)

  @PublishedApi
  internal fun newOutboundContext(
    name: String,
    ziplineFunctions: List<ZiplineFunction<*>>,
  ) = OutboundBridge.Context(name, json, this, ziplineFunctions)

  internal inner class InboundService<T : ZiplineService>(
    val service: T,
    val context: InboundBridge.Context,
    val handlers: Map<String, ZiplineFunction<T>>,
  ) {
    fun call(call: InboundCall): Array<String> {
      return try {
        val handler = handlers[call.funName]
        if (handler == null) {
          call.unexpectedFunction(handlers.keys.toList())
        } else {
          val decodedArgs = handler.argSerializers.map { call.parameter(it) }
          val response = handler.call(service, decodedArgs)
          call.result(handler.resultSerializer as KSerializer<Any?>, response)
        }
      } catch (e: Throwable) {
        call.resultException(e)
      }
    }

    fun callSuspending(call: InboundCall): Array<String> {
      val suspendCallbackName = call.callbackName
      val job = scope.launch {
        val result = try {
          val handler = handlers[call.funName]
          if (handler == null) {
            call.unexpectedFunction(handlers.keys.toList())
          } else {
            val decodedArgs = handler.argSerializers.map { call.parameter(it) }
            val response = handler.callSuspending(service, decodedArgs)
            call.result(handler.resultSerializer as KSerializer<Any?>, response)
          }
        } catch (e: Exception) {
          call.resultException(e)
        }
        scope.ensureActive() // Don't resume a continuation if the Zipline has since been closed.
        val suspendCallback = take<SuspendCallback>(suspendCallbackName)
        suspendCallback.call(result)
      }

      val cancelCallbackName = cancelCallbackName(suspendCallbackName)
      bind<CancelCallback>(cancelCallbackName, object : CancelCallback {
        override fun cancel() {
          job.cancel()
        }
      })
      job.invokeOnCompletion {
        remove(cancelCallbackName)
      }

      return arrayOf()
    }
  }
}
