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

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

// TODO(jwilson): merge with QuickJs?
class KtBridge internal constructor(
  private val outboundBridge: InternalBridge
) {
  private val inboundHandlers = mutableMapOf<String, InboundService<*>>()
  private var nextId = 1

  internal val inboundBridge = object : InternalBridge {
    override fun invoke(
      instanceName: String,
      funName: String,
      encodedArguments: ByteArray
    ): ByteArray {
      val handler = inboundHandlers[instanceName] ?: error("no handler for $instanceName")
      return handler.call(InboundCall(funName, encodedArguments, handler.jsAdapter))
    }

    override fun invokeSuspending(
      instanceName: String,
      funName: String,
      encodedArguments: ByteArray,
      callbackName: String
    ) {
      val handler = inboundHandlers[instanceName] ?: error("no handler for $instanceName")
      // TODO(jwilson): confirm how we bootstrap a coroutine on an inbound call.
      GlobalScope.launch {
        val result = handler.callSuspending(
          InboundCall(funName, encodedArguments, handler.jsAdapter)
        )
        val callback = get<SuspendCallback>(callbackName, BuiltInJsAdapter)
        callback.success(result)
      }
    }
  }

  fun <T : Any> set(name: String, jsAdapter: JsAdapter, instance: T) {
    error("unexpected call to KtBridge.set: is KtBridge plugin configured?")
  }

  @PublishedApi
  internal fun set(name: String, handler: InboundService<*>) {
    inboundHandlers[name] = handler
  }

  @PublishedApi
  internal fun remove(name: String) {
    val removed = inboundHandlers.remove(name)
    require(removed != null) { "unable to find $name: was it removed twice?" }
  }

  fun <T : Any> get(name: String, jsAdapter: JsAdapter): T {
    error("unexpected call to KtBridge.get: is KtBridge plugin configured?")
  }

  @PublishedApi
  internal fun <T : Any> get(
    name: String,
    outboundClientFactory: OutboundClientFactory<T>
  ): T {
    return outboundClientFactory.create(
        OutboundCall.Factory(name, outboundClientFactory.jsAdapter, this, outboundBridge)
    )
  }

  internal fun generateName(): String {
    return "app.cash.quickjs.${nextId++}"
  }
}
