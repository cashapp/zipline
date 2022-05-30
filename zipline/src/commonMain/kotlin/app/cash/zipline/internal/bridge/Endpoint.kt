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

import app.cash.zipline.EventListener
import app.cash.zipline.ZiplineApiMismatchException
import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.decodeFromStringFast
import app.cash.zipline.internal.encodeToStringFast
import kotlin.coroutines.Continuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * An outbound channel for delivering calls to the other platform, and an inbound channel for
 * receiving calls from the other platform.
 */
class Endpoint internal constructor(
  internal val scope: CoroutineScope,
  internal val userSerializersModule: SerializersModule,
  internal val eventListener: EventListener,
  internal val outboundChannel: CallChannel,
) {
  internal val inboundServices = mutableMapOf<String, InboundService<*>>()
  private var nextId = 1

  internal val incompleteContinuations = mutableSetOf<Continuation<*>>()

  val serviceNames: Set<String>
    get() = inboundServices.keys.toSet()

  val clientNames: Set<String>
    get() = outboundChannel.serviceNamesArray().toSet()

  internal val callSerializer = InternalCallSerializer(this)

  /** This uses both Zipline-provided serializers and user-provided serializers. */
  internal val json: Json = Json {
    useArrayPolymorphism = true
    serializersModule = SerializersModule {
      contextual(PassByReference::class, PassByReferenceSerializer(this@Endpoint))
      contextual(Throwable::class, ThrowableSerializer)
      include(userSerializersModule)
    }
  }

  internal val inboundChannel = object : CallChannel {
    override fun serviceNamesArray(): Array<String> {
      return serviceNames.toTypedArray()
    }

    override fun call(encodedArguments: Array<String>): Array<String> {
      val call = json.decodeFromStringFast(callSerializer, encodedArguments.single())
      val service = call.inboundService ?: error("no handler for ${call.serviceName}")

      return when {
        call.callbackName != null -> service.callSuspending(call)
        else -> arrayOf(service.call(call))
      }
    }

    override fun disconnect(instanceName: String): Boolean {
      return inboundServices.remove(instanceName) != null
    }
  }

  fun <T : ZiplineService> bind(name: String, instance: T) {
    error("unexpected call to Endpoint.bind: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> bind(
    name: String,
    service: T,
    adapter: ZiplineServiceAdapter<T>
  ) {
    eventListener.bindService(name, service)

    val functions: List<ZiplineFunction<T>> = adapter.ziplineFunctions(json.serializersModule)
    inboundServices[name] = InboundService(name, service, functions)
  }

  fun <T : ZiplineService> take(name: String): T {
    error("unexpected call to Endpoint.take: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> take(name: String, adapter: ZiplineServiceAdapter<T>): T {
    // Detect leaked old services when creating new services.
    detectLeaks()

    val ziplineFunctions = adapter.ziplineFunctions(json.serializersModule)
    val outboundContext = newOutboundContext(name, ziplineFunctions)
    val result = adapter.outboundService(outboundContext)
    eventListener.takeService(name, result)
    trackLeaks(eventListener, name, outboundContext, result)
    return result
  }

  @PublishedApi
  internal fun remove(name: String): InboundService<*>? {
    return inboundServices.remove(name)
  }

  internal fun generateName(prefix: String): String {
    return "$prefix${nextId++}"
  }

  /** Derives the name of a [CancelCallback] from the name of a [SuspendCallback]. */
  internal fun cancelCallbackName(name: String): String {
    return "$name/cancel"
  }

  @PublishedApi
  internal fun newOutboundContext(
    name: String,
    ziplineFunctions: List<ZiplineFunction<*>>,
  ) = OutboundCallHandler(name, json, this, ziplineFunctions)

  internal inner class InboundService<T : ZiplineService>(
    private val serviceName: String,
    private val service: T,
    functionsList: List<ZiplineFunction<T>>,
  ) {
    val functions: Map<String, ZiplineFunction<T>> = functionsList.associateBy { it.name }

    fun call(call: InternalCall): String {
      // Removes the handler in calls to [ZiplineService.close]. We remove before dispatching so
      // it'll always be removed even if the call stalls or throws.
      if (call.functionName == "fun close(): kotlin.Unit") {
        inboundServices.remove(call.serviceName)
      }

      val function = call.function as ZiplineFunction<ZiplineService>?
      val args = call.args

      val result: Result<Any?> = when {
        function == null -> {
          Result.failure(unexpectedFunction(call.functionName))
        }
        else -> {
          val callStart = eventListener.callStart(serviceName, service, function.name, args)
          val theResult = try {
            val success = function.call(service, args)
            Result.success(success)
          } catch (e: Throwable) {
            Result.failure(e)
          }
          eventListener.callEnd(serviceName, service, function.name, args, theResult, callStart)
          theResult
        }
      }

      return json.encodeToStringFast(
        (call.function?.callResultSerializer ?: failureResultSerializer) as ResultSerializer<Any?>,
        result,
      )
    }

    fun callSuspending(call: InternalCall): Array<String> {
      val suspendCallbackName = call.callbackName!!
      val job = scope.launch {
        val function = call.function as ZiplineFunction<ZiplineService>?
        val args = call.args

        val result: Result<Any?> = when {
          function == null -> {
            Result.failure(unexpectedFunction(call.functionName))
          }
          else -> {
            val callStart = eventListener.callStart(serviceName, service, function.name, args)
            val theResult = try {
              val success = function.callSuspending(service, args)
              Result.success(success)
            } catch (e: Throwable) {
              Result.failure(e)
            }
            eventListener.callEnd(serviceName, service, function.name, args, theResult, callStart)
            theResult
          }
        }

        val encodedResult = json.encodeToStringFast(
          (call.function?.callResultSerializer ?: failureResultSerializer) as KSerializer<Any?>,
          result,
        )

        scope.ensureActive() // Don't resume a continuation if the Zipline has since been closed.
        val suspendCallback = take<SuspendCallback>(suspendCallbackName)
        suspendCallback.call(encodedResult)
      }

      val cancelCallbackName = cancelCallbackName(suspendCallbackName)
      bind<CancelCallback>(cancelCallbackName, object : CancelCallback {
        override fun cancel() {
          job.cancel()
        }
      })
      job.invokeOnCompletion {
        remove(cancelCallbackName)
      }

      return arrayOf()
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
}
