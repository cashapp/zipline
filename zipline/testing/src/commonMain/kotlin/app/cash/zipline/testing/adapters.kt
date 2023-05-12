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
package app.cash.zipline.testing

import app.cash.zipline.ZiplineService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

/** This is a service that requires a kotlinx.serialization adapter to be registered for use. */
interface AdaptersService : ZiplineService {
  fun echo(request: AdaptersRequest): AdaptersResponse
}

// Note that this is not @Serializable.
data class AdaptersRequest(
  val message: String,
)

// Note that this is not @Serializable.
data class AdaptersResponse(
  val message: String,
)

internal object AdaptersRequestSerializer : KSerializer<AdaptersRequest> {
  override val descriptor = PrimitiveSerialDescriptor(
    "app.cash.zipline.testing.AdaptersRequestSerializer",
    PrimitiveKind.STRING,
  )

  override fun serialize(encoder: Encoder, value: AdaptersRequest) {
    encoder.encodeString(value.message)
  }

  override fun deserialize(decoder: Decoder): AdaptersRequest {
    return AdaptersRequest(decoder.decodeString())
  }
}

internal object AdaptersResponseSerializer : KSerializer<AdaptersResponse> {
  override val descriptor = PrimitiveSerialDescriptor(
    "app.cash.zipline.testing.AdaptersResponseSerializer",
    PrimitiveKind.STRING,
  )

  override fun serialize(encoder: Encoder, value: AdaptersResponse) {
    encoder.encodeString(value.message)
  }

  override fun deserialize(decoder: Decoder): AdaptersResponse {
    return AdaptersResponse(decoder.decodeString())
  }
}

val AdaptersRequestSerializersModule: SerializersModule = SerializersModule {
  contextual(AdaptersRequest::class, AdaptersRequestSerializer)
}

val AdaptersResponseSerializersModule: SerializersModule = SerializersModule {
  contextual(AdaptersResponse::class, AdaptersResponseSerializer)
}

val AdaptersSerializersModule: SerializersModule = SerializersModule {
  include(AdaptersRequestSerializersModule)
  include(AdaptersResponseSerializersModule)
}
