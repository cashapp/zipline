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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okio.ByteString

abstract class ZiplineHttpClient {
  abstract suspend fun download(
    url: String,
    requestHeaders: List<Pair<String, String>>,
  ): ByteString

  /**
   * Opens a receive-only web socket to [url], and returns a flow that emits each message pushed by
   * the server.
   *
   * This is not a general-purpose web socket API, and serves only the needs of [ZiplineLoader]'s
   * code update signaling for development. For example, this does not expose HTTP response headers,
   * binary messages, open events, or close events.
   *
   * The flow terminates when the web socket is closed. This will be immediately if the web socket
   * cannot be established, after a graceful shutdown, or after an abrupt disconnection. The close
   * reason is not exposed in this API.
   *
   * The default implementation returns an empty flow.
   */
  open suspend fun openDevelopmentServerWebSocket(
    url: String,
    requestHeaders: List<Pair<String, String>>,
  ): Flow<String> = flowOf()
}
