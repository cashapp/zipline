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

import app.cash.zipline.internal.ZIPLINE_GUEST_NAME
import app.cash.zipline.internal.ZIPLINE_HOST_NAME
import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.PotatoService
import app.cash.zipline.testing.SuspendingEchoService
import app.cash.zipline.testing.SuspendingPotatoService
import app.cash.zipline.testing.loadTestingJs
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.startsWith
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ZiplineTest {
  private val dispatcher = StandardTestDispatcher()
  private val zipline = Zipline.create(dispatcher)

  @Before fun setUp() = runTest(dispatcher) {
    zipline.loadTestingJs()
  }

  @After fun tearDown() = runTest(dispatcher) {
    zipline.close()
  }

  @Test fun cannotTakeOrBindServiceAfterClose(): Unit = runTest(dispatcher) {
    zipline.close()
    assertFailsWith<IllegalStateException> {
      zipline.take<EchoService>("helloService")
    }
    assertFailsWith<IllegalStateException> {
      zipline.bind<EchoService>("supService", JvmEchoService("sup"))
    }
  }

  @Test fun callServiceAfterCloseFailsGracefully() = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")
    val service = zipline.take<EchoService>("helloService")

    zipline.close()
    val e = assertFailsWith<IllegalStateException> {
      service.echo(EchoRequest("Jake"))
    }
    assertThat(e.message).isEqualTo("Zipline closed")
  }

  @Test fun jvmCallJsService() = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")

    val helloService = zipline.take<EchoService>("helloService")
    val yoService = zipline.take<EchoService>("yoService")
    assertThat(helloService.echo(EchoRequest("Jake")))
      .isEqualTo(EchoResponse("hello from JavaScript, Jake"))
    assertThat(yoService.echo(EchoRequest("Kevin")))
      .isEqualTo(EchoResponse("yo from JavaScript, Kevin"))
  }

  @Test fun jsCallJvmService() = runTest(dispatcher) {
    zipline.bind<EchoService>("supService", JvmEchoService("sup"))

    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSupService('homie')"))
      .isEqualTo("JavaScript received 'sup from the JVM, homie' from the JVM")
  }

  @Test fun suspendingJvmCallJsService() = runTest(dispatcher) {
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

  @Test fun suspendingJsCallJvmService() = runTest(dispatcher) {
    val jvmSuspendingEchoService = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        return EchoResponse("hello from the suspending JVM, ${request.message}")
      }
    }

    zipline.bind<SuspendingEchoService>(
      "jvmSuspendingEchoService",
      jvmSuspendingEchoService,
    )

    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSuspendingEchoService('Eric')")
    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.suspendingEchoResult"))
      .isEqualTo("hello from the suspending JVM, Eric")
  }

  /**
   * Don't crash and don't log exceptions if a suspending call completes after the QuickJS instance
   * is closed. Just ignore the response.
   */
  @Test fun suspendingJvmCallCompletesAfterClose() = runTest(dispatcher) {
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
      jvmSuspendingEchoService,
    )

    lock.withPermit {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSuspendingEchoService('Eric')")
    }
    // Let the suspended call complete.
    forceSuspend()
    val e = assertFailsWith<IllegalStateException> {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.suspendingEchoResult")
    }
    assertThat(e.message).isEqualTo("QuickJs instance was closed")
  }

  @Test fun suspendingJsCallCompletesAfterClose(): Unit = runTest(dispatcher) {
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

  /**
   * This is a regression test for the following scenario:
   *
   *  1. An asynchronous job is enqueued with a 0 millisecond delay (ie. `setTimeout(job, 0)`).
   *  2. Zipline is closed.
   *  3. The asynchronous job is executed.
   *
   * For correct behavior the job must fail with a non-fatal [CancellationException] and not a fatal
   * [IllegalStateException].
   */
  @Test fun suspendedJsCallResumesAfterClose(): Unit = runTest(dispatcher) {
    val lock1 = Mutex(locked = true)
    val lock2 = Mutex(locked = true)

    val jvmSuspendingEchoService = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        when (request.message) {
          "A" -> {
            lock1.lock()
            lock2.unlock()
          }
          "B" -> {
            lock1.unlock()
          }
        }
        return EchoResponse(request.message)
      }
    }

    zipline.bind<SuspendingEchoService>(
      "jvmSuspendingEchoService",
      jvmSuspendingEchoService,
    )

    val deferredA = async {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSuspendingEchoService('A')")
    }
    val deferredB = async {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSuspendingEchoService('B')")
    }

    lock2.lock()
    zipline.close()
    deferredA.await()
    deferredB.await()
  }

  @Test fun serviceNamesAndClientNames(): Unit = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.initZipline()")
    assertThat(zipline.serviceNames).containsExactlyInAnyOrder(ZIPLINE_HOST_NAME)
    assertThat(zipline.clientNames).containsExactlyInAnyOrder(ZIPLINE_GUEST_NAME)

    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")
    assertThat(zipline.serviceNames).containsExactlyInAnyOrder(ZIPLINE_HOST_NAME)
    assertThat(zipline.clientNames).containsExactlyInAnyOrder(
      ZIPLINE_GUEST_NAME,
      "helloService",
      "yoService",
    )

    zipline.bind<EchoService>("supService", JvmEchoService("sup"))
    assertThat(zipline.serviceNames).containsExactlyInAnyOrder(
      ZIPLINE_HOST_NAME,
      "supService",
    )
    assertThat(zipline.clientNames).containsExactlyInAnyOrder(
      ZIPLINE_GUEST_NAME,
      "helloService",
      "yoService",
    )
  }

  @Test fun jvmCallIncompatibleJsService() = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")

    val e = assertFailsWith<ZiplineApiMismatchException> {
      zipline.take<PotatoService>("helloService").echo()
    }
    assertThat(e.message.replace("\t", "  ")).startsWith(
      """
      |no such method (incompatible API versions?)
      |  called service:
      |    helloService
      |  called function:
      |    fun echo(): app.cash.zipline.testing.EchoResponse
      |  available functions:
      |    fun close(): kotlin.Unit
      |    fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse
      """.trimMargin(),
    )
  }

  @Test fun suspendingJvmCallIncompatibleJsService() = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")

    val e = assertFailsWith<ZiplineApiMismatchException> {
      zipline.take<SuspendingPotatoService>("helloService").echo()
    }
    assertThat(e.message.replace("\t", "  ")).startsWith(
      """
      |no such method (incompatible API versions?)
      |  called service:
      |    helloService
      |  called function:
      |    suspend fun echo(): app.cash.zipline.testing.EchoResponse
      |  available functions:
      |    fun close(): kotlin.Unit
      |    fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse
      """.trimMargin(),
    )
  }

  @Test fun jsCallIncompatibleJvmService() = runTest(dispatcher) {
    zipline.bind<PotatoService>("supService", JvmPotatoService("sup"))

    val e = assertFailsWith<QuickJsException> {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSupService('homie')")
    }
    assertThat(e.message!!.replace("\t", "  ")).startsWith(
      """
      |no such method (incompatible API versions?)
      |  called service:
      |    supService
      |  called function:
      |    fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse
      |  available functions:
      |    fun close(): kotlin.Unit
      |    fun echo(): app.cash.zipline.testing.EchoResponse
      """.trimMargin(),
    )
  }

  @Test fun suspendingJsCallIncompatibleJvmService() = runTest(dispatcher) {
    zipline.bind<PotatoService>("jvmSuspendingPotatoService", JvmPotatoService("Veyndan"))

    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSuspendingPotatoService('Veyndan')")
    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.suspendingPotatoResult") as String?)
      .isNull()

    val exceptionString = zipline.quickJs.evaluate("testing.app.cash.zipline.testing.suspendingPotatoException") as String?
    assertThat(exceptionString!!.replace("\t", "  ")).startsWith(
        """
        |ZiplineApiMismatchException: no such method (incompatible API versions?)
        |  called service:
        |    jvmSuspendingPotatoService
        |  called function:
        |    suspend fun echo(): app.cash.zipline.testing.EchoResponse
        |  available functions:
        |    fun close(): kotlin.Unit
        |    fun echo(): app.cash.zipline.testing.EchoResponse
        """.trimMargin(),
      )
  }

  @Test fun jvmCallUnknownJsService() = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.initZipline()")

    val noSuchService = zipline.take<EchoService>("noSuchService")
    val e = assertFailsWith<ZiplineApiMismatchException> {
      noSuchService.echo(EchoRequest("hello"))
    }
    assertThat(e.message.replace("\t", "  ")).startsWith(
      """
      |no such service (service closed?)
      |  called service:
      |    noSuchService
      |  available services:
      |    zipline/guest
      """.trimMargin(),
    )
  }

  @Test fun suspendingJvmCallUnknownJsService() = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.initZipline()")

    val noSuchService = zipline.take<SuspendingEchoService>("noSuchService")
    val e = assertFailsWith<ZiplineApiMismatchException> {
      noSuchService.suspendingEcho(EchoRequest("hello"))
    }
    assertThat(e.message.replace("\t", "  ")).startsWith(
      """
      |no such service (service closed?)
      |  called service:
      |    noSuchService
      |  available services:
      |    zipline/guest
      """.trimMargin(),
    )
  }

  @Test fun jsCallUnknownJvmService() = runTest(dispatcher) {
    val e = assertFailsWith<QuickJsException> {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSupService('homie')")
    }
    assertThat(e.message!!.replace("\t", "  ")).startsWith(
      """
      |no such service (service closed?)
      |  called service:
      |    supService
      |  available services:
      |    zipline/host
      """.trimMargin(),
    )
  }

  @Test fun suspendingJsCallUnknownJvmService() = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSuspendingPotatoService('Veyndan')")
    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.suspendingPotatoResult") as String?)
      .isNull()

    val exceptionString = zipline.quickJs.evaluate("testing.app.cash.zipline.testing.suspendingPotatoException") as String?
    assertThat(exceptionString!!.replace("\t", "  ")).startsWith(
      """
      |ZiplineApiMismatchException: no such service (service closed?)
      |  called service:
      |    jvmSuspendingPotatoService
      |  available services:
      |    zipline/host
      """.trimMargin(),
    )
  }

  @Test fun cancelContextJobDoesntDestroyZipline() = runTest(dispatcher) {
    val jvmSuspendingEchoService = object : SuspendingEchoService {
      var callCount = 0
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        callCount++
        if (callCount == 2) {
          currentCoroutineContext().cancel()
        }
        return EchoResponse("response $callCount")
      }
    }

    zipline.bind<SuspendingEchoService>(
      "jvmSuspendingEchoService",
      jvmSuspendingEchoService,
    )

    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSuspendingEchoService('')")
    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.suspendingEchoResult"))
      .isEqualTo("response 1")

    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSuspendingEchoService('')")
    assertThat(
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.suspendingEchoResult") as String,
    ).startsWith("ZiplineException: kotlinx.coroutines.JobCancellationException")

    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSuspendingEchoService('')")
    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.suspendingEchoResult"))
      .isEqualTo("response 3")
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
