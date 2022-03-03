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
import app.cash.zipline.testing.EchoZiplineService
import app.cash.zipline.testing.newEndpointPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

internal class ZiplineServiceTest {
  @Test
  fun requestAndResponse() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val events = ArrayDeque<String>()
    val responses = ArrayDeque<String>()
    val service = object : EchoZiplineService {
      override fun echo(request: EchoRequest): EchoResponse {
        events += "request message='${request.message}'"
        return EchoResponse(responses.removeFirst())
      }

      override fun close() {
        events += "close"
      }
    }

    endpointA.bind<EchoZiplineService>("helloService", service)
    assertEquals(setOf("helloService"), endpointA.serviceNames)

    val client = endpointB.take<EchoZiplineService>("helloService")
    assertEquals(setOf("helloService"), endpointB.clientNames)

    responses += "this is a curt response"
    val response = client.echo(EchoRequest("this is a happy request"))
    assertEquals("this is a curt response", response.message)
    assertEquals("request message='this is a happy request'", events.removeFirst())

    client.close()
    assertEquals("close", events.removeFirst())
    assertEquals(setOf(), endpointB.clientNames)
    assertEquals(setOf(), endpointA.serviceNames)

    assertNull(events.removeFirstOrNull())
  }

  @Test
  fun transmitServices() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    endpointA.bind<EchoServiceFactory>("factory", FactoryService())
    val factoryClient = endpointB.take<EchoServiceFactory>("factory")

    val helloService = factoryClient.create("hello")
    val supService = factoryClient.create("sup")

    assertEquals(EchoResponse("hello Jesse"), helloService.echo(EchoRequest("Jesse")))
    assertEquals(EchoResponse("sup Kevin"), supService.echo(EchoRequest("Kevin")))
    assertEquals(EchoResponse("hello Jake"), helloService.echo(EchoRequest("Jake")))
    assertEquals(EchoResponse("sup Stephen"), supService.echo(EchoRequest("Stephen")))
  }

  @Test
  fun registerSerializerToSerializeServicesAsMembers() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)
    val serializersModule = SerializersModule {
      contextual(EchoService::class, ziplineServiceAdapter())
    }
    endpointA.userSerializersModule = serializersModule
    endpointB.userSerializersModule = serializersModule

    val serializer = serializersModule.serializer<EchoService>()
    println(serializer)

    endpointA.bind<ServiceAndHashFactory>("factory", RealServiceAndHashFactory())
    val factoryClient = endpointB.take<ServiceAndHashFactory>("factory")

    val (helloService, helloHash) = factoryClient.create("hello")
    val (supService, supHash) = factoryClient.create("sup")

    assertEquals(EchoResponse("hello Jesse"), helloService.echo(EchoRequest("Jesse")))
    assertEquals(EchoResponse("sup Kevin"), supService.echo(EchoRequest("Kevin")))
    assertEquals(EchoResponse("hello Jake"), helloService.echo(EchoRequest("Jake")))
    assertEquals(EchoResponse("sup Stephen"), supService.echo(EchoRequest("Stephen")))
  }

  @Test
  fun closingAnOutboundServiceRemovesItAndPreventsFurtherCalls() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    endpointA.bind<EchoServiceFactory>("factory", FactoryService())
    val factoryClient = endpointB.take<EchoServiceFactory>("factory")
    assertEquals(setOf("factory"), endpointA.serviceNames)

    val helloService = factoryClient.create("hello")
    assertEquals(setOf("factory", "zipline/1"), endpointA.serviceNames)

    assertEquals(EchoResponse("hello Jesse"), helloService.echo(EchoRequest("Jesse")))
    helloService.close()
    val failure = assertFailsWith<IllegalStateException> {
      helloService.echo(EchoRequest("Jake"))
    }
    assertTrue("no handler" in (failure.message ?: ""), failure.message)
    assertEquals(setOf("factory"), endpointA.serviceNames)
  }

  interface EchoServiceFactory : ZiplineService {
    fun create(greeting: String): EchoService
  }

  class GreetingService(val greeting: String) : EchoService {
    override fun echo(request: EchoRequest): EchoResponse {
      return EchoResponse("$greeting ${request.message}")
    }
  }

  class FactoryService : EchoServiceFactory {
    override fun create(greeting: String): EchoService {
      return GreetingService(greeting)
    }
  }

  @Serializable
  data class ServiceAndHash(
    @Contextual
    val service: EchoService,
    val hash: String,
  )

  interface ServiceAndHashFactory : ZiplineService {
    fun create(greeting: String): ServiceAndHash
  }

  class RealServiceAndHashFactory : ServiceAndHashFactory {
    override fun create(greeting: String): ServiceAndHash {
      return ServiceAndHash(service = GreetingService(greeting), hash = "1234")
    }
  }
}
