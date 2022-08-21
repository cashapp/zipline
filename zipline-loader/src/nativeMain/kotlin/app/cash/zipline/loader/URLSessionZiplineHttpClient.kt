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
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.ByteString
import okio.IOException
import okio.toByteString
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithURL

internal class URLSessionZiplineHttpClient(
  private val urlSession: NSURLSession,
) : ZiplineHttpClient {
  override suspend fun download(url: String): ByteString {
    val nsUrl = NSURL(string = url)
    return suspendCancellableCoroutine { continuation ->
      val task = urlSession.dataTaskWithURL(
        url = nsUrl,
      ) { data: NSData?, response: NSURLResponse?, error: NSError? ->
        if (error != null) {
          continuation.resumeWithException(IOException(error.description))
          return@dataTaskWithURL
        }

        if (response !is NSHTTPURLResponse || data == null) {
          continuation.resumeWithException(IOException("unexpected response: $response"))
          return@dataTaskWithURL
        }

        if (response.statusCode !in 200 until 300) {
          continuation.resumeWithException(
            IOException("failed to fetch $url: ${response.statusCode}")
          )
          return@dataTaskWithURL
        }

        continuation.resume(data.toByteString())
      }

      continuation.invokeOnCancellation {
        task.cancel()
      }

      task.resume()
    }
  }
}
