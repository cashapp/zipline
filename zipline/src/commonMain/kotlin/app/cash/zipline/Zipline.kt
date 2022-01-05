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
package app.cash.zipline

import kotlinx.serialization.modules.SerializersModule

expect class Zipline {
  val serializersModule: SerializersModule

  /** Name of services that have been published with [set]. */
  val serviceNames: Set<String>

  /** Names of services that can be consumed with [get]. */
  val clientNames: Set<String>

  fun <T : ZiplineService> set(name: String, instance: T)

  fun <T : ZiplineService> get(name: String): T
}
