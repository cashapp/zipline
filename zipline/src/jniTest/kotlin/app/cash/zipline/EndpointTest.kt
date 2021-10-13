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
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingDeque
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class EndpointTest {
  private val dispatcher = TestCoroutineDispatcher()
  private val endpointA: Endpoint
  private val endpointB: Endpoint

  init {
    val (endpointA, endpointB) = newEndpointPair(CoroutineScope(dispatcher))
    this.endpointA = endpointA
    this.endpointB = endpointB
  }

  @Test
  fun requestAndResponse() {
    val requests = LinkedBlockingDeque<String>()
    val responses = LinkedBlockingDeque<String>()
    val service = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        requests += request.message
        return EchoResponse(responses.take())
      }
    }

    endpointA.set<EchoService>("helloService", service)
    val client = endpointB.get<EchoService>("helloService")

    responses += "this is a curt response"
    val response = client.echo(EchoRequest("this is a happy request"))
    assertEquals("this is a curt response", response.message)
    assertEquals("this is a happy request", requests.poll())
    assertNull(responses.poll())
    assertNull(requests.poll())
  }

  interface NullableEchoService {
    fun echo(request: EchoRequest?): EchoResponse?
  }

  @Test
  fun nullRequest() {
    val service = object : NullableEchoService {
      override fun echo(request: EchoRequest?): EchoResponse? {
        assertNull(request)
        return EchoResponse("received null")
      }
    }

    endpointA.set<NullableEchoService>("helloService", service)
    val client = endpointB.get<NullableEchoService>("helloService")

    val response = client.echo(null)
    assertThat(response?.message).isEqualTo("received null")
  }

  @Test
  fun nullResponse() {
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
  fun suspendingRequestAndResponse(): Unit = runBlocking(dispatcher) {
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

    val deferredResponse: Deferred<EchoResponse> = async {
      client.suspendingEcho(EchoRequest("this is a happy request"))
    }

    assertEquals("this is a happy request", requests.receive())
    responses.send("this is a response")

    assertEquals("this is a response", deferredResponse.await().message)
  }

  @Test
  fun callThrowsException() {
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
    assertThat(thrownException).hasMessageThat()
      .isEqualTo("java.lang.IllegalStateException: boom!")
  }

  @Test
  fun suspendingCallThrowsException(): Unit = runBlocking(dispatcher) {
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
    assertThat(thrownException).hasMessageThat()
      .isEqualTo("java.lang.IllegalStateException: boom!")
  }

  @Test
  fun suspendingCallbacksCreateTemporaryReferences(): Unit = runBlocking(dispatcher) {
    val echoService = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        // In the middle of a suspending call there's a temporary reference to the callback.
        assertThat(endpointA.serviceNames).containsExactly("echoService")
        assertThat(endpointB.clientNames).containsExactly("echoService")
        assertThat(endpointA.clientNames).containsExactly("zipline/1")
        assertThat(endpointB.serviceNames).containsExactly("zipline/1")
        return EchoResponse("hello, ${request.message}")
      }
    }

    endpointA.set<SuspendingEchoService>("echoService", echoService)
    val client = endpointB.get<SuspendingEchoService>("echoService")

    val echoResponse = client.suspendingEcho(EchoRequest("Jesse"))
    assertThat(echoResponse).isEqualTo(EchoResponse("hello, Jesse"))

    // Confirm that these temporary references are cleaned up when the suspending call returns.
    assertThat(endpointA.serviceNames).containsExactly("echoService")
    assertThat(endpointB.clientNames).containsExactly("echoService")
    assertThat(endpointA.clientNames).isEmpty()
    assertThat(endpointB.serviceNames).isEmpty()
  }
}
