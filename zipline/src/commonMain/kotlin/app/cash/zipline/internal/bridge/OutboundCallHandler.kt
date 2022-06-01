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

    val result = endpoint.json.decodeFromStringFast(function.kotlinResultSerializer, encodedResult)
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
    val suspendCallback = RealSuspendCallback<Any?>()
    val call = RealCall(
      serviceName = serviceName,
      serviceOrNull = service,
      functionName = function.name,
      function = function,
      suspendCallback = suspendCallback,
      args = argsList,
    )
    suspendCallback.call = call
    val encodedCall = endpoint.json.encodeToStringFast(endpoint.callSerializer, call)
    suspendCallback.callStart = endpoint.eventListener.callStart(call)

    return suspendCancellableCoroutine { continuation ->
      suspendCallback.continuation = continuation
      endpoint.incompleteContinuations += continuation
      endpoint.scope.launch {
        val encodedCancelCallback = endpoint.outboundChannel.call(encodedCall)
        val cancelCallback = endpoint.json.decodeFromStringFast(
          cancelCallbackSerializer,
          encodedCancelCallback,
        )

        continuation.invokeOnCancellation {
          // TODO(jwilson): post this work to the zipline dispatcher
          if (suspendCallback.completed) return@invokeOnCancellation
          cancelCallback.cancel()
        }
      }
    }
  }

  private inner class RealSuspendCallback<R> : SuspendCallback<R> {
    lateinit var call: RealCall
    lateinit var continuation: Continuation<R>
    var callStart: Any? = null

    /** True once this has been called. Used to prevent cancel-after-complete. */
    var completed = false

    override fun success(result: R) {
      call(Result.success(result))
    }

    override fun failure(result: Throwable) {
      call(Result.failure(result))
    }

    private fun call(result: Result<R>) {
      completed = true
      // Suspend callbacks are one-shot. When triggered, remove them immediately.
      endpoint.remove(this@RealSuspendCallback)
      endpoint.incompleteContinuations -= continuation
      endpoint.eventListener.callEnd(call, result, callStart)
      continuation.resumeWith(result)
    }
  }
}
