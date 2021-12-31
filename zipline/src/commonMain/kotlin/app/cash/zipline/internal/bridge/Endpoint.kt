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

import app.cash.zipline.FlowReference
import app.cash.zipline.FlowReferenceSerializer
import app.cash.zipline.ZiplineReference
import app.cash.zipline.ZiplineService
import kotlin.coroutines.Continuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * An outbound channel for delivering calls to the other platform, and an inbound channel for
 * receiving calls from the other platform.
 */
class Endpoint internal constructor(
  internal val scope: CoroutineScope,
  internal val outboundChannel: CallChannel,
) {
  internal val inboundHandlers = mutableMapOf<String, InboundCallHandler>()
  private var nextId = 1

  internal val incompleteContinuations = mutableSetOf<Continuation<*>>()

  val serviceNames: Set<String>
    get() = inboundHandlers.keys.toSet()

  val clientNames: Set<String>
    get() = outboundChannel.serviceNamesArray().toSet()

  /** If null, the user must still call Zipline.get() on Kotlin/JS. */
  internal var userSerializersModule: SerializersModule? = null
    set(value) {
      field = value
      serializersModule = computeSerializersModule()
    }

  /** Unions Zipline-provided serializers with user-provided serializers. */
  private var serializersModule: SerializersModule = computeSerializersModule()

  private fun computeSerializersModule(): SerializersModule {
    return SerializersModule {
      contextual(Throwable::class, ThrowableSerializer)
      contextual(FlowReference::class) {
        FlowReferenceSerializer(ziplineReferenceSerializer(ziplineServiceAdapter()), it[0])
      }

      include(userSerializersModule ?: EmptySerializersModule)
    }
  }

  internal val inboundChannel = object : CallChannel {
    override fun serviceNamesArray(): Array<String> {
      return serviceNames.toTypedArray()
    }

    override fun invoke(
      instanceName: String,
      funName: String,
      encodedArguments: Array<String>
    ): Array<String> {
      val handler = takeHandler(instanceName, funName)
      val inboundCall = InboundCall(handler.context, funName, encodedArguments)
      return try {
        handler.call(inboundCall)
      } catch (e: Throwable) {
        inboundCall.resultException(e)
      }
    }

    override fun invokeSuspending(
      instanceName: String,
      funName: String,
      encodedArguments: Array<String>,
      callbackName: String
    ) {
      val handler = takeHandler(instanceName, funName)
      scope.launch {
        val callback = get<SuspendCallback>(callbackName)
        val inboundCall = InboundCall(handler.context, funName, encodedArguments)
        val result = try {
          handler.callSuspending(inboundCall)
        } catch (e: Exception) {
          inboundCall.resultException(e)
        }
        scope.ensureActive() // Don't resume a continuation if the Zipline has since been closed.
        callback.call(result)
      }
    }

    /**
     * Note that this removes the handler for calls to is [ZiplineService.close]. We remove before
     * dispatching so it'll always be removed even if the call stalls or throws.
     */
    private fun takeHandler(instanceName: String, funName: String): InboundCallHandler {
      val result = when (funName) {
        "close" -> inboundHandlers.remove(instanceName)
        else -> inboundHandlers[instanceName]
      }
      return result ?: error("no handler for $instanceName")
    }

    override fun disconnect(instanceName: String): Boolean {
      return inboundHandlers.remove(instanceName) != null
    }
  }

  @PublishedApi
  internal fun <T : ZiplineService> ziplineReferenceSerializer(
    adapter: ZiplineServiceAdapter<T>
  ): KSerializer<ZiplineReference<T>> = ZiplineReferenceSerializer(this, adapter)

  fun <T : ZiplineService> set(name: String, instance: T) {
    error("unexpected call to Endpoint.set: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> set(
    name: String,
    service: T,
    adapter: ZiplineServiceAdapter<T>
  ) {
    inboundHandlers[name] = adapter.inboundCallHandler(service, newInboundContext())
  }

  fun <T : ZiplineService> get(name: String): T {
    error("unexpected call to Endpoint.get: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> get(name: String, adapter: ZiplineServiceAdapter<T>): T {
    return adapter.outboundService(newOutboundContext(name))
  }

  @PublishedApi
  internal fun remove(name: String): InboundCallHandler? {
    return inboundHandlers.remove(name)
  }

  internal fun generateName(): String {
    return "zipline/${nextId++}"
  }

  @PublishedApi
  internal fun newInboundContext() = InboundBridge.Context(serializersModule, this)

  @PublishedApi
  internal fun newOutboundContext(name: String) =
    OutboundBridge.Context(name, serializersModule, this)
}
