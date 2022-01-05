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

import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * Generated code extends this base class to make calls into an application-layer interface that is
 * implemented by another platform in the same process.
 */
@PublishedApi
internal interface OutboundBridge {
  class Context(
    private val instanceName: String,
    val serializersModule: SerializersModule,
    @PublishedApi internal val endpoint: Endpoint,
  ) {
    val json = Json {
      useArrayPolymorphism = true
      serializersModule = this@Context.serializersModule
    }

    fun newCall(funName: String, parameterCount: Int): OutboundCall {
      return OutboundCall(
        this,
        instanceName,
        endpoint,
        funName,
        parameterCount
      )
    }
  }
}

/**
 * This class models a single call sent to another Kotlin platform in the same process.
 *
 * It should be used to help implement an application-layer interface that is implemented by the
 * other platform. Implement each function in that interface to create an [OutboundCall] by calling
 * [Factory.create], pass in each received argument to [parameter], and then call [invoke] to
 * perform the cross-platform call.
 */
@PublishedApi
internal class OutboundCall(
  private val context: OutboundBridge.Context,
  private val instanceName: String,
  private val endpoint: Endpoint,
  private val funName: String,
  private val parameterCount: Int,
) {
  private val arguments = ArrayList<String>(parameterCount * 2)
  private var callCount = 0

  fun <T> parameter(serializer: KSerializer<T>, value: T) {
    require(callCount++ < parameterCount)
    if (value == null) {
      arguments += LABEL_NULL
      arguments += ""
    } else {
      arguments += LABEL_VALUE
      arguments += context.json.encodeToString(serializer, value)
    }
  }

  fun <R> invoke(serializer: KSerializer<R>): R {
    require(callCount++ == parameterCount)
    val encodedResult = endpoint.outboundChannel.invoke(
      instanceName,
      funName,
      arguments.toTypedArray()
    )

    return encodedResult.decodeResult(serializer).getOrThrow()
  }

  @PublishedApi
  internal suspend fun <R> invokeSuspending(serializer: KSerializer<R>): R {
    require(callCount++ == parameterCount)
    return suspendCoroutine { continuation ->
      context.endpoint.incompleteContinuations += continuation
      context.endpoint.scope.launch {
        val callbackName = endpoint.generateName()
        val callback = RealSuspendCallback(callbackName, continuation, serializer)
        endpoint.set<SuspendCallback>(callbackName, callback)
        endpoint.outboundChannel.invokeSuspending(
          instanceName,
          funName,
          arguments.toTypedArray(),
          callbackName
        )
      }
    }
  }

  private inner class RealSuspendCallback<R>(
    val callbackName: String,
    val continuation: Continuation<R>,
    val serializer: KSerializer<R>
  ) : SuspendCallback {
    override fun call(response: Array<String>) {
      // Suspend callbacks are one-shot. When triggered, remove them immediately.
      endpoint.remove(callbackName)
      val result = response.decodeResult(serializer)
      context.endpoint.incompleteContinuations -= continuation
      continuation.resumeWith(result)
    }
  }

  private fun <R> Array<String>.decodeResult(serializer: KSerializer<R>): Result<R> {
    var i = 0
    while (i < size) {
      when (this[0]) {
        LABEL_NULL -> {
          return Result.success(null as R)
        }
        LABEL_VALUE -> {
          return Result.success(context.json.decodeFromString(serializer, this[1]))
        }
        LABEL_EXCEPTION -> {
          return Result.failure(context.json.decodeFromString(ThrowableSerializer, this[1]))
        }
        else -> i += 2
      }
    }
    throw IllegalStateException("no result")
  }
}
