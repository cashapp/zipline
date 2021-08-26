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
package app.cash.quickjs

import app.cash.quickjs.OutboundCall.Factory
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import okio.Buffer

/**
 * This class models a single call sent to another Kotlin platform in the same process.
 *
 * It should be used to help implement an application-layer interface that is implemented by the
 * other platform. Implement each function in that interface to create an [OutboundCall] by calling
 * [Factory.create], pass in each received argument to [parameter], and then call [invoke] to
 * perform the cross-platform call.
 */
@PublishedApi
internal class OutboundCall private constructor(
  private val instanceName: String,
  private val jsAdapter: JsAdapter,
  private val internalBridge: InternalBridge,
  private val funName: String,
  private val parameterCount: Int,
) {
  private val buffer = Buffer()
    .apply {
      writeInt(parameterCount)
    }
  private var callCount = 0
  private val eachValueBuffer = Buffer()

  @OptIn(ExperimentalStdlibApi::class)
  inline fun <reified T> parameter(value: T) = parameter(typeOf<T>(), value)

  fun <T> parameter(type: KType, value: T) {
    require(callCount++ < parameterCount)
    if (value == null) {
      buffer.writeInt(-1)
    } else {
      jsAdapter.encode(value, eachValueBuffer, type)
      buffer.writeInt(eachValueBuffer.size.toInt())
      buffer.writeAll(eachValueBuffer)
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  inline fun <reified R> invoke(): R {
    return invoke(typeOf<R>())
  }

  fun <R> invoke(type: KType): R {
    require(callCount++ == parameterCount)
    val encodedArguments = buffer.readByteArray()
    val encodedResponse = internalBridge.invokeJs(instanceName, funName, encodedArguments)
    buffer.write(encodedResponse)
    val byteCount = buffer.readInt()
    if (byteCount == -1) {
      return null as R
    } else {
      eachValueBuffer.write(buffer, byteCount.toLong())
      return jsAdapter.decode(eachValueBuffer, type)
    }
  }

  class Factory internal constructor(
    private val instanceName: String,
    private val jsAdapter: JsAdapter,
    private val internalBridge: InternalBridge,
  ) {
    fun create(
      funName: String,
      parameterCount: Int
    ): OutboundCall {
      return OutboundCall(instanceName, jsAdapter, internalBridge, funName, parameterCount)
    }
  }
}
