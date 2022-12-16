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

import app.cash.zipline.internal.Console
import app.cash.zipline.internal.CoroutineEventLoop
import app.cash.zipline.internal.EventListenerService
import app.cash.zipline.internal.EventLoop
import app.cash.zipline.internal.HostConsole
import app.cash.zipline.internal.HostEventListenerService
import app.cash.zipline.internal.JsPlatform
import app.cash.zipline.internal.bridge.CallChannel
import app.cash.zipline.internal.bridge.Endpoint
import app.cash.zipline.internal.bridge.EventListenerAdapter
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter
import app.cash.zipline.internal.consoleName
import app.cash.zipline.internal.eventListenerName
import app.cash.zipline.internal.eventLoopName
import app.cash.zipline.internal.initModuleLoader
import app.cash.zipline.internal.jsPlatformName
import app.cash.zipline.internal.loadJsModule
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

actual class Zipline private constructor(
  val quickJs: QuickJs,
  userSerializersModule: SerializersModule,
  dispatcher: CoroutineDispatcher,
  private val scope: CoroutineScope,
  private val eventListener: EventListener,
) {
  private val endpoint = Endpoint(
    scope = scope,
    userSerializersModule = userSerializersModule,
    eventListener = EventListenerAdapter(eventListener, this),
    outboundChannel = object : CallChannel {
      /** Lazily fetch the channel to call into JS. */
      private val jsInboundBridge: CallChannel by lazy(mode = LazyThreadSafetyMode.NONE) {
        quickJs.getInboundChannel()
      }

      override fun serviceNamesArray(): Array<String> {
        return jsInboundBridge.serviceNamesArray()
      }

      override fun call(callJson: String): String {
        check(scope.isActive) { "Zipline closed" }
        return jsInboundBridge.call(callJson)
      }

      override fun disconnect(instanceName: String): Boolean {
        return jsInboundBridge.disconnect(instanceName)
      }
    },
  )

  actual val json: Json
    get() = endpoint.json

  internal actual val serviceNames: Set<String>
    get() = endpoint.serviceNames

  internal actual val clientNames: Set<String>
    get() = endpoint.clientNames

  private var closed = false

  init {
    // Eagerly publish the channel so they can call us.
    quickJs.initOutboundChannel(endpoint.inboundChannel)

    endpoint.bind<Console>(
      name = consoleName,
      instance = HostConsole,
    )

    // Connect platforms using our newly-bootstrapped channels.
    val jsPlatform = endpoint.take<JsPlatform>(
      name = jsPlatformName,
    )
    val eventLoop = CoroutineEventLoop(dispatcher, scope, jsPlatform)
    endpoint.bind<EventLoop>(
      name = eventLoopName,
      instance = eventLoop,
    )
    val eventListenerService = HostEventListenerService(this, eventListener)
    endpoint.bind<EventListenerService>(
      name = eventListenerName,
      instance = eventListenerService,
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
  fun close() {
    if (closed) return
    closed = true

    scope.cancel()
    quickJs.close()

    // Don't wait for a JS continuation to resume, it never will. Canceling `scope` doesn't do this
    // because each continuation is in its caller's scope.
    for (continuation in endpoint.incompleteContinuations) {
      continuation.resumeWithException(CancellationException("Zipline closed"))
    }
    endpoint.incompleteContinuations.clear()
    eventListener.ziplineClosed(this)
  }

  fun loadJsModule(script: String, id: String) {
    quickJs.loadJsModule(script, id)
  }

  fun loadJsModule(bytecode: ByteArray, id: String) {
    quickJs.loadJsModule(id, bytecode)
  }

  companion object {
    fun create(
      dispatcher: CoroutineDispatcher,
      serializersModule: SerializersModule = EmptySerializersModule(),
      eventListener: EventListener = EventListener.NONE,
    ): Zipline {
      val quickJs = QuickJs.create()
      // TODO(jwilson): figure out a 512 KiB limit caused intermittent stack overflow failures.
      quickJs.maxStackSize = 0L
      quickJs.initModuleLoader()

      val scope = CoroutineScope(dispatcher)
      val result = Zipline(quickJs, serializersModule, dispatcher, scope, eventListener)
      eventListener.ziplineCreated(result)
      return result
    }
  }
}
