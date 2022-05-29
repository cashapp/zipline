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

import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.decodeFromStringFast
import app.cash.zipline.internal.encodeToStringFast
import app.cash.zipline.internal.ziplineInternalPrefix
import kotlin.coroutines.Continuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Generated code extends this base class to make calls into an application-layer interface that is
 * implemented by another platform in the same process.
 */
@PublishedApi
internal interface OutboundBridge {
  class Context(
    private val instanceName: String,
    val json: Json,
    @PublishedApi internal val endpoint: Endpoint,
    private val ziplineFunctions: List<ZiplineFunction<*>>,
  ) {
    val serializersModule = json.serializersModule
    var closed = false

    private fun newCall(funName: String, parameterCount: Int): OutboundCall {
      return OutboundCall(
        this,
        instanceName,
        endpoint,
        funName,
        parameterCount
      )
    }

    fun call(
      service: ZiplineService,
      functionIndex: Int,
      vararg args: Any?,
    ): Any? {
      val function = ziplineFunctions[functionIndex]
      val call = newCall(function.name, function.argSerializers.size)
      for (i in function.argSerializers.indices) {
        call.parameter(function.argSerializers[i] as KSerializer<Any?>, args[i])
      }
      return call.invoke(service, function.resultSerializer)
    }

    suspend fun callSuspending(
      service: ZiplineService,
      functionIndex: Int,
      vararg args: Any?,
    ): Any? {
      val function = ziplineFunctions[functionIndex]
      val call = newCall(function.name, function.argSerializers.size)
      for (i in function.argSerializers.indices) {
        call.parameter(function.argSerializers[i] as KSerializer<Any?>, args[i])
      }
      return call.invokeSuspending(service, function.resultSerializer)
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
  private val arguments = ArrayList<Any?>(parameterCount)
  private val encodedArguments = ArrayList<String>(6 + (parameterCount * 2))
  private var callCount = 0

  init {
    encodedArguments += LABEL_SERVICE_NAME
    encodedArguments += instanceName
    encodedArguments += LABEL_FUN_NAME
    encodedArguments += funName
    encodedArguments += LABEL_CALLBACK_NAME
    encodedArguments += ""
  }

  fun <T> parameter(serializer: KSerializer<T>, value: T) {
    require(callCount++ < parameterCount)
    arguments += value
    if (value == null) {
      encodedArguments += LABEL_NULL
      encodedArguments += ""
    } else {
      encodedArguments += LABEL_VALUE
      encodedArguments += context.json.encodeToStringFast(serializer, value)
    }
  }

  fun <R> invoke(service: ZiplineService, serializer: KSerializer<R>): R {
    require(callCount++ == parameterCount)
    val callStartResult = endpoint.eventListener.callStart(instanceName, service, funName, arguments)
    val encodedResult = endpoint.outboundChannel.call(encodedArguments.toTypedArray())

    val result = encodedResult.decodeResult(serializer)
    endpoint.eventListener.callEnd(instanceName, service, funName, arguments, result, callStartResult)
    return result.getOrThrow()
  }

  @PublishedApi
  internal suspend fun <R> invokeSuspending(
    service: ZiplineService,
    serializer: KSerializer<R>,
  ): R {
    require(callCount++ == parameterCount)
    val callStartResult = endpoint.eventListener.callStart(instanceName, service, funName, arguments)
    return suspendCancellableCoroutine { continuation ->
      context.endpoint.incompleteContinuations += continuation
      context.endpoint.scope.launch {
        val callbackName = endpoint.generateName(prefix = ziplineInternalPrefix)
        encodedArguments[encodedArguments.indexOf(LABEL_CALLBACK_NAME) + 1] = callbackName

        val suspendCallback = RealSuspendCallback(
          service = service,
          suspendCallbackName = callbackName,
          continuation = continuation,
          serializer = serializer,
          callStartResult = callStartResult,
        )
        endpoint.bind<SuspendCallback>(callbackName, suspendCallback)

        continuation.invokeOnCancellation {
          if (suspendCallback.completed) return@invokeOnCancellation
          val cancelCallbackName = endpoint.cancelCallbackName(callbackName)
          val cancelCallback = endpoint.take<CancelCallback>(cancelCallbackName)
          cancelCallback.cancel()
        }

        endpoint.outboundChannel.call(encodedArguments.toTypedArray())
      }
    }
  }

  private inner class RealSuspendCallback<R>(
    val service: ZiplineService,
    val suspendCallbackName: String,
    val continuation: Continuation<R>,
    val serializer: KSerializer<R>,
    val callStartResult: Any?,
  ) : SuspendCallback {
    /** True once this has been called. Used to prevent cancel-after-complete. */
    var completed = false

    override fun call(response: Array<String>) {
      completed = true
      // Suspend callbacks are one-shot. When triggered, remove them immediately.
      endpoint.remove(suspendCallbackName)
      val result = response.decodeResult(serializer)
      context.endpoint.incompleteContinuations -= continuation
      context.endpoint.eventListener.callEnd(
        name = instanceName,
        service = service,
        functionName = funName,
        args = arguments,
        result = result,
        callStartResult = callStartResult
      )
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
          return Result.success(context.json.decodeFromStringFast(serializer, this[1]))
        }
        LABEL_EXCEPTION -> {
          return Result.failure(context.json.decodeFromStringFast(ThrowableSerializer, this[1]))
        }
        else -> i += 2
      }
    }
    throw IllegalStateException("no result")
  }
}
