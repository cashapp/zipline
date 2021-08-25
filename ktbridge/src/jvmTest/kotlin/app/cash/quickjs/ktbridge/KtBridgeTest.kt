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
package app.cash.quickjs.ktbridge

import app.cash.quickjs.QuickJs
import app.cash.quickjs.ktbridge.testing.EchoRequest
import app.cash.quickjs.ktbridge.testing.EchoResponse
import app.cash.quickjs.ktbridge.testing.helloService
import app.cash.quickjs.ktbridge.testing.prepareJvmBridges
import app.cash.quickjs.ktbridge.testing.yoService
import com.google.common.truth.Truth.assertThat
import java.io.BufferedReader
import org.junit.After
import org.junit.Test

class KtBridgeTest {
  private val quickjs = QuickJs.create()

  @After fun tearDown() {
    quickjs.close()
  }

  @Test fun `jvm call js service`() {
    val testingJs = KtBridgeTest::class.java.getResourceAsStream("/testing.js")!!
        .bufferedReader()
        .use(BufferedReader::readText)
    quickjs.evaluate(testingJs, "testing.js")
    quickjs.evaluate("testing.app.cash.quickjs.ktbridge.testing.prepareJsBridges()")

    val ktBridge = createKtBridge(quickjs)

    assertThat(ktBridge.helloService.echo(EchoRequest("Jake")))
      .isEqualTo(EchoResponse("hello from JavaScript, Jake"))
    assertThat(ktBridge.yoService.echo(EchoRequest("Kevin")))
      .isEqualTo(EchoResponse("yo from JavaScript, Kevin"))
  }

  @Test fun `js call jvm service`() {
    val testingJs = KtBridgeTest::class.java.getResourceAsStream("/testing.js")!!
      .bufferedReader()
      .use(BufferedReader::readText)
    quickjs.evaluate(testingJs, "testing.js")

    val ktBridge = createKtBridge(quickjs)
    prepareJvmBridges(ktBridge)

    assertThat(quickjs.evaluate(
      "testing.app.cash.quickjs.ktbridge.testing.callSupService('homie')"
    )).isEqualTo("JavaScript received 'sup from the JVM, homie' from the JVM")
  }
}
