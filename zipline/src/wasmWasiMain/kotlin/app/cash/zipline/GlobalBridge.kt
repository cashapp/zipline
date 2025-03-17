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

import app.cash.zipline.internal.GuestService
import app.cash.zipline.internal.bridge.CallChannel

internal object GlobalBridge : GuestService, CallChannel {
  private val zipline: Zipline
    get() = theOnlyZipline ?: error("Zipline isn't ready: did you call Zipline.get() yet?")

  private val inboundChannel: CallChannel
    get() = zipline.endpoint.inboundChannel

  override val serviceNames: Set<String>
    get() = zipline.endpoint.serviceNames

  override fun serviceType(name: String) = zipline.endpoint.serviceType(name)

  override fun call(callJson: String) = inboundChannel.call(callJson)

  override fun disconnect(instanceName: String) = inboundChannel.disconnect(instanceName)

  override fun close() {
  }

  override fun runJob(timeoutId: Int) {
  }
}
