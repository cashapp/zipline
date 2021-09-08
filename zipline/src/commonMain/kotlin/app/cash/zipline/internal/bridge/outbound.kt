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

import app.cash.zipline.DefaultZiplineSerializersModule
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import okio.Buffer

/**
 * Generated code extends this base class to make calls into an application-layer interface that is
 * implemented by another platform in the same process.
 */
//@PublishedApi
//internal
abstract class OutboundClientFactory<T : Any>(
//  internal
  val serializersModule: SerializersModule
) {
  abstract fun create(callFactory: OutboundCall.Factory): T
}

/**
 * This class models a single call sent to another Kotlin platform in the same process.
 *
 * It should be used to help implement an application-layer interface that is implemented by the
 * other platform. Implement each function in that interface to create an [OutboundCall] by calling
 * [Factory.create], pass in each received argument to [parameter], and then call [invoke] to
 * perform the cross-platform call.
 */
//@PublishedApi
//internal
class OutboundCall private constructor(
  private val instanceName: String,
  val serializersModule: SerializersModule,
  private val ktBridge: KtBridge,
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
  inline fun <reified T> parameter(value: T) = parameter(serializersModule.serializer(), value)

  fun <T> parameter(serializer: KSerializer<T>, value: T) {
    require(callCount++ < parameterCount)
    if (value == null) {
      buffer.writeInt(BYTE_COUNT_NULL)
    } else {
      eachValueBuffer.writeJsonUtf8(serializer, value)
      buffer.writeInt(eachValueBuffer.size.toInt())
      buffer.writeAll(eachValueBuffer)
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  inline fun <reified R> invoke(): R {
    return invoke(serializersModule.serializer())
  }

  fun <R> invoke(serializer: KSerializer<R>): R {
    require(callCount++ == parameterCount)
    val encodedArguments = buffer.readByteArray()
    val encodedResult = internalBridge.invoke(instanceName, funName, encodedArguments)
    val result = encodedResult.decodeResult(serializer)
    return result.getOrThrow()
  }

  @OptIn(ExperimentalStdlibApi::class)
  suspend inline fun <reified R> invokeSuspending(): R {
    return invokeSuspending(serializersModule.serializer())
  }

  @PublishedApi
  internal suspend fun <R> invokeSuspending(serializer: KSerializer<R>): R {
    return suspendCoroutine { continuation ->
      require(callCount++ == parameterCount)
      val callbackName = ktBridge.generateName()
      val callback = RealSuspendCallback(callbackName, continuation, serializer)
      ktBridge.set<SuspendCallback>(callbackName, DefaultZiplineSerializersModule, callback)
      val encodedArguments = buffer.readByteArray()
      internalBridge.invokeSuspending(instanceName, funName, encodedArguments, callbackName)
    }
  }

  private inner class RealSuspendCallback<R>(
    val callbackName: String,
    val continuation: Continuation<R>,
    val serializer: KSerializer<R>
  ) : SuspendCallback {
    override fun call(encodedResponse: ByteArray) {
      // Suspend callbacks are one-shot. When triggered, remove them immediately.
      ktBridge.remove(callbackName)
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
          return Result.success(eachValueBuffer.readJsonUtf8(serializer))
        }
      }
      RESULT_TYPE_EXCEPTION -> {
        val byteCount = buffer.readInt()
        eachValueBuffer.write(buffer, byteCount.toLong())
        val throwable = eachValueBuffer.readJsonUtf8(serializersModule.serializer<Throwable>())
        return Result.failure(throwable)
      }
      else -> {
        error("unexpected result type")
      }
    }
  }

  class Factory internal constructor(
    private val instanceName: String,
    private val serializersModule: SerializersModule,
    private val ktBridge: KtBridge,
    private val internalBridge: InternalBridge,
  ) {
    fun create(
      funName: String,
      parameterCount: Int
    ): OutboundCall {
      return OutboundCall(
        instanceName,
        serializersModule,
        ktBridge,
        internalBridge,
        funName,
        parameterCount
      )
    }
  }
}
