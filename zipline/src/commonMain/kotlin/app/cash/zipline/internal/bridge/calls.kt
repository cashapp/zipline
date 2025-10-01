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

import app.cash.zipline.CallResult
import app.cash.zipline.ZiplineApiMismatchException
import app.cash.zipline.ZiplineFunction
import app.cash.zipline.ZiplineScoped
import app.cash.zipline.ZiplineService
import app.cash.zipline.ziplineServiceSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonDecoder

internal class InternalCall(
  /** This is not-null, but may refer to a service that is not known by this endpoint. */
  val serviceName: String,

  /** This is non-null for outbound calls. */
  val argsListSerializer: ArgsListSerializer? = null,

  /** This is non-null for suspending outbound calls. */
  val suspendCallbackSerializer: KSerializer<*>? = null,

  /** This is absent for outbound calls. */
  val inboundService: InboundService<*>? = null,

  /**
   * The function being called. If the function is unknown to the receiver, it will synthesize a
   * [ZiplineFunction] instance that always throws [ZiplineApiMismatchException].
   */
  val function: ZiplineFunction<*>,

  /**
   * If this function is suspending, this callback is not null. The function returns an encoded
   * [CancelCallback] and the response is delivered to the [SuspendCallback].
   */
  val suspendCallback: SuspendCallback<Any?>? = null,

  val args: List<*>,
) {
  override fun toString() = "Call(receiver=$serviceName, function=${function.signature}, args=$args)"
}

/** This uses [Int] as a placeholder; in practice the element type depends on the argument type. */
private val argsListDescriptor = ListSerializer(Int.serializer()).descriptor

/** This uses [Int] as a placeholder; it doesn't matter 'cause we're only encoding failures. */
internal val failureSuspendCallbackSerializer = ziplineServiceSerializer<SuspendCallback<Int>>()

/** Serialize any cancel callback using pass-by-reference. */
internal val cancelCallbackSerializer = ziplineServiceSerializer<CancelCallback>()

/**
 * Encode and decode calls using `ZiplineFunction.argsListSerializer`.
 *
 * When serializing outbound calls the function instance is a member of the call. To deserialize
 * inbound calls the function must be looked up from [endpoint] using the service and function name.
 *
 * This serializer is weird! Its args serializer is dependent on other properties. Therefore, it
 * (reasonably) assumes that JSON is decoded in the same order it's encoded.
 */
internal class RealCallSerializer(
  private val endpoint: Endpoint,
) : KSerializer<InternalCall> {

  override val descriptor = buildClassSerialDescriptor("RealCall") {
    element("service", String.serializer().descriptor)
    element("function", String.serializer().descriptor)
    element("callback", String.serializer().descriptor)
    element("args", argsListDescriptor)
  }

  override fun serialize(encoder: Encoder, value: InternalCall) {
    encoder.encodeStructure(descriptor) {
      encodeStringElement(descriptor, 0, value.serviceName)
      encodeStringElement(descriptor, 1, value.function.id)
      if (value.suspendCallback != null) {
        @Suppress("UNCHECKED_CAST") // We don't declare a type T for the result of this call.
        encodeSerializableElement(
          descriptor,
          2,
          value.suspendCallbackSerializer as KSerializer<Any?>,
          value.suspendCallback,
        )
      }
      encodeSerializableElement(descriptor, 3, value.argsListSerializer!!, value.args)
    }
  }

  override fun deserialize(decoder: Decoder): InternalCall {
    val pushedTakeScope = endpoint.takeScope
    try {
      return decoder.decodeStructure(descriptor) {
        var serviceName = ""
        var inboundService: InboundService<*>? = null
        var functionId = ""
        var function: ZiplineFunction<*>? = null
        var suspendCallback: SuspendCallback<Any?>? = null
        var args = listOf<Any?>()
        while (true) {
          when (val index = decodeElementIndex(descriptor)) {
            0 -> {
              serviceName = decodeStringElement(descriptor, index)
              inboundService = endpoint.inboundServices[serviceName]
              endpoint.takeScope = (inboundService?.service as? ZiplineScoped)?.scope
            }
            1 -> {
              functionId = decodeStringElement(descriptor, index)
              function = inboundService?.type?.functionsById?.get(functionId)
            }
            2 -> {
              @Suppress("UNCHECKED_CAST") // We don't declare a type T for the result of this call.
              val serializer = when (function) {
                is SuspendingZiplineFunction<*> -> function.suspendCallbackSerializer
                // We can use any suspend callback if we're only returning failures.
                else -> failureSuspendCallbackSerializer
              } as KSerializer<SuspendCallback<Any?>>

              suspendCallback = decodeSerializableElement(
                descriptor,
                index,
                serializer,
              )
            }
            3 -> {
              val argsListSerializer = when (function) {
                is SuspendingZiplineFunction<*> -> function.argsListSerializer
                is ReturningZiplineFunction<*> -> function.argsListSerializer
                else -> null
              }
              if (argsListSerializer != null) {
                args = decodeSerializableElement(descriptor, index, argsListSerializer)
              } else {
                // Discard args for unknown function.
                (decoder as JsonDecoder).decodeJsonElement()
              }
            }
            DECODE_DONE -> break
            else -> error("Unexpected index: $index")
          }
        }
        return@decodeStructure InternalCall(
          serviceName = serviceName,
          inboundService = inboundService ?: unknownService(),
          function = function ?: unknownFunction<ZiplineService>(
            functionId,
            suspendCallback,
            when (inboundService) {
              null -> ZiplineApiMismatchException.UNKNOWN_SERVICE
              else -> ZiplineApiMismatchException.UNKNOWN_FUNCTION
            },
          ),
          suspendCallback = suspendCallback,
          args = args,
        )
      }
    } finally {
      endpoint.takeScope = pushedTakeScope
    }
  }

  /** Returns a fake service that implements no functions. */
  private fun unknownService(): InboundService<*> {
    return InboundService(
      type = RealZiplineServiceType<ZiplineService>(
        name = "Unknown",
        functions = listOf(),
      ),
      service = object : ZiplineService {},
      endpoint = endpoint,
    )
  }

  /** Returns a function that always throws [ZiplineApiMismatchException] when called. */
  private fun <T : ZiplineService> unknownFunction(
    functionId: String,
    suspendCallback: SuspendCallback<Any?>?,
    message: String,
  ): ZiplineFunction<T> {
    if (suspendCallback != null) {
      return object : SuspendingZiplineFunction<T>(
        id = functionId,
        signature = "suspend fun unknownFunction(): kotlin.Unit",
        argSerializers = listOf(),
        // Placeholder; we're only encoding failures.
        resultSerializer = Int.serializer(),
        suspendCallbackSerializer = failureSuspendCallbackSerializer,
      ) {
        override suspend fun callSuspending(service: T, args: List<*>) = throw ZiplineApiMismatchException(message)
      }
    } else {
      return object : ReturningZiplineFunction<T>(
        id = functionId,
        signature = "fun unknownFunction(): kotlin.Unit",
        argSerializers = listOf(),
        // Placeholder; we're only encoding failures.
        resultSerializer = Int.serializer(),
      ) {
        override fun call(service: T, args: List<*>) = throw ZiplineApiMismatchException(message)
      }
    }
  }
}

