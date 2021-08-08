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

/**
 * Call this in Kotlin/JVM to get an object that's defined in Kotlin/JS.
 */
inline fun <reified T : Any> QuickJs.getBridgeToJs(
  webpackModuleName: String,
  packageName: String,
  propertyName: String,
  jsAdapter: JsAdapter
): T = getBridgeToJs(webpackModuleName, packageName, propertyName, jsAdapter, T::class)

fun <T : Any> QuickJs.getBridgeToJs(
  webpackModuleName: String,
  packageName: String,
  propertyName: String,
  jsAdapter: JsAdapter,
  type: KClass<T>
): T {
  val bridgeToJs: BridgeToJs<T> = run {
    val globalName = "ktBridge_${nextGlobalId.getAndIncrement()}"
    evaluate("this.$globalName = $webpackModuleName.$packageName.$propertyName")
    get(globalName, BridgeToJs::class.java) as BridgeToJs<T>
  }

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

      val encodedResponse = bridgeToJs.invokeJs(method.name, buffer.readByteArray())
      buffer.write(encodedResponse)

      return jsAdapter.decode(buffer, method.returnType.kotlin)
    }
  }

  val proxy = Proxy.newProxyInstance(type.java.classLoader, arrayOf(type.java), invocationHandler)
  return type.cast(proxy)
}

private val nextGlobalId = AtomicInteger(1)
