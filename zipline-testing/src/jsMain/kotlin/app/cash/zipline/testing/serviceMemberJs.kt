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
package app.cash.zipline.testing

import app.cash.zipline.Zipline

/** Wraps a service provided by a caller and adds a prefix. */
class JsServiceTransformer : ServiceTransformer {
  override fun addPrefix(serviceAndPrefix: ServiceAndPrefix): ServiceAndPrefix {
    val (prefix, delegate) = serviceAndPrefix
    return ServiceAndPrefix(prefix = "", PrefixingEchoService(prefix, delegate))
  }
}

private class PrefixingEchoService(
  private val prefix: String,
  private val delegate: EchoService,
) : EchoService {
  override fun echo(request: EchoRequest): EchoResponse {
    val response = delegate.echo(request)
    return response.copy(message = prefix + response.message)
  }

  override fun close() {
    delegate.close()
  }
}

private val zipline by lazy { Zipline.get(ServiceMemberSerializersModule) }

@JsExport
fun prepareServiceMemberJsBridges() {
  zipline.bind<ServiceTransformer>(
    "serviceTransformer",
    JsServiceTransformer(),
  )
}
