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

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString.Companion.encodeUtf8
import okio.IOException
import org.junit.Test

class OkHttpZiplineHttpClientTest {
  private val server = MockWebServer()
  private val client = OkHttpZiplineHttpClient(OkHttpClient())

  @Test
  fun happyPath(): Unit = runBlocking {
    server.enqueue(
      MockResponse()
        .setBody("hello")
    )

    val content = client.download(server.url("/foo").toString())
    assertEquals(content, "hello".encodeUtf8())

    val request = server.takeRequest()
    assertEquals("/foo", request.path)
  }

  @Test
  fun connectivityFailure(): Unit = runBlocking {
    val url = server.url("/")
    server.shutdown() // Force a failure.

    assertFailsWith<IOException> {
      client.download(url.toString())
    }
  }

  @Test
  fun unsuccessfulResponseCode(): Unit = runBlocking {
    server.enqueue(
      MockResponse()
        .setResponseCode(404)
        .setBody("hello")
    )

    val url = server.url("/foo")
    val exception = assertFailsWith<IOException> {
      client.download(url.toString())
    }
    assertEquals("failed to fetch $url: 404", exception.message)
  }
}
