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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

internal class ShutdownTest {
  private val sampleService1 = object : EchoService {
    override fun echo(request: EchoRequest) = error("unexpected call")
  }

  @Test
  fun shutdownEndpointWithNoServicesTriggersImmediateClose() = runBlocking {
    val (endpointA, _) = newEndpointPair(this)
    assertEquals(endpointA.state, Endpoint.State.READY)

    endpointA.shutdown()
    assertEquals(endpointA.state, Endpoint.State.CLOSED)
  }

  @Test
  fun closingOnlyServiceTriggersEndpointClose() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    endpointB.bind<EchoService>("service1", sampleService1)
    val service1 = endpointA.take<EchoService>("service1")
    endpointA.shutdown()
    assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN)

    service1.close()
    assertEquals(endpointA.state, Endpoint.State.CLOSED)
  }

  @Test
  fun closingLastServiceTriggersEndpointClose() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    endpointB.bind<EchoService>("service1", sampleService1)
    endpointB.bind<EchoService>("service2", sampleService1)
    endpointB.bind<EchoService>("service3", sampleService1)
    val service1 = endpointA.take<EchoService>("service1")
    val service2 = endpointA.take<EchoService>("service2")
    val service3 = endpointA.take<EchoService>("service3")
    endpointA.shutdown()
    assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN)

    service1.close()
    assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN)

    service3.close()
    assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN)

    service2.close()
    assertEquals(endpointA.state, Endpoint.State.CLOSED)
  }

  @Test
  fun cannotBindOrTakeWhenShuttingDown() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    // Get in the shutdown state.
    endpointB.bind<EchoService>("service1", sampleService1)
    val service1 = endpointA.take<EchoService>("service1")
    endpointA.shutdown()
    assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN)

    // Binds and takes don't work.
    val takeException = assertFailsWith<IllegalStateException> {
      endpointA.take<EchoService>("service1")
    }
    assertEquals("shutting down", takeException.message)
    val bindException = assertFailsWith<IllegalStateException> {
      endpointA.bind<EchoService>("service1", sampleService1)
    }
    assertEquals("shutting down", bindException.message)

    service1.close()
  }

  @Test
  fun cannotBindOrTakeWhenClosed() = runBlocking {
    val (endpointA, _) = newEndpointPair(this)

    // Get in the closed state.
    endpointA.shutdown()
    assertEquals(endpointA.state, Endpoint.State.CLOSED)

    // Binds and takes don't work.
    val takeException = assertFailsWith<IllegalStateException> {
      endpointA.take<EchoService>("service1")
    }
    assertEquals("closed", takeException.message)
    val bindException = assertFailsWith<IllegalStateException> {
      endpointA.bind<EchoService>("service1", sampleService1)
    }
    assertEquals("closed", bindException.message)
  }

  @Test
  fun returningFromOutboundSuspendingFunctionTriggersEndpointClose() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val client = endpointA.take<SuspendingEchoService>("service")

    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN) // Not closed yet!
        client.close()
        assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN) // Still not closed!
        return EchoResponse(request.message)
      }
    }
    endpointB.bind<SuspendingEchoService>("service", service)
    endpointA.shutdown()
    assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN)

    client.suspendingEcho(EchoRequest("hello"))
    assertEquals(endpointA.state, Endpoint.State.CLOSED) // Closed when it receives the callback.
  }

  @Test
  fun throwingFromOutboundSuspendingFunctionTriggersEndpointClose() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val client = endpointA.take<SuspendingEchoService>("service")

    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN) // Not closed yet!
        client.close()
        assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN) // Still not closed!
        throw Exception("boom!")
      }
    }
    endpointB.bind<SuspendingEchoService>("service", service)
    endpointA.shutdown()
    assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN)

    val exception = assertFailsWith<Exception> {
      client.suspendingEcho(EchoRequest("hello"))
    }
    assertTrue("boom!" in exception.message!!)
    assertEquals(endpointA.state, Endpoint.State.CLOSED) // Closed when it receives the callback.
  }

  @Test
  fun returningFromInboundSuspendingFunctionTriggersEndpointClose() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    endpointB.bind<EchoService>("service1", sampleService1)

    // If we were to shutdown endpointA now, it'd take effect immediately because endpointA has no
    // outbound clients. So we create one just to keep it open long enough for a suspending call to
    // start. This service is strictly necessary to get our test service into the right state.
    val justPreventCloseService = endpointA.take<EchoService>("service1")

    val client = endpointB.take<SuspendingEchoService>("service")
    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN) // Not closed yet!
        justPreventCloseService.close()
        assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN) // Still not closed!
        return EchoResponse(request.message)
      }
    }
    endpointA.bind<SuspendingEchoService>("service", service)
    endpointA.shutdown()

    assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN)
    client.suspendingEcho(EchoRequest("hello"))
    assertEquals(endpointA.state, Endpoint.State.CLOSED) // Closed after it calls the callback.
  }

  @Test
  fun throwingFromInboundSuspendingFunctionTriggersEndpointClose() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    endpointB.bind<EchoService>("service1", sampleService1)

    // Prevent endpointA.shutdown() from closing immediately.
    val justPreventCloseService = endpointA.take<EchoService>("service1")

    val client = endpointB.take<SuspendingEchoService>("service")
    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN) // Not closed yet!
        justPreventCloseService.close()
        assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN) // Still not closed!
        throw Exception("boom!")
      }
    }
    endpointA.bind<SuspendingEchoService>("service", service)
    endpointA.shutdown()

    assertEquals(endpointA.state, Endpoint.State.SHUTTING_DOWN)
    val exception = assertFailsWith<Exception> {
      client.suspendingEcho(EchoRequest("hello"))
    }
    assertTrue("boom!" in exception.message!!)
    assertEquals(endpointA.state, Endpoint.State.CLOSED) // Closed after it calls the callback.
  }
}
