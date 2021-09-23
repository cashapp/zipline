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
package app.cash.zipline

import kotlin.reflect.KClass

/**
 * An EMCAScript (Javascript) interpreter backed by the 'QuickJS' native engine.
 *
 * This class is NOT thread safe. If multiple threads access an instance concurrently it must be
 * synchronized externally.
 */
expect class QuickJs {
  companion object {
    /**
     * Create a new interpreter instance. Calls to this method **must** matched with
     * calls to [close] on the returned instance to avoid leaking native memory.
     */
    fun create(): QuickJs

    val version: String
  }

  /**
   * The interrupt handler is polled frequently during code execution.
   *
   * Using any interrupt handler may have a significant performance cost. Use a null handler for
   * best performance.
   */
  var interruptHandler: InterruptHandler?

  /** Default is -1. Use -1 for no limit. */
  var memoryLimit: Long

  /** Default is 256 KiB. Use -1 to disable automatic GC. */
  var gcThreshold: Long

  /** Default is 512 KiB. Use 0 to disable the maximum stack size check. */
  var maxStackSize: Long

  /**
   * Evaluate [script] and return any result. [fileName] will be used in error
   * reporting.
   *
   * @throws QuickJsException if there is an error evaluating the script.
   */
  fun evaluate(script: String, fileName: String = "?"): Any?

  /**
   * Compile [sourceCode] and return the bytecode. [fileName] will be used in error
   * reporting.
   *
   * @throws QuickJsException if the sourceCode could not be compiled.
   */
  fun compile(sourceCode: String, fileName: String): ByteArray

  /**
   * Load and execute [bytecode] and return the result.
   *
   * @throws QuickJsException if there is an error loading or executing the code.
   */
  fun execute(bytecode: ByteArray): Any?

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
  operator fun <T : Any> get(name: String, type: KClass<T>): T

  /**
   * Provides [instance] to JavaScript as a global object called [name]. [type]
   * defines the interface implemented by [instance] that will be accessible to JavaScript.
   * [type] must be an interface that does not extend any other interfaces, and cannot define
   * any overloaded methods.
   *
   * Methods of the interface may return `void` or any of the following supported argument
   * types: `boolean`, [Boolean], `int`, [Integer], `double`, [Double], [String].
   */
  operator fun <T : Any> set(name: String, type: KClass<T>, instance: T)

  fun close()
}
