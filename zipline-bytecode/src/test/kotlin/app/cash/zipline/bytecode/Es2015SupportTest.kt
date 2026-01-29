/*
 * Copyright (C) 2025 Cash App
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
package app.cash.zipline.bytecode

import app.cash.zipline.QuickJs
import assertk.assertThat
import assertk.assertions.startsWith
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Test

/**
 * Test that we benefit from QuickJS' support for ES2015 features.
 */
class Es2015SupportTest {
  private val quickJs = QuickJs.create()

  @After
  fun tearDown() {
    quickJs.close()
  }

  /** Confirm that when we write JS as ES2015 we get stack traces with function names in them. */
  @Test
  fun stackTracesHaveFunctionNames() {
    val js = """
      |class Bomb {
      |  constructor(message) {
      |    this.message = message;
      |  }
      |
      |  sayHello() {
      |    this.goBoom3();
      |  }
      |
      |  goBoom3() {
      |    this.goBoom2();
      |  }
      |
      |  goBoom2() {
      |    this.goBoom1();
      |  }
      |
      |  goBoom1() {
      |    throw Error(this.message);
      |  }
      |}
      """.trimMargin()

    val bytecode = quickJs.compile(js, "demo.js")
    quickJs.execute(bytecode)
    val exception = assertFailsWith<Exception> {
      quickJs.evaluate("new Bomb(\"boom!\").sayHello()")
    }
    assertThat(exception.stackTraceToString()).startsWith(
      """
      |app.cash.zipline.QuickJsException: boom!
      |	at JavaScript.goBoom1(demo.js:19)
      |	at JavaScript.goBoom2(demo.js:15)
      |	at JavaScript.goBoom3(demo.js:11)
      |	at JavaScript.sayHello(demo.js:7)
      |	at JavaScript.<eval>(?)
      """.trimMargin(),
    )
  }
}
