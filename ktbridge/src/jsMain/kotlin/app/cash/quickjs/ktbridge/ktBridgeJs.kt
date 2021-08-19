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

actual interface BridgeToJs<T : Any>

/**
 * Call this to expose a JavaScript service to the JVM.
 *
 * The KtBridge Kotlin compiler plugin will rewrite calls to this function to insert a third
 * parameter to invoke the 3-argument overload.
 */
fun <T : Any> createJsService(jsAdapter: JsAdapter, service: T): BridgeToJs<T> =
  error("unexpected call to createJsService: is KtBridge plugin configured?")

/** This is invoked by compiler-plugin-rewritten code. */
@PublishedApi
internal fun <T : Any> createJsService(
  jsAdapter: JsAdapter,
  service: T,
  block: (InboundCall<T>) -> ByteArray
) : BridgeToJs<T> {
  return object : BridgeToJs<T>, InternalBridge<T> {
    override fun invokeJs(funName: String, encodedArguments: ByteArray): ByteArray {
      val inboundCall = InboundCall(service, funName, encodedArguments, jsAdapter)
      return block(inboundCall)
    }
  }
}

