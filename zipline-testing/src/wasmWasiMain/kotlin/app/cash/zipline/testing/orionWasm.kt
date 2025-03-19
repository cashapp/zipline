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
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor

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
  println("creating descriptor done!!")
  val descriptor = PrimitiveSerialDescriptor("PassByReference", PrimitiveKind.STRING)
  println("creating descriptor done!!")

  theOnlyZipline = Zipline.get()
  println("zipline : $theOnlyZipline")
  val clock = zipline.take<Clock>("clock")
  println("one!!")
  zipline.bind<HttpActionService>("httpActionService", OrionEchoService("hello", clock))
  println("two!!")

  retainExportedFunctions()
  println("three!!")

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
