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
package app.cash.quickjs

import app.cash.quickjs.internal.bridge.KtBridge
import app.cash.quickjs.testing.EchoJsAdapter
import app.cash.quickjs.testing.EchoRequest
import app.cash.quickjs.testing.EchoResponse
import app.cash.quickjs.testing.EchoService
import app.cash.quickjs.testing.SuspendingEchoService
import app.cash.quickjs.testing.newKtBridgePair
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class CallBridgesTest {
  private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  private val bridgeA: KtBridge
  private val bridgeB: KtBridge
  
  init {
    val (bridgeA, bridgeB) = newKtBridgePair(dispatcher)
    this.bridgeA = bridgeA
    this.bridgeB = bridgeB
  }

  @After
  fun tearDown() {
    dispatcher.close()
  }

  @Test
  fun inboundCallRequestAndResponse() {
    val requests = LinkedBlockingDeque<String>()
    val responses = LinkedBlockingDeque<String>()
    val service = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        requests += request.message
        return EchoResponse(responses.take())
      }
    }

    bridgeA.set<EchoService>("helloService", EchoJsAdapter, service)
    val client = bridgeB.get<EchoService>("helloService", EchoJsAdapter)

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

    bridgeA.set<NullableEchoService>("helloService", EchoJsAdapter, service)
    val client = bridgeB.get<NullableEchoService>("helloService", EchoJsAdapter)

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

    bridgeA.set<NullableEchoService>("helloService", EchoJsAdapter, service)
    val client = bridgeB.get<NullableEchoService>("helloService", EchoJsAdapter)

    val response = client.echo(EchoRequest("send me null please?"))
    assertNull(response)
  }

  @Test
  fun suspendingRequestAndResponse() {
    val requests = Channel<String>(1)
    val responses = Channel<String>(1)
    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        requests.send(request.message)
        return EchoResponse(responses.receive())
      }
    }

    bridgeA.set<SuspendingEchoService>("helloService", EchoJsAdapter, service)
    val client = bridgeB.get<SuspendingEchoService>("helloService", EchoJsAdapter)

    runBlocking(dispatcher) {
      val deferredResponse: Deferred<EchoResponse> = async {
        client.suspendingEcho(EchoRequest("this is a happy request"))
      }

      assertEquals("this is a happy request", requests.receive())
      responses.send("this is a response")

      assertEquals("this is a response", deferredResponse.await().message)
    }
  }
}
