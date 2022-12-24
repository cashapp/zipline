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

import app.cash.zipline.internal.HttpClient
import app.cash.zipline.internal.JsPlatform
import app.cash.zipline.internal.bridge.CallChannel
import app.cash.zipline.internal.bridge.inboundChannelName
import kotlin.js.Promise
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.w3c.fetch.Headers
import org.w3c.fetch.Request
import org.w3c.fetch.Response
import org.w3c.fetch.ResponseInit

/**
 * JS-global bindings for implementing a complete JS platform (setTimeout, console), and receiving
 * calls from the host platform (inboundChannel).
 *
 * Creating this has the side effect of setting variables on `globalThis`, so we can initialize the
 * host platform and JS platform in any order.
 */
internal object GlobalBridge : JsPlatform, CallChannel {
  private var nextTimeoutId = 1
  private val jobs = mutableMapOf<Int, Job>()

  private val zipline: Zipline
    get() = THE_ONLY_ZIPLINE ?: error("Zipline isn't ready: did you call Zipline.get() yet?")

  private val inboundChannel: CallChannel
    get() = zipline.endpoint.inboundChannel

  init {
    @Suppress("UNUSED_VARIABLE") // Used in raw JS code below.
    val globalBridge = this
    js(
      """
      globalThis.$inboundChannelName = globalBridge;

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
      globalThis.fetch = function(resource, options) {
        return globalBridge.fetch(new Request(resource, options));
      };
      """
    )
  }

  override fun serviceNamesArray() = inboundChannel.serviceNamesArray()

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
    zipline.eventLoop.setTimeout(timeoutId, timeout)
    return timeoutId
  }

  @JsName("clearTimeout")
  fun clearTimeout(timeoutId: Int) {
    jobs.remove(timeoutId)
    zipline.eventLoop.clearTimeout(timeoutId)
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
    zipline.console.log(level, argumentsList.joinToString(separator = " "), throwable)
  }

  @JsName("fetch")
  @OptIn(DelicateCoroutinesApi::class)
  fun fetch(request: Request): Promise<Response> {
    val clientRequest = HttpClient.Request(
      method = request.method,
      url = request.url,
      headers = buildMap {
         for (item in request.headers.asDynamic().iterator()) {
           put(item[0], item[1])
         }
      },
    )
    return GlobalScope.promise {
      val clientResponse = zipline.httpClient.execute(clientRequest)
      Response(
        body = null,
        init = ResponseInit(
          status = clientResponse.status,
          statusText = clientResponse.statusText,
          headers = Headers().apply {
            for ((name, value) in clientResponse.headers) {
              set(name, value)
            }
          }
        ),
      )
    }
  }

  private class Job(
    val handler: dynamic,
    val arguments: Array<out Any?>
  )
}
