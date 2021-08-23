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

import java.io.Closeable
import java.lang.IllegalArgumentException
import java.lang.UnsupportedOperationException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.logging.Logger

/**
 * An EMCAScript (Javascript) interpreter backed by the 'QuickJS' native engine.
 *
 * This class is NOT thread safe. If multiple threads access an instance concurrently it must be
 * synchronized externally.
 */
class QuickJs private constructor(private var context: Long) : Closeable {
  companion object {
    init {
      loadNativeLibrary()
    }

    /**
     * Create a new interpreter instance. Calls to this method **must** matched with
     * calls to [close] on the returned instance to avoid leaking native memory.
     */
    @JvmStatic
    fun create(): QuickJs {
      val context = createContext()
      if (context == 0L) {
        throw OutOfMemoryError("Cannot create QuickJs instance")
      }
      return QuickJs(context)
    }

    @JvmStatic
    private external fun createContext(): Long
  }

  /**
   * Evaluate [script] and return any result. [fileName] will be used in error
   * reporting.
   *
   * @throws QuickJsException if there is an error evaluating the script.
   */
  @JvmOverloads
  fun evaluate(script: String, fileName: String = "?"): Any? {
    return evaluate(context, script, fileName)
  }

  /**
   * Provides [instance] to JavaScript as a global object called [name]. [type]
   * defines the interface implemented by [instance] that will be accessible to JavaScript.
   * [type] must be an interface that does not extend any other interfaces, and cannot define
   * any overloaded methods.
   *
   * Methods of the interface may return `void` or any of the following supported argument
   * types: `boolean`, [Boolean], `int`, [Integer], `double`, [Double], [String].
   */
  operator fun <T : Any> set(name: String, type: Class<T>, instance: T) {
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

  /**
   * Attaches to a global JavaScript object called `name` that implements `type`.
   * `type` defines the interface implemented in JavaScript that will be accessible to Java.
   * `type` must be an interface that does not extend any other interfaces, and cannot define
   * any overloaded methods.
   *
   * Methods of the interface may return `void` or any of the following supported argument
   * types: `boolean`, [Boolean], `int`, [Integer], `double`,
   * [Double], [String].
   */
  operator fun <T : Any> get(name: String, type: Class<T>): T {
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

  /**
   * Compile [sourceCode] and return the bytecode. [fileName] will be used in error
   * reporting.
   *
   * @throws QuickJsException if the sourceCode could not be compiled.
   */
  fun compile(sourceCode: String, fileName: String): ByteArray {
    return compile(context, sourceCode, fileName)
  }

  /**
   * Load and execute [bytecode] and return the result.
   *
   * @throws QuickJsException if there is an error loading or executing the code.
   */
  fun execute(bytecode: ByteArray): Any? {
    return execute(context, bytecode)
  }

  /**
   * Release the native resources associated with this object. You **must** call this
   * method for each instance to avoid leaking native memory.
   */
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
