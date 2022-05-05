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
  internal val inboundHandlers = mutableMapOf<String, InboundCallHandler>()
  private var nextId = 1

  internal val incompleteContinuations = mutableSetOf<Continuation<*>>()

  val serviceNames: Set<String>
    get() = inboundHandlers.keys.toSet()

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

    override fun invoke(encodedArguments: Array<String>): Array<String> {
      val inboundCall = InboundCall(encodedArguments)
      val handler = takeHandler(inboundCall.serviceName, inboundCall.funName)
      inboundCall.context = handler.context

      return when {
        inboundCall.callbackName.isNotEmpty() -> invokeSuspending(inboundCall, handler)
        else -> invoke(inboundCall, handler)
      }
    }

    private fun invoke(call: InboundCall, handler: InboundCallHandler): Array<String> {
      return try {
        handler.call(call)
      } catch (e: Throwable) {
        call.resultException(e)
      }
    }

    private fun invokeSuspending(call: InboundCall, handler: InboundCallHandler): Array<String> {
      val suspendCallbackName = call.callbackName
      val job = scope.launch {
        val result = try {
          handler.callSuspending(call)
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

    /**
     * Note that this removes the handler for calls to is [ZiplineService.close]. We remove before
     * dispatching so it'll always be removed even if the call stalls or throws.
     */
    private fun takeHandler(instanceName: String, funName: String): InboundCallHandler {
      val result = when (funName) {
        "fun close(): kotlin.Unit" -> inboundHandlers.remove(instanceName)
        else -> inboundHandlers[instanceName]
      }
      return result ?: error("no handler for $instanceName")
    }

    override fun disconnect(instanceName: String): Boolean {
      return inboundHandlers.remove(instanceName) != null
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
    inboundHandlers[name] = adapter.inboundCallHandler(service, newInboundContext(name, service))
  }

  fun <T : ZiplineService> take(name: String): T {
    error("unexpected call to Endpoint.take: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> take(name: String, adapter: ZiplineServiceAdapter<T>): T {
    // Detect leaked old services when creating new services.
    detectLeaks()

    val outboundContext: OutboundBridge.Context = newOutboundContext(name)
    val result = adapter.outboundService(outboundContext)
    eventListener.takeService(name, result)
    trackLeaks(eventListener, name, outboundContext, result)
    return result
  }

  @PublishedApi
  internal fun remove(name: String): InboundCallHandler? {
    return inboundHandlers.remove(name)
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
  internal fun newOutboundContext(name: String) = OutboundBridge.Context(name, json, this)
}
