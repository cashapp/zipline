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

actual abstract class QuickJs {
  actual abstract val engineVersion: String

  actual fun <T : Any> get(name: String, jsAdapter: JsAdapter): T {
    error("unexpected call to QuickJs.get: is QuickJs Gradle plugin configured?")
  }
  actual fun <T : Any> set(name: String, jsAdapter: JsAdapter, instance: T) {
    error("unexpected call to QuickJs.set: is QuickJs Gradle plugin configured?")
  }

  @PublishedApi
  internal abstract fun set(name: String, handler: InboundService<*>)

  @PublishedApi
  internal abstract fun <T : Any> get(
    name: String,
    outboundClientFactory: OutboundClientFactory<T>,
  ): T

  companion object : QuickJs() {
    override val engineVersion get() = quickJsVersion

    private val outboundBridge: InternalBridge
      get() = js("globalThis").app_cash_quickjs_outboundBridge

    private val inboundHandlers = mutableMapOf<String, InboundService<*>>()
    internal val inboundBridge = object : InternalBridge {
      override fun invokeJs(
        instanceName: String,
        funName: String,
        encodedArguments: ByteArray
      ): ByteArray {
        val handler = inboundHandlers[instanceName] ?: error("no handler for $instanceName")
        return handler.call(InboundCall(funName, encodedArguments, handler.jsAdapter))
      }
    }

    override fun <T : Any> get(name: String, outboundClientFactory: OutboundClientFactory<T>): T {
      return outboundClientFactory.create(
          OutboundCall.Factory(name, outboundClientFactory.jsAdapter, outboundBridge))
    }

    override fun set(name: String, handler: InboundService<*>) {
      inboundHandlers[name] = handler
    }
  }
}

fun main() {
  js("globalThis").app_cash_quickjs_inboundBridge = QuickJs.inboundBridge
}
