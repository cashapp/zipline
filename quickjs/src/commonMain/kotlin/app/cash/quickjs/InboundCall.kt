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

import kotlin.reflect.KType
import kotlin.reflect.typeOf
import okio.Buffer

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
  val funName: String,
  encodedArguments: ByteArray,
  private val jsAdapter: JsAdapter,
) {
  private val buffer = Buffer().write(encodedArguments)
  private val parameterCount = buffer.readInt()
  private var callCount = 0
  private val eachValueBuffer = Buffer()

  @OptIn(ExperimentalStdlibApi::class)
  inline fun <reified T : Any> parameter(): T = parameter(typeOf<T>())

  fun <T> parameter(type: KType): T {
    require(callCount++ < parameterCount)
    val byteCount = buffer.readInt()
    if (byteCount == -1) {
      return null as T
    } else {
      eachValueBuffer.write(buffer, byteCount.toLong())
      return jsAdapter.decode(eachValueBuffer, type)
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  inline fun <reified R> result(value: R): ByteArray {
    return result(typeOf<R>(), value)
  }

  fun <R> result(type: KType, value: R): ByteArray {
    require(callCount++ == parameterCount)
    if (value == null) {
      buffer.writeInt(-1)
    } else {
      jsAdapter.encode(value, eachValueBuffer, type)
      buffer.writeInt(eachValueBuffer.size.toInt())
      buffer.writeAll(eachValueBuffer)
    }
    return buffer.readByteArray()
  }

  fun unexpectedFunction(): ByteArray = error("unexpected function: $funName")
}
