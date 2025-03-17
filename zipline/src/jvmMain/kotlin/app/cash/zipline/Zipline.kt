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

import app.cash.plywood.WasmEngine
import app.cash.plywood.WasmFunction
import app.cash.plywood.WasmModule
import app.cash.plywood.bridge.HostBridge
import app.cash.zipline.internal.CoroutineEventLoop
import app.cash.zipline.internal.EventListenerAdapter
import app.cash.zipline.internal.FakeKotlinStdlib
import app.cash.zipline.internal.GuestService
import app.cash.zipline.internal.HostService
import app.cash.zipline.internal.RealHostService
import app.cash.zipline.internal.ZIPLINE_GUEST_NAME
import app.cash.zipline.internal.ZIPLINE_HOST_NAME
import app.cash.zipline.internal.bridge.CallChannel
import app.cash.zipline.internal.bridge.Endpoint
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter
import app.cash.zipline.internal.bridge.ZiplineWasmBridge
import app.cash.zipline.internal.bridge.stopTrackingLeaks
import app.cash.zipline.internal.bridge.theOnlyCancellationException
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

actual class Zipline private constructor(
  private val wasmEngine: WasmEngine,
  userSerializersModule: SerializersModule,
  dispatcher: CoroutineDispatcher,
  private val scope: CoroutineScope,
  val eventListener: EventListener,
) : AutoCloseable {
  private val plywoodBridge = HostBridge()
  private val ziplineWasmBridge = ZiplineWasmBridge(plywoodBridge)
  private var wasmModuleSpec: WasmModule.Spec? = null
  private var wasmModule: WasmModule? = null

  private val endpoint = Endpoint(
    scope = scope,
    userSerializersModule = userSerializersModule,
    eventListener = EventListenerAdapter(eventListener, this),
    outboundChannel = object : CallChannel {
      /** Lazily fetch the channel to call into Wasm. */
      private val callChannel: CallChannel by lazy(mode = LazyThreadSafetyMode.NONE) {
        ziplineWasmBridge.outboundCallChannel(wasmModule!!)
      }

      override fun call(callJson: String): String {
        check(scope.isActive) { "Zipline closed" }
        return callChannel.call(callJson)
      }

      override fun disconnect(instanceName: String): Boolean {
        return callChannel.disconnect(instanceName)
      }
    },
    oppositeProvider = {
      guest
    },
  )

  private val guest: GuestService = endpoint.take(ZIPLINE_GUEST_NAME)

  actual val json: Json
    get() = endpoint.json

  internal actual val serviceNames: Set<String>
    get() = endpoint.serviceNames

  internal actual val clientNames: Set<String>
    get() = guest.serviceNames

  private var closed = false

  private val attachments = mutableMapOf<KClass<*>, Any>()

  init {
    val eventLoop = CoroutineEventLoop(dispatcher, scope, guest)

    endpoint.bind<HostService>(
      name = ZIPLINE_HOST_NAME,
      instance = RealHostService(endpoint, this, eventListener, eventLoop),
    )
  }

  actual fun <T : ZiplineService> bind(name: String, instance: T) {
    error("unexpected call to Zipline.bind: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> bind(
    name: String,
    service: T,
    adapter: ZiplineServiceAdapter<T>,
  ) {
    check(scope.isActive) { "closed" }
    endpoint.bind(name, service, adapter)
  }

  actual fun <T : ZiplineService> take(
    name: String,
    scope: ZiplineScope,
  ): T {
    error("unexpected call to Zipline.take: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> take(
    name: String,
    scope: ZiplineScope = ZiplineScope(),
    adapter: ZiplineServiceAdapter<T>,
  ): T {
    check(this.scope.isActive) { "closed" }
    return endpoint.take(name, scope, adapter)
  }

  /**
   * Release resources held by this instance. It is an error to do any of the following after
   * calling close:
   *
   *  * Call [take] or [bind].
   *  * Accessing [quickJs].
   *  * Accessing the objects returned from [take].
   */
  override fun close() {
    if (closed) return
    closed = true

    var thrown: Throwable? = null

    scope.cancel(theOnlyCancellationException)

    // Close all caller-provided services that are still open. We clear the map to prevent possible
    // retain cycles on Kotlin/Native where some objects may be reference-counted.
    val inboundServicesToClose = endpoint.inboundServices.values.toTypedArray()
    endpoint.inboundServices.clear()
    for (inboundService in inboundServicesToClose) {
      try {
        inboundService.service.close()
      } catch (e: Throwable) {
        if (thrown != null) thrown = e
      }
    }

    wasmModule?.close()
    wasmModuleSpec?.close()
    wasmEngine.close()

    // Don't wait for a JS continuation to resume, it never will. Canceling `scope` doesn't do this
    // because each continuation is in its caller's scope.
    for (continuation in endpoint.incompleteContinuations) {
      continuation.resumeWithException(CancellationException("Zipline closed"))
    }
    endpoint.incompleteContinuations.clear()
    stopTrackingLeaks(endpoint)
    eventListener.ziplineClosed(this)

    if (thrown != null) {
      throw thrown
    }
  }

  fun loadWasmModule(bytecode: ByteArray) {
    check(wasmModuleSpec == null && wasmModule == null)
    val kotlinStdlib = FakeKotlinStdlib()
    val spec = wasmEngine.spec(bytecode)
    wasmModuleSpec = spec
    wasmModule = spec.create { module ->
      kotlinStdlib.imports(module) + plywoodBridge.imports(module) + ziplineWasmBridge.imports(module, endpoint.inboundChannel)
    }
  }

  fun wasmFunction(name: String): WasmFunction? {
    return wasmModule?.function(name)
  }

  actual fun <T : Any> getOrPutAttachment(key: KClass<T>, compute: () -> T): T {
    val value = attachments.getOrPut(key, compute)
    return key.cast(value)
  }

  companion object {
    fun create(
      dispatcher: CoroutineDispatcher,
      serializersModule: SerializersModule = EmptySerializersModule(),
      eventListener: EventListener = EventListener.NONE,
    ): Zipline {
      val engine = WasmEngine()
      val scope = CoroutineScope(dispatcher)
      val result = Zipline(engine, serializersModule, dispatcher, scope, eventListener)
      eventListener.ziplineCreated(result)
      return result
    }
  }
}
