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
import app.cash.zipline.ZiplineFunction
import app.cash.zipline.ZiplineScope
import app.cash.zipline.ZiplineScoped
import app.cash.zipline.ZiplineService
import app.cash.zipline.ZiplineServiceType
import kotlin.coroutines.Continuation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer

/**
 * Generated code uses this to make outbound calls.
 */
@PublishedApi
internal class OutboundCallHandler(
  val sourceType: ZiplineServiceType<*>,
  private val serviceName: String,
  private val endpoint: Endpoint,
  private val adapter: ZiplineServiceAdapter<*>,
  internal val scope: ZiplineScope,
  internal val serviceState: ServiceState = ServiceState(),
) {
  /**
   * Returns a copy of this call handler that targets the same service, but that applies [scope] to
   * outbound services produced by this service.
   */
  fun withScope(scope: ZiplineScope): OutboundCallHandler {
    return OutboundCallHandler(
      sourceType,
      serviceName,
      endpoint,
      adapter,
      scope,
      serviceState,
    )
  }

  /** Returns the type of this service as reported by the opposite endpoint. */
  val targetType: SerializableZiplineServiceType?
    get() = endpoint.opposite.serviceType(serviceName)

  /** Returns a new service that uses this to send calls. */
  fun <T : ZiplineService> outboundService(): T {
    return adapter.outboundService(this) as T
  }

  /** Used by generated code to call a function. */
  fun call(
    service: ZiplineService,
    functionIndex: Int,
    vararg args: Any?,
  ): Any? {
    val function = sourceType.functions[functionIndex] as ReturningZiplineFunction<*>
    return callInternal(
      service,
      function,
      function.argsListSerializer,
      function.resultSerializer,
      *args,
    )
  }

  internal fun callInternal(
    service: ZiplineService,
    function: ZiplineFunction<*>,
    argsListSerializer: ArgsListSerializer,
    resultSerializer: ResultOrCallbackSerializer<*>,
    vararg args: Any?,
  ): Any? {
    if (function.isClose) {
      if (serviceState.closed) return Unit // ZiplineService.close() is idempotent.
      serviceState.closed = true
      scope.remove(this)
    } else {
      check(!serviceState.closed) {
        """
        |$adapter $serviceName is closed, failed to call:
        |  $function
        """.trimMargin()
      }
    }

    val argsList = args.toList()
    val internalCall = InternalCall(
      serviceName = serviceName,
      argsListSerializer = argsListSerializer,
      function = function,
      args = argsList,
    )
    val externalCall = endpoint.callCodec.encodeCall(internalCall, service)
    val callStart = when (service) {
      !is SuspendCallback<*> -> endpoint.eventListener.callStart(externalCall)
      else -> Unit // Don't call callStart() for suspend callbacks.
    }
    val encodedResult = endpoint.outboundChannel.call(externalCall.encodedCall)
    return endpoint.withTakeScope(scope) {
      val callResult = endpoint.callCodec.decodeResult(resultSerializer, encodedResult)
      when (service) {
        !is SuspendCallback<*> -> {
          endpoint.eventListener.callEnd(externalCall, callResult, callStart)
        }
        else -> {
          Unit // Don't call callEnd() for suspend callbacks.
        }
      }
      return@withTakeScope callResult.result
        .withApiMismatchMessage(function)
        .getOrThrow()
    }
  }

  /** Used by generated code to call a suspending function. */
  suspend fun callSuspending(
    service: ZiplineService,
    functionIndex: Int,
    vararg args: Any?,
  ): Any? {
    val function = sourceType.functions[functionIndex] as SuspendingZiplineFunction<*>
    return callSuspendingInternal(
      service,
      function,
      function.argsListSerializer,
      function.resultOrCallbackSerializer,
      function.suspendCallbackSerializer,
      *args,
    )
  }

  internal suspend fun callSuspendingInternal(
    service: ZiplineService,
    function: ZiplineFunction<*>,
    argsListSerializer: ArgsListSerializer,
    resultOrCallbackSerializer: ResultOrCallbackSerializer<*>,
    suspendCallbackSerializer: KSerializer<*>,
    vararg args: Any?,
  ): Any? {
    endpoint.scope.ensureActive()

    check(!serviceState.closed) {
      """
      |$adapter $serviceName is closed, failed to call:
      |  $function
      """.trimMargin()
    }

    val argsList = args.toList()
    val suspendCallback = RealSuspendCallback<Any?>()
    val internalCall = InternalCall(
      serviceName = serviceName,
      argsListSerializer = argsListSerializer,
      suspendCallbackSerializer = suspendCallbackSerializer,
      function = function,
      suspendCallback = suspendCallback,
      args = argsList,
    )
    suspendCallback.internalCall = internalCall
    val externalCall = endpoint.callCodec.encodeCall(internalCall, service)
    suspendCallback.externalCall = externalCall
    suspendCallback.callStart = endpoint.eventListener.callStart(externalCall)

    val resultOrCallbackJson = endpoint.outboundChannel.call(externalCall.encodedCall)
    val encodedResultOrCallback = endpoint.withTakeScope(scope) {
      endpoint.callCodec.decodeResultOrCallback(resultOrCallbackSerializer, resultOrCallbackJson)
    }

    // If the call returned a cancel callback, then it suspended and hasn't returned yet. Suspend
    // the current coroutine until the called function completes.
    val cancelCallback = encodedResultOrCallback.value.callback
    if (cancelCallback != null) {
      return suspendCancellableCoroutine { continuation ->
        suspendCallback.continuation = continuation
        endpoint.incompleteContinuations += continuation
        continuation.invokeOnCancellation {
          endpoint.scope.launch {
            if (!suspendCallback.completed) {
              cancelCallback.cancel()
            }
          }
        }
      }
    }

    // The call didn't suspend. Return its result without suspending.
    val callResult = encodedResultOrCallback.callResult!!

    // Suspend callbacks are one-shot. When triggered, remove them immediately.
    val name = suspendCallback.passByReferenceName
    if (name != null) endpoint.remove(name)

    endpoint.eventListener.callEnd(externalCall, callResult, suspendCallback.callStart)

    return callResult.result
      .withApiMismatchMessage(function)
      .getOrThrow()
  }

  override fun toString() = serviceName

  /**
   * Shared state for the handled service. If we have multiple outbound call handlers for the same
   * target service (perhaps with different scopes), this state is shared between them.
   */
  class ServiceState {
    var closed = false
  }

  private inner class RealSuspendCallback<R> :
    SuspendCallback<R>,
    HasPassByReferenceName,
    ZiplineScoped {
    lateinit var internalCall: InternalCall
    lateinit var externalCall: Call
    lateinit var continuation: Continuation<R>
    var callStart: Any? = null

    override var passByReferenceName: String? = null
    override val scope: ZiplineScope get() = this@OutboundCallHandler.scope

    /** True once this has been called. Used to prevent cancel-after-complete. */
    var completed = false

    override fun success(result: R) {
      call(Result.success(result))
    }

    override fun failure(result: Throwable) {
      call(Result.failure(result))
    }

    private fun call(result: Result<R>) {
      val suspendCall = endpoint.callCodec.lastInboundCall!!
      val callResult = CallResult(result, suspendCall.encodedCall, suspendCall.serviceNames)
      completed = true

      // Suspend callbacks are one-shot. When triggered, remove them immediately.
      val name = passByReferenceName
      if (name != null) endpoint.remove(name)
      endpoint.incompleteContinuations -= continuation

      endpoint.eventListener.callEnd(externalCall, callResult, callStart)
      continuation.resumeWith(result)
    }

    override fun toString() = "SuspendCallback/$internalCall"
  }

  private fun <T> Result<T>.withApiMismatchMessage(
    called: ZiplineFunction<*>,
  ): Result<T> {
    if (isSuccess) return this
    val throwable = exceptionOrNull()!!
    if (throwable !is ZiplineApiMismatchException) return this // Not an API mismatch.

    return try {
      when {
        ZiplineApiMismatchException.UNKNOWN_FUNCTION in throwable.message -> {
          val calledService = targetType ?: return this
          val message = buildString {
            appendLine("no such method (incompatible API versions?)")
            appendLine("\tcalled service:")
            append("\t\t")
            appendLine(serviceName)
            appendLine("\tcalled function:")
            append("\t\t")
            appendLine(called.signature)
            appendLine("\tavailable functions:")
            calledService.functions.joinTo(this, separator = "\n") { "\t\t${it.signature}" }
          }
          Result.failure(ZiplineApiMismatchException(message))
        }

        ZiplineApiMismatchException.UNKNOWN_SERVICE in throwable.message -> {
          val message = buildString {
            appendLine("no such service (service closed?)")
            appendLine("\tcalled service:")
            append("\t\t")
            appendLine(serviceName)
            appendLine("\tavailable services:")
            endpoint.opposite.serviceNames.joinTo(this, separator = "\n") { "\t\t$it" }
          }
          Result.failure(ZiplineApiMismatchException(message))
        }

        else -> this
      }
    } catch (e: Exception) {
      this // Unlikely edge case may occur if the EndpointService closed. Skip adding API details.
    }
  }
}
