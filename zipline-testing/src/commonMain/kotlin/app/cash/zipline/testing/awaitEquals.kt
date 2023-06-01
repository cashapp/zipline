/*
 * Copyright (C) 2023 Cash App
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
package app.cash.zipline.testing

import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

/**
 * Yields or delays until [expected] equals the result of [actual].
 *
 * Use this to assert asynchronously triggered side effects, such as resource cleanups.
 */
suspend fun <T> awaitEquals(
  expected: T?,
  delay: Duration = Duration.ZERO,
  attempts: Int = 5,
  actual: () -> T?,
) {
  var actualValue = actual()
  if (expected == actualValue) return
  repeat(attempts) {
    if (delay.isPositive()) {
      delay(delay)
    } else {
      yield()
    }
    actualValue = actual()
    if (expected == actualValue) return
  }
  throw AssertionError("$expected != $actualValue")
}
