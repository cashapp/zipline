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
package app.cash.quickjs

import java.io.Closeable

/**
 * An EMCAScript (Javascript) interpreter backed by the 'QuickJS' native engine.
 *
 * This class is NOT thread safe. If multiple threads access an instance concurrently it must be
 * synchronized externally.
 */
actual abstract class QuickJs : Closeable {
  actual abstract val engineVersion: String

  actual fun <T : Any> get(name: String, jsAdapter: JsAdapter): T {
    error("unexpected call to QuickJs.get: is QuickJs Gradle plugin configured?")
  }
  actual fun <T : Any> set(name: String, jsAdapter: JsAdapter, instance: T) {
    error("unexpected call to QuickJs.set: is QuickJs Gradle plugin configured?")
  }

  @PublishedApi
  internal abstract fun set(name: String, handler: InboundService<*>)

  @PublishedApi
  internal abstract fun <T : Any> get(
    name: String,
    outboundClientFactory: OutboundClientFactory<T>,
  ): T

  /**
   * Evaluate [script] and return any result. [fileName] will be used in error
   * reporting.
   *
   * @throws QuickJsException if there is an error evaluating the script.
   */
  abstract fun evaluate(script: String, fileName: String = "?"): Any?

  /**
   * Provides [instance] to JavaScript as a global object called [name]. [type]
   * defines the interface implemented by [instance] that will be accessible to JavaScript.
   * [type] must be an interface that does not extend any other interfaces, and cannot define
   * any overloaded methods.
   *
   * Methods of the interface may return `void` or any of the following supported argument
   * types: `boolean`, [Boolean], `int`, [Integer], `double`, [Double], [String].
   */
  abstract operator fun <T : Any> set(name: String, type: Class<T>, instance: T)

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
  abstract operator fun <T : Any> get(name: String, type: Class<T>): T

  /**
   * Compile [sourceCode] and return the bytecode. [fileName] will be used in error
   * reporting.
   *
   * @throws QuickJsException if the sourceCode could not be compiled.
   */
  abstract fun compile(sourceCode: String, fileName: String): ByteArray

  /**
   * Load and execute [bytecode] and return the result.
   *
   * @throws QuickJsException if there is an error loading or executing the code.
   */
  abstract fun execute(bytecode: ByteArray): Any?

  companion object {
    /**
     * Create a new interpreter instance. Calls to this method **must** matched with
     * calls to [close] on the returned instance to avoid leaking native memory.
     */
    @JvmStatic
    fun create(): QuickJs = JniQuickJs.create()
  }
}
