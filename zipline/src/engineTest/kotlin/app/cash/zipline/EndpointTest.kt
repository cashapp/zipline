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
import app.cash.zipline.testing.GenericEchoService
import app.cash.zipline.testing.SuspendingEchoService
import app.cash.zipline.testing.kotlinBuiltInSerializersModule
import app.cash.zipline.testing.newEndpointPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine

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

    endpointA.bind<EchoService>("helloService", service)
    val client = endpointB.take<EchoService>("helloService")

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

    endpointA.bind<NullableEchoService>("helloService", service)
    val client = endpointB.take<NullableEchoService>("helloService")

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

    endpointA.bind<NullableEchoService>("helloService", service)
    val client = endpointB.take<NullableEchoService>("helloService")

    val response = client.echo(EchoRequest("send me null please?"))
    assertNull(response)
  }

  @Test
  fun suspendingRequestAndResponse() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val requests = Channel<String>(1)
    val responses = Channel<String>(1)
    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        requests.send(request.message)
        return EchoResponse(responses.receive())
      }
    }

    endpointA.bind<SuspendingEchoService>("helloService", service)
    val client = endpointB.take<SuspendingEchoService>("helloService")

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

    endpointA.bind<EchoService>("helloService", service)
    val client = endpointB.take<EchoService>("helloService")

    val thrownException = assertFailsWith<ZiplineException> {
      client.echo(EchoRequest(""))
    }
    assertTrue(thrownException.message!!.contains(".IllegalStateException: boom!"))
  }

  @Test
  fun suspendingCallThrowsException() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        throw IllegalStateException("boom!")
      }
    }

    endpointA.bind<SuspendingEchoService>("helloService", service)
    val client = endpointB.take<SuspendingEchoService>("helloService")

    val thrownException = assertFailsWith<ZiplineException> {
      client.suspendingEcho(EchoRequest(""))
    }
    assertTrue(thrownException.message!!.contains(".IllegalStateException: boom!"))
  }

  @Test
  fun suspendingCallbacksCreateTemporaryReferences() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val echoService = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        // In the middle of a suspending call there's a temporary reference to the callback.
        assertEquals(setOf("echoService", "zipline/host-1"), endpointA.serviceNames)
        assertEquals(setOf("echoService", "zipline/host-1"), endpointB.clientNames)
        assertEquals(setOf("zipline/host-1"), endpointA.clientNames)
        assertEquals(setOf("zipline/host-1"), endpointB.serviceNames)
        return EchoResponse("hello, ${request.message}")
      }
    }

    endpointA.bind<SuspendingEchoService>("echoService", echoService)
    val client = endpointB.take<SuspendingEchoService>("echoService")

    val echoResponse = client.suspendingEcho(EchoRequest("Jesse"))
    assertEquals(EchoResponse("hello, Jesse"), echoResponse)

    // Confirm that these temporary references are cleaned up when the suspending call returns.
    assertEquals(setOf("echoService"), endpointA.serviceNames)
    assertEquals(setOf("echoService"), endpointB.clientNames)
    assertEquals(setOf(), endpointA.clientNames)
    assertEquals(setOf(), endpointB.serviceNames)
  }

  @Test
  fun suspendingCallCanceled() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val requests = Channel<String>(1)
    val cancels = Channel<Throwable?>(1)
    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        return suspendCancellableCoroutine { continuation ->
          continuation.invokeOnCancellation { throwable ->
            cancels.trySend(throwable)
          }
          requests.trySend(request.message)
          // This continuation never resumes normally!
        }
      }
    }

    endpointA.bind<SuspendingEchoService>("helloService", service)
    val client = endpointB.take<SuspendingEchoService>("helloService")

    val deferredResponse = async {
      client.suspendingEcho(EchoRequest("this request won't get a response"))
    }

    assertEquals("this request won't get a response", requests.receive())
    deferredResponse.cancel()
    assertTrue(cancels.receive() is CancellationException)
  }

  @Test
  fun multipleCancelsAreIdempotent() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val requests = Channel<String>(1)
    val cancels = Channel<Throwable?>(1)
    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        return suspendCancellableCoroutine { continuation ->
          continuation.invokeOnCancellation { throwable ->
            cancels.trySend(throwable)
          }
          requests.trySend(request.message)
          // This continuation never resumes normally!
        }
      }
    }

    endpointA.bind<SuspendingEchoService>("helloService", service)
    val client = endpointB.take<SuspendingEchoService>("helloService")

    val deferredResponse = async {
      client.suspendingEcho(EchoRequest("this request won't get a response"))
    }

    assertEquals("this request won't get a response", requests.receive())
    deferredResponse.cancel()
    deferredResponse.cancel()
    deferredResponse.cancel()
    assertTrue(cancels.receive() is CancellationException)
  }

  @Test
  fun cancelAfterResult() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val requests = Channel<String>(1)
    val responses = Channel<String>(1)
    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        requests.send(request.message)
        return EchoResponse(responses.receive())
      }
    }

    endpointA.bind<SuspendingEchoService>("helloService", service)
    val client = endpointB.take<SuspendingEchoService>("helloService")

    val deferredResponse = async {
      client.suspendingEcho(EchoRequest("this is a happy request"))
    }

    assertEquals("this is a happy request", requests.receive())
    responses.send("this is a response")

    assertEquals("this is a response", deferredResponse.await().message)
    deferredResponse.cancel()
  }

  @Test
  fun genericRequestAndResponse() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this, kotlinBuiltInSerializersModule)

    val stringService = object : GenericEchoService<String> {
      override fun genericEcho(request: String): List<String> {
        return listOf("x", request)
      }
    }
    endpointA.bind<GenericEchoService<String>>("strings", stringService)
    val stringClient = endpointB.take<GenericEchoService<String>>("strings")

    val mapsService = object : GenericEchoService<Map<String, Int>> {
      override fun genericEcho(request: Map<String, Int>): List<Map<String, Int>> {
        return listOf(mapOf(), request)
      }
    }
    endpointA.bind<GenericEchoService<Map<String, Int>>>("maps", mapsService)
    val mapsClient = endpointB.take<GenericEchoService<Map<String, Int>>>("maps")

    assertEquals(
      listOf(mapOf(), mapOf("one" to 1, "two" to 2)),
      mapsClient.genericEcho(mapOf("one" to 1, "two" to 2))
    )

    assertEquals(
      listOf("x", "hello"),
      stringClient.genericEcho("hello")
    )
  }

  @Test
  fun cancelIsAlwaysReceivedOnZiplineDispatcher() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)
    val channel = Channel<String>(1)

    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        return suspendCancellableCoroutine {
          it.invokeOnCancellation {
            channel.trySend("Canceled on $dispatcher")
          }
          channel.trySend("Invoked on $dispatcher")
        }
      }
    }

    endpointA.bind<SuspendingEchoService>("helloService", service)
    val client = endpointB.take<SuspendingEchoService>("helloService")

    val deferredResponse = async {
      client.suspendingEcho(EchoRequest("ping"))
    }

    assertEquals(
      "Invoked on $dispatcher",
      channel.receive(),
    )

    launch(Dispatchers.Unconfined) {
      deferredResponse.cancel()
    }

    assertFailsWith<CancellationException> {
      deferredResponse.await()
    }

    assertEquals(
      "Canceled on $dispatcher",
      channel.receive(),
    )
  }

  @Test
  fun extendInterface() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : ExtendsEchoService {
      override fun echo(request: EchoRequest) = EchoResponse("ack ${request.message}")
    }

    endpointA.bind<ExtendsEchoService>("helloService", service)
    val client = endpointB.take<ExtendsEchoService>("helloService")

    val response = client.echo(EchoRequest("hello"))
    assertEquals("ack hello", response.message)
  }

  interface ExtendsEchoService : ZiplineService, ExtendableInterface

  interface ExtendableInterface {
    fun echo(request: EchoRequest): EchoResponse
  }

  @OptIn(ExperimentalStdlibApi::class)
  private val CoroutineScope.dispatcher: CoroutineDispatcher
    get() = coroutineContext[CoroutineDispatcher.Key]!!
}
