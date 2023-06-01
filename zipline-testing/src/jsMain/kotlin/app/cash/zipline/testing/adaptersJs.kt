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

class JsAdaptersService : AdaptersService {
  override fun echo(request: AdaptersRequest): AdaptersResponse {
    return AdaptersResponse("thank you for using your serializers, ${request.message}")
  }
}

private val zipline by lazy { Zipline.get(AdaptersSerializersModule) }

@JsExport
fun prepareAdaptersJsBridges() {
  zipline.bind<AdaptersService>(
    "adaptersService",
    JsAdaptersService(),
  )
}

@JsExport
fun callAdaptersService(): String {
  val service = zipline.take<AdaptersService>("adaptersService")
  val response = service.echo(AdaptersRequest("Jesse"))
  return "JavaScript received ${response.message}"
}
