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

import okio.Buffer

fun <T : Any> createBridgeToJs(service: T, jsAdapter: JsAdapter): BridgeToJs<T> =
  JsBridgeToJs(service, jsAdapter)

private class JsBridgeToJs<T : Any>(val service: T, val jsAdapter: JsAdapter) : BridgeToJs<T> {
  override fun invokeJs(funName: String, arguments: ByteArray): ByteArray {
    val buffer = Buffer()
    val eachValueBuffer = Buffer()
    buffer.write(arguments)
    val argumentCount = buffer.readInt()
    val arguments = arrayOfNulls<Any?>(argumentCount)
    for (i in 0 until argumentCount) {
      val argumentByteCount = buffer.readInt()
      eachValueBuffer.write(buffer, argumentByteCount.toLong())
      val argument = jsAdapter.decode(eachValueBuffer, Any::class)
      arguments[i] = argument
    }
    require(buffer.exhausted())
    @Suppress("UNUSED_VARIABLE") val service = this.service
    val result = js("""service[funName].apply(service, arguments)""")
    jsAdapter.encode(result, buffer, Any::class)
    return buffer.readByteArray()
  }
}
