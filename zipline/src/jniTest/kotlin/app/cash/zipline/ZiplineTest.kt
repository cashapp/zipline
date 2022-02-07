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
import app.cash.zipline.testing.PotatoService
import app.cash.zipline.testing.SuspendingEchoService
import app.cash.zipline.testing.SuspendingPotatoService
import app.cash.zipline.testing.helloService
import app.cash.zipline.testing.yoService
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZiplineTest {
  private val dispatcher = TestCoroutineDispatcher()
  private val zipline = Zipline.create(dispatcher)
  private val uncaughtExceptionHandler = TestUncaughtExceptionHandler()

  @Before fun setUp(): Unit = runBlocking(dispatcher) {
    zipline.loadTestingJs()
    uncaughtExceptionHandler.setUp()
  }

  @After fun tearDown(): Unit = runBlocking(dispatcher) {
    zipline.close()
    uncaughtExceptionHandler.tearDown()
  }

  @Test fun cannotTakeOrBindServiceAfterClose(): Unit = runBlocking(dispatcher) {
    zipline.close()
    assertThat(assertFailsWith<IllegalStateException> {
      zipline.take<EchoService>("helloService")
    })
    assertThat(assertFailsWith<IllegalStateException> {
      zipline.bind<EchoService>("supService", JvmEchoService("sup"))
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
    zipline.bind<EchoService>("supService", JvmEchoService("sup"))

    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSupService('homie')"))
      .isEqualTo("JavaScript received 'sup from the JVM, homie' from the JVM")
  }

  @Test fun suspendingJvmCallJsService(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareSuspendingJsBridges()")

    val jsSuspendingEchoService = zipline.take<SuspendingEchoService>("jsSuspendingEchoService")

    val deferred = async {
      jsSuspendingEchoService.suspendingEcho(EchoRequest("Jake"))
    }

    assertThat(deferred.isCompleted).isFalse()
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.unblockSuspendingJs()")

    assertThat(deferred.await())
      .isEqualTo(EchoResponse("hello from suspending JavaScript, Jake"))
  }

  @Test fun suspendingJsCallJvmService(): Unit = runBlocking(dispatcher) {
    val jvmSuspendingEchoService = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        return EchoResponse("hello from the suspending JVM, ${request.message}")
      }
    }

    zipline.bind<SuspendingEchoService>(
      "jvmSuspendingEchoService",
      jvmSuspendingEchoService
    )

    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSuspendingEchoService('Eric')")
    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.suspendingEchoResult"))
      .isEqualTo("hello from the suspending JVM, Eric")
  }

  /**
   * Don't crash and don't log exceptions if a suspending call completes after the QuickJS instance
   * is closed. Just ignore the response.
   */
  @Test fun suspendingJvmCallCompletesAfterClose(): Unit = runBlocking(dispatcher) {
    val lock = Semaphore(1)
    val jvmSuspendingEchoService = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        lock.withPermit {
          zipline.close()
        }
        return EchoResponse("sup from the suspending JVM, ${request.message}")
      }
    }

    zipline.bind<SuspendingEchoService>(
      "jvmSuspendingEchoService",
      jvmSuspendingEchoService
    )

    lock.withPermit {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSuspendingEchoService('Eric')")
    }
    val e = assertFailsWith<IllegalStateException> {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.suspendingEchoResult")
    }
    assertThat(e).hasMessageThat().isEqualTo("QuickJs instance was closed")
  }

  @Test fun suspendingJsCallCompletesAfterClose(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareSuspendingJsBridges()")

    val jsSuspendingEchoService =
      zipline.take<SuspendingEchoService>("jsSuspendingEchoService")

    val deferred = async {
      jsSuspendingEchoService.suspendingEcho(EchoRequest("Jake"))
    }

    zipline.close()

    assertFailsWith<CancellationException> {
      deferred.await()
    }
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

    zipline.bind<EchoService>("supService", JvmEchoService("sup"))
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

  @Test fun jvmCallIncompatibleJsService(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")

    assertThat(assertFailsWith<Exception> {
      zipline.take<PotatoService>("helloService").echo()
    }).hasMessageThat().startsWith("""
      ZiplineApiMismatchException: no such method (incompatible API versions?)
      	called:
      		fun echo(): app.cash.zipline.testing.EchoResponse
      	available:
      		fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse
      		fun close(): kotlin.Unit
     		at
      """.trimIndent()
    )
  }

  @Test fun suspendingJvmCallIncompatibleJsService(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")

    assertThat(assertFailsWith<Exception> {
      zipline.take<SuspendingPotatoService>("helloService").echo()
    }).hasMessageThat().startsWith("""
      ZiplineApiMismatchException: no such method (incompatible API versions?)
      	called:
      		suspend fun echo(): app.cash.zipline.testing.EchoResponse
      	available:
      		fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse
      		fun close(): kotlin.Unit
     		at
      """.trimIndent()
    )
  }

  @Test fun jsCallIncompatibleJvmService(): Unit = runBlocking(dispatcher) {
    zipline.bind<PotatoService>("supService", JvmPotatoService("sup"))

    assertThat(assertFailsWith<QuickJsException> {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSupService('homie')")
    }).hasMessageThat().startsWith("""
      app.cash.zipline.ZiplineApiMismatchException: no such method (incompatible API versions?)
      	called:
      		fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse
      	available:
      		fun echo(): app.cash.zipline.testing.EchoResponse
      		fun close(): kotlin.Unit
     		at
      """.trimIndent()
    )
  }

  @Test fun suspendingJsCallIncompatibleJvmService(): Unit = runBlocking(dispatcher) {
    zipline.bind<PotatoService>("jvmSuspendingPotatoService", JvmPotatoService("Veyndan"))

    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSuspendingPotatoService('Veyndan')")
    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.suspendingPotatoResult") as String?)
      .isNull()

    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.suspendingPotatoException") as String?)
      .startsWith("""
        Exception: app.cash.zipline.ZiplineApiMismatchException: no such method (incompatible API versions?)
        	called:
        		suspend fun echo(): app.cash.zipline.testing.EchoResponse
        	available:
        		fun echo(): app.cash.zipline.testing.EchoResponse
        		fun close(): kotlin.Unit
        	at
        """.trimIndent()
      )
  }

  private class JvmEchoService(private val greeting: String) : EchoService {
    override fun echo(request: EchoRequest): EchoResponse {
      return EchoResponse("$greeting from the JVM, ${request.message}")
    }
  }

  private class JvmPotatoService(private val greeting: String) : PotatoService {
    override fun echo(): EchoResponse {
      return EchoResponse("$greeting from the JVM, anonymous")
    }
  }
}
