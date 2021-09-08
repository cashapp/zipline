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
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import okio.Buffer

/**
 * Generated code extends this base class to receive calls into an application-layer interface from
 * another platform in the same process.
 */
@PublishedApi
internal abstract class InboundService<T : Any>(
  internal val serializersModule: SerializersModule
) {
  abstract fun call(inboundCall: InboundCall): ByteArray

  abstract suspend fun callSuspending(inboundCall: InboundCall): ByteArray
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
  val funName: String,
  encodedArguments: ByteArray,
  val serializersModule: SerializersModule,
) {
  private val buffer = Buffer().write(encodedArguments)
  private val parameterCount = buffer.readInt()
  private var callCount = 0
  private val eachValueBuffer = Buffer()

  @OptIn(ExperimentalStdlibApi::class)
  inline fun <reified T : Any> parameter(): T = parameter(serializersModule.serializer())

  fun <T> parameter(serializer: KSerializer<T>): T {
    require(callCount++ < parameterCount)
    val byteCount = buffer.readInt()
    if (byteCount == BYTE_COUNT_NULL) {
      return null as T
    } else {
      eachValueBuffer.write(buffer, byteCount.toLong())
      return eachValueBuffer.readJsonUtf8(serializer)
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  inline fun <reified R> result(value: R): ByteArray {
    return result(serializersModule.serializer(), value)
  }

  fun <R> result(serializer: KSerializer<R>, value: R): ByteArray {
    require(callCount++ == parameterCount)
    buffer.writeByte(RESULT_TYPE_NORMAL.toInt())
    if (value == null) {
      buffer.writeInt(BYTE_COUNT_NULL)
    } else {
      eachValueBuffer.writeJsonUtf8(serializer, value)
      buffer.writeInt(eachValueBuffer.size.toInt())
      buffer.writeAll(eachValueBuffer)
    }
    return buffer.readByteArray()
  }

  fun unexpectedFunction(): ByteArray = error("unexpected function: $funName")

  @OptIn(ExperimentalStdlibApi::class)
  fun resultException(e: Throwable): ByteArray {
    buffer.clear()
    eachValueBuffer.clear()
    eachValueBuffer.writeJsonUtf8(serializersModule.serializer(), e)
    buffer.writeByte(RESULT_TYPE_EXCEPTION.toInt())
    buffer.writeInt(eachValueBuffer.size.toInt())
    buffer.writeAll(eachValueBuffer)
    return buffer.readByteArray()
  }
}
