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

package app.cash.zipline.loader

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString
import okio.IOException

class FakeZiplineHttpClient : ZiplineHttpClient {
  var filePathToByteString: Map<String, ByteString> = mapOf()
  val developmentServerWebSocketsLock = Mutex()
  val developmentServerWebSockets = mutableMapOf<String, Channel<String>>()

  suspend fun sendUpdate(url: String? = null) {
    var socket: Channel<String>? = null
    while (socket == null) {
      socket = developmentServerWebSocketsLock.withLock {
        url?.let { developmentServerWebSockets[it] }
          ?: developmentServerWebSockets.entries.firstOrNull()?.value
      }
      delay(100.milliseconds)
    }
    socket.send("reload")
  }

  fun closeUpdatesChannel(url: String? = null) {
    val socket: Channel<String>? = url?.let { developmentServerWebSockets[it] }
      ?: developmentServerWebSockets.entries.firstOrNull()?.value
    socket?.close()
//    while (socket == null) {
//      socket = developmentServerWebSocketsLock.withLock {
//        url?.let { developmentServerWebSockets[it] }
//          ?: developmentServerWebSockets.entries.firstOrNull()?.value
//      }
//      delay(100.milliseconds)
//    }
//    socket.close()
  }

  override suspend fun download(
    url: String,
    requestHeaders: List<Pair<String, String>>,
  ): ByteString {
    return filePathToByteString[url] ?: throw IOException("404: $url not found")
  }

  override suspend fun openDevelopmentServerWebSocket(
    url: String,
    requestHeaders: List<Pair<String, String>>,
  ): Flow<String> {
    val socket = developmentServerWebSocketsLock.withLock {
      developmentServerWebSockets[url] = Channel()
      developmentServerWebSockets[url]!!
    }
    return socket.consumeAsFlow()
  }
}
