/*
 * Copyright (C) 2022 Block, Inc.
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

import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.LoggingEventListener
import app.cash.zipline.testing.PotatoService
import app.cash.zipline.testing.SuspendingEchoService
import app.cash.zipline.testing.loadTestingJs
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.startsWith
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * This test exercises event listeners using QuickJS.
 */
class EventListenerTest {
  private val dispatcher = StandardTestDispatcher()
  private val eventListener = LoggingEventListener()
  private val zipline = Zipline.create(dispatcher, eventListener = eventListener)

  @Before fun setUp(): Unit = runTest(dispatcher) {
    zipline.loadTestingJs()
    eventListener.takeAll() // Skip events created by loadTestingJs().
  }

  @After fun tearDown(): Unit = runTest(dispatcher) {
    zipline.close()
  }

  @Test fun jvmCallJsService() = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")

    val helloService = zipline.take<EchoService>("helloService")
    assertThat(helloService.echo(EchoRequest("Jake")))
      .isEqualTo(EchoResponse("hello from JavaScript, Jake"))

    val name = "helloService"
    val funName = "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
    val request = "[EchoRequest(message=Jake)]"
    assertThat(eventListener.take()).isEqualTo("takeService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).isEqualTo("callEnd 1 $name $funName $request Success(EchoResponse(message=hello from JavaScript, Jake))")
  }

  @Test fun jsCallJvmService() = runTest(dispatcher) {
    val jvmEchoService = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        return EchoResponse("sup from the JVM, ${request.message}")
      }
    }
    zipline.bind<EchoService>("supService", jvmEchoService)

    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSupService('homie')"))
      .isEqualTo("JavaScript received 'sup from the JVM, homie' from the JVM")

    val name = "supService"
    val funName = "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
    val request = "[EchoRequest(message=homie)]"
    assertThat(eventListener.take()).isEqualTo("bindService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).isEqualTo("callEnd 1 $name $funName $request Success(EchoResponse(message=sup from the JVM, homie))")
  }

  @Test fun suspendingJvmCallJsService() = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareSuspendingJsBridges()")
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.unblockSuspendingJs()")

    val jsSuspendingEchoService = zipline.take<SuspendingEchoService>("jsSuspendingEchoService")

    jsSuspendingEchoService.suspendingEcho(EchoRequest("Jake"))

    val name = "jsSuspendingEchoService"
    val funName = "suspend fun suspendingEcho(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
    val request = "[EchoRequest(message=Jake)]"
    assertThat(eventListener.take()).isEqualTo("takeService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).isEqualTo("callEnd 1 $name $funName $request Success(EchoResponse(message=hello from suspending JavaScript, Jake))")
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

    val name = "jvmSuspendingEchoService"
    val funName = "suspend fun suspendingEcho(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
    val request = "[EchoRequest(message=Eric)]"
    assertThat(eventListener.take()).isEqualTo("bindService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).isEqualTo("callEnd 1 $name $funName $request Success(EchoResponse(message=hello from the suspending JVM, Eric))")
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
    val name = "helloService"
    val funName = "fun echo(): app.cash.zipline.testing.EchoResponse"
    val request = "[]"
    assertThat(eventListener.take()).isEqualTo("takeService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).startsWith("callEnd 1 $name $funName $request Failure(app.cash.zipline.ZiplineApiMismatchException")
  }

  @Test fun jvmCallUnknownJsService() = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.initZipline()")

    val e = assertFailsWith<ZiplineApiMismatchException> {
      zipline.take<EchoService>("helloService").echo(EchoRequest("hello"))
    }
    assertThat(e.message.replace("\t", "  ")).startsWith(
      """
      |no such service (service closed?)
      |  called service:
      |    helloService
      |  available services:
      |    zipline/guest
      """.trimMargin(),
    )
    val name = "helloService"
    val funName = "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
    val request = "[EchoRequest(message=hello)]"
    assertThat(eventListener.take()).isEqualTo("takeService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).startsWith("callEnd 1 $name $funName $request Failure(app.cash.zipline.ZiplineApiMismatchException")
  }

  @Test fun jsCallIncompatibleJvmService() = runTest(dispatcher) {
    val jvmPotatoService = object : PotatoService {
      override fun echo(): EchoResponse {
        error("unexpected call")
      }
    }
    zipline.bind<PotatoService>("supService", jvmPotatoService)

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

    val name = "supService"
    val funName = "fun unknownFunction(): kotlin.Unit"
    val request = "[]"
    assertThat(eventListener.take()).isEqualTo("bindService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).startsWith("callEnd 1 $name $funName $request Failure(app.cash.zipline.ZiplineApiMismatchException")
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

    val name = "supService"
    val funName = "fun unknownFunction(): kotlin.Unit"
    val request = "[]"
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).startsWith("callEnd 1 $name $funName $request Failure(app.cash.zipline.ZiplineApiMismatchException")
  }

  @Test fun ziplineClosed() = runTest(dispatcher) {
    zipline.close()
    assertThat(eventListener.take()).isEqualTo("ziplineClosed")

    // Close is idempotent and doesn't repeat events.
    zipline.close()
    assertThat(eventListener.takeAll()).isEmpty()
  }

  /**
   * We had a bug where EventListeners that called [ZiplineService.toString] would trigger a crash
   * on a lateinit value in the suspend callback.
   */
  @Test fun serviceToStrings() = runTest(dispatcher) {
    val outboundServiceToString =
      "SuspendingEchoService\$Companion\$Adapter\$GeneratedOutboundService"
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareSuspendingJsBridges()")
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.unblockSuspendingJs()")

    val service = zipline.take<SuspendingEchoService>("jsSuspendingEchoService")
    service.suspendingEcho(EchoRequest("Jake"))

    val event1 = eventListener.takeEntry(skipInternalServices = false)
    assertThat(event1.log).isEqualTo("takeService jsSuspendingEchoService")
    assertThat(event1.serviceToString!!)
      .contains(outboundServiceToString)

    val event2 = eventListener.takeEntry(skipInternalServices = false)
    assertThat(event2.log).startsWith("bindService zipline/host-1")
    assertThat(event2.serviceToString!!)
      .startsWith("SuspendCallback/Call(receiver=jsSuspendingEchoService")

    val event3 = eventListener.takeEntry(skipInternalServices = false)
    assertThat(event3.log).startsWith("callStart 1 jsSuspendingEchoService")
    assertThat(event3.serviceToString!!)
      .contains(outboundServiceToString)
  }
}
