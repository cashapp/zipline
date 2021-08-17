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
package app.cash.quickjs.ktbridge

import kotlin.reflect.KClass
import okio.Buffer

/**
 * This class models a single call into JavaScript. Each use should call [parameter] once for each
 * parameter of [funName], then [result] for the function result. This will automatically decode
 * parameters to the requested type and encode results.
 *
 * Call [unexpectedFunction] if an unexpected function is encountered.
 */
class InboundCall<T: Any>(
  val service: T,
  val funName: String,
  encodedArguments: ByteArray,
  private val jsAdapter: JsAdapter,
) {
  private val buffer = Buffer().write(encodedArguments)
  private val parameterCount = buffer.readInt()
  private var callCount = 0
  private val eachParameterBuffer = Buffer()

  inline fun <reified T : Any> parameter(): T = parameter(T::class)

  fun <T : Any> parameter(type: KClass<T>): T {
    require(callCount++ < parameterCount)
    val byteCount = buffer.readInt()
    eachParameterBuffer.write(buffer, byteCount.toLong())
    return jsAdapter.decode(eachParameterBuffer, type)
  }

  inline fun <reified R : Any> result(value: R): ByteArray {
    return result(R::class, value)
  }

  fun <R : Any> result(type: KClass<R>, value: R): ByteArray {
    require(callCount++ == parameterCount)
    jsAdapter.encode(value, buffer, type)
    return buffer.readByteArray()
  }

  fun unexpectedFunction(): ByteArray = error("unexpected function: $funName")
}
