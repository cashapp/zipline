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

internal interface InternalCall

internal class OutboundCall(
  val serviceName: String,
  val function: ZiplineFunction<*>,
  val callbackName: String?,
  val args: List<*>,
) : InternalCall

internal class InboundCall(
  val serviceName: String?,
  val service: Endpoint.InboundService<*>?,
  val functionName: String?,
  val function: ZiplineFunction<*>?,

  /**
   * If this function is suspending, this callback name is not null. This function returns an empty
   * array and the response is delivered to the [SuspendCallback]. Suspending calls may be canceled
   * before it returns by using the [CancelCallback].
   */
  val callbackName: String?,
  val args: List<*>?,
) : InternalCall

/** This uses [Int] as a placeholder; in practice the element type depends on the argument type. */
private val argsListDescriptor = ListSerializer(Int.serializer()).descriptor

/** This uses [Int] as a placeholder; it doesn't matter 'cause we're only encoding failures. */
internal val failureResultSerializer = ResultSerializer(Int.serializer())

internal class InternalCallSerializer(
  private val endpoint: Endpoint,
) : KSerializer<InternalCall> {

  override val descriptor = buildClassSerialDescriptor("InternalCall") {
    element("service", String.serializer().descriptor)
    element("function", String.serializer().descriptor)
    element("callback", String.serializer().descriptor)
    element("args", argsListDescriptor)
  }

  override fun serialize(encoder: Encoder, value: InternalCall) {
    check(value is OutboundCall)

    encoder.encodeStructure(descriptor) {
      encodeStringElement(descriptor, 0, value.serviceName)
      encodeStringElement(descriptor, 1, value.function.name)
      if (value.callbackName != null) {
        encodeStringElement(descriptor, 2, value.callbackName)
      }
      encodeSerializableElement(descriptor, 3, value.function.argsListSerializer, value.args)
    }
  }

  override fun deserialize(decoder: Decoder): InternalCall {
    return decoder.decodeStructure(descriptor) {
      var serviceName: String? = null
      var service: Endpoint.InboundService<*>? = null
      var functionName: String? = null
      var function: ZiplineFunction<*>? = null
      var callbackName: String? = null
      var args: List<*>? = null
      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          0 -> {
            serviceName = decodeStringElement(descriptor, index)
            service = endpoint.inboundServices[serviceName]
          }
          1 -> {
            functionName = decodeStringElement(descriptor, index)
            function = service?.functions?.get(functionName)
          }
          2 -> {
            callbackName = decodeStringElement(descriptor, index)
          }
          3 -> {
            if (function != null) {
              args = decodeSerializableElement(descriptor, index, function.argsListSerializer)
            } else {
              // Discard args for unknown function.
              (decoder as JsonDecoder).decodeJsonElement()
            }
          }
          DECODE_DONE -> break
          else -> error("Unexpected index: $index")
        }
      }
      return@decodeStructure InboundCall(
        serviceName,
        service,
        functionName,
        function,
        callbackName,
        args
      )
    }
  }
}

internal class ArgsListSerializer(
  private val serializers: List<KSerializer<*>>,
) : KSerializer<List<*>> {
  override val descriptor = argsListDescriptor

  override fun serialize(encoder: Encoder, value: List<*>) {
    check(value.size == serializers.size)
    encoder.encodeStructure(descriptor) {
      for (i in serializers.indices) {
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


internal class ResultSerializer<T>(
  private val successSerializer: KSerializer<T>,
) : KSerializer<Result<T>> {

  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Result") {
    element("success", successSerializer.descriptor)
    element("failure", ThrowableSerializer.descriptor)
  }

  override fun serialize(encoder: Encoder, value: Result<T>) {
    encoder.encodeStructure(descriptor) {
      if (value.isSuccess) {
        encodeSerializableElement(descriptor, 0, successSerializer, value.getOrNull() as T)
      } else {
        encodeSerializableElement(descriptor, 1, ThrowableSerializer, value.exceptionOrNull()!!)
      }
    }
  }

  override fun deserialize(decoder: Decoder): Result<T> {
    return decoder.decodeStructure(descriptor) {
      var result: Result<T>? = null
      while (true) {
        result = when (val index = decodeElementIndex(descriptor)) {
          0 -> Result.success(decodeSerializableElement(descriptor, 0, successSerializer))
          1 -> Result.failure(decodeSerializableElement(descriptor, 1, ThrowableSerializer))
          DECODE_DONE -> break
          else -> error("Unexpected index: $index")
        }
      }
      return@decodeStructure result!!
    }
  }
}
