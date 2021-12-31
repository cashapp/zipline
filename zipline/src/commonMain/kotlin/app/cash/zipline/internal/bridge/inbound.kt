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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * Generated code extends this base class to receive calls into an application-layer interface from
 * another platform in the same process.
 */
@PublishedApi
internal interface InboundBridge {
  class Context(
    val serializersModule: SerializersModule,
    @PublishedApi internal val endpoint: Endpoint,
  ) {
    val json = Json {
      useArrayPolymorphism = true
      serializersModule = this@Context.serializersModule
    }
  }
}

@PublishedApi
internal interface InboundCallHandler {
  val context: InboundBridge.Context

  fun call(inboundCall: InboundCall): Array<String>

  suspend fun callSuspending(inboundCall: InboundCall): Array<String>
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
  private val context: InboundBridge.Context,
  val funName: String,
  val arguments: Array<String>,
) {
  private var i = 0

  fun <T> parameter(serializer: KSerializer<T>): T {
    while (i < arguments.size) {
      when (arguments[i]) {
        LABEL_VALUE -> {
          val result = context.json.decodeFromString(serializer, arguments[i + 1])
          i += 2
          return result
        }
        LABEL_NULL -> {
          i += 2
          return null as T
        }
        else -> {
          i += 2 // Ignore unknown argument.
        }
      }
    }
    throw IllegalStateException("no such parameter")
  }

  fun <R> result(serializer: KSerializer<R>, value: R): Array<String> {
    return when {
      value != null -> arrayOf(LABEL_VALUE, context.json.encodeToString(serializer, value))
      else -> arrayOf(LABEL_NULL, "")
    }
  }

  fun unexpectedFunction(): Array<String> = error("unexpected function: $funName")

  fun resultException(e: Throwable): Array<String> {
    return arrayOf(LABEL_EXCEPTION, context.json.encodeToString(ThrowableSerializer, e))
  }
}
