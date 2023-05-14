/*
 * Copyright (C) 2022 Block, Inc.
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
package app.cash.zipline.loader

import app.cash.zipline.testing.awaitEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString.Companion.encodeUtf8
import okio.IOException
import org.junit.Test

class OkHttpZiplineHttpClientTest {
  private val server = MockWebServer()
  val okHttpClient = OkHttpClient()
  private val client = okHttpClient.asZiplineHttpClient()

  @Test
  fun happyPath(): Unit = runBlocking {
    server.enqueue(
      MockResponse()
        .setBody("hello"),
    )

    val content = client.download(server.url("/foo").toString(), listOf())
    assertEquals(content, "hello".encodeUtf8())

    val request = server.takeRequest()
    assertEquals("/foo", request.path)
  }

  @Test
  fun requestHeaders(): Unit = runBlocking {
    server.enqueue(
      MockResponse()
        .setBody("hello"),
    )

    val content = client.download(
      server.url("/foo").toString(),
      listOf(
        "Header-One" to "a",
        "Header-Two" to "b",
        "Header-One" to "c",
      ),
    )
    assertEquals(content, "hello".encodeUtf8())

    val request = server.takeRequest()
    assertEquals(listOf("a", "c"), request.headers.values("Header-One"))
    assertEquals(listOf("b"), request.headers.values("Header-Two"))
  }

  @Test
  fun connectivityFailure(): Unit = runBlocking {
    val url = server.url("/")
    server.shutdown() // Force a failure.

    assertFailsWith<IOException> {
      client.download(url.toString(), listOf())
    }
  }

  @Test
  fun unsuccessfulResponseCode(): Unit = runBlocking {
    server.enqueue(
      MockResponse()
        .setResponseCode(404)
        .setBody("hello"),
    )

    val url = server.url("/foo")
    val exception = assertFailsWith<IOException> {
      client.download(url.toString(), listOf())
    }
    assertEquals("failed to fetch $url: 404", exception.message)
  }

  @Test
  fun tearDownWebSocketWhenClosed(): Unit = runBlocking {
    val webSocketListener = object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        assertEquals(1, okHttpClient.connectionPool.connectionCount())
        webSocket.send("reload")
        webSocket.close(1000, null)
      }
    }

    server.enqueue(MockResponse().withWebSocketUpgrade(webSocketListener))
    val fastCodeFlow = client.openDevelopmentServerWebSocket(server.url("/ws").toString(), listOf())

    val channel = Channel<String>(capacity = Int.MAX_VALUE)
    val job = launch {
      fastCodeFlow.collect {
        channel.send(it)
      }
      channel.close()
    }

    assertEquals("reload", channel.receive())
    assertTrue(channel.receiveCatching().isClosed)

    job.cancel()

    // assert that websocket is not leaked
    awaitEquals(0, delay = 100.milliseconds) {
      okHttpClient.connectionPool.connectionCount()
    }
  }

  @Test
  fun tearDownWebSocketOpenFails(): Unit = runBlocking {
    // Return a regular HTTP response, which will fail the web socket.
    server.enqueue(MockResponse())
    val fastCodeFlow = client.openDevelopmentServerWebSocket(server.url("/ws").toString(), listOf())

    assertEquals(listOf(), fastCodeFlow.toList())

    // If there are any HTTP connections in OkHttp, they had better be idle.
    assertEquals(
      okHttpClient.connectionPool.idleConnectionCount(),
      okHttpClient.connectionPool.connectionCount(),
    )
  }
}
