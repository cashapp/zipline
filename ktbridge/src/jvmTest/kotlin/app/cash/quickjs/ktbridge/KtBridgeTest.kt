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
import app.cash.quickjs.ktbridge.testing.EchoJsAdapter
import app.cash.quickjs.ktbridge.testing.EchoRequest
import app.cash.quickjs.ktbridge.testing.EchoResponse
import app.cash.quickjs.ktbridge.testing.EchoService
import com.google.common.truth.Truth.assertThat
import java.io.File
import okio.buffer
import okio.source
import org.junit.After
import org.junit.Before
import org.junit.Test

class KtBridgeTest {
  private lateinit var quickjs: QuickJs

  @Before fun setUp() {
    quickjs = QuickJs.create()
  }

  @After fun tearDown() {
    quickjs.close()
  }

  @Test fun happyPath() {
    val testingJs = File("testing/build/distributions/testing.js").source().buffer().use { source ->
      source.readUtf8()
    }
    quickjs.evaluate(testingJs, "testing.js")

    val moduleName = "testing"
    val packageName = "app.cash.quickjs.ktbridge.testing"

    val helloService = quickjs.getBridgeToJs<EchoService>(
      webpackModuleName = moduleName,
      packageName = packageName,
      propertyName = "helloService",
      jsAdapter = EchoJsAdapter
    )

    val yoService = quickjs.getBridgeToJs<EchoService>(
      webpackModuleName = moduleName,
      packageName = packageName,
      propertyName = "yoService",
      jsAdapter = EchoJsAdapter
    )

    assertThat(helloService.echo(EchoRequest("Jake")))
      .isEqualTo(EchoResponse("hello from JavaScript, Jake"))
    assertThat(yoService.echo(EchoRequest("Kevin")))
      .isEqualTo(EchoResponse("yo from JavaScript, Kevin"))
  }
}
