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

class JsMessageInterfaceService :
  RequestInterfaceService,
  ResponseInterfaceService {
  override fun echo(request: MessageInterface) = "JS received an interface, ${request.message}"

  override fun echo(request: String) = RealMessageInterface("JS returned an interface, $request")
}

private val zipline by lazy { Zipline.get(MessageInterfaceSerializersModule) }

@JsExport
fun prepareInterfaceSerializersJsBridges() {
  zipline.bind<RequestInterfaceService>(
    "requestInterfaceService",
    JsMessageInterfaceService(),
  )
  zipline.bind<ResponseInterfaceService>(
    "responseInterfaceService",
    JsMessageInterfaceService(),
  )
}

@JsExport
fun callInterfaceResponse(): String {
  val service = zipline.take<ResponseInterfaceService>("responseInterfaceService")
  return service.echo("Jesse").message
}

@JsExport
fun callInterfaceRequest(): String {
  val service = zipline.take<RequestInterfaceService>("requestInterfaceService")
  return service.echo(RealMessageInterface("Jesse"))
}
