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

import app.cash.zipline.internal.bridge.CallChannel

class LoggingCallChannel : CallChannel {
  val log = mutableListOf<String>()
  var callResult = ""
  var disconnectThrow = false
  var disconnectResult = true

  override fun call(callJson: String): String {
    log += "call($callJson)"
    return callResult
  }

  override fun disconnect(instanceName: String): Boolean {
    log += "disconnect($instanceName)"
    if (disconnectThrow) throw UnsupportedOperationException("boom!")
    return disconnectResult
  }
}
