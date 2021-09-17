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

import app.cash.zipline.internal.HostPlatform
import app.cash.zipline.internal.JsPlatform
import app.cash.zipline.internal.bridge.CallChannel
import app.cash.zipline.internal.bridge.Endpoint
import app.cash.zipline.internal.bridge.InboundBridge
import app.cash.zipline.internal.bridge.OutboundBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

actual abstract class Zipline {
  actual abstract val engineVersion: String

  actual abstract val serviceNames: Set<String>

  actual abstract val clientNames: Set<String>

  actual fun <T : Any> get(name: String, serializersModule: SerializersModule): T {
    error("unexpected call to Zipline.get: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal abstract fun <T : Any> get(
    name: String,
    bridge: OutboundBridge<T>
  ): T

  actual fun <T : Any> set(name: String, serializersModule: SerializersModule, instance: T) {
    error("unexpected call to Zipline.set: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal abstract fun <T : Any> set(name: String, bridge: InboundBridge<T>)

  companion object : Zipline() {
    override val engineVersion: String
      get() = THE_ONLY_ZIPLINE.engineVersion

    override val serviceNames: Set<String>
      get() = THE_ONLY_ZIPLINE.serviceNames

    override val clientNames: Set<String>
      get() = THE_ONLY_ZIPLINE.clientNames

    override fun <T : Any> get(name: String, outboundBridge: OutboundBridge<T>): T {
      return THE_ONLY_ZIPLINE.get(name, outboundBridge)
    }

    override fun <T : Any> set(name: String, bridge: InboundBridge<T>) {
      THE_ONLY_ZIPLINE.set(name, bridge)
    }
  }
}

private class ZiplineJs : Zipline(), JsPlatform, CallChannel  {
  private val endpoint = Endpoint(Dispatchers.Main, outboundChannel = this)
  private val hostPlatform: HostPlatform
  private var nextTimeoutId = 1
  private val jobs = mutableMapOf<Int, Job>()

  /** Lazily fetch the bridge to call out. */
  private val jsOutboundChannel: dynamic
    get() = js("""globalThis.app_cash_zipline_outboundChannel""")

  init {
    // Eagerly publish the bridge so they can call us.
    val inboundChannel = endpoint.inboundChannel
    js(
      """
      globalThis.app_cash_zipline_inboundChannel = inboundChannel;
      """
    )

    // Create global functions for JavaScript callers.
    val jsPlatform = this
    js(
      """
      globalThis.setTimeout = function(handler, delay) {
        return jsPlatform.setTimeout(handler, delay, arguments);
      };
      globalThis.clearTimeout = function(timeoutID) {
        return jsPlatform.clearTimeout(timeoutID);
      };
      globalThis.console = {
        error: function() { jsPlatform.consoleMessage('error', arguments) },
        info: function() { jsPlatform.consoleMessage('info', arguments) },
        log: function() { jsPlatform.consoleMessage('log', arguments) },
        warn: function() { jsPlatform.consoleMessage('warn', arguments) },
      };
      """
    )

    // Connect platforms using our newly-bootstrapped bridges.
    endpoint.set<JsPlatform>(
      name = "zipline/js",
      serializersModule = EmptySerializersModule,
      instance = this
    )
    hostPlatform = endpoint.get(
      name = "zipline/host",
      serializersModule = EmptySerializersModule
    )
  }

  override val engineVersion
    get() = quickJsVersion

  override val serviceNames: Set<String>
    get() = endpoint.serviceNames

  override val clientNames: Set<String>
    get() = endpoint.clientNames

  override fun serviceNamesArray(): Array<String> {
    return jsOutboundChannel.serviceNamesArray()
  }

  override fun invoke(
    instanceName: String,
    funName: String,
    encodedArguments: ByteArray
  ): ByteArray {
    return jsOutboundChannel.invoke(instanceName, funName, encodedArguments)
  }

  override fun invokeSuspending(
    instanceName: String,
    funName: String,
    encodedArguments: ByteArray,
    callbackName: String
  ) {
    return jsOutboundChannel.invokeSuspending(instanceName, funName, encodedArguments, callbackName)
  }

  override fun disconnect(instanceName: String): Boolean {
    return jsOutboundChannel.disconnect(instanceName)
  }

  override fun <T : Any> get(
    name: String,
    outboundBridge: OutboundBridge<T>
  ): T {
    return endpoint.get(name, outboundBridge)
  }

  override fun <T : Any> set(name: String, bridge: InboundBridge<T>) {
    endpoint.set(name, bridge)
  }

  override fun runJob(timeoutId: Int) {
    val job = jobs.remove(timeoutId) ?: return
    job.handler.apply(null, job.arguments)
  }

  @JsName("setTimeout")
  fun setTimeout(handler: dynamic, timeout: Int, vararg arguments: Any?): Int {
    val timeoutId = nextTimeoutId++
    jobs[timeoutId] = Job(handler, arguments)
    hostPlatform.setTimeout(timeoutId, timeout)
    return timeoutId
  }

  @JsName("clearTimeout")
  fun clearTimeout(handle: Int) {
    jobs.remove(handle)
    // TODO(jwilson): tell the host platform to clear the timeout.
  }

  /**
   * Note that this doesn't currently implement `%o`, `%d`, `%s`, etc.
   * https://developer.mozilla.org/en-US/docs/Web/API/console#outputting_text_to_the_console
   */
  @JsName("consoleMessage")
  fun consoleMessage(level: String, vararg arguments: Any?) {
    hostPlatform.consoleMessage(level, arguments.joinToString(separator = " "))
  }

  private class Job(
    val handler: dynamic,
    val arguments: Array<out Any?>
  )
}

/**
 * We declare this as a top-level property because that causes this object to be constructed when
 * the code is loaded, and we rely on that for the side-effects performed in the ZiplineJs
 * constructor.
 */
private val THE_ONLY_ZIPLINE: Zipline = ZiplineJs()
