/*
 * Copyright (C) 2024 Cash App
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
package app.cash.zipline

import app.cash.zipline.internal.bridge.ArgsListSerializer
import app.cash.zipline.internal.bridge.OutboundService
import app.cash.zipline.internal.bridge.ResultOrCallbackSerializer
import app.cash.zipline.internal.bridge.ReturningZiplineFunction
import app.cash.zipline.internal.bridge.SuspendCallback
import app.cash.zipline.internal.bridge.SuspendingZiplineFunction
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.encodeToDynamic

/**
 * Returns a function that accepts and returns plain JavaScript objects, instead of regular Kotlin
 * values. (The function's receiver will still receive regular Kotlin values as parameters, and
 * return a regular Kotlin value as a result.)
 *
 * Use this in highly performance-sensitive code to reduce the amount of work required to call
 * bridged functions on Zipline services.
 */
@ExperimentalSerializationApi
fun <T : ZiplineService> ZiplineFunction<T>.asDynamicFunction(): (service: T, args: List<*>) -> Any? {
  require(this is ReturningZiplineFunction<T>) {
    "asDynamicFunction() cannot be called on $this"
  }

  val resultSerializer = ResultOrCallbackSerializer(FastDynamicSerializer)
  val argsListSerializer = ArgsListSerializer(
    serializers = List(argsListSerializer.serializers.size) { FastDynamicSerializer },
  )

  return { service, args ->
    val callHandler = (service as OutboundService).callHandler
    callHandler.callInternal(
      service = service,
      function = this@asDynamicFunction,
      argsListSerializer = argsListSerializer,
      resultSerializer = resultSerializer,
      args = args.toTypedArray(),
    )
  }
}

/**
 * Returns a suspending function that accepts and returns plain JavaScript objects, instead of
 * regular Kotlin values. (The function's receiver will still receive regular Kotlin values as
 * parameters, and return a regular Kotlin value as a result.)
 *
 * Use this in highly performance-sensitive code to reduce the amount of work required to call
 * bridged functions on Zipline services.
 */
@ExperimentalSerializationApi
fun <T : ZiplineService> ZiplineFunction<T>.asDynamicSuspendingFunction(): suspend (service: T, args: List<*>) -> Any? {
  require(this is SuspendingZiplineFunction<T>) {
    "asDynamicSuspendingFunction() cannot be called on $this"
  }

  val resultOrCallbackSerializer = ResultOrCallbackSerializer(FastDynamicSerializer)
  val argsListSerializer = ArgsListSerializer(
    serializers = List(argsListSerializer.serializers.size) { FastDynamicSerializer },
  )
  val dynamicSuspendCallbackSerializer = ziplineServiceSerializer<SuspendCallback<Any?>>(
    kClass = SuspendCallback::class,
    typeArgumentsSerializers = listOf(FastDynamicSerializer),
  )

  return { service, args ->
    val callHandler = (service as OutboundService).callHandler
    callHandler.callSuspendingInternal(
      service = service,
      function = this@asDynamicSuspendingFunction,
      argsListSerializer = argsListSerializer,
      resultOrCallbackSerializer = resultOrCallbackSerializer,
      suspendCallbackSerializer = dynamicSuspendCallbackSerializer,
      args = args.toTypedArray(),
    )
  }
}

/**
 * ‘Encode’ JavaScript objects as JSON by assigning them without any intermediate copies.
 *
 * **This is not a general-purpose serializer** and can only be used with
 * `Json.encodeToStringFast()`.
 *
 * In particular, this assumes that the target [Encoder] is building a JavaScript object tree and
 * not a string or stream. It assumes [AbstractEncoder.encodeValue] will accept a JavaScript object
 * directly. It also assumes the output JSON tree will be used once and discarded, and skips the
 * deep copy of input values.
 */
@ExperimentalSerializationApi
internal object FastDynamicSerializer : KSerializer<Any?> {
  override val descriptor = SerialDescriptor(
    serialName = "dynamic",
    original = ContextualSerializer(Any::class).descriptor,
  )

  override fun deserialize(decoder: Decoder): Any? {
    // Slow path: decode a JSON element, then convert that to a JavaScript value.
    val jsonElement = (decoder as JsonDecoder).decodeJsonElement()

    // Work around https://github.com/Kotlin/kotlinx.serialization/pull/2712
    if (jsonElement === JsonNull) return null

    return Json.encodeToDynamic(JsonElement.serializer(), jsonElement)
  }

  override fun serialize(encoder: Encoder, value: Any?) {
    require(encoder is AbstractEncoder)

    // Fast path: assign the JavaScript value directly without traversing it or copying it.
    val encoded = value.asDynamic()
    encoder.encodeValue(encoded)
  }
}
