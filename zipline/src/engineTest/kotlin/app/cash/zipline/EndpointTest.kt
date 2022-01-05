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

import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.SuspendingEchoService
import app.cash.zipline.testing.newEndpointPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

internal class EndpointTest {
  @Test
  fun requestAndResponse() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val requests = ArrayDeque<String>()
    val responses = ArrayDeque<String>()
    val service = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        requests += request.message
        return EchoResponse(responses.removeFirst())
      }
    }

    endpointA.set<EchoService>("helloService", service)
    val client = endpointB.get<EchoService>("helloService")

    responses += "this is a curt response"
    val response = client.echo(EchoRequest("this is a happy request"))
    assertEquals("this is a curt response", response.message)
    assertEquals("this is a happy request", requests.removeFirst())
    assertNull(responses.removeFirstOrNull())
    assertNull(requests.removeFirstOrNull())
  }

  interface NullableEchoService : ZiplineService {
    fun echo(request: EchoRequest?): EchoResponse?
  }

  @Test
  fun nullRequest() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : NullableEchoService {
      override fun echo(request: EchoRequest?): EchoResponse? {
        assertNull(request)
        return EchoResponse("received null")
      }
    }

    endpointA.set<NullableEchoService>("helloService", service)
    val client = endpointB.get<NullableEchoService>("helloService")

    val response = client.echo(null)
    assertEquals("received null", response?.message)
  }

  @Test
  fun nullResponse() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : NullableEchoService {
      override fun echo(request: EchoRequest?): EchoResponse? {
        assertEquals("send me null please?", request?.message)
        return null
      }
    }

    endpointA.set<NullableEchoService>("helloService", service)
    val client = endpointB.get<NullableEchoService>("helloService")

    val response = client.echo(EchoRequest("send me null please?"))
    assertNull(response)
  }

  @Test
  fun suspendingRequestAndResponse(): Unit = runBlocking() {
    val (endpointA, endpointB) = newEndpointPair(this)

    val requests = Channel<String>(1)
    val responses = Channel<String>(1)
    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        requests.send(request.message)
        return EchoResponse(responses.receive())
      }
    }

    endpointA.set<SuspendingEchoService>("helloService", service)
    val client = endpointB.get<SuspendingEchoService>("helloService")

    val deferredResponse = async {
      client.suspendingEcho(EchoRequest("this is a happy request"))
    }

    assertEquals("this is a happy request", requests.receive())
    responses.send("this is a response")

    assertEquals("this is a response", deferredResponse.await().message)
  }

  @Test
  fun callThrowsException() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        throw IllegalStateException("boom!")
      }
    }

    endpointA.set<EchoService>("helloService", service)
    val client = endpointB.get<EchoService>("helloService")

    val thrownException = assertFailsWith<Exception> {
      client.echo(EchoRequest(""))
    }
    assertTrue(thrownException.message!!.contains(".IllegalStateException: boom!"))
  }

  @Test
  fun suspendingCallThrowsException(): Unit = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        throw IllegalStateException("boom!")
      }
    }

    endpointA.set<SuspendingEchoService>("helloService", service)
    val client = endpointB.get<SuspendingEchoService>("helloService")

    val thrownException = assertFailsWith<Exception> {
      client.suspendingEcho(EchoRequest(""))
    }
    assertTrue(thrownException.message!!.contains(".IllegalStateException: boom!"))
  }

  @Test
  fun suspendingCallbacksCreateTemporaryReferences(): Unit = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val echoService = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        // In the middle of a suspending call there's a temporary reference to the callback.
        assertEquals(setOf("echoService"), endpointA.serviceNames)
        assertEquals(setOf("echoService"), endpointB.clientNames)
        assertEquals(setOf("zipline/1"), endpointA.clientNames)
        assertEquals(setOf("zipline/1"), endpointB.serviceNames)
        return EchoResponse("hello, ${request.message}")
      }
    }

    endpointA.set<SuspendingEchoService>("echoService", echoService)
    val client = endpointB.get<SuspendingEchoService>("echoService")

    val echoResponse = client.suspendingEcho(EchoRequest("Jesse"))
    assertEquals(EchoResponse("hello, Jesse"), echoResponse)

    // Confirm that these temporary references are cleaned up when the suspending call returns.
    assertEquals(setOf("echoService"), endpointA.serviceNames)
    assertEquals(setOf("echoService"), endpointB.clientNames)
    assertEquals(setOf(), endpointA.clientNames)
    assertEquals(setOf(), endpointB.serviceNames)
  }
}
