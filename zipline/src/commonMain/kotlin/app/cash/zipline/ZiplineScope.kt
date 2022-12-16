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
package app.cash.zipline

class ZiplineScope {
  internal var closed = false
    private set

  private val services = mutableSetOf<ZiplineService>()

  internal fun add(result: ZiplineService) {
    check(!closed)
    services += result
  }

  internal fun remove(result: ZiplineService) {
    services -= result
  }

  fun close() {
    if (closed) return
    closed = true

    val servicesCopy = services.toTypedArray() // Because close() mutates the set.
    for (service in servicesCopy) {
      service.close()
    }
  }
}
