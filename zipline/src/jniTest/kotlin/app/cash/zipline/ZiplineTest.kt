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

import app.cash.zipline.testing.AdaptersRequest
import app.cash.zipline.testing.AdaptersRequestSerializersModule
import app.cash.zipline.testing.AdaptersResponse
import app.cash.zipline.testing.AdaptersResponseSerializersModule
import app.cash.zipline.testing.AdaptersSerializersModule
import app.cash.zipline.testing.AdaptersService
import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoSerializersModule
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.SuspendingEchoService
import app.cash.zipline.testing.helloService
import app.cash.zipline.testing.jsSuspendingEchoService
import app.cash.zipline.testing.prepareSuspendingJvmBridges
import app.cash.zipline.testing.yoService
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.serialization.modules.EmptySerializersModule
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
      zipline.get<EchoService>("helloService", EchoSerializersModule)
    })
    assertThat(assertThrows<IllegalStateException> {
      zipline.set<EchoService>("supService", EchoSerializersModule, JvmEchoService("sup"))
    })
  }

  @Test fun callServiceAfterCloseFailsGracefully(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")
    val service = zipline.helloService

    zipline.close()
    assertThat(assertThrows<IllegalStateException> {
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
    zipline.set<EchoService>("supService", EchoSerializersModule, JvmEchoService("sup"))

    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSupService('homie')"))
      .isEqualTo("JavaScript received 'sup from the JVM, homie' from the JVM")
  }

  @Test fun jvmCallJsServiceThatThrows(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareThrowingJsBridges()")

    assertThat(assertThrows<Exception> {
      zipline.helloService.echo(EchoRequest("Jake"))
    }).hasMessageThat().contains("boom!") // 'IllegalStateException' prefix lost when we minify JS.
  }

  @Test fun jsCallJvmServiceThatThrows(): Unit = runBlocking(dispatcher) {
    zipline.set<EchoService>("supService", EchoSerializersModule, JvmThrowingEchoService())

    assertThat(assertThrows<QuickJsException> {
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

  @Test fun missingGetReturnValueSerializerFailsFast(): Unit = runBlocking(dispatcher) {
    assertThat(assertThrows<IllegalArgumentException> {
      zipline.get<AdaptersService>("adaptersService", AdaptersRequestSerializersModule)
    }).hasMessageThat().contains("Serializer for class 'AdaptersResponse' is not found.")
  }

  @Test fun missingGetParameterSerializerFailsFast(): Unit = runBlocking(dispatcher) {
    assertThat(assertThrows<IllegalArgumentException> {
      zipline.get<AdaptersService>("adaptersService", AdaptersResponseSerializersModule)
    }).hasMessageThat().contains("Serializer for class 'AdaptersRequest' is not found.")
  }

  @Test fun presentGetSerializersSucceeds(): Unit = runBlocking(dispatcher) {
    val service = zipline.get<AdaptersService>("adaptersService", AdaptersSerializersModule)
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareAdaptersJsBridges()")

    assertThat(service.echo(AdaptersRequest("Andrew")))
      .isEqualTo(AdaptersResponse("thank you for using your serializers, Andrew"))
  }

  @Test fun missingSetReturnValueSerializerFailsFast(): Unit = runBlocking(dispatcher) {
    assertThat(assertThrows<IllegalArgumentException> {
      zipline.set<AdaptersService>(
        "adaptersService",
        AdaptersRequestSerializersModule,
        JvmAdaptersService()
      )
    }).hasMessageThat().contains("Serializer for class 'AdaptersResponse' is not found.")
  }

  @Test fun missingSetParameterSerializerFailsFast(): Unit = runBlocking(dispatcher) {
    assertThat(assertThrows<IllegalArgumentException> {
      zipline.set<AdaptersService>(
        "adaptersService",
        AdaptersResponseSerializersModule,
        JvmAdaptersService()
      )
    }).hasMessageThat().contains("Serializer for class 'AdaptersRequest' is not found.")
  }

  @Test fun presentSetSerializersSucceeds(): Unit = runBlocking(dispatcher) {
    zipline.set<AdaptersService>(
      "adaptersService",
      AdaptersSerializersModule,
      JvmAdaptersService()
    )
    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callAdaptersService()"))
      .isEqualTo("JavaScript received nice adapters, Jesse")
  }

  @Test fun serviceNamesAndClientNames(): Unit = runBlocking(dispatcher) {
    assertThat(zipline.serviceNames).containsExactly(
      "zipline/host",
    )
    assertThat(zipline.clientNames).containsExactly(
      "zipline/js",
    )

    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")
    assertThat(zipline.serviceNames).containsExactly(
      "zipline/host",
    )
    assertThat(zipline.clientNames).containsExactly(
      "zipline/js",
      "helloService",
      "yoService",
    )

    zipline.set<EchoService>("supService", EchoSerializersModule, JvmEchoService("sup"))
    assertThat(zipline.serviceNames).containsExactly(
      "zipline/host",
      "supService",
    )
    assertThat(zipline.clientNames).containsExactly(
      "zipline/js",
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

  private class JvmAdaptersService : AdaptersService {
    override fun echo(request: AdaptersRequest): AdaptersResponse {
      return AdaptersResponse("nice adapters, ${request.message}")
    }
  }
}
