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
package app.cash.quickjs

import app.cash.quickjs.internal.bridge.InboundService
import app.cash.quickjs.internal.bridge.InternalBridge
import app.cash.quickjs.internal.bridge.KtBridge
import app.cash.quickjs.internal.bridge.OutboundClientFactory
import kotlinx.coroutines.Dispatchers

actual abstract class Zipline {
  actual abstract val engineVersion: String

  actual fun <T : Any> get(name: String, jsAdapter: JsAdapter): T {
    error("unexpected call to Zipline.get: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal abstract fun <T : Any> get(
    name: String,
    outboundClientFactory: OutboundClientFactory<T>
  ): T

  actual fun <T : Any> set(name: String, jsAdapter: JsAdapter, instance: T) {
    error("unexpected call to Zipline.set: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal abstract fun set(name: String, service: InboundService<*>)

  companion object : Zipline() {
    override val engineVersion: String
      get() = THE_ONLY_ZIPLINE.engineVersion

    override fun <T : Any> get(name: String, outboundClientFactory: OutboundClientFactory<T>): T {
      return THE_ONLY_ZIPLINE.get(name, outboundClientFactory)
    }

    override fun set(name: String, service: InboundService<*>) {
      THE_ONLY_ZIPLINE.set(name, service)
    }
  }
}

private class ZiplineJs : Zipline(), JsPlatform, InternalBridge  {
  private val ktBridge = KtBridge(Dispatchers.Main, outboundBridge = this)
  private val hostPlatform: HostPlatform
  private var nextTimeoutId = 1
  private val jobs = mutableMapOf<Int, Job>()

  /** Lazily fetch the bridge to call out. */
  private val jsOutboundBridge: dynamic
    get() = js("""globalThis.app_cash_quickjs_outboundBridge""")

  init {
    // Eagerly publish the bridge so they can call us.
    val inboundBridge = ktBridge.inboundBridge
    js(
      """
      globalThis.app_cash_quickjs_inboundBridge = inboundBridge;
      """
    )

    // Create global functions for JavaScript callers.
    val jsPlatform = this
    js(
      """
      globalThis.setTimeout = function(handler, delay, args) {
        return jsPlatform.setTimeout(handler, delay, args);
      };
      globalThis.clearTimeout = function(timeoutID) {
        return jsPlatform.clearTimeout(timeoutID);
      };
      """
    )

    // Connect platforms using our newly-bootstrapped bridges.
    ktBridge.set<JsPlatform>(
      name = "app.cash.quickjs.jsPlatform",
      jsAdapter = BuiltInJsAdapter,
      instance = this
    )
    hostPlatform = ktBridge.get(
      name = "app.cash.quickjs.hostPlatform",
      jsAdapter = BuiltInJsAdapter
    )
  }

  override val engineVersion
    get() = quickJsVersion

  override fun invoke(
    instanceName: String,
    funName: String,
    encodedArguments: ByteArray
  ): ByteArray {
    return jsOutboundBridge.invoke(instanceName, funName, encodedArguments)
  }

  override fun invokeSuspending(
    instanceName: String,
    funName: String,
    encodedArguments: ByteArray,
    callbackName: String
  ) {
    return jsOutboundBridge.invokeSuspending(instanceName, funName, encodedArguments, callbackName)
  }

  override fun <T : Any> get(
    name: String,
    outboundClientFactory: OutboundClientFactory<T>
  ): T {
    return ktBridge.get(name, outboundClientFactory)
  }

  override fun set(name: String, service: InboundService<*>) {
    ktBridge.set(name, service)
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
