/*
 * Copyright (C) 2023 Block, Inc.
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

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Manages a single-threaded dispatcher for testing.
 */
class ZiplineTestRule : TestRule {
  private val exceptions = mutableListOf<Throwable>()

  private val executorService = ThreadPoolExecutor(
    0, 1, 100, TimeUnit.MILLISECONDS, LinkedBlockingQueue()
  ) { runnable ->
    Thread(runnable, "ZiplineTestRule").also {
      it.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, exception ->
        exceptions += exception
      }
    }
  }

  val dispatcher = executorService.asCoroutineDispatcher()

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        try {
          base.evaluate()
        } finally {
          executorService.shutdown()
          executorService.awaitTermination(3, TimeUnit.SECONDS)

          if (exceptions.isNotEmpty()) {
            throw AssertionError("uncaught exception").initCause(exceptions.first())
          }
        }
      }
    }
  }
}
