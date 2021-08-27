/*
 * Copyright (C) 2015 Square, Inc.
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
package app.cash.quickjs

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.logging.Logger
import kotlin.LazyThreadSafetyMode.NONE

internal class JniQuickJs(private var context: Long) : QuickJs(), InternalBridge {
  companion object {
    init {
      loadNativeLibrary()
    }

    fun create(): JniQuickJs {
      val context = createContext()
      if (context == 0L) {
        throw OutOfMemoryError("Cannot create QuickJs instance")
      }
      val quickJs = JniQuickJs(context)

      // Eagerly expose the instance to JS as the outbound bridge so they can call us.
      quickJs.set(
          name = "app_cash_quickjs_outboundBridge",
          type = InternalBridge::class.java,
          instance = quickJs,
      )

      return quickJs
    }

    @JvmStatic
    private external fun createContext(): Long
  }

  private val inboundHandlers = mutableMapOf<String, InboundService<*>>()
  private val outboundBridge by lazy(NONE) {
    get(name = "app_cash_quickjs_inboundBridge", type = InternalBridge::class.java)
  }

  override fun invokeJs(
    instanceName: String,
    funName: String,
    encodedArguments: ByteArray
  ): ByteArray {
    val handler = inboundHandlers[instanceName] ?: error("no handler for $instanceName")
    return handler.call(InboundCall(funName, encodedArguments, handler.jsAdapter))
  }

  override fun set(name: String, handler: InboundService<*>) {
    inboundHandlers[name] = handler
  }

  override fun <T : Any> get(
    name: String,
    outboundClientFactory: OutboundClientFactory<T>
  ): T {
    return outboundClientFactory.create(
        OutboundCall.Factory(name, outboundClientFactory.jsAdapter, outboundBridge)
    )
  }

  override val engineVersion get() = quickJsVersion

  override fun evaluate(script: String, fileName: String): Any? {
    return evaluate(context, script, fileName)
  }

  override operator fun <T : Any> set(name: String, type: Class<T>, instance: T) {
    if (!type.isInterface) {
      throw UnsupportedOperationException(
          "Only interfaces can be bound. Received: $type")
    }
    if (type.interfaces.isNotEmpty()) {
      throw UnsupportedOperationException("$type must not extend other interfaces")
    }
    if (!type.isInstance(instance)) {
      throw IllegalArgumentException(
          instance.javaClass.toString() + " is not an instance of " + type)
    }
    val methods = mutableMapOf<String, Method>()
    for (method in type.methods) {
      if (methods.put(method.name, method) != null) {
        throw UnsupportedOperationException(method.name + " is overloaded in " + type)
      }
    }
    set(context, name, instance, methods.values.toTypedArray())
  }

  override operator fun <T : Any> get(name: String, type: Class<T>): T {
    if (!type.isInterface) {
      throw UnsupportedOperationException(
          "Only interfaces can be proxied. Received: $type")
    }
    if (type.interfaces.isNotEmpty()) {
      throw UnsupportedOperationException("$type must not extend other interfaces")
    }
    val methods = mutableMapOf<String, Method>()
    for (method in type.methods) {
      if (methods.put(method.name, method) != null) {
        throw UnsupportedOperationException(method.name + " is overloaded in " + type)
      }
    }
    val instance = get(context, name, methods.values.toTypedArray())
    if (instance == 0L) {
      throw OutOfMemoryError("Cannot create QuickJs proxy to $name")
    }
    val proxy = Proxy.newProxyInstance(type.classLoader, arrayOf(type),
        object : InvocationHandler {
          @Throws(Throwable::class)
          override fun invoke(
            proxy: Any,
            method: Method,
            args: Array<Any>?,
          ): Any? {
            val nonNullArgs = args ?: emptyArray()
            // If the method is a method from Object then defer to normal invocation.
            return if (method.declaringClass == Any::class.java) {
              method.invoke(this, *nonNullArgs)
            } else {
              call(context, instance, method, nonNullArgs)
            }
          }

          override fun toString(): String {
            return String.format("QuickJsProxy{name=%s, type=%s}", name, type.name)
          }
        })
    @Suppress("UNCHECKED_CAST")
    return proxy as T
  }

  override fun compile(sourceCode: String, fileName: String): ByteArray {
    return compile(context, sourceCode, fileName)
  }

  override fun execute(bytecode: ByteArray): Any? {
    return execute(context, bytecode)
  }

  override fun close() {
    val contextToClose = context
    if (contextToClose != 0L) {
      context = 0
      destroyContext(contextToClose)
    }
  }

  protected fun finalize() {
    if (context != 0L) {
      Logger.getLogger(javaClass.name).warning("QuickJs instance leaked!")
    }
  }

  private external fun destroyContext(context: Long)
  private external fun evaluate(context: Long, sourceCode: String, fileName: String): Any?
  private external operator fun get(context: Long, name: String, methods: Array<Any>): Long
  private external operator fun set(context: Long, name: String, `object`: Any, methods: Array<Any>)
  private external fun call(context: Long, instance: Long, method: Any, args: Array<Any>): Any
  private external fun execute(context: Long, bytecode: ByteArray): Any?
  private external fun compile(context: Long, sourceCode: String, fileName: String): ByteArray
}
