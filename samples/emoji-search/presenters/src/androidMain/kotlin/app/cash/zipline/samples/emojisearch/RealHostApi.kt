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
package app.cash.zipline.samples.emojisearch

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class RealHostApi(
  private val client: OkHttpClient
) : HostApi {
  override suspend fun httpCall(url: String, headers: Map<String, String>): String {
    return suspendCancellableCoroutine { continuation ->
      val call = client.newCall(
        Request.Builder()
          .url(url)
          .headers(headers.toHeaders())
          .build()
      )
      call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
          continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
          val responseString = response.body!!.string()
          continuation.resumeWith(Result.success(responseString))
        }
      })
    }
  }
}
