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

actual class Zipline internal constructor() {
  private val endpoint = Endpoint(
    dispatcher = Dispatchers.Main,
    outboundChannel = object : CallChannel {
      /** Lazily fetch the channel to call out. */
      private val jsOutboundChannel: dynamic
        get() = js("""globalThis.app_cash_zipline_outboundChannel""")

      override fun serviceNamesArray(): Array<String> {
        return jsOutboundChannel.serviceNamesArray()
      }

      override fun invoke(
        instanceName: String,
        funName: String,
        encodedArguments: Array<String>
      ): Array<String> {
        return jsOutboundChannel.invoke(
          instanceName,
          funName,
          encodedArguments
        )
      }

      override fun invokeSuspending(
        instanceName: String,
        funName: String,
        encodedArguments: Array<String>,
        callbackName: String
      ) {
        return jsOutboundChannel.invokeSuspending(
          instanceName,
          funName,
          encodedArguments,
          callbackName
        )
      }

      override fun disconnect(instanceName: String): Boolean {
        return jsOutboundChannel.disconnect(instanceName)
      }
    }
  )

  actual val serializersModule: SerializersModule
    get() = endpoint.userSerializersModule!!

  actual val engineVersion
    get() = quickJsVersion

  actual val serviceNames: Set<String>
    get() = endpoint.serviceNames

  actual val clientNames: Set<String>
    get() = endpoint.clientNames

  init {
    // Eagerly publish the channel so they can call us.
    val inboundChannel = endpoint.inboundChannel
    js(
      """
      globalThis.app_cash_zipline_inboundChannel = inboundChannel;
      """
    )

    // Connect platforms using our newly-bootstrapped channels.
    val hostPlatform = endpoint.get<HostPlatform>(
      name = "zipline/host"
    )
    endpoint.set<JsPlatform>(
      name = "zipline/js",
      instance = RealJsPlatform(hostPlatform)
    )
  }

  actual fun <T : Any> get(name: String): T {
    error("unexpected call to Zipline.get: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : Any> get(name: String, outboundBridge: OutboundBridge<T>): T {
    return endpoint.get(name, outboundBridge)
  }

  actual fun <T : Any> set(name: String, instance: T) {
    error("unexpected call to Zipline.set: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : Any> set(name: String, bridge: InboundBridge<T>) {
    endpoint.set(name, bridge)
  }

  companion object {
    fun get(serializersModule: SerializersModule = EmptySerializersModule): Zipline {
      if (THE_ONLY_ZIPLINE.endpoint.userSerializersModule == null) {
        THE_ONLY_ZIPLINE.endpoint.userSerializersModule = serializersModule
      }
      require(serializersModule == THE_ONLY_ZIPLINE.endpoint.userSerializersModule) {
        "get() called multiple times with non-equal serializersModule instances"
      }
      return THE_ONLY_ZIPLINE
    }
  }
}

private class RealJsPlatform(
  val hostPlatform: HostPlatform
) : JsPlatform {
  private var nextTimeoutId = 1
  private val jobs = mutableMapOf<Int, Job>()

  init {
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
private val THE_ONLY_ZIPLINE = Zipline()
