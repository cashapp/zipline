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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transformLatest

/**
 * Returns a flow that emits every time this flow emits, and also emits whenever the websocket
 * for the most recent URL signals an update.
 *
 * Use this in development mode to trigger code updates as soon as the development server has them.
 *
 * @param pollingInterval the emit period if an updates web socket cannot be established.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<String>.withDevelopmentServerPush(
  httpClient: ZiplineHttpClient,
  pollingInterval: Duration = 500.milliseconds,
): Flow<String> {
  return transformLatest { manifestUrl ->
    // Attempt once before any websocket setup or polling.
    emit(manifestUrl)

    while (true) {
      // Collect reload signals on a web socket.
      val webSocketUrl = schemeAndAuthority(manifestUrl) + "/ws"
      val flow = httpClient.openDevelopmentServerWebSocket(webSocketUrl, listOf())
      flow.takeWhile { message ->
        when (message) {
          "reload" -> {
            emit(manifestUrl)
            true
          }
          "heartbeat" -> true
          else -> false // Not the web socket we were expecting. Fall back to polling.
        }
      }.collect()

      // If our web socket was exhausted or never worked? Poll once, then try the web socket again.
      // This ensures we reconnect if the web socket server is restarted.
      delay(pollingInterval)
      emit(manifestUrl)
    }
  }
}

/**
 * Returns a string like "http://host:8080" given a full URL like "http://host:8080/path?query".
 * This is naive and assumes a well-formed URL.
 */
fun schemeAndAuthority(url: String): String {
  val regex = Regex("([^/]+//[^/]+)")
  val matchResult = regex.matchAt(url, index = 0)
  require(matchResult != null) { "expected a URL but was $url" }
  return matchResult.value
}
