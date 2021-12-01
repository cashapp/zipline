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

import app.cash.zipline.Zipline
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class JsSuspendingEchoService(
  private val greeting: String
) : SuspendingEchoService {
  override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
    mutex.withLock {
      return EchoResponse("$greeting from suspending JavaScript, ${request.message}")
    }
  }
}

private val zipline by lazy { Zipline.get() }

private val mutex = Mutex(locked = true)

@JsExport
fun prepareSuspendingJsBridges() {
  zipline.set<SuspendingEchoService>(
    "jsSuspendingEchoService",
    JsSuspendingEchoService("hello")
  )
}

@JsExport
fun unblockSuspendingJs() {
  mutex.unlock()
}

@JsExport
fun callSuspendingEchoService(message: String) {
  val service = zipline.get<SuspendingEchoService>("jvmSuspendingEchoService")
  GlobalScope.launch {
    suspendingEchoResult = service.suspendingEcho(EchoRequest(message)).message
  }
}

@JsExport
var suspendingEchoResult: String? = null
