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

import app.cash.zipline.internal.bridge.ziplineServiceAdapter
import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.newEndpointPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

internal class ZiplineReferenceTest {
  @Test
  fun referenceCanBeUsedWithoutPassingThroughZipline() {
    val helloService = GreetingService("hello")
    val reference = ZiplineReference<EchoService>(helloService)
    assertSame(helloService, reference.get())
  }

  @Test
  fun bridgeServicesBeforeEndpointsConnected() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val requests = ArrayDeque<String>()
    val responses = ArrayDeque<String>()
    val service = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        requests += request.message
        return EchoResponse(responses.removeFirst())
      }
    }

    val referenceA = ZiplineReference<EchoService>(service)
    (referenceA as InboundZiplineReference<*>).connect(endpointA, "helloService")

    // Note that we cast OutboundZiplineReference<EchoService> down to ZiplineReference<EchoService>
    // before calling get(). This is necessary because the code rewriter doesn't rewrite for
    // subclasses of ZiplineReference.
    val referenceB: ZiplineReference<EchoService> =
      OutboundZiplineReference(ziplineServiceAdapter())
    (referenceB as OutboundZiplineReference<EchoService>).connect(endpointB, "helloService")

    val client = referenceB.get()

    responses += "this is a curt response"
    val response = client.echo(EchoRequest("this is a happy request"))
    assertEquals("this is a curt response", response.message)
    assertEquals("this is a happy request", requests.removeFirst())
    assertNull(responses.removeFirstOrNull())
    assertNull(requests.removeFirstOrNull())
  }

  interface EchoServiceFactory : ZiplineService {
    fun create(greeting: String): ZiplineReference<EchoService>
  }

  @Test
  fun transmitReferences() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    endpointA.set<EchoServiceFactory>("factory", FactoryService())
    val factoryClient = endpointB.get<EchoServiceFactory>("factory")

    val helloServiceReference = factoryClient.create("hello")
    val helloService = helloServiceReference.get()

    val supServiceReference = factoryClient.create("sup")
    val supService = supServiceReference.get()

    assertEquals(EchoResponse("hello Jesse"), helloService.echo(EchoRequest("Jesse")))
    assertEquals(EchoResponse("sup Kevin"), supService.echo(EchoRequest("Kevin")))
    assertEquals(EchoResponse("hello Jake"), helloService.echo(EchoRequest("Jake")))
    assertEquals(EchoResponse("sup Stephen"), supService.echo(EchoRequest("Stephen")))
  }

  @Test
  fun closingAnOutboundReferenceRemovesItAndPreventsFurtherCalls() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    endpointA.set<EchoServiceFactory>("factory", FactoryService())
    val factoryClient = endpointB.get<EchoServiceFactory>("factory")
    assertEquals(setOf("factory"), endpointA.serviceNames)

    val outboundReference = factoryClient.create("hello")
    assertEquals(setOf("factory", "zipline/1"), endpointA.serviceNames)
    val service = outboundReference.get()

    assertEquals(EchoResponse("hello Jesse"), service.echo(EchoRequest("Jesse")))
    outboundReference.close()
    val failure = assertFailsWith<IllegalStateException> {
      service.echo(EchoRequest("Jesse"))
    }
    assertTrue("no handler" in (failure.message ?: ""), failure.message)
    assertEquals(setOf("factory"), endpointA.serviceNames)
  }

  @Test
  fun closingAnInboundReferenceRemovesItAndPreventsFurtherCalls() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    // Eagerly create this so we have something to return later.
    val inboundReference = ZiplineReference<EchoService>(
      GreetingService("hello")
    )
    class FactoryService : EchoServiceFactory {
      override fun create(greeting: String): ZiplineReference<EchoService> {
        return inboundReference
      }
    }

    endpointA.set<EchoServiceFactory>("factory", FactoryService())
    val factoryClient = endpointB.get<EchoServiceFactory>("factory")
    assertEquals(setOf("factory"), endpointA.serviceNames)

    val outboundReference = factoryClient.create("hello")
    assertEquals(setOf("factory", "zipline/1"), endpointA.serviceNames)
    val service = outboundReference.get()

    assertEquals(EchoResponse("hello Jesse"), service.echo(EchoRequest("Jesse")))
    inboundReference.close()
    val failure = assertFailsWith<IllegalStateException> {
      service.echo(EchoRequest("Jesse"))
    }
    assertTrue("no handler" in (failure.message ?: ""), failure.message)
    assertEquals(setOf("factory"), endpointA.serviceNames)
  }

  class GreetingService(val greeting: String) : EchoService {
    override fun echo(request: EchoRequest): EchoResponse {
      return EchoResponse("$greeting ${request.message}")
    }
  }

  class FactoryService : EchoServiceFactory {
    override fun create(greeting: String): ZiplineReference<EchoService> {
      val service = GreetingService(greeting)
      return ZiplineReference(service)
    }
  }
}
