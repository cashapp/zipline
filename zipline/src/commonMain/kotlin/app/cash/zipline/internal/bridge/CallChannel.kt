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

import app.cash.zipline.InboundZiplineReference
import app.cash.zipline.OutboundZiplineReference
import app.cash.zipline.ZiplineReference
import app.cash.zipline.ZiplineService
import kotlin.js.JsName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal const val inboundChannelName = "app_cash_zipline_inboundChannel"
internal const val outboundChannelName = "app_cash_zipline_outboundChannel"

@PublishedApi
internal interface CallChannel {
  /** Returns names can receive calls to [invoke] and [invokeSuspending]. */
  @JsName("serviceNamesArray")
  fun serviceNamesArray(): Array<String>

  /**
   * Internal function used to bridge method calls from Java or Android to JavaScript.
   *
   * The structure of [encodedArguments] is a series of alternating label/value pairs.
   *
   *  * Label `v`: the following value is a non-null parameter value.
   *  * Label `n`: the following value is an empty string and the parameter value is null.
   *
   * The structure of the result is also a series of alternating label/value pairs.
   *
   *  * Label `v`: the following value is a non-null normal result value.
   *  * Label `n`: the following value is an empty string and the result value is null.
   *  * Label `t`: the following value is a non-null thrown exception value.
   */
  @JsName("invoke")
  fun invoke(
    instanceName: String,
    funName: String,
    encodedArguments: Array<String>,
  ): Array<String>

  /** Like [invoke], but the response is delivered to the [SuspendCallback] named [callbackName]. */
  @JsName("invokeSuspending")
  fun invokeSuspending(
    instanceName: String,
    funName: String,
    encodedArguments: Array<String>,
    callbackName: String,
  )

  /**
   * Remove [instanceName] from the receiver. After making this call it is an error to make calls
   * with this name.
   *
   * @return true if the instance name existed.
   */
  @JsName("disconnect")
  fun disconnect(instanceName: String): Boolean
}

internal const val LABEL_VALUE = "v"
internal const val LABEL_NULL = "n"
internal const val LABEL_EXCEPTION = "t"

internal object ThrowableSerializer : KSerializer<Throwable> {
  override val descriptor = PrimitiveSerialDescriptor("ZiplineThrowable", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Throwable) {
    encoder.encodeString(toOutboundString(value))
  }

  override fun deserialize(decoder: Decoder): Throwable {
    return toInboundThrowable(decoder.decodeString())
  }
}

/**
 * This is a special serializer because it's scoped to an endpoint. It is not a general-purpose
 * serializer and only works in Zipline.
 *
 * To send a reference to an inbound service, we register it with the endpoint and transmit the
 * registered identifier.
 *
 * To receive a reference, we record the received identifier and return it when making calls against
 * the referenced service.
 */
internal class ZiplineReferenceSerializer<T : ZiplineService>(
  val endpoint: Endpoint,
  val adapter: ZiplineServiceAdapter<T>,
) : KSerializer<ZiplineReference<T>> {
  override val descriptor = PrimitiveSerialDescriptor("ZiplineReference", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: ZiplineReference<T>) {
    val name = endpoint.generateName()
    if (value is InboundZiplineReference<*>) {
      value.connect(endpoint, name)
      encoder.encodeString(name)
    } else {
      error("serializing an outbound reference is not implemented")
    }
  }

  override fun deserialize(decoder: Decoder): ZiplineReference<T> {
    val name = decoder.decodeString()
    val reference = OutboundZiplineReference(adapter)
    reference.connect(endpoint, name)
    return reference
  }
}
