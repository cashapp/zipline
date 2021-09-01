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

import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Need to coordinate mutual exclusion")
class SetTimeoutTest {
  private val quickjs = QuickJs.create()

  @Before fun setUp() {
    quickjs.loadTestingJs()
  }

  @After fun tearDown() {
    quickjs.close()
  }

  @Test fun happyPath() {
    quickjs.evaluate("""
      var greeting = 'hello';
      
      var sayGoodbye = function() {
        greeting = 'goodbye';
      };
      
      setTimeout(sayGoodbye, 100);
    """.trimIndent())

    assertEquals("hello", quickjs.evaluate("greeting"))
    Thread.sleep(200)
    assertEquals("goodbye", quickjs.evaluate("greeting"))
  }
}
