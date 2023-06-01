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
package app.cash.zipline.testing

import app.cash.zipline.ZiplineService
import app.cash.zipline.ziplineServiceSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

/**
 * This test exercises sending a service as a parameter in, and also receiving it as a parameter
 * out. In practice, it's unnecessary to do these two things simultaneously.
 */
interface ServiceTransformer : ZiplineService {
  fun addPrefix(serviceAndPrefix: ServiceAndPrefix): ServiceAndPrefix
}

@Serializable
data class ServiceAndPrefix(
  val prefix: String,
  @Contextual val service: EchoService,
)

val ServiceMemberSerializersModule: SerializersModule = SerializersModule {
  contextual(EchoService::class, ziplineServiceSerializer())
}
