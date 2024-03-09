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

import kotlin.reflect.KClass
import kotlinx.serialization.json.Json

expect class Zipline {
  /** Name of services that have been published with [bind]. */
  internal val serviceNames: Set<String>

  /** Names of services that can be consumed with [take]. */
  internal val clientNames: Set<String>

  /**
   * The JSON codec for exchanging messages with the other endpoint.
   *
   * This instance supports encoding [ZiplineService] implementations can be passed by reference
   * when partnered with [ziplineServiceSerializer].
   */
  val json: Json

  fun <T : ZiplineService> bind(name: String, instance: T)

  fun <T : ZiplineService> take(
    name: String,
    scope: ZiplineScope = ZiplineScope(),
  ): T

  /**
   * Attaches a computed to this Zipline using [key] as a key.
   *
   * Use this API to attach services, features, or other application data to a Zipline instance so
   * that you may read it later.
   */
  fun <T : Any> getOrPutAttachment(key: KClass<T>, compute: () -> T): T
}
