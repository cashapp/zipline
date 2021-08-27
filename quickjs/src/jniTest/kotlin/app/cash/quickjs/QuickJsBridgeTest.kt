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

import app.cash.quickjs.testing.EchoRequest
import app.cash.quickjs.testing.EchoResponse
import app.cash.quickjs.testing.helloService
import app.cash.quickjs.testing.prepareJvmBridges
import app.cash.quickjs.testing.yoService
import com.google.common.truth.Truth.assertThat
import java.io.BufferedReader
import org.junit.After
import org.junit.Test

class QuickJsBridgeTest {
  private val quickjs = QuickJs.create()

  @After fun tearDown() {
    quickjs.close()
  }

  @Test fun jvmCallJsService() {
    val testingJs = QuickJsBridgeTest::class.java.getResourceAsStream("/testing.js")!!
        .bufferedReader()
        .use(BufferedReader::readText)
    quickjs.evaluate(testingJs, "testing.js")
    quickjs.evaluate("testing.app.cash.quickjs.testing.prepareJsBridges()")

    assertThat(quickjs.helloService.echo(EchoRequest("Jake")))
      .isEqualTo(EchoResponse("hello from JavaScript, Jake"))
    assertThat(quickjs.yoService.echo(EchoRequest("Kevin")))
      .isEqualTo(EchoResponse("yo from JavaScript, Kevin"))
  }

  @Test fun jsCallJvmService() {
    val testingJs = QuickJsBridgeTest::class.java.getResourceAsStream("/testing.js")!!
      .bufferedReader()
      .use(BufferedReader::readText)
    quickjs.evaluate(testingJs, "testing.js")

    prepareJvmBridges(quickjs)

    assertThat(quickjs.evaluate(
      "testing.app.cash.quickjs.testing.callSupService('homie')"
    )).isEqualTo("JavaScript received 'sup from the JVM, homie' from the JVM")
  }
}
