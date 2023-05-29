/*
 * Copyright (C) 2022 Square, Inc.
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

import app.cash.zipline.Call
import app.cash.zipline.CallResult
import app.cash.zipline.EventListener
import app.cash.zipline.Zipline
import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.bridge.Endpoint

/** Adapts the endpoint listener to add a constant Zipline parameter. */
internal class EventListenerAdapter(
  val delegate: EventListener,
  val zipline: Zipline,
) : Endpoint.EventListener() {
  override fun bindService(name: String, service: ZiplineService) {
    delegate.bindService(zipline, name, service)
  }

  override fun takeService(name: String, service: ZiplineService) {
    delegate.takeService(zipline, name, service)
  }

  override fun serviceLeaked(name: String) {
    delegate.serviceLeaked(zipline, name)
  }

  override fun callStart(call: Call): Any? {
    return delegate.callStart(zipline, call)
  }

  override fun callEnd(call: Call, result: CallResult, startValue: Any?) {
    delegate.callEnd(zipline, call, result, startValue)
  }
}
