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
interface SerializableEndpoint

internal class DeserializedEndpoint(
  var name: String,
  var endpoint: Endpoint,
) : SerializableEndpoint

internal class SerializedEndpoint<T : ZiplineService>(
  val service: T,
  val adapter: ZiplineServiceAdapter<T>,
) : SerializableEndpoint {
  fun bindToEndpoint(endpoint: Endpoint, name: String) {
    endpoint.bind(name, service, adapter)
  }
}

internal class SerializableEndpointSerializer(
  val endpoint: Endpoint,
) : KSerializer<SerializableEndpoint> {
  override val descriptor = PrimitiveSerialDescriptor("SerializableEndpoint", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: SerializableEndpoint) {
    require(value is SerializedEndpoint<*>)
    val name = endpoint.generateName()
    value.bindToEndpoint(endpoint, name)
    encoder.encodeString(name)
  }

  override fun deserialize(decoder: Decoder): SerializableEndpoint {
    val name = decoder.decodeString()
    return DeserializedEndpoint(name, endpoint)
  }
}
