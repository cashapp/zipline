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

import app.cash.zipline.ZiplineApiMismatchException
import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.decodeFromStringFast
import app.cash.zipline.internal.encodeToStringFast
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Generated code extends this base class to receive calls into an application-layer interface from
 * another platform in the same process.
 */
@PublishedApi
internal interface InboundBridge {
  class Context(
    val name: String,
    val service: ZiplineService,
    val json: Json,
    @PublishedApi internal val endpoint: Endpoint,
  ) {
    val serializersModule = json.serializersModule
  }
}

@PublishedApi
internal interface InboundCallHandler {
  val context: InboundBridge.Context

  fun call(inboundCall: InboundCall): Array<String>

  suspend fun callSuspending(inboundCall: InboundCall): Array<String>
}

@PublishedApi
internal abstract class InboundCallHandler2(
  val argSerializers: List<KSerializer<*>>,
  val resultSerializer: KSerializer<*>,
) {
  open fun call(args: List<*>): Any? {
    error("unexpected call")
  }

  open suspend fun callSuspending(args: List<*>): Any? {
    error("unexpected call")
  }
}

/**
 * This class models a single call received from another Kotlin platform in the same process.
 *
 * Each use should call [parameter] once for each parameter of [funName], then [result] for the
 * function result. This will automatically decode parameters to the requested type and encode
 * results.
 *
 * Call [unexpectedFunction] if an unexpected function is encountered.
 */
@PublishedApi
internal class InboundCall(
  val encodedArguments: Array<String>,
) {
  internal lateinit var context: InboundBridge.Context
  val serviceName: String
  val funName: String
  val callbackName: String

  private val arguments = ArrayList<Any?>(encodedArguments.size / 2)
  private var callStartResult: Any? = null
  private var i = 0

  init {
    var serviceName: String? = null
    var funName: String? = null
    var callbackName: String? = null
    var skippedArguments = false
    while (i < encodedArguments.size) {
      when (encodedArguments[i]) {
        LABEL_SERVICE_NAME -> serviceName = encodedArguments[i + 1]
        LABEL_FUN_NAME -> funName = encodedArguments[i + 1]
        LABEL_CALLBACK_NAME -> callbackName = encodedArguments[i + 1]
        else -> skippedArguments = true
      }
      i += 2
      if (serviceName != null && funName != null && callbackName != null) break
    }

    this.serviceName = serviceName!!
    this.funName = funName!!
    this.callbackName = callbackName!!
    if (skippedArguments) i = 0
  }

  fun <T> parameter(serializer: KSerializer<T>): T {
    while (i < encodedArguments.size) {
      val result = when (encodedArguments[i]) {
        LABEL_VALUE -> context.json.decodeFromStringFast(serializer, encodedArguments[i + 1])
        LABEL_NULL -> null as T
        else -> {
          // Ignore unknown argument.
          i += 2
          continue
        }
      }
      arguments += result
      i += 2
      if (i == encodedArguments.size) {
        callStartResult = context.endpoint.eventListener.callStart(context.name, context.service, funName, arguments)
      }
      return result
    }
    throw IllegalStateException("no such parameter")
  }

  fun <R> result(serializer: KSerializer<R>, value: R): Array<String> {
    context.endpoint.eventListener.callEnd(context.name, context.service, funName, arguments, Result.success(value), callStartResult)
    return when {
      value != null -> arrayOf(LABEL_VALUE, context.json.encodeToStringFast(serializer, value))
      else -> arrayOf(LABEL_NULL, "")
    }
  }

  fun unexpectedFunction(supportedFunctionNames: List<String>): Array<String> = throw ZiplineApiMismatchException(
    buildString {
      appendLine("no such method (incompatible API versions?)")
      appendLine("\tcalled:")
      append("\t\t")
      appendLine(funName)
      appendLine("\tavailable:")
      supportedFunctionNames.joinTo(this, separator = "\n") { "\t\t$it" }
    }
  )

  fun resultException(e: Throwable): Array<String> {
    context.endpoint.eventListener.callEnd(context.name, context.service, funName, arguments, Result.failure(e), callStartResult)
    return arrayOf(LABEL_EXCEPTION, context.json.encodeToStringFast(ThrowableSerializer, e))
  }
}
