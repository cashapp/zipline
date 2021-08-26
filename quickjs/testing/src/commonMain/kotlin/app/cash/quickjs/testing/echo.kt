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
package app.cash.quickjs.testing

import app.cash.quickjs.JsAdapter
import kotlin.reflect.KType
import okio.Buffer

interface EchoService {
  fun echo(request: EchoRequest): EchoResponse
}

data class EchoRequest(
  val message: String
)

data class EchoResponse(
  val message: String
)

object EchoJsAdapter : JsAdapter {
  override fun <T : Any> encode(value: T, sink: Buffer, type: KType) {
    when (type.classifier) {
      EchoRequest::class -> sink.writeUtf8((value as EchoRequest).message)
      EchoResponse::class -> sink.writeUtf8((value as EchoResponse).message)
      else -> error("unexpected type: $type")
    }
  }

  override fun <T : Any> decode(source: Buffer, type: KType): T {
    return when (type.classifier) {
      EchoRequest::class -> EchoRequest(source.readUtf8()) as T
      EchoResponse::class -> EchoResponse(source.readUtf8()) as T
      else -> error("unexpected type: $type")
    }
  }
}
