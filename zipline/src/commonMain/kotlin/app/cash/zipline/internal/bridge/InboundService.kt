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

import app.cash.zipline.Call
import app.cash.zipline.CallResult
import app.cash.zipline.ZiplineService
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive

/**
 * Inbound calls use this to call into the real service.
 */
internal class InboundService<T : ZiplineService>(
  internal val type: RealZiplineServiceType<T>,
  internal val service: T,
  private val endpoint: Endpoint,
) {
  fun call(
    internalCall: InternalCall,
    externalCall: Call,
  ): String {
    @Suppress("UNCHECKED_CAST") // We found this function by matching on its service type T.
    val function = internalCall.function as ReturningZiplineFunction<ZiplineService>

    // Removes the handler in calls to [ZiplineService.close]. We remove before dispatching so
    // it'll always be removed even if the call stalls or throws.
    if (function.isClose) {
      endpoint.remove(internalCall.serviceName)
    }

    val callStart = when (externalCall.service) {
      !is SuspendCallback<*> -> endpoint.eventListener.callStart(externalCall)
      else -> Unit // Don't call callStart() for suspend callbacks.
    }

    val theResult = runCatching {
      function.call(service, internalCall.args)
    }

    val callResult = endpoint.callCodec.encodeResult(function, theResult)
    when (externalCall.service) {
      !is SuspendCallback<*> -> endpoint.eventListener.callEnd(externalCall, callResult, callStart)
      else -> Unit // Don't call callEnd() for suspend callbacks.
    }
    return callResult.encodedResult
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun callSuspending(
    internalCall: InternalCall,
    externalCall: Call,
    suspendCallback: SuspendCallback<Any?>,
  ): String {
    @Suppress("UNCHECKED_CAST") // We found this function by matching on its service type T.
    val function = internalCall.function as SuspendingZiplineFunction<ZiplineService>

    val callStart = endpoint.eventListener.callStart(externalCall)

    val deferred = endpoint.scope.async(start = UNDISPATCHED) {
      val args = internalCall.args
      runCatching {
        function.callSuspending(service, args)
      }
    }

    // If the deferred is still active, that means the call suspended. Prepare a cancel callback
    // that can cancel the coroutine and return that.
    if (deferred.isActive) {
      val cancelCallback = object : CancelCallback, HasPassByReferenceName {
        override var passByReferenceName: String? = null

        override fun cancel() {
          deferred.cancel()
        }

        override fun toString() = "CancelCallback/$internalCall"
      }

      deferred.invokeOnCompletion {
        val name = cancelCallback.passByReferenceName
        if (name != null) endpoint.remove(name)

        val failure = deferred.getCompletionExceptionOrNull()
        val kotlinResult = when {
          failure != null -> Result.failure(failure)
          else -> deferred.getCompleted()
        }

        // Don't resume a continuation if the Zipline has since been closed.
        if (!endpoint.scope.isActive) return@invokeOnCompletion

        endpoint.callCodec.nextOutboundCallCallback = { callbackCall ->
          endpoint.eventListener.callEnd(
            externalCall,
            CallResult(kotlinResult, callbackCall.encodedCall, callbackCall.serviceNames),
            callStart,
          )
        }

        when {
          kotlinResult.isFailure -> suspendCallback.failure(kotlinResult.exceptionOrNull()!!)
          else -> suspendCallback.success(kotlinResult.getOrNull())
        }
      }

      val encodedResultOrCallback = endpoint.callCodec.encodeResultOrCallback(
        function,
        ResultOrCallback<Unit>(callback = cancelCallback),
      )
      return encodedResultOrCallback.json
    }

    // The function returned without suspending. Convert our Deferred<Result<T>> into a Result<T>,
    // which will be failure if _either_ the Deferred failed or if it contains a failure Result.
    val failure = deferred.getCompletionExceptionOrNull()
    val result = when {
      failure != null -> Result.failure(failure)
      else -> deferred.getCompleted()
    }

    val encodedResultOrCallback = endpoint.callCodec.encodeResultOrCallback(
      function,
      ResultOrCallback(result = result),
    )

    endpoint.eventListener.callEnd(
      externalCall,
      encodedResultOrCallback.callResult!!,
      callStart,
    )

    return encodedResultOrCallback.json
  }

  override fun toString() = service.toString()
}
