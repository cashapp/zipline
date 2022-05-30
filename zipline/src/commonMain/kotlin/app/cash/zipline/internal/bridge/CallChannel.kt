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
  /** Returns names of services that can receive calls to [call]. */
  @JsName("serviceNamesArray")
  fun serviceNamesArray(): Array<String>

  /**
   * Internal function used to bridge method calls from either Kotlin/JVM or Kotlin/Native to
   * Kotlin/JS.
   */
  @JsName("call")
  fun call(callJson: String): String

  /**
   * Remove [instanceName] from the receiver. After making this call it is an error to make calls
   * with this name.
   *
   * @return true if the instance name existed.
   */
  @JsName("disconnect")
  fun disconnect(instanceName: String): Boolean
}

internal object ThrowableSerializer : KSerializer<Throwable> {
  override val descriptor = PrimitiveSerialDescriptor("ZiplineThrowable", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Throwable) {
    encoder.encodeString(toOutboundString(value))
  }

  override fun deserialize(decoder: Decoder): Throwable {
    return toInboundThrowable(decoder.decodeString())
  }
}
