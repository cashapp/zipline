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
import app.cash.zipline.ZiplineApiMismatchException
import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.encodeToStringFast
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Inbound calls use this to call into the real service.
 */
internal class InboundService<T : ZiplineService>(
  internal val service: T,
  private val endpoint: Endpoint,
  functionsList: List<ZiplineFunction<T>>,
) {
  val functions: Map<String, ZiplineFunction<T>> = functionsList.associateBy { it.name }

  fun call(
    internalCall: InternalCall,
    externalCall: Call,
  ): String {
    val function = internalCall.function as ZiplineFunction<ZiplineService>?
      ?: return endpoint.callCodec.encodeFailure(unexpectedFunction(internalCall.functionName))

    // Removes the handler in calls to [ZiplineService.close]. We remove before dispatching so
    // it'll always be removed even if the call stalls or throws.
    if (function.isClose) {
      endpoint.inboundServices.remove(internalCall.serviceName)
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

  fun callSuspending(
    internalCall: InternalCall,
    externalCall: Call,
    suspendCallback: SuspendCallback<Any?>,
  ): String {
    val job = endpoint.scope.launch {
      val function = internalCall.function as ZiplineFunction<ZiplineService>?
      val args = internalCall.args

      val result: Result<Any?>
      if (function == null) {
        result = Result.failure(unexpectedFunction(internalCall.functionName))
      } else {
        val callStart = endpoint.eventListener.callStart(externalCall)
        result = runCatching {
          function.callSuspending(service, args)
        }
        endpoint.callCodec.nextOutboundCallCallback = { callbackCall ->
          endpoint.eventListener.callEnd(
            externalCall,
            CallResult(result, callbackCall.encodedCall, callbackCall.serviceNames),
            callStart,
          )
        }
      }

      // Don't resume a continuation if the Zipline has since been closed.
      endpoint.scope.ensureActive()

      when {
        result.isSuccess -> suspendCallback.success(result.getOrNull())
        else -> suspendCallback.failure(result.exceptionOrNull()!!)
      }
    }

    val cancelCallback = object : CancelCallback {
      override fun cancel() {
        job.cancel()
      }
    }
    job.invokeOnCompletion {
      endpoint.remove(cancelCallback)
    }

    return endpoint.json.encodeToStringFast(cancelCallbackSerializer, cancelCallback)
  }

  private fun unexpectedFunction(functionName: String?) = ZiplineApiMismatchException(
    buildString {
      appendLine("no such method (incompatible API versions?)")
      appendLine("\tcalled:")
      append("\t\t")
      appendLine(functionName)
      appendLine("\tavailable:")
      functions.keys.joinTo(this, separator = "\n") { "\t\t$it" }
    }
  )
}
