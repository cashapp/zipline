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

import app.cash.zipline.ZiplineScope
import app.cash.zipline.ZiplineService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * This is used with [ZiplineServiceAdapter] to pass services between runtimes by reference and not
 * by value. This calls [Endpoint.bind] when serializing, and deserializers call [Endpoint.take].
 */
interface PassByReference

internal class ReceiveByReference(
  var name: String,
  var endpoint: Endpoint,
) : PassByReference {
  fun <T : ZiplineService> take(adapter: ZiplineServiceAdapter<T>): T {
    val scope = endpoint.takeScope ?: ZiplineScope()
    return endpoint.take(name, scope, adapter)
  }
}

internal class SendByReference<T : ZiplineService>(
  val service: T,
  val adapter: ZiplineServiceAdapter<T>,
) : PassByReference {
  fun bind(endpoint: Endpoint, name: String) {
    endpoint.bind(name, service, adapter)
  }
}

internal class PassByReferenceSerializer(
  val endpoint: Endpoint,
) : KSerializer<PassByReference> {
  override val descriptor = PrimitiveSerialDescriptor("PassByReference", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: PassByReference) {
    require(value is SendByReference<*>)
    val serviceName = endpoint.generatePassByReferenceName()
    if (value.service is HasPassByReferenceName) {
      value.service.passByReferenceName = serviceName
    }
    endpoint.callCodec.encodedServiceNames += serviceName
    value.bind(endpoint, serviceName)
    encoder.encodeString(serviceName)
  }

  override fun deserialize(decoder: Decoder): PassByReference {
    val serviceName = decoder.decodeString()
    endpoint.callCodec.decodedServiceNames += serviceName
    return ReceiveByReference(serviceName, endpoint)
  }
}

/**
 * Implemented by concrete [ZiplineService] implementations that close themselves: [SuspendCallback]
 * and [CancelCallback]. Otherwise, they wouldn't know what to pass to [Endpoint.remove]!
 *
 * Not appropriate for general use, where the receiver must call [ZiplineService.close].
 */
internal interface HasPassByReferenceName {
  var passByReferenceName: String?
}