internal class ArgsListSerializer(
  internal val serializers: List<KSerializer<*>>,
) : KSerializer<List<*>> {
  override val descriptor = argsListDescriptor

  override fun serialize(encoder: Encoder, value: List<*>) {
    check(value.size == serializers.size)
    encoder.encodeStructure(descriptor) {
      for (i in serializers.indices) {
        @Suppress("UNCHECKED_CAST") // We don't have a type argument T for each parameter.
        encodeSerializableElement(descriptor, i, serializers[i] as KSerializer<Any?>, value[i])
      }
    }
  }

  override fun deserialize(decoder: Decoder): List<*> {
    return decoder.decodeStructure(descriptor) {
      val result = mutableListOf<Any?>()
      for (i in serializers.indices) {
        check(decodeElementIndex(descriptor) == i)
        result += decodeSerializableElement(descriptor, i, serializers[i])
      }
      check(decodeElementIndex(descriptor) == DECODE_DONE)
      return@decodeStructure result
    }
  }
}

/**
 * Immediate result from invoking a returning or suspending function, that may include either a
 * value (if the call never suspended) or a cancel callback (if it did suspend).
 */
internal class ResultOrCallback<T>(
  /** The function returned or failed without suspending. */
  val result: Result<T>? = null,

  /** The function suspended. Only non-null for suspend calls. */
  val callback: CancelCallback? = null,
) {
  init {
    require((callback != null) != (result != null))
  }
}

/** Combination of [ResultOrCallback] and [app.cash.zipline.CallResult]. */
internal class EncodedResultOrCallback(
  val value: ResultOrCallback<*>,
  val json: String,
  serviceNames: List<String>,
) {
  val serviceNames: List<String> = serviceNames.toList() // Defensive copy.

  /** The call result. Null if this is a callback. */
  val callResult: CallResult?
    get() {
      val result = value.result ?: return null
      return CallResult(
        result,
        json,
        serviceNames,
      )
    }
}

internal class ResultOrCallbackSerializer<T>(
  internal val successSerializer: KSerializer<T>,
) : KSerializer<ResultOrCallback<T>> {

  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Result") {
    element("cancelCallback", cancelCallbackSerializer.descriptor)
    element("failure", ThrowableSerializer.descriptor)
    element("success", successSerializer.descriptor)
  }

  override fun serialize(encoder: Encoder, value: ResultOrCallback<T>) {
    encoder.encodeStructure(descriptor) {
      if (value.callback != null) {
        encodeSerializableElement(descriptor, 0, cancelCallbackSerializer, value.callback)
        return@encodeStructure
      }

      val result = value.result!!
      val throwable = result.exceptionOrNull()
      if (throwable != null) {
        encodeSerializableElement(descriptor, 1, ThrowableSerializer, throwable)
        return@encodeStructure
      }

      @Suppress("UNCHECKED_CAST") // We know the value of a success result is a 'T'.
      encodeSerializableElement(descriptor, 2, successSerializer, result.getOrNull() as T)
    }
  }

  override fun deserialize(decoder: Decoder): ResultOrCallback<T> {
    return decoder.decodeStructure(descriptor) {
      var result: Result<T>? = null
      var callback: CancelCallback? = null

      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          0 -> callback = decodeSerializableElement(descriptor, 0, cancelCallbackSerializer)
          1 -> result = Result.failure(
            decodeSerializableElement(descriptor, 1, ThrowableSerializer),
          )
          2 -> result = Result.success(
            decodeSerializableElement(descriptor, 2, successSerializer),
          )
          DECODE_DONE -> break
          else -> error("Unexpected index: $index")
        }
      }
      return@decodeStructure ResultOrCallback(result, callback)
    }
  }
}
