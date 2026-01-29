/*
 * Copyright (C) 2023 Cash App
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
package app.cash.zipline.internal

import app.cash.zipline.ZiplineService

/**
 * Host functions for use by guest code.
 *
 * Unlike typical interfaces that collect functions that share a purpose, this interface collects
 * functions that share a common implementation mechanism: the host platform.
 */
internal interface HostService :
  EndpointService,
  ZiplineService {
  fun setTimeout(timeoutId: Int, delayMillis: Int)

  fun clearTimeout(timeoutId: Int)

  fun log(level: String, message: String, throwable: Throwable?)

  /**
   * Event forwarded to the host event listener. We don't need to bridge all event functions
   * because the peer already sees its side of those calls.
   */
  fun serviceLeaked(name: String)
}
