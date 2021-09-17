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

import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import okio.Buffer

/**
 * Generated code extends this base class to make calls into an application-layer interface that is
 * implemented by another platform in the same process.
 */
@PublishedApi
internal abstract class OutboundBridge<T : Any>(
  val serializersModule: SerializersModule
) {
  abstract fun create(context: Context): T

  class Context(
    private val instanceName: String,
    val serializersModule: SerializersModule,
    private val endpoint: Endpoint,
  ) {
    val json = Json {
      serializersModule = this@Context.serializersModule
    }
    val throwableSerializer = serializersModule.serializer<Throwable>()

    fun newCall(funName: String, parameterCount: Int): OutboundCall {
      return OutboundCall(
        this,
        instanceName,
        endpoint,
        funName,
        parameterCount
      )
    }
  }
}

/**
 * This class models a single call sent to another Kotlin platform in the same process.
 *
 * It should be used to help implement an application-layer interface that is implemented by the
 * other platform. Implement each function in that interface to create an [OutboundCall] by calling
 * [Factory.create], pass in each received argument to [parameter], and then call [invoke] to
 * perform the cross-platform call.
 */
@PublishedApi
internal class OutboundCall(
  private val context: OutboundBridge.Context,
  private val instanceName: String,
  private val endpoint: Endpoint,
  private val funName: String,
  private val parameterCount: Int,
) {
  private val buffer = Buffer()
    .apply {
      writeInt(parameterCount)
    }
  private var callCount = 0
  private val eachValueBuffer = Buffer()

  fun <T> parameter(serializer: KSerializer<T>, value: T) {
    require(callCount++ < parameterCount)
    if (value == null) {
      buffer.writeInt(BYTE_COUNT_NULL)
    } else {
      eachValueBuffer.writeJsonUtf8(context.json, serializer, value)
      buffer.writeInt(eachValueBuffer.size.toInt())
      buffer.writeAll(eachValueBuffer)
    }
  }

  fun <R> invoke(serializer: KSerializer<R>): R {
    require(callCount++ == parameterCount)
    val encodedArguments = buffer.readByteArray()
    val encodedResult = endpoint.outboundChannel.invoke(
      instanceName,
      funName,
      encodedArguments
    )
    val result = encodedResult.decodeResult(serializer)
    return result.getOrThrow()
  }

  @PublishedApi
  internal suspend fun <R> invokeSuspending(serializer: KSerializer<R>): R {
    return coroutineScope {
      suspendCoroutine { continuation ->
        require(callCount++ == parameterCount)
        val callbackName = endpoint.generateName()
        val callback = RealSuspendCallback(callbackName, continuation, serializer)
        endpoint.set<SuspendCallback>(callbackName, EmptySerializersModule, callback)
        val encodedArguments = buffer.readByteArray()
        endpoint.outboundChannel.invokeSuspending(
          instanceName,
          funName,
          encodedArguments,
          callbackName
        )
      }
    }
  }

  private inner class RealSuspendCallback<R>(
    val callbackName: String,
    val continuation: Continuation<R>,
    val serializer: KSerializer<R>
  ) : SuspendCallback {
    override fun call(encodedResponse: ByteArray) {
      // Suspend callbacks are one-shot. When triggered, remove them immediately.
      endpoint.inboundHandlers.remove(callbackName)
      val result = encodedResponse.decodeResult(serializer)
      continuation.resumeWith(result)
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun <R> ByteArray.decodeResult(serializer: KSerializer<R>): Result<R> {
    buffer.write(this)
    when (buffer.readByte()) {
      RESULT_TYPE_NORMAL -> {
        val byteCount = buffer.readInt()
        if (byteCount == BYTE_COUNT_NULL) {
          return Result.success(null as R)
        } else {
          eachValueBuffer.write(buffer, byteCount.toLong())
          return Result.success(eachValueBuffer.readJsonUtf8(context.json, serializer))
        }
      }
      RESULT_TYPE_EXCEPTION -> {
        val byteCount = buffer.readInt()
        eachValueBuffer.write(buffer, byteCount.toLong())
        val throwable = eachValueBuffer.readJsonUtf8(context.json, context.throwableSerializer)
        return Result.failure(throwable)
      }
      else -> {
        error("unexpected result type")
      }
    }
  }
}
