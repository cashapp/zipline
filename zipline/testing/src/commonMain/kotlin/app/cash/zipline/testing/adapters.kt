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

import app.cash.zipline.DefaultZiplineSerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * This is a service that requires a kotlinx.serialization adapter to be registered for use.
 */
interface AdaptersService {
  fun echo(request: AdaptersRequest): AdaptersResponse
}

// Note that this is not @Serializable.
class AdaptersRequest(
  val message: String
)

// Note that this is not @Serializable.
class AdaptersResponse(
  val message: String
)

val AdaptersSerializersModule: SerializersModule = SerializersModule {
  include(DefaultZiplineSerializersModule)
}

