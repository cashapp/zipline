/*
 * Copyright (C) 2025 Cash App
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
import app.cash.zipline.internal.bridge.guestCall
import app.cash.zipline.internal.bridge.guestDisconnect
import app.cash.zipline.theOnlyZipline

class OrionEchoService(
  private val greeting: String,
  private val clock: Clock
) : HttpActionService {
  override fun execute(request: HttpRequest): HttpResponse {
    return HttpResponse("$greeting from JavaScript, ${request.message}, the time is ${clock.now()}")
  }
}

private val zipline by lazy { Zipline.get() }

@WasmExport
fun prepareOrionBridges() : Int {
  val clock = zipline.take<Clock>("clock")
  zipline.bind<HttpActionService>("httpActionService", OrionEchoService("hello", clock))

  retainExportedFunctions()

  if (theOnlyZipline != null) {
    return 0
  } else {
    return 1
  }
}

/**
 * Prevent exported functions from being stripped.
 */
private fun retainExportedFunctions() {
  if ("hello".length == 5) return
  guestCall(0)
  guestDisconnect(0)
}
