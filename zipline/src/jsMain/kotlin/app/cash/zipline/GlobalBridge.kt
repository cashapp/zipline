/*
 * Copyright (C) 2022 Block, Inc.
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

import app.cash.zipline.internal.GuestService
import app.cash.zipline.internal.bridge.CallChannel

/**
 * JS-global bindings for implementing a complete JS platform (setTimeout, console), and receiving
 * calls from the host platform (inboundChannel).
 *
 * Creating this has the side effect of setting variables on `globalThis`, so we can initialize the
 * host platform and JS platform in any order.
 */
internal object GlobalBridge : GuestService, CallChannel {
  private var nextTimeoutId = 1
  private val jobs = mutableMapOf<Int, Job>()

  private val zipline: Zipline
    get() = theOnlyZipline ?: error("Zipline isn't ready: did you call Zipline.get() yet?")

  private val inboundChannel: CallChannel
    get() = zipline.endpoint.inboundChannel

  init {
    @Suppress("UNUSED_VARIABLE") // Used in raw JS code below.
    val globalBridge = this
    js(
      """
      globalThis.app_cash_zipline_inboundChannel = globalBridge;

      globalThis.setTimeout = function(handler, delay) {
        return globalBridge.setTimeout(handler, delay, arguments);
      };
      globalThis.clearTimeout = function(timeoutID) {
        return globalBridge.clearTimeout(timeoutID);
      };
      globalThis.console = {
        error: function() { globalBridge.consoleMessage('error', arguments) },
        info: function() { globalBridge.consoleMessage('info', arguments) },
        log: function() { globalBridge.consoleMessage('log', arguments) },
        warn: function() { globalBridge.consoleMessage('warn', arguments) },
      };
      """,
    )
  }

  override val serviceNames: Set<String>
    get() = zipline.endpoint.serviceNames

  override fun serviceType(name: String) = zipline.endpoint.serviceType(name)

  override fun call(callJson: String) = inboundChannel.call(callJson)

  override fun disconnect(instanceName: String) = inboundChannel.disconnect(instanceName)

  override fun runJob(timeoutId: Int) {
    val job = jobs.remove(timeoutId) ?: return
    job.handler.apply(null, job.arguments)
  }

  override fun close() {
  }

  @JsName("setTimeout")
  fun setTimeout(handler: dynamic, timeout: Int, vararg arguments: Any?): Int {
    val timeoutId = nextTimeoutId++
    jobs[timeoutId] = Job(handler, arguments)
    zipline.host.setTimeout(timeoutId, timeout)
    return timeoutId
  }

  @JsName("clearTimeout")
  fun clearTimeout(timeoutId: Int) {
    jobs.remove(timeoutId)
    zipline.host.clearTimeout(timeoutId)
  }

  /**
   * Note that this doesn't currently implement `%o`, `%d`, `%s`, etc.
   * https://developer.mozilla.org/en-US/docs/Web/API/console#outputting_text_to_the_console
   */
  @JsName("consoleMessage")
  fun consoleMessage(level: String, vararg arguments: Any?) {
    var throwable: Throwable? = null
    val argumentsList = mutableListOf<Any?>()
    for (argument in arguments) {
      if (throwable == null && argument is Throwable) {
        throwable = argument
      } else {
        argumentsList += argument
      }
    }
    zipline.host.log(level, argumentsList.joinToString(separator = " "), throwable)
  }

  private class Job(
    val handler: dynamic,
    val arguments: Array<out Any?>,
  )
}
