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

import app.cash.quickjs.testing.EchoJsAdapter
import app.cash.quickjs.testing.EchoRequest
import app.cash.quickjs.testing.EchoResponse
import app.cash.quickjs.testing.EchoService
import app.cash.quickjs.testing.JvmEchoService
import app.cash.quickjs.testing.helloService
import app.cash.quickjs.testing.jsSuspendingEchoService
import app.cash.quickjs.testing.prepareJvmBridges
import app.cash.quickjs.testing.prepareSuspendingJvmBridges
import app.cash.quickjs.testing.yoService
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZiplineTest {
  private val dispatcher = TestCoroutineDispatcher()
  private val zipline = Zipline.create(dispatcher)

  @Before fun setUp(): Unit = runBlocking(dispatcher) {
    zipline.loadTestingJs()
  }

  @After fun tearDown(): Unit = runBlocking(dispatcher) {
    zipline.close()
  }

  @Test fun version(): Unit = runBlocking(dispatcher) {
    assertNotEquals("", zipline.engineVersion)
  }

  @Test fun cannotGetOrSetServiceAfterClose(): Unit = runBlocking(dispatcher) {
    zipline.close()
    assertThat(assertThrows<IllegalStateException> {
      zipline.get<EchoService>("helloService", EchoJsAdapter)
    })
    assertThat(assertThrows<IllegalStateException> {
      zipline.set<EchoService>("supService", EchoJsAdapter, JvmEchoService("sup"))
    })
  }

  @Test fun callServiceAfterCloseFailsGracefully(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.quickjs.testing.prepareJsBridges()")
    val service = zipline.helloService

    zipline.close()
    assertThat(assertThrows<IllegalStateException> {
      service.echo(EchoRequest("Jake"))
    }).hasMessageThat().isEqualTo("Zipline closed")
  }

  @Test fun jvmCallJsService(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.quickjs.testing.prepareJsBridges()")

    assertThat(zipline.helloService.echo(EchoRequest("Jake")))
      .isEqualTo(EchoResponse("hello from JavaScript, Jake"))
    assertThat(zipline.yoService.echo(EchoRequest("Kevin")))
      .isEqualTo(EchoResponse("yo from JavaScript, Kevin"))
  }

  @Test fun jsCallJvmService(): Unit = runBlocking(dispatcher) {
    prepareJvmBridges(zipline)

    assertThat(zipline.quickJs.evaluate("testing.app.cash.quickjs.testing.callSupService('homie')"))
      .isEqualTo("JavaScript received 'sup from the JVM, homie' from the JVM")
  }

  @Test fun suspendingJvmCallJsService(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.quickjs.testing.prepareSuspendingJsBridges()")

    assertThat(zipline.jsSuspendingEchoService.suspendingEcho(EchoRequest("Jake")))
      .isEqualTo(EchoResponse("hello from suspending JavaScript, Jake"))
  }

  @Test fun suspendingJsCallJvmService(): Unit = runBlocking(dispatcher) {
    prepareSuspendingJvmBridges(zipline)

    zipline.quickJs.evaluate("testing.app.cash.quickjs.testing.callSuspendingEchoService('Eric')")
  }
}
