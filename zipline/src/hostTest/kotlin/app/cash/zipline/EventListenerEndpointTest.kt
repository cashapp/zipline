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
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.SuspendingEchoService
import app.cash.zipline.testing.newEndpointPair
import app.cash.zipline.testing.signatureHash
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/**
 * This test exercises EventListeners where both endpoints are on the same platform.
 */
internal class EventListenerEndpointTest {
  private val clientListener = CallListener()
  private val serviceListener = CallListener()

  @AfterTest
  fun tearDown() {
    assertTrue(clientListener.calls.isEmpty())
    assertTrue(clientListener.results.isEmpty())
    assertTrue(serviceListener.calls.isEmpty())
    assertTrue(serviceListener.results.isEmpty())
  }

  @Test
  fun simpleRequestAndResponse() = runBlocking(Unconfined) {
    val (clientEndpoint, serviceEndpoint) = newEndpointPair(
      scope = this,
      listenerA = clientListener,
      listenerB = serviceListener,
    )

    val service = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        return EchoResponse("pong")
      }
    }

    serviceEndpoint.bind<EchoService>("echoService", service)
    val client = clientEndpoint.take<EchoService>("echoService")
    client.echo(EchoRequest("ping"))

    val clientCall = clientListener.calls.removeFirst()
    assertEquals("echoService", clientCall.serviceName)
    assertEquals(client, clientCall.service)
    assertEquals(
      "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse",
      clientCall.function.signature,
    )
    assertEquals(listOf(EchoRequest("ping")), clientCall.args)
    assertEquals(
      """
      |{
      |  "service": "echoService",
      |  "function": "${"fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse".signatureHash()}",
      |  "args": [
      |    {
      |      "message": "ping"
      |    }
      |  ]
      |}
      """.trimMargin(),
      prettyPrint(clientCall.encodedCall),
    )
    assertEquals(listOf(), clientCall.serviceNames)

    val serviceCall = serviceListener.calls.removeFirst()
    assertEquals("echoService", serviceCall.serviceName)
    assertEquals(service, serviceCall.service) // Note this is different from clientCall.service.
    assertEquals(
      "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse",
      serviceCall.function.signature,
    )
    assertEquals(listOf(EchoRequest("ping")), serviceCall.args)
    assertEquals(
      """
      |{
      |  "service": "echoService",
      |  "function": "${"fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse".signatureHash()}",
      |  "args": [
      |    {
      |      "message": "ping"
      |    }
      |  ]
      |}
      """.trimMargin(),
      prettyPrint(serviceCall.encodedCall),
    )
    assertEquals(listOf(), serviceCall.serviceNames)

    val clientResult = clientListener.results.removeFirst()
    assertEquals(EchoResponse("pong"), clientResult.result.getOrNull())
    assertEquals(
      """
      |{
      |  "success": {
      |    "message": "pong"
      |  }
      |}
      """.trimMargin(),
      prettyPrint(clientResult.encodedResult),
    )
    assertEquals(listOf(), clientResult.serviceNames)

    val serviceResult = serviceListener.results.removeFirst()
    assertEquals(EchoResponse("pong"), serviceResult.result.getOrNull())
    assertEquals(
      """
      |{
      |  "success": {
      |    "message": "pong"
      |  }
      |}
      """.trimMargin(),
      prettyPrint(serviceResult.encodedResult),
    )
    assertEquals(listOf(), serviceResult.serviceNames)
  }

  @Test
  fun suspendingRequestAndResponse() = runBlocking(Unconfined) {
    val (clientEndpoint, serviceEndpoint) = newEndpointPair(
      scope = this,
      listenerA = clientListener,
      listenerB = serviceListener,
    )

    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        // Force a suspend with async.
        val result = async {
          EchoResponse("pong")
        }
        return result.await()
      }
    }

    serviceEndpoint.bind<SuspendingEchoService>("echoService", service)
    val client = clientEndpoint.take<SuspendingEchoService>("echoService")
    client.suspendingEcho(EchoRequest("ping"))

    val clientCall = clientListener.calls.removeFirst()
    assertEquals("echoService", clientCall.serviceName)
    assertEquals(client, clientCall.service)
    assertEquals(
      "suspend fun suspendingEcho(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse",
      clientCall.function.signature,
    )
    assertEquals(listOf(EchoRequest("ping")), clientCall.args)
    assertEquals(
      """
      |{
      |  "service": "echoService",
      |  "function": "${"suspend fun suspendingEcho(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse".signatureHash()}",
      |  "callback": "zipline/host-1",
      |  "args": [
      |    {
      |      "message": "ping"
      |    }
      |  ]
      |}
      """.trimMargin(),
      prettyPrint(clientCall.encodedCall),
    )
    assertEquals(listOf("zipline/host-1"), clientCall.serviceNames) // This is the callback.

    val serviceCall = serviceListener.calls.removeFirst()
    assertEquals("echoService", serviceCall.serviceName)
    assertEquals(service, serviceCall.service) // Note this is different from clientCall.service.
    assertEquals(
      "suspend fun suspendingEcho(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse",
      serviceCall.function.signature,
    )
    assertEquals(listOf(EchoRequest("ping")), serviceCall.args)
    assertEquals(
      """
      |{
      |  "service": "echoService",
      |  "function": "${"suspend fun suspendingEcho(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse".signatureHash()}",
      |  "callback": "zipline/host-1",
      |  "args": [
      |    {
      |      "message": "ping"
      |    }
      |  ]
      |}
      """.trimMargin(),
      prettyPrint(serviceCall.encodedCall),
    )
    assertEquals(listOf("zipline/host-1"), serviceCall.serviceNames)

    val clientResult = clientListener.results.removeFirst()
    assertEquals(EchoResponse("pong"), clientResult.result.getOrNull())
    assertEquals(
      """
      |{
      |  "service": "zipline/host-1",
      |  "function": "${"fun success(T): kotlin.Unit".signatureHash()}",
      |  "args": [
      |    {
      |      "message": "pong"
      |    }
      |  ]
      |}
      """.trimMargin(),
      prettyPrint(clientResult.encodedResult),
    )
    assertEquals(listOf(), clientResult.serviceNames)

    val serviceResult = serviceListener.results.removeFirst()
    assertEquals(EchoResponse("pong"), serviceResult.result.getOrNull())
    assertEquals(
      """
      |{
      |  "service": "zipline/host-1",
      |  "function": "${"fun success(T): kotlin.Unit".signatureHash()}",
      |  "args": [
      |    {
      |      "message": "pong"
      |    }
      |  ]
      |}
      """.trimMargin(),
      prettyPrint(serviceResult.encodedResult),
    )
    assertEquals(listOf(), serviceResult.serviceNames)
  }

  @Test
  fun suspendingRequestAndResponseWithoutSuspend() = runBlocking(Unconfined) {
    val (clientEndpoint, serviceEndpoint) = newEndpointPair(
      scope = this,
      listenerA = clientListener,
      listenerB = serviceListener,
    )

    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        return EchoResponse("pong") // Doesn't suspend.
      }
    }

    serviceEndpoint.bind<SuspendingEchoService>("echoService", service)
    val client = clientEndpoint.take<SuspendingEchoService>("echoService")
    client.suspendingEcho(EchoRequest("ping"))

    val clientCall = clientListener.calls.removeFirst()
    assertEquals("echoService", clientCall.serviceName)
    assertEquals(client, clientCall.service)
    assertEquals(
      "suspend fun suspendingEcho(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse",
      clientCall.function.signature,
    )
    assertEquals(listOf(EchoRequest("ping")), clientCall.args)
    assertEquals(
      """
      |{
      |  "service": "echoService",
      |  "function": "${"suspend fun suspendingEcho(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse".signatureHash()}",
      |  "callback": "zipline/host-1",
      |  "args": [
      |    {
      |      "message": "ping"
      |    }
      |  ]
      |}
      """.trimMargin(),
      prettyPrint(clientCall.encodedCall),
    )
    assertEquals(listOf("zipline/host-1"), clientCall.serviceNames) // This is the callback.

    val serviceCall = serviceListener.calls.removeFirst()
    assertEquals("echoService", serviceCall.serviceName)
    assertEquals(service, serviceCall.service) // Note this is different from clientCall.service.
    assertEquals(
      "suspend fun suspendingEcho(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse",
      serviceCall.function.signature,
    )
    assertEquals(listOf(EchoRequest("ping")), serviceCall.args)
    assertEquals(
      """
      |{
      |  "service": "echoService",
      |  "function": "${"suspend fun suspendingEcho(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse".signatureHash()}",
      |  "callback": "zipline/host-1",
      |  "args": [
      |    {
      |      "message": "ping"
      |    }
      |  ]
      |}
      """.trimMargin(),
      prettyPrint(serviceCall.encodedCall),
    )
    assertEquals(listOf("zipline/host-1"), serviceCall.serviceNames)

    val clientResult = clientListener.results.removeFirst()
    assertEquals(EchoResponse("pong"), clientResult.result.getOrNull())
    assertEquals(
      """
      |{
      |  "success": {
      |    "message": "pong"
      |  }
      |}
      """.trimMargin(),
      prettyPrint(clientResult.encodedResult),
    )
    assertEquals(listOf(), clientResult.serviceNames)

    val serviceResult = serviceListener.results.removeFirst()
    assertEquals(EchoResponse("pong"), serviceResult.result.getOrNull())
    assertEquals(
      """
      |{
      |  "success": {
      |    "message": "pong"
      |  }
      |}
      """.trimMargin(),
      prettyPrint(serviceResult.encodedResult),
    )
    assertEquals(listOf(), serviceResult.serviceNames)
  }

  @Test
  fun serviceRequestAndResponse() = runBlocking(Unconfined) {
    val (clientEndpoint, serviceEndpoint) = newEndpointPair(
      scope = this,
      listenerA = clientListener,
      listenerB = serviceListener,
    )

    val service = object : EchoTransformer {
      override fun transform(prefix: String, service: EchoService): EchoService {
        return object : EchoService {
          override fun echo(request: EchoRequest): EchoResponse {
            return EchoResponse("$prefix ${request.message}")
          }

          override fun close() {
            service.close()
          }
        }
      }
    }

    serviceEndpoint.bind<EchoTransformer>("echoTransformer", service)
    val client = clientEndpoint.take<EchoTransformer>("echoTransformer")
    val serviceArgument = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        return EchoResponse("pong")
      }
    }
    val transformedService = client.transform("hello", serviceArgument)

    val clientCall = clientListener.calls.removeFirst()
    assertEquals("echoTransformer", clientCall.serviceName)
    assertEquals(client, clientCall.service)
    assertEquals(
      "fun transform(kotlin.String, app.cash.zipline.testing.EchoService): app.cash.zipline.testing.EchoService",
      clientCall.function.signature,
    )
    assertEquals(listOf("hello", serviceArgument), clientCall.args)
    assertEquals(
      """
      |{
      |  "service": "echoTransformer",
      |  "function": "${"fun transform(kotlin.String, app.cash.zipline.testing.EchoService): app.cash.zipline.testing.EchoService".signatureHash()}",
      |  "args": [
      |    "hello",
      |    "zipline/host-1"
      |  ]
      |}
      """.trimMargin(),
      prettyPrint(clientCall.encodedCall),
    )
    assertEquals(listOf("zipline/host-1"), clientCall.serviceNames) // This is the callback.

    val serviceCall = serviceListener.calls.removeFirst()
    assertEquals("echoTransformer", serviceCall.serviceName)
    assertEquals(service, serviceCall.service) // Note this is different from clientCall.service.
    assertEquals(
      "fun transform(kotlin.String, app.cash.zipline.testing.EchoService): app.cash.zipline.testing.EchoService",
      serviceCall.function.signature,
    )
    assertEquals("hello", serviceCall.args[0])
    assertNotSame(serviceCall.args[1], serviceArgument) // The service receives a stub.
    assertTrue(serviceCall.args[1] is EchoService)
    assertEquals(
      """
      |{
      |  "service": "echoTransformer",
      |  "function": "${"fun transform(kotlin.String, app.cash.zipline.testing.EchoService): app.cash.zipline.testing.EchoService".signatureHash()}",
      |  "args": [
      |    "hello",
      |    "zipline/host-1"
      |  ]
      |}
      """.trimMargin(),
      prettyPrint(serviceCall.encodedCall),
    )
    assertEquals(listOf("zipline/host-1"), serviceCall.serviceNames)

    val clientResult = clientListener.results.removeFirst()
    assertEquals(transformedService, clientResult.result.getOrNull()) // This is a stub.
    assertEquals(
      """
      |{
      |  "success": "zipline/host-1"
      |}
      """.trimMargin(),
      prettyPrint(clientResult.encodedResult),
    )
    assertEquals(listOf("zipline/host-1"), clientResult.serviceNames)

    val serviceResult = serviceListener.results.removeFirst()
    assertTrue(serviceResult.result.getOrNull() is EchoService)
    assertNotSame(transformedService, serviceResult.result.getOrNull()) // This is the original.
    assertEquals(
      """
      |{
      |  "success": "zipline/host-1"
      |}
      """.trimMargin(),
      prettyPrint(serviceResult.encodedResult),
    )
    assertEquals(listOf("zipline/host-1"), serviceResult.serviceNames)
  }

  interface EchoTransformer : ZiplineService {
    fun transform(prefix: String, service: EchoService): EchoService
  }

  class CallListener : Endpoint.EventListener() {
    val calls = ArrayDeque<Call>()
    val results = ArrayDeque<CallResult>()

    override fun callStart(call: Call): Any? {
      calls += call
      return null
    }

    override fun callEnd(call: Call, result: CallResult, startValue: Any?) {
      results += result
    }
  }
}
