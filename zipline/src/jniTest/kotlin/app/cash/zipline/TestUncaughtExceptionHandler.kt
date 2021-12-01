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

/**
 * Track uncaught exceptions in tests. We'd prefer `CoroutineExceptionHandler` except that only
 * works if it's configured for every coroutine, and we don't necessarily know which ones will be
 * launched by the code under test.
 */
class TestUncaughtExceptionHandler : Thread.UncaughtExceptionHandler {
  private var previousDefaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
  private val exceptions = mutableListOf<Throwable>()

  fun setUp() {
    previousDefaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler(this)
  }

  fun tearDown() {
    Thread.setDefaultUncaughtExceptionHandler(previousDefaultUncaughtExceptionHandler)
    if (exceptions.isNotEmpty()) {
      throw AssertionError("uncaught exception").initCause(exceptions.first())
    }
  }

  override fun uncaughtException(thread: Thread, exception: Throwable) {
    exceptions += exception
  }
}
