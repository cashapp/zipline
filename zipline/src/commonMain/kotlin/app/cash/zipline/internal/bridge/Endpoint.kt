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

import app.cash.zipline.Call
import app.cash.zipline.CallResult
import app.cash.zipline.ZiplineScope
import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.EndpointService
import app.cash.zipline.internal.passByReferencePrefix
import app.cash.zipline.ziplineServiceSerializer
import kotlin.coroutines.Continuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * An outbound channel for delivering calls to the other platform, and an inbound channel for
 * receiving calls from the other platform.
 *
 * @param oppositeProvider this must be lazy because it's implemented on top of the two endpoints.
 */
internal class Endpoint internal constructor(
  internal val scope: CoroutineScope,
  internal val userSerializersModule: SerializersModule,
  internal val eventListener: EventListener,
  internal val outboundChannel: CallChannel,
  internal val oppositeProvider: () -> EndpointService,
) : EndpointService {
  internal val inboundServices = mutableMapOf<String, InboundService<*>>()
  private var nextId = 1

  internal val incompleteContinuations = mutableSetOf<Continuation<*>>()

  override val serviceNames
    get() = inboundServices.keys.toSet()

  internal var takeScope: ZiplineScope? = null

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
      contextual(Long::class, LongSerializer)
      contextual(Flow::class) { serializers ->
        FlowSerializer(
          ziplineServiceSerializer<FlowZiplineService<Any?>>(
            FlowZiplineService::class,
            serializers,
          ),
        )
      }
      contextual(StateFlow::class) { serializers ->
        StateFlowSerializer(
          ziplineServiceSerializer<StateFlowZiplineService<Any?>>(
            StateFlowZiplineService::class,
            serializers,
          ),
        )
      }
      include(userSerializersModule)
    }
  }

  internal val callCodec = CallCodec(this)

  val inboundChannel = object : CallChannel {
    override fun call(callJson: String): String {
      val internalCall = callCodec.decodeCall(callJson)
      val inboundService = internalCall.inboundService!!
      val externalCall = callCodec.lastInboundCall!!

      return when {
        internalCall.suspendCallback != null -> inboundService.callSuspending(
          internalCall,
          externalCall,
          internalCall.suspendCallback,
        )
        else -> inboundService.call(
          internalCall,
          externalCall,
        )
      }
    }

    override fun disconnect(instanceName: String): Boolean {
      return remove(instanceName) != null
    }
  }

  private val serviceTypeCache = mutableMapOf<String, RealZiplineServiceType<*>>()

  internal val opposite: EndpointService
    get() = oppositeProvider()

  @Suppress("UNUSED_PARAMETER") // Parameters are used by the compiler plug-in.
  fun <T : ZiplineService> bind(name: String, instance: T) {
    error("unexpected call to Endpoint.bind: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> bind(
    name: String,
    service: T,
    adapter: ZiplineServiceAdapter<T>,
  ) {
    eventListener.bindService(name, service)
    val type = serviceType(adapter)
    inboundServices[name] = InboundService(type, service, this)
  }

  @Suppress("UNUSED_PARAMETER") // Parameter is used by the compiler plug-in.
  fun <T : ZiplineService> take(
    name: String,
    scope: ZiplineScope = ZiplineScope(),
  ): T {
    error("unexpected call to Endpoint.take: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> take(
    name: String,
    scope: ZiplineScope = ZiplineScope(),
    adapter: ZiplineServiceAdapter<T>,
  ): T {
    // Detect leaked old services when creating new services.
    detectLeaks()

    val type = serviceType(adapter)
    val callHandler = OutboundCallHandler(type, name, this, adapter, scope)
    val result = callHandler.outboundService<T>()
    if (result.usesScope()) {
      scope.add(callHandler)
    }
    eventListener.takeService(name, result)
    trackLeaks(this, name, callHandler, result)
    return result
  }

  /**
   * Returns true if this is a service that must be closed, either explicitly or using a scope.
   *
   * (Both [SuspendCallback] and [CancelCallback] close themselves automatically when the call
   * completes.)
   */
  private fun ZiplineService.usesScope(): Boolean {
    if (this is SuspendCallback<*>) return false
    if (this is CancelCallback) return false
    return true
  }

  internal fun <T> withTakeScope(scope: ZiplineScope, block: () -> T): T {
    val pushedTakeScope = takeScope
    takeScope = scope
    try {
      return block()
    } finally {
      takeScope = pushedTakeScope
    }
  }

  @PublishedApi
  internal fun remove(name: String): InboundService<*>? {
    return inboundServices.remove(name)
  }

  internal fun generatePassByReferenceName(): String {
    return "$passByReferencePrefix${nextId++}"
  }

  override fun serviceType(name: String): SerializableZiplineServiceType? {
    val type = inboundServices[name]?.type ?: return null
    return SerializableZiplineServiceType(type)
  }

  private fun <T : ZiplineService> serviceType(
    adapter: ZiplineServiceAdapter<T>,
  ): RealZiplineServiceType<T> {
    @Suppress("UNCHECKED_CAST") // The 'T' type arguments always match the keys.
    return serviceTypeCache.getOrPut(adapter.serialName) {
      RealZiplineServiceType(
        adapter.serialName,
        adapter.ziplineFunctions(json.serializersModule),
      )
    } as RealZiplineServiceType<T>
  }

  /**
   * Subset of the Zipline EventListener for events endpoints trigger. Unlike Zipline's
   * EventListener, this one is used in both host and guest code.
   */
  open class EventListener {
    open fun bindService(name: String, service: ZiplineService) {
    }

    open fun takeService(name: String, service: ZiplineService) {
    }

    open fun serviceLeaked(name: String) {
    }

    open fun callStart(call: Call): Any? {
      return null
    }

    open fun callEnd(call: Call, result: CallResult, startValue: Any?) {
    }
  }
}
