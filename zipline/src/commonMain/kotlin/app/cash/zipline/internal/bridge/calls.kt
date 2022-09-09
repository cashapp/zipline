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

import app.cash.zipline.ZiplineApiMismatchException
import app.cash.zipline.ZiplineFunction
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
)

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
      encodeStringElement(descriptor, 1, value.function.name)
      if (value.suspendCallback != null) {
        val function = value.function as SuspendingZiplineFunction<*>
        @Suppress("UNCHECKED_CAST") // We don't declare a type T for the result of this call.
        encodeSerializableElement(
          descriptor,
          2,
          function.suspendCallbackSerializer as KSerializer<Any?>,
          value.suspendCallback,
        )
        encodeSerializableElement(descriptor, 3, function.argsListSerializer, value.args)
      } else {
        val function = value.function as ReturningZiplineFunction<*>
        encodeSerializableElement(descriptor, 3, function.argsListSerializer, value.args)
      }
    }
  }

  override fun deserialize(decoder: Decoder): InternalCall {
    return decoder.decodeStructure(descriptor) {
      var serviceName = ""
      var inboundService: InboundService<*>? = null
      var functionName = ""
      var function: ZiplineFunction<*>? = null
      var suspendCallback: SuspendCallback<Any?>? = null
      var args = listOf<Any?>()
      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          0 -> {
            serviceName = decodeStringElement(descriptor, index)
            inboundService = endpoint.inboundServices[serviceName]
          }
          1 -> {
            functionName = decodeStringElement(descriptor, index)
            function = inboundService?.functions?.get(functionName)
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
          serviceName, functionName, inboundService, suspendCallback
        ),
        suspendCallback = suspendCallback,
        args = args
      )
    }
  }

  /** Returns a fake service that implements no functions. */
  private fun unknownService(): InboundService<*> {
    return InboundService(
      service = object : ZiplineService {},
      endpoint = endpoint,
      functionsList = listOf()
    )
  }

  /** Returns a function that always throws [ZiplineApiMismatchException] when called. */
  private fun <T : ZiplineService> unknownFunction(
    serviceName: String,
    functionName: String,
    inboundService: InboundService<*>?,
    suspendCallback: SuspendCallback<Any?>?,
  ): ZiplineFunction<T> {
    val message = buildString {
      if (inboundService == null) {
        appendLine("no such service (service closed?)")
        appendLine("\tcalled service:")
        append("\t\t")
        appendLine(serviceName)
        appendLine("\tavailable services:")
        endpoint.inboundServices.keys.joinTo(this, separator = "\n") { "\t\t$it" }
      } else {
        appendLine("no such method (incompatible API versions?)")
        appendLine("\tcalled function:")
        append("\t\t")
        appendLine(functionName)
        appendLine("\tavailable functions:")
        inboundService.functions.keys.joinTo(this, separator = "\n") { "\t\t$it" }
      }
    }

    if (suspendCallback != null) {
      return object : SuspendingZiplineFunction<T>(
        name = functionName,
        argSerializers = listOf(),
        suspendCallbackSerializer = failureSuspendCallbackSerializer,
      ) {
        override suspend fun callSuspending(service: T, args: List<*>) =
          throw ZiplineApiMismatchException(message)
      }
    } else {
      return object : ReturningZiplineFunction<T>(
        name = functionName,
        argSerializers = listOf(),
        resultSerializer = Int.serializer(), // Placeholder; we're only encoding failures.
      ) {
        override fun call(service: T, args: List<*>) =
          throw ZiplineApiMismatchException(message)
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


internal class ResultSerializer<T>(
  internal val successSerializer: KSerializer<T>,
) : KSerializer<Result<T>> {

  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Result") {
    element("success", successSerializer.descriptor)
    element("failure", ThrowableSerializer.descriptor)
  }

  override fun serialize(encoder: Encoder, value: Result<T>) {
    encoder.encodeStructure(descriptor) {
      if (value.isSuccess) {
        @Suppress("UNCHECKED_CAST") // We know the value of a success result is a 'T'.
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
