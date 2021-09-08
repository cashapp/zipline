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
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JsSuspendingEchoService(
  private val greeting: String
) : SuspendingEchoService {
  override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
    return EchoResponse("$greeting from suspending JavaScript, ${request.message}")
  }
}

@JsExport
fun prepareSuspendingJsBridges() {
  Zipline.set<SuspendingEchoService>(
    "jsSuspendingEchoService",
    EchoSerializersModule,
    JsSuspendingEchoService("hello")
  )
}

@JsExport
fun callSuspendingEchoService(message: String) {
  val service = Zipline.get<SuspendingEchoService>("jvmSuspendingEchoService", EchoSerializersModule)
  CoroutineScope(EmptyCoroutineContext).launch(Dispatchers.Main) {
    service.suspendingEcho(EchoRequest(message))
  }
}
