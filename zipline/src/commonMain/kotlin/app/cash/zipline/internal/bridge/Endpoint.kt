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
package app.cash.zipline.internal.bridge

import app.cash.zipline.Handle
import app.cash.zipline.InboundHandle
import app.cash.zipline.OutboundHandle
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * An outbound channel for delivering calls to the other platform, and an inbound channel for
 * receiving calls from the other platform.
 */
class Endpoint internal constructor(
  private val dispatcher: CoroutineDispatcher,
  internal val outboundChannel: CallChannel,
) {
  internal val inboundHandlers = mutableMapOf<String, InboundCallHandler>()
  private var nextId = 1

  /**
   * Installed by [InboundBridge] and [OutboundBridge] to add serializers required by core types
   * like [Throwable].
   */
  internal val builtInSerializersModule = SerializersModule {
    contextual(Throwable::class, ThrowableSerializer)
    contextual(Handle::class) { HandleSerializer<Any>(this@Endpoint) }
  }

  internal val inboundChannel = object : CallChannel {
    override fun invoke(
      instanceName: String,
      funName: String,
      encodedArguments: ByteArray
    ): ByteArray {
      val handler = inboundHandlers[instanceName] ?: error("no handler for $instanceName")
      val inboundCall = InboundCall(handler.context, funName, encodedArguments)
      return try {
        handler.call(inboundCall)
      } catch (e: Throwable) {
        inboundCall.resultException(e)
      }
    }

    override fun invokeSuspending(
      instanceName: String,
      funName: String,
      encodedArguments: ByteArray,
      callbackName: String
    ) {
      val handler = inboundHandlers[instanceName] ?: error("no handler for $instanceName")
      CoroutineScope(EmptyCoroutineContext).launch(dispatcher) {
        val callback = get<SuspendCallback>(callbackName, EmptySerializersModule)
        val inboundCall = InboundCall(handler.context, funName, encodedArguments)
        val result = try {
          handler.callSuspending(inboundCall)
        } catch (e: Exception) {
          inboundCall.resultException(e)
        }
        callback.call(result)
      }
    }
  }

  fun <T : Any> set(name: String, serializersModule: SerializersModule, instance: T) {
    error("unexpected call to Zipline.set: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : Any> set(name: String, inboundBridge: InboundBridge<T>) {
    val handle = InboundHandle(inboundBridge)
    handle.connect(this, name)
  }

  fun <T : Any> get(name: String, serializersModule: SerializersModule): T {
    error("unexpected call to Zipline.get: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : Any> get(
    name: String,
    outboundBridge: OutboundBridge<T>
  ): T {
    val handle = OutboundHandle<T>()
    handle.connect(this, name)
    return handle.get(outboundBridge)
  }

  internal fun generateName(): String {
    return "app.cash.zipline.${nextId++}"
  }
}
