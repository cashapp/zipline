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

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.concurrent.freeze
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.IOException
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.addValue
import platform.Foundation.dataTaskWithRequest

internal class URLSessionZiplineHttpClient(
  private val urlSession: NSURLSession,
) : ZiplineHttpClient() {
  init {
    maybeFreeze()
  }

  override suspend fun download(
    url: String,
    requestHeaders: List<Pair<String, String>>,
  ): ByteString {
    val nsUrl = NSURL(string = url)
    return suspendCancellableCoroutine { continuation: CancellableContinuation<ByteString> ->
      val completionHandler = CompletionHandler(url, continuation)

      val task = urlSession.dataTaskWithRequest(
        request = NSMutableURLRequest(nsUrl).apply {
          for ((name, value) in requestHeaders) {
            addValue(value = value, forHTTPHeaderField = name)
          }
        },
        completionHandler = completionHandler::invoke.maybeFreeze(),
      )

      continuation.invokeOnCancellation {
        task.cancel()
      }

      task.resume()
    }
  }
}

private class CompletionHandler(
  private val url: String,
  private val continuation: CancellableContinuation<ByteString>,
) {
  fun invoke(data: NSData?, response: NSURLResponse?, error: NSError?) {
    if (error != null) {
      continuation.resumeWithException(IOException(error.description))
      return
    }

    if (response !is NSHTTPURLResponse || data == null) {
      continuation.resumeWithException(IOException("unexpected response: $response"))
      return
    }

    if (response.statusCode !in 200 until 300) {
      continuation.resumeWithException(
        IOException("failed to fetch $url: ${response.statusCode}"),
      )
      return
    }

    continuation.resume(data.toByteString())
  }
}

/** Freeze this when executing on Kotlin/Native's strict memory model. */
@OptIn(ExperimentalNativeApi::class)
private fun <T> T.maybeFreeze(): T {
  return if (Platform.memoryModel == MemoryModel.STRICT) {
    this.freeze()
  } else {
    this
  }
}
