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
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.reflect.cast
import okio.Buffer

actual interface BridgeToJs<T : Any> {
  fun get(quickJs: QuickJs): T
}

fun <T : Any> createJsClient(jsAdapter: JsAdapter, webpackModuleName: String): BridgeToJs<T> =
  error("unexpected call to createBridgeToJs: is KtBridge plugin configured?")

/** This is invoked by compiler-plugin-rewritten code. */
// @Deprecated(
//   level = DeprecationLevel.HIDDEN,
//   message = "call the one-argument form and let the compiler rewrite calls to use this",
// )
fun <T : Any> createJsClient(
  type: KClass<T>,
  jsAdapter: JsAdapter,
  webpackModuleName: String,
  packageName: String,
  propertyName: String,
): BridgeToJs<T> {
  return object : BridgeToJs<T> {
    override fun get(quickJs: QuickJs): T {
      val globalName = "ktBridge_${nextGlobalId.getAndIncrement()}"
      quickJs.evaluate("this.$globalName = $webpackModuleName.$packageName.$propertyName")
      val internalBridge = quickJs.get(globalName, InternalBridge::class.java) as InternalBridge<T>
      return internalBridge.toProxy(type, jsAdapter)
    }
  }
}

// TODO(jwilson): make private.
fun <T : Any> InternalBridge<T>.toProxy(
  type: KClass<T>,
  jsAdapter: JsAdapter
): T {
  val invocationHandler = object : InvocationHandler {
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {
      val args = args ?: arrayOf()

      val buffer = Buffer()
      val eachValueBuffer = Buffer()

      buffer.writeInt(args.size)
      for (i in args.indices) {
        jsAdapter.encode(args[i], eachValueBuffer, method.parameterTypes[i].kotlin as KClass<Any>)
        buffer.writeInt(eachValueBuffer.size.toInt())
        buffer.writeAll(eachValueBuffer)
      }

      val encodedResponse = invokeJs(method.name, buffer.readByteArray())
      buffer.write(encodedResponse)

      return jsAdapter.decode(buffer, method.returnType.kotlin)
    }
  }

  val proxy = Proxy.newProxyInstance(type.java.classLoader, arrayOf(type.java), invocationHandler)
  return type.cast(proxy)
}

private val nextGlobalId = AtomicInteger(1)
