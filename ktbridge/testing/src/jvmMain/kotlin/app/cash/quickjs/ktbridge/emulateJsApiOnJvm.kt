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
package app.cash.quickjs.ktbridge

import app.cash.quickjs.QuickJs

/*
 * This file mirrors the Kotlin/JS API for Kotlin/JVM to be used by KtBridgePluginTest. We use this
 * to confirm the generated bridge works without a JavaScript engine. This is particularly
 * convenient for testing the compiler plugin.
 *
 * We'd prefer to map the JS sources in to the JVM tests, but that drags in multiplatform machinery
 * due to our use of expect/actual.
 */

fun <T : Any> createJsService(jsAdapter: JsAdapter, service: T): BridgeToJs<T> =
  error("unexpected call")

fun <T : Any> createJsService(
  jsAdapter: JsAdapter,
  service: T,
  block: (InboundCall<T>) -> ByteArray
) : BridgeToJs<T> {
  return object : BridgeToJs<T>, InternalBridge {
    override fun invokeJs(funName: String, encodedArguments: ByteArray): ByteArray {
      val inboundCall = InboundCall(service, funName, encodedArguments, jsAdapter)
      return block(inboundCall)
    }

    override fun get(quickJs: QuickJs): T = error("unexpected call")
  }
}

