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
import app.cash.zipline.internal.passByReferencePrefix
import app.cash.zipline.internal.ziplineInternalPrefix
import kotlin.coroutines.Continuation
import kotlinx.coroutines.CoroutineScope
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
  internal val inboundServices = mutableMapOf<String, InboundService<*>>()
  private var outboundServiceCount = 0
  private var nextId = 1
  internal var state = State.READY

  internal val incompleteContinuations = mutableSetOf<Continuation<*>>()

  val serviceNames: Set<String>
    get() = inboundServices.keys.toSet()

  val clientNames: Set<String>
    get() = outboundChannel.serviceNamesArray().toSet()

  /** This uses both Zipline-provided serializers and user-provided serializers. */
  internal val json: Json = Json {
    useArrayPolymorphism = true

    // For backwards-compatibility, allow new fields to be introduced.
    ignoreUnknownKeys = true

    // Because host and JS may disagree on default values, it's best to encode them.
    encodeDefaults = true

    // Support map keys whose values are arrays or objects.
    allowStructuredMapKeys = true

    serializersModule = SerializersModule {
      contextual(PassByReference::class, PassByReferenceSerializer(this@Endpoint))
      contextual(Throwable::class, ThrowableSerializer)
      include(userSerializersModule)
    }
  }

  internal val callCodec = CallCodec(this)

  internal val inboundChannel = object : CallChannel {
    override fun serviceNamesArray(): Array<String> {
      return serviceNames.toTypedArray()
    }

    override fun call(callJson: String): String {
      val internalCall = callCodec.decodeCall(callJson)
      val inboundService = internalCall.inboundService!!
      val externalCall = callCodec.lastInboundCall!!

      return when {
        internalCall.suspendCallback != null -> inboundService.callSuspending(
          internalCall,
          externalCall,
          internalCall.suspendCallback
        )
        else -> inboundService.call(
          internalCall,
          externalCall
        )
      }
    }

    override fun disconnect(instanceName: String): Boolean {
      return inboundServices.remove(instanceName) != null
    }
  }

  @Suppress("UNUSED_PARAMETER") // Parameters are used by the compiler plug-in.
  fun <T : ZiplineService> bind(name: String, instance: T) {
    error("unexpected call to Endpoint.bind: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> bind(
    name: String,
    service: T,
    adapter: ZiplineServiceAdapter<T>
  ) {
    checkReady(name)

    eventListener.bindService(name, service)

    val functions = adapter.ziplineFunctions(json.serializersModule)
    inboundServices[name] = InboundService(service, this, functions)
  }

  @Suppress("UNUSED_PARAMETER") // Parameter is used by the compiler plug-in.
  fun <T : ZiplineService> take(name: String): T {
    error("unexpected call to Endpoint.take: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> take(name: String, adapter: ZiplineServiceAdapter<T>): T {
    checkReady(name)

    // Detect leaked old services when creating new services.
    detectLeaks()

    val functions = adapter.ziplineFunctions(json.serializersModule)
    val callHandler = OutboundCallHandler(name, this, functions)
    val result = adapter.outboundService(callHandler)
    outboundServiceCount++
    eventListener.takeService(name, result)
    trackLeaks(eventListener, name, callHandler, result)
    return result
  }

  internal fun remove(service: ZiplineService) {
    val i = inboundServices.values.iterator()
    while (i.hasNext()) {
      val inboundService = i.next()
      if (inboundService.service === service) {
        i.remove()
        return
      }
    }
  }

  @PublishedApi
  internal fun outboundServiceClosed() {
    outboundServiceCount--
    if (outboundServiceCount == 0 && state == State.SHUTTING_DOWN) {
      state = State.CLOSED
    }
  }

  internal fun generatePassByReferenceName(): String {
    return "$passByReferencePrefix${nextId++}"
  }

  private fun checkReady(name: String) {
    // Internal binds and takes are always allowed. Otherwise, suspend functions and
    // pass-by-reference would break after shutdown().
    if (name.startsWith(ziplineInternalPrefix)) return

    when (state) {
      State.SHUTTING_DOWN -> throw IllegalStateException("shutting down")
      State.CLOSED -> throw IllegalStateException("closed")
      else -> return
    }
  }

  fun shutdown() {
    state = when {
      outboundServiceCount > 0 -> State.SHUTTING_DOWN
      else -> State.CLOSED
    }
  }

  /**
   * Endpoints have a lifecycle so that the mechanism that powers them (ie. QuickJS) can be cleaned
   * up when it is no longer needed.
   *
   * READY: the endpoint is new and available for calls to bind() and take(), and functions on those
   *     services to be invoked.
   *
   * SHUTDOWN: the endpoint expects no further calls to bind() or take(), but the services already
   *     taken may be used indefinitely. This includes passing services by reference, which will
   *     implicitly bind and take new services. When the last of all services is closed, the
   *     endpoint is automatically closed.
   *
   * CLOSED: the endpoint expects no further calls to bind() or take(), and no further calls to the
   *     functions on the services that have been taken. This state is reached when all such
   *     services are closed.
   */
  internal enum class State {
    READY,
    SHUTTING_DOWN,
    CLOSED,
  }
}
