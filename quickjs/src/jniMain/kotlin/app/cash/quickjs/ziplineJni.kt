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
import java.util.logging.Logger
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

actual abstract class Zipline {
  actual abstract val engineVersion: String

  abstract val quickJs: QuickJs

  abstract val dispatcher: CoroutineDispatcher

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

  companion object {
    fun create(
      dispatcher: CoroutineDispatcher,
    ): Zipline {
      val quickJs = QuickJs.create()
      // TODO(jwilson): figure out a 512 KiB limit caused intermittent stack overflow failures.
      quickJs.maxStackSize = 0L
      return ZiplineJvm(quickJs, dispatcher)
    }
  }
}

private class ZiplineJvm(
  override val quickJs: QuickJs,
  override val dispatcher: CoroutineDispatcher,
) : Zipline(), InternalBridge, HostPlatform {
  private val ktBridge = KtBridge(dispatcher, outboundBridge = this)
  private val jsPlatform: JsPlatform
  private val logger = Logger.getLogger(Zipline::class.qualifiedName)

  /** Lazily fetch the bridge to call into JS. */
  private val jsInboundBridge: InternalBridge by lazy(mode = LazyThreadSafetyMode.NONE) {
    quickJs.get(
      name = "app_cash_quickjs_inboundBridge",
      type = InternalBridge::class.java
    )
  }

  init {
    // Eagerly publish the bridge so they can call us.
    quickJs.set(
      name = "app_cash_quickjs_outboundBridge",
      type = InternalBridge::class.java,
      instance = ktBridge.inboundBridge
    )

    // Connect platforms using our newly-bootstrapped bridges.
    ktBridge.set<HostPlatform>(
      name = "app.cash.quickjs.hostPlatform",
      jsAdapter = BuiltInJsAdapter,
      instance = this
    )
    jsPlatform = ktBridge.get(
      name = "app.cash.quickjs.jsPlatform",
      jsAdapter = BuiltInJsAdapter
    )
  }

  override val engineVersion: String
    get() = quickJsVersion

  override fun invoke(
    instanceName: String,
    funName: String,
    encodedArguments: ByteArray
  ): ByteArray {
    return jsInboundBridge.invoke(instanceName, funName, encodedArguments)
  }

  override fun invokeSuspending(
    instanceName: String,
    funName: String,
    encodedArguments: ByteArray,
    callbackName: String
  ) {
    return jsInboundBridge.invokeSuspending(instanceName, funName, encodedArguments, callbackName)
  }

  override fun <T : Any> get(
    name: String,
    outboundClientFactory: OutboundClientFactory<T>
  ): T {
    return ktBridge.get(name, outboundClientFactory)
  }

  override fun set(name: String, handler: InboundService<*>) {
    ktBridge.set(name, handler)
  }

  override fun setTimeout(timeoutId: Int, delayMillis: Int) {
    CoroutineScope(EmptyCoroutineContext).launch(dispatcher) {
      delay(delayMillis.toLong())
      jsPlatform.runJob(timeoutId)
    }
  }

  override fun consoleMessage(level: String, message: String) {
    when (level) {
      "warn" -> logger.warning(message)
      "error" -> logger.severe(message)
      else -> logger.info(message)
    }
  }
}
