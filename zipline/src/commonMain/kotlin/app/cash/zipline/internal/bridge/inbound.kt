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
package app.cash.zipline.internal.bridge

import app.cash.zipline.ZiplineApiMismatchException
import app.cash.zipline.ZiplineService
import kotlinx.serialization.json.Json

/**
 * Generated code extends this base class to receive calls into an application-layer interface from
 * another platform in the same process.
 */
@PublishedApi
internal interface InboundBridge {
  class Context(
    val name: String,
    val service: ZiplineService,
    val json: Json,
    @PublishedApi internal val endpoint: Endpoint,
  ) {
    val serializersModule = json.serializersModule
  }
}

fun unexpectedFunction(functionName: String?, supportedFunctionNames: Set<String>) = ZiplineApiMismatchException(
  buildString {
    appendLine("no such method (incompatible API versions?)")
    appendLine("\tcalled:")
    append("\t\t")
    appendLine(functionName)
    appendLine("\tavailable:")
    supportedFunctionNames.joinTo(this, separator = "\n") { "\t\t$it" }
  }
)
