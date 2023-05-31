/*
 * Copyright (C) 2022 Block, Inc.
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

import assertk.assertThat
import assertk.assertions.startsWith
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Confirm our JavaScript engine doesn't allow eval.
 */
class NoEvalTest {
  private val quickJs = QuickJs.create()

  @AfterTest
  fun tearDown() {
    quickJs.close()
  }

  @Test
  fun quickJsDoesNotSupportEval() {
    // QuickJs.evaluate works
    assertEquals(6, quickJs.evaluate("3+3", "shouldSucceed.js"))

    val e = assertFailsWith<Exception> {
      // eval in JS code doesn't
      quickJs.evaluate("eval('3+3')", "shouldFail.js")
    }
    assertThat(e.message!!).startsWith("eval is not supported")
  }
}
