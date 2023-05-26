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
package app.cash.zipline.internal

import app.cash.zipline.EventListener
import app.cash.zipline.Zipline
import app.cash.zipline.internal.bridge.Endpoint

internal class RealHostService(
  private val endpoint: Endpoint,
  private val zipline: Zipline,
  private val eventListener: EventListener,
  private val eventLoop: CoroutineEventLoop,
) : HostService {
  override val serviceNames: Set<String>
    get() = endpoint.serviceNames

  override fun serviceType(name: String) = endpoint.serviceType(name)

  override fun setTimeout(timeoutId: Int, delayMillis: Int) {
    eventLoop.setTimeout(timeoutId, delayMillis)
  }

  override fun clearTimeout(timeoutId: Int) {
    eventLoop.clearTimeout(timeoutId)
  }

  override fun log(level: String, message: String, throwable: Throwable?) {
    app.cash.zipline.internal.log(level, message, throwable)
  }

  override fun serviceLeaked(name: String) {
    eventListener.serviceLeaked(zipline, name)
  }
}
