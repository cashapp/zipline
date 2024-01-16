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
package app.cash.zipline.bytecode

import app.cash.zipline.QuickJs
import assertk.assertThat
import assertk.assertions.startsWith
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Test

class StripLineNumbersTest {
  private val quickJs = QuickJs.create()

  @After fun tearDown() {
    quickJs.close()
  }

  @Test fun happyPath() {
    val js = """
      |function sayHello() {
      |  goBoom3();
      |}
      |function goBoom3() {
      |  goBoom2();
      |}
      |function goBoom2() {
      |  goBoom1();
      |}
      |function goBoom1() {
      |  throw Error('boom!');
      |}
      |
      """.trimMargin()

    val bytecode = quickJs.compile(js, "demo.js")
    val updatedBytecode = stripLineNumbers(bytecode)
    quickJs.execute(updatedBytecode)
    val exception = assertFailsWith<Exception> {
      quickJs.evaluate("sayHello()")
    }
    assertThat(exception.stackTraceToString()).startsWith(
      """
      |app.cash.zipline.QuickJsException: boom!
      |	at JavaScript.goBoom1(demo.js)
      |	at JavaScript.goBoom2(demo.js)
      |	at JavaScript.goBoom3(demo.js)
      |	at JavaScript.sayHello(demo.js)
      |	at JavaScript.<eval>(?)
      |
      """.trimMargin(),
    )
  }
}
