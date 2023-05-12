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

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

internal class OkHttpZiplineHttpClient(
  private val okHttpClient: OkHttpClient,
) : ZiplineHttpClient() {
  override suspend fun download(
    url: String,
    requestHeaders: List<Pair<String, String>>,
  ): ByteString {
    return suspendCancellableCoroutine { continuation ->
      val call = okHttpClient.newCall(
        Request.Builder()
          .url(url)
          .apply {
            for ((name, value) in requestHeaders) {
              addHeader(name, value)
            }
          }
          .build(),
      )

      continuation.invokeOnCancellation {
        call.cancel()
      }

      call.enqueue(object : Callback {
        override fun onFailure(
          call: Call,
          e: IOException,
        ) {
          continuation.resumeWithException(e)
        }

        override fun onResponse(
          call: Call,
          response: Response,
        ) {
          val byteString = response.use {
            try {
              if (!response.isSuccessful) {
                throw IOException("failed to fetch $url: ${response.code}")
              }
              response.body!!.byteString()
            } catch (e: IOException) {
              continuation.resumeWithException(e)
              return@onResponse
            }
          }

          continuation.resume(byteString)
        }
      })
    }
  }

  override suspend fun openDevelopmentServerWebSocket(
    url: String,
    requestHeaders: List<Pair<String, String>>,
  ): Flow<String> {
    val request = Request.Builder()
      .url(url)
      .build()

    return callbackFlow {
      val webSocket = okHttpClient.newWebSocket(
        request,
        object : WebSocketListener() {
          override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            channel.close()
            webSocket.close(1000, null)
          }

          override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            channel.close()
          }

          override fun onMessage(webSocket: WebSocket, text: String) {
            trySendBlocking(text)
          }
        },
      )

      awaitClose {
        webSocket.cancel()
      }
    }
  }
}
