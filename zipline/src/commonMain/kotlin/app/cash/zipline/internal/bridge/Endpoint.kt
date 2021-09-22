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

import app.cash.zipline.InboundZiplineReference
import app.cash.zipline.OutboundZiplineReference
import app.cash.zipline.ZiplineReference
import app.cash.zipline.ZiplineSerializer
import app.cash.zipline.ZiplineSerializerSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * An outbound channel for delivering calls to the other platform, and an inbound channel for
 * receiving calls from the other platform.
 */
class Endpoint internal constructor(
  internal val scope: CoroutineScope,
  internal val outboundChannel: CallChannel,
) {
  internal val inboundHandlers = mutableMapOf<String, InboundCallHandler>()
  private var nextId = 1

  val serviceNames: Set<String>
    get() = inboundHandlers.keys.toSet()

  val clientNames: Set<String>
    get() = outboundChannel.serviceNamesArray().toSet()

  /** If null, the user must still call Zipline.get() on Kotlin/JS. */
  internal var userSerializersModule: SerializersModule? = null
    set(value) {
      field = value
      serializersModule = computeSerializersModule()
    }

  /** Unions Zipline-provided serializers with user-provided serializers. */
  internal var serializersModule: SerializersModule = computeSerializersModule()

  private fun computeSerializersModule(): SerializersModule {
    return SerializersModule {
      contextual(Throwable::class, ThrowableSerializer)
      contextual(ZiplineReference::class) { ZiplineReferenceSerializer<Any>(this@Endpoint) }
      contextual(ZiplineSerializer::class) {
        ZiplineSerializerSerializer(
          endpoint = this@Endpoint,
          delegate = it[0] as KSerializer<Any>
        )
      }
      include(userSerializersModule ?: EmptySerializersModule)
    }
  }

  internal val inboundChannel = object : CallChannel {
    override fun serviceNamesArray(): Array<String> {
      return serviceNames.toTypedArray()
    }

    override fun invoke(
      instanceName: String,
      funName: String,
      encodedArguments: Array<String>
    ): Array<String> {
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
      encodedArguments: Array<String>,
      callbackName: String
    ) {
      val handler = inboundHandlers[instanceName] ?: error("no handler for $instanceName")
      scope.launch {
        val callback = get<SuspendCallback>(callbackName)
        val inboundCall = InboundCall(handler.context, funName, encodedArguments)
        val result = try {
          handler.callSuspending(inboundCall)
        } catch (e: Exception) {
          inboundCall.resultException(e)
        }
        callback.call(result)
      }
    }

    override fun disconnect(instanceName: String): Boolean {
      return inboundHandlers.remove(instanceName) != null
    }
  }

  fun <T : Any> set(name: String, instance: T) {
    error("unexpected call to Zipline.set: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : Any> set(name: String, inboundBridge: InboundBridge<T>) {
    val reference = InboundZiplineReference(inboundBridge)
    reference.connect(this, name)
  }

  fun <T : Any> get(name: String): T {
    error("unexpected call to Zipline.get: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : Any> get(name: String, outboundBridge: OutboundBridge<T>): T {
    val reference = OutboundZiplineReference<T>()
    reference.connect(this, name)
    return reference.get(outboundBridge)
  }

  internal fun generateName(): String {
    return "zipline/${nextId++}"
  }
}
