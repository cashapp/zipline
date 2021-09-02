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

import java.io.BufferedReader
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

inline fun <reified T : Throwable> assertThrows(body: () -> Unit): T {
  try {
    body()
  } catch (t: Throwable) {
    if (t is T) {
      return t
    }
    throw t
  }
  throw AssertionError(
      "Expected body to fail with ${T::class.simpleName!!} but completed successfully")
}

fun Zipline.loadTestingJs() {
  val testingJs = Zipline::class.java.getResourceAsStream("/testing.js")!!
    .bufferedReader()
    .use(BufferedReader::readText)
  quickJs.evaluate(testingJs, "testing.js")
}

/** Execute [block] on the JavaScript dispatcher and wait for it to return. */
inline fun <T> Zipline.runBlocking(crossinline block: suspend Zipline.() -> T): T {
  return kotlinx.coroutines.runBlocking(dispatcher) {
    block()
  }
}

/** Enqueue [block] to run on the JavaScript dispatcher and return immediately. */
inline fun Zipline.launch(crossinline block: suspend Zipline.() -> Unit) {
  CoroutineScope(EmptyCoroutineContext).launch(dispatcher) {
    block()
  }
}
