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

import app.cash.zipline.ZiplineApiMismatchException
import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.encodeToStringFast
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

/**
 * Inbound calls use this to call into the real service.
 */
internal class InboundService<T : ZiplineService>(
  internal val service: T,
  private val endpoint: Endpoint,
  functionsList: List<ZiplineFunction<T>>,
) {
  val functions: Map<String, ZiplineFunction<T>> = functionsList.associateBy { it.name }

  fun call(call: RealCall): String {
    val function = call.function as ZiplineFunction<ZiplineService>?

    val result: Result<Any?> = when {
      function == null -> {
        Result.failure(unexpectedFunction(call.functionName))
      }
      else -> {
        // Removes the handler in calls to [ZiplineService.close]. We remove before dispatching so
        // it'll always be removed even if the call stalls or throws.
        if (function.isClose) {
          endpoint.inboundServices.remove(call.serviceName)
        }

        val callStart = endpoint.eventListener.callStart(call)
        val theResult = try {
          val success = function.call(service, call.args)
          Result.success(success)
        } catch (e: Throwable) {
          Result.failure(e)
        }
        endpoint.eventListener.callEnd(call, theResult, callStart)
        theResult
      }
    }

    return endpoint.json.encodeToStringFast(
      (call.function?.callResultSerializer ?: failureResultSerializer) as ResultSerializer<Any?>,
      result,
    )
  }

  fun callSuspending(call: RealCall): String {
    val suspendCallbackName = call.callbackName!!
    val job = endpoint.scope.launch {
      val function = call.function as ZiplineFunction<ZiplineService>?
      val args = call.args

      val result: Result<Any?> = when {
        function == null -> {
          Result.failure(unexpectedFunction(call.functionName))
        }
        else -> {
          val callStart = endpoint.eventListener.callStart(call)
          val theResult = try {
            val success = function.callSuspending(service, args)
            Result.success(success)
          } catch (e: Throwable) {
            Result.failure(e)
          }
          endpoint.eventListener.callEnd(call, theResult, callStart)
          theResult
        }
      }

      val encodedResult = endpoint.json.encodeToStringFast(
        (call.function?.callResultSerializer ?: failureResultSerializer) as KSerializer<Any?>,
        result,
      )

      endpoint.scope.ensureActive() // Don't resume a continuation if the Zipline has since been closed.
      val suspendCallback = endpoint.take<SuspendCallback>(suspendCallbackName)
      suspendCallback.call(encodedResult)
    }

    val cancelCallbackName = endpoint.cancelCallbackName(suspendCallbackName)
    endpoint.bind<CancelCallback>(cancelCallbackName, object : CancelCallback {
      override fun cancel() {
        job.cancel()
      }
    })
    job.invokeOnCompletion {
      endpoint.remove(cancelCallbackName)
    }

    return ""
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
