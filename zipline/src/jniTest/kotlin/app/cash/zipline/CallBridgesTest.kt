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

import app.cash.zipline.internal.bridge.KtBridge
import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoSerializersModule
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.SuspendingEchoService
import app.cash.zipline.testing.newKtBridgePair
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingDeque
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
internal class CallBridgesTest {
  private val dispatcher = TestCoroutineDispatcher()
  private val bridgeA: KtBridge
  private val bridgeB: KtBridge
  
  init {
    val (bridgeA, bridgeB) = newKtBridgePair(dispatcher)
    this.bridgeA = bridgeA
    this.bridgeB = bridgeB
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

    bridgeA.set<EchoService>("helloService", EchoSerializersModule, service)
    val client = bridgeB.get<EchoService>("helloService", EchoSerializersModule)

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

    bridgeA.set<NullableEchoService>("helloService", EchoSerializersModule, service)
    val client = bridgeB.get<NullableEchoService>("helloService", EchoSerializersModule)

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

    bridgeA.set<NullableEchoService>("helloService", EchoSerializersModule, service)
    val client = bridgeB.get<NullableEchoService>("helloService", EchoSerializersModule)

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

    bridgeA.set<SuspendingEchoService>("helloService", EchoSerializersModule, service)
    val client = bridgeB.get<SuspendingEchoService>("helloService", EchoSerializersModule)

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

    bridgeA.set<EchoService>("helloService", EchoSerializersModule, service)
    val client = bridgeB.get<EchoService>("helloService", EchoSerializersModule)

    val thrownException = assertThrows<Exception> {
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

    bridgeA.set<SuspendingEchoService>("helloService", EchoSerializersModule, service)
    val client = bridgeB.get<SuspendingEchoService>("helloService", EchoSerializersModule)

    val thrownException = assertThrows<Exception> {
      client.suspendingEcho(EchoRequest(""))
    }
    assertThat(thrownException).hasMessageThat()
      .isEqualTo("java.lang.IllegalStateException: boom!")
  }
}
