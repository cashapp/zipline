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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.IOException
import platform.Foundation.NSURLSession

/**
 * This tests our Kotlin/Native URLSessionZiplineHttpClient. Unfortunately this test is not enabled
 * by default as we don't have an equivalent to MockWebServer for Kotlin/Native.
 */
class URLSessionZiplineHttpClientTest {
  var enabled = false

  @Test
  fun happyPath(): Unit = runBlocking {
    if (!enabled) return@runBlocking

    val httpClient = URLSessionZiplineHttpClient(NSURLSession.sharedSession)
    val download = httpClient.download("https://squareup.com/robots.txt", listOf())
    println(download.utf8())
  }

  @Test
  fun requestHeaders(): Unit = runBlocking {
    if (!enabled) return@runBlocking

    val httpClient = URLSessionZiplineHttpClient(NSURLSession.sharedSession)
    val download = httpClient.download(
      "https://squareup.com/robots.txt",
      listOf(
        "Header-One" to "a",
        "Header-Two" to "b",
        "Header-One" to "c",
      ),
    )
    println(download.utf8())
  }

  @Test
  fun connectivityFailure(): Unit = runBlocking {
    if (!enabled) return@runBlocking

    val httpClient = URLSessionZiplineHttpClient(NSURLSession.sharedSession)
    val exception = assertFailsWith<IOException> {
      httpClient.download("https://198.51.100.1/robots.txt", listOf()) // Unreachable IP address.
    }
    assertTrue("The request timed out." in exception.message!!, exception.message)
  }

  @Test
  fun nonSuccessfulResponseCode(): Unit = runBlocking {
    if (!enabled) return@runBlocking

    val httpClient = URLSessionZiplineHttpClient(NSURLSession.sharedSession)
    val exception = assertFailsWith<IOException> {
      httpClient.download("https://squareup.com/.well-known/404", listOf())
    }
    assertEquals(
      "failed to fetch https://squareup.com/.well-known/404: 404",
      exception.message,
    )
  }
}
