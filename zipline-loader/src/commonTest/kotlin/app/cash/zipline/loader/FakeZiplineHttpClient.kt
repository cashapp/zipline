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

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import okio.ByteString
import okio.IOException

class FakeZiplineHttpClient : ZiplineHttpClient() {
  var filePathToByteString: Map<String, ByteString> = mapOf()
  val webSockets = mutableMapOf<String, Channel<String>>()
  val log = Channel<String>(capacity = Int.MAX_VALUE)

  suspend fun sendDevelopmentServerUpdate(url: String) {
    val channel = webSockets.getOrPut(url) { Channel() }
    channel.send("reload")
  }

  suspend fun sendDevelopmentServerError(url: String) {
    val channel = webSockets.getOrPut(url) { Channel() }
    channel.send("boom!")
  }

  suspend fun closeDevelopmentServerChannel(url: String) {
    log.send("close socket $url")
    val channel = webSockets.getOrPut(url) { Channel() }
    channel.close()
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
    log.send("open socket $url")
    val channel = webSockets.getOrPut(url) { Channel() }
    return channel.consumeAsFlow()
  }
}
