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
package app.cash.zipline

import app.cash.zipline.internal.consoleName
import app.cash.zipline.internal.eventLoopName
import app.cash.zipline.internal.jsPlatformName
import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.helloService
import app.cash.zipline.testing.jsSuspendingEchoService
import app.cash.zipline.testing.prepareSuspendingJvmBridges
import app.cash.zipline.testing.yoService
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.After
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

  @Test fun cannotGetOrSetServiceAfterClose(): Unit = runBlocking(dispatcher) {
    zipline.close()
    assertThat(assertFailsWith<IllegalStateException> {
      zipline.get<EchoService>("helloService")
    })
    assertThat(assertFailsWith<IllegalStateException> {
      zipline.set<EchoService>("supService", JvmEchoService("sup"))
    })
  }

  @Test fun callServiceAfterCloseFailsGracefully(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")
    val service = zipline.helloService

    zipline.close()
    assertThat(assertFailsWith<IllegalStateException> {
      service.echo(EchoRequest("Jake"))
    }).hasMessageThat().isEqualTo("Zipline closed")
  }

  @Test fun jvmCallJsService(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")

    assertThat(zipline.helloService.echo(EchoRequest("Jake")))
      .isEqualTo(EchoResponse("hello from JavaScript, Jake"))
    assertThat(zipline.yoService.echo(EchoRequest("Kevin")))
      .isEqualTo(EchoResponse("yo from JavaScript, Kevin"))
  }

  @Test fun jsCallJvmService(): Unit = runBlocking(dispatcher) {
    zipline.set<EchoService>("supService", JvmEchoService("sup"))

    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSupService('homie')"))
      .isEqualTo("JavaScript received 'sup from the JVM, homie' from the JVM")
  }

  @Test fun jvmCallJsServiceThatThrows(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareThrowingJsBridges()")

    assertThat(assertFailsWith<Exception> {
      zipline.helloService.echo(EchoRequest("Jake"))
    }).hasMessageThat().contains("boom!") // 'IllegalStateException' prefix lost when we minify JS.
  }

  @Test fun jsCallJvmServiceThatThrows(): Unit = runBlocking(dispatcher) {
    zipline.set<EchoService>("supService", JvmThrowingEchoService())

    assertThat(assertFailsWith<QuickJsException> {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSupService('homie')")
    }).hasMessageThat().contains("java.lang.IllegalStateException: boom!")
  }

  @Test fun suspendingJvmCallJsService(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareSuspendingJsBridges()")

    assertThat(zipline.jsSuspendingEchoService.suspendingEcho(EchoRequest("Jake")))
      .isEqualTo(EchoResponse("hello from suspending JavaScript, Jake"))
  }

  @Test fun suspendingJsCallJvmService(): Unit = runBlocking(dispatcher) {
    prepareSuspendingJvmBridges(zipline)

    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSuspendingEchoService('Eric')")
  }

  @Test fun serviceNamesAndClientNames(): Unit = runBlocking(dispatcher) {
    assertThat(zipline.serviceNames).containsExactly(
      eventLoopName,
      consoleName,
    )
    assertThat(zipline.clientNames).containsExactly(
      jsPlatformName,
    )

    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")
    assertThat(zipline.serviceNames).containsExactly(
      eventLoopName,
      consoleName,
    )
    assertThat(zipline.clientNames).containsExactly(
      jsPlatformName,
      "helloService",
      "yoService",
    )

    zipline.set<EchoService>("supService", JvmEchoService("sup"))
    assertThat(zipline.serviceNames).containsExactly(
      eventLoopName,
      consoleName,
      "supService",
    )
    assertThat(zipline.clientNames).containsExactly(
      jsPlatformName,
      "helloService",
      "yoService",
    )
  }

  private class JvmEchoService(private val greeting: String) : EchoService {
    override fun echo(request: EchoRequest): EchoResponse {
      return EchoResponse("$greeting from the JVM, ${request.message}")
    }
  }

  private class JvmThrowingEchoService : EchoService {
    override fun echo(request: EchoRequest): EchoResponse {
      throw IllegalStateException("boom!")
    }
  }
}
