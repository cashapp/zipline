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

/**
 * Generated code uses this to make outbound calls.
 */
@PublishedApi
internal class OutboundCallHandler(
  private val serviceName: String,
  private val endpoint: Endpoint,
  private val functionsList: List<ZiplineFunction<*>>,
) {
  /** Used by generated code when closing a service. */
  var closed = false

  /** Used by generated code to call a function. */
  fun call(
    service: ZiplineService,
    functionIndex: Int,
    vararg args: Any?,
  ): Any? {
    val function = functionsList[functionIndex]
    val argsList = args.toList()
    val call = RealCall(
      serviceName = serviceName,
      serviceOrNull = service,
      functionName = function.name,
      function = function,
      args = argsList
    )
    val encodedCall = endpoint.json.encodeToStringFast(endpoint.callSerializer, call)

    val callStart = endpoint.eventListener.callStart(call)
    val encodedResult = endpoint.outboundChannel.call(encodedCall)

    val result = endpoint.json.decodeFromStringFast(function.callResultSerializer, encodedResult)
    endpoint.eventListener.callEnd(call, result, callStart)
    return result.getOrThrow()
  }

  /** Used by generated code to call a suspending function. */
  suspend fun callSuspending(
    service: ZiplineService,
    functionIndex: Int,
    vararg args: Any?,
  ): Any? {
    val function = functionsList[functionIndex]
    val argsList = args.toList()
    val callbackName = endpoint.generateName(prefix = ziplineInternalPrefix)
    val call = RealCall(
      serviceName = serviceName,
      serviceOrNull = service,
      functionName = function.name,
      function = function,
      callbackName = callbackName,
      args = argsList
    )
    val encodedCall = endpoint.json.encodeToStringFast(endpoint.callSerializer, call)
    val callStart = endpoint.eventListener.callStart(call)
    return suspendCancellableCoroutine { continuation ->
      endpoint.incompleteContinuations += continuation
      endpoint.scope.launch {
        val suspendCallback = RealSuspendCallback(call, continuation, callStart)
        endpoint.bind<SuspendCallback>(callbackName, suspendCallback)

        continuation.invokeOnCancellation {
          if (suspendCallback.completed) return@invokeOnCancellation
          val cancelCallbackName = endpoint.cancelCallbackName(callbackName)
          val cancelCallback = endpoint.take<CancelCallback>(cancelCallbackName)
          cancelCallback.cancel()
        }

        endpoint.outboundChannel.call(encodedCall)
      }
    }
  }

  private inner class RealSuspendCallback<R>(
    val call: RealCall,
    val continuation: Continuation<R>,
    val callStart: Any?,
  ) : SuspendCallback {
    /** True once this has been called. Used to prevent cancel-after-complete. */
    var completed = false

    override fun call(response: String) {
      completed = true
      // Suspend callbacks are one-shot. When triggered, remove them immediately.
      endpoint.remove(call.callbackName!!)
      val result = endpoint.json.decodeFromStringFast(
        call.function!!.callResultSerializer as ResultSerializer<R>,
        response
      )
      endpoint.incompleteContinuations -= continuation
      endpoint.eventListener.callEnd(call, result, callStart)
      continuation.resumeWith(result)
    }
  }
}
