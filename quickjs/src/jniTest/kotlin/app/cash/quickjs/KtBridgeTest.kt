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
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

class KtBridgeTest {
  private val zipline = Zipline.create()

  @Before fun setUp() {
    zipline.runBlocking {
      zipline.loadTestingJs()
    }
  }

  @After fun tearDown() {
    zipline.runBlocking {
      quickJs.close()
    }
    (zipline.dispatcher as? ExecutorCoroutineDispatcher)?.close()
  }

  @Test fun jvmCallJsService() {
    zipline.runBlocking {
      quickJs.evaluate("testing.app.cash.quickjs.testing.prepareJsBridges()")

      assertThat(helloService.echo(EchoRequest("Jake")))
        .isEqualTo(EchoResponse("hello from JavaScript, Jake"))
      assertThat(yoService.echo(EchoRequest("Kevin")))
        .isEqualTo(EchoResponse("yo from JavaScript, Kevin"))
    }
  }

  @Test fun jsCallJvmService() {
    zipline.runBlocking {
      prepareJvmBridges(zipline)

      assertThat(quickJs.evaluate(
        "testing.app.cash.quickjs.testing.callSupService('homie')"
      )).isEqualTo("JavaScript received 'sup from the JVM, homie' from the JVM")
    }
  }
}
