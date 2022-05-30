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
package app.cash.zipline.internal.bridge

import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.decodeFromStringFast
import app.cash.zipline.internal.encodeToStringFast
import app.cash.zipline.internal.ziplineInternalPrefix
import kotlin.coroutines.Continuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json

/**
 * Generated code uses this to make outbound calls.
 */
@PublishedApi
internal class OutboundCallHandler(
  private val serviceName: String,
  val json: Json,
  @PublishedApi internal val endpoint: Endpoint,
  private val ziplineFunctions: List<ZiplineFunction<*>>,
) {
  var closed = false

  fun call(
    service: ZiplineService,
    functionIndex: Int,
    vararg args: Any?,
  ): Any? {
    val function = ziplineFunctions[functionIndex]
    val argsList = args.toList()
    val call = InternalCall(
      serviceName = serviceName,
      inboundService = null,
      functionName = function.name,
      function = function,
      callbackName = null,
      args = argsList
    )
    val encodedCall = json.encodeToStringFast(endpoint.callSerializer, call)

    val callStart = endpoint.eventListener.callStart(serviceName, service, function.name, argsList)
    val encodedResult = endpoint.outboundChannel.call(arrayOf(encodedCall))

    val result = json.decodeFromStringFast(function.callResultSerializer, encodedResult.single())
    endpoint.eventListener.callEnd(
      serviceName,
      service,
      function.name,
      argsList,
      result,
      callStart
    )
    return result.getOrThrow()
  }

  suspend fun callSuspending(
    service: ZiplineService,
    functionIndex: Int,
    vararg args: Any?,
  ): Any? {
    val function = ziplineFunctions[functionIndex]
    val argsList = args.toList()
    val callbackName = endpoint.generateName(prefix = ziplineInternalPrefix)
    val call = InternalCall(
      serviceName = serviceName,
      inboundService = null,
      functionName = function.name,
      function = function,
      callbackName = callbackName,
      args = argsList
    )
    val encodedCall = json.encodeToStringFast(endpoint.callSerializer, call)
    val callStartResult =
      endpoint.eventListener.callStart(serviceName, service, function.name, argsList)
    return suspendCancellableCoroutine { continuation ->
      endpoint.incompleteContinuations += continuation
      endpoint.scope.launch {
        val suspendCallback = RealSuspendCallback(
          call = call,
          service = service,
          continuation = continuation,
          callStartResult = callStartResult,
        )
        endpoint.bind<SuspendCallback>(callbackName, suspendCallback)

        continuation.invokeOnCancellation {
          if (suspendCallback.completed) return@invokeOnCancellation
          val cancelCallbackName = endpoint.cancelCallbackName(callbackName)
          val cancelCallback = endpoint.take<CancelCallback>(cancelCallbackName)
          cancelCallback.cancel()
        }

        endpoint.outboundChannel.call(arrayOf(encodedCall))
      }
    }
  }

  private inner class RealSuspendCallback<R>(
    val call: InternalCall,
    val service: ZiplineService,
    val continuation: Continuation<R>,
    val callStartResult: Any?,
  ) : SuspendCallback {
    /** True once this has been called. Used to prevent cancel-after-complete. */
    var completed = false

    override fun call(response: String) {
      completed = true
      // Suspend callbacks are one-shot. When triggered, remove them immediately.
      endpoint.remove(call.callbackName!!)
      val result = json.decodeFromStringFast(
        call.function!!.callResultSerializer as ResultSerializer<R>,
        response
      )
      endpoint.incompleteContinuations -= continuation
      endpoint.eventListener.callEnd(
        name = serviceName,
        service = service,
        functionName = call.function.name,
        args = call.args,
        result = result,
        callStartResult = callStartResult
      )
      continuation.resumeWith(result)
    }
  }
}
