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

class JsThrowingEchoService : EchoService {
  override fun echo(request: EchoRequest): EchoResponse {
    goBoom3()
  }
  private fun goBoom3(): Nothing {
    goBoom2()
  }
  private fun goBoom2(): Nothing {
    goBoom1()
  }
  private fun goBoom1(): Nothing {
    throw IllegalStateException("boom!")
  }
}

class JsDelegatingEchoService(
  private val delegate: EchoService,
) : EchoService {
  override fun echo(request: EchoRequest): EchoResponse {
    return delegate3(request)
  }
  private fun delegate3(request: EchoRequest): EchoResponse {
    return delegate2(request)
  }
  private fun delegate2(request: EchoRequest): EchoResponse {
    return delegate1(request)
  }
  private fun delegate1(request: EchoRequest): EchoResponse {
    return delegate.echo(request)
  }
}

private val zipline by lazy { Zipline.get() }

@JsExport
fun prepareThrowingJsBridges() {
  zipline.bind<EchoService>("throwingService", JsThrowingEchoService())
}

@JsExport
fun callThrowingService(message: String): String {
  val service = zipline.take<EchoService>("throwingService")
  val echoResponse = service.echo(EchoRequest(message))
  return "JavaScript received '${echoResponse.message}' from the JVM"
}

@JsExport
fun prepareDelegatingService() {
  val delegate = zipline.take<EchoService>("throwingService")
  zipline.bind<EchoService>("delegatingService", JsDelegatingEchoService(delegate))
}
