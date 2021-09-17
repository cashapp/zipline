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

import app.cash.zipline.internal.bridge.Endpoint
import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoSerializersModule
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.newEndpointPair
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingDeque
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.serialization.modules.EmptySerializersModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ZiplineReferenceTest {
  private val dispatcher = TestCoroutineDispatcher()
  private val endpointA: Endpoint
  private val endpointB: Endpoint

  init {
    val (endpointA, endpointB) = newEndpointPair(dispatcher)
    this.endpointA = endpointA
    this.endpointB = endpointB
  }

  @Test
  fun bridgeServicesBeforeEndpointsConnected() {
    val requests = LinkedBlockingDeque<String>()
    val responses = LinkedBlockingDeque<String>()
    val service = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        requests += request.message
        return EchoResponse(responses.take())
      }
    }

    val referenceA = ZiplineReference<EchoService>(EchoSerializersModule, service)
    (referenceA as InboundZiplineReference<*>).connect(endpointA, "helloService")

    // Note that we cast OutboundZiplineReference<EchoService> down to ZiplineReference<EchoService>
    // before calling get(). This is necessary because we don't implement rewrites for overrides.
    val referenceB: ZiplineReference<EchoService> = OutboundZiplineReference()
    (referenceB as OutboundZiplineReference<EchoService>).connect(endpointB, "helloService")

    val client = referenceB.get(EchoSerializersModule)

    responses += "this is a curt response"
    val response = client.echo(EchoRequest("this is a happy request"))
    assertEquals("this is a curt response", response.message)
    assertEquals("this is a happy request", requests.poll())
    assertNull(responses.poll())
    assertNull(requests.poll())
  }

  interface EchoServiceFactory {
    fun create(greeting: String): ZiplineReference<EchoService>
  }

  @Test
  fun transmitReferences() {
    endpointA.set<EchoServiceFactory>("factory", EmptySerializersModule, FactoryService())
    val factoryClient = endpointB.get<EchoServiceFactory>("factory", EmptySerializersModule)

    val helloServiceReference = factoryClient.create("hello")
    val helloService = helloServiceReference.get(EmptySerializersModule)

    val supServiceReference = factoryClient.create("sup")
    val supService = supServiceReference.get(EmptySerializersModule)

    assertThat(helloService.echo(EchoRequest("Jesse"))).isEqualTo(EchoResponse("hello Jesse"))
    assertThat(supService.echo(EchoRequest("Kevin"))).isEqualTo(EchoResponse("sup Kevin"))
    assertThat(helloService.echo(EchoRequest("Jake"))).isEqualTo(EchoResponse("hello Jake"))
    assertThat(supService.echo(EchoRequest("Stephen"))).isEqualTo(EchoResponse("sup Stephen"))
  }

  @Test
  fun closingAnOutboundReferenceRemovesItAndPreventsFurtherCalls() {
    endpointA.set<EchoServiceFactory>("factory", EmptySerializersModule, FactoryService())
    val factoryClient = endpointB.get<EchoServiceFactory>("factory", EmptySerializersModule)
    assertThat(endpointA.serviceNames).containsExactly("factory")

    val outboundReference = factoryClient.create("hello")
    assertThat(endpointA.serviceNames).containsExactly("factory", "zipline/1")
    val service = outboundReference.get(EmptySerializersModule)

    assertThat(service.echo(EchoRequest("Jesse"))).isEqualTo(EchoResponse("hello Jesse"))
    outboundReference.close()
    assertThat(assertThrows<IllegalStateException> {
      service.echo(EchoRequest("Jesse"))
    }).hasMessageThat().contains("no handler")
    assertThat(endpointA.serviceNames).containsExactly("factory")
  }

  @Test
  fun closingAnInboundReferenceRemovesItAndPreventsFurtherCalls() {
    // Eagerly create this so we have something to return later.
    val inboundReference = ZiplineReference<EchoService>(
      EmptySerializersModule,
      GreetingService("hello")
    )
    class FactoryService : EchoServiceFactory {
      override fun create(greeting: String): ZiplineReference<EchoService> {
        return inboundReference
      }
    }

    endpointA.set<EchoServiceFactory>("factory", EmptySerializersModule, FactoryService())
    val factoryClient = endpointB.get<EchoServiceFactory>("factory", EmptySerializersModule)
    assertThat(endpointA.serviceNames).containsExactly("factory")

    val outboundReference = factoryClient.create("hello")
    assertThat(endpointA.serviceNames).containsExactly("factory", "zipline/1")
    val service = outboundReference.get(EmptySerializersModule)

    assertThat(service.echo(EchoRequest("Jesse"))).isEqualTo(EchoResponse("hello Jesse"))
    inboundReference.close()
    assertThat(assertThrows<IllegalStateException> {
      service.echo(EchoRequest("Jesse"))
    }).hasMessageThat().contains("no handler")
    assertThat(endpointA.serviceNames).containsExactly("factory")
  }

  class GreetingService(val greeting: String) : EchoService {
    override fun echo(request: EchoRequest): EchoResponse {
      return EchoResponse("$greeting ${request.message}")
    }
  }

  class FactoryService : EchoServiceFactory {
    override fun create(greeting: String): ZiplineReference<EchoService> {
      val service = GreetingService(greeting)
      return ZiplineReference(EmptySerializersModule, service)
    }
  }
}
