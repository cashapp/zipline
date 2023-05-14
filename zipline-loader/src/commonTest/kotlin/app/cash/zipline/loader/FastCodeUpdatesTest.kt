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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class FastCodeUpdatesTest {
  private val client = FakeZiplineHttpClient()

  @Test
  internal fun opensWebSocketAndReceivesUpdates() = runTest {
    val testDuration = testTimeSource.measureTime {
      val manifestUrl = "http://localhost:8080/manifest.json"
      val wsUrl = "http://localhost:8080/ws"
      val manifestUrlFlow = flowOf(manifestUrl)
      val fastCodeFlow = manifestUrlFlow.withDevelopmentServerPush(client, 500.milliseconds)

      val channel = Channel<String>(capacity = Int.MAX_VALUE)
      val job = launch {
        fastCodeFlow.collect {
          channel.send(it)
        }
        channel.close()
      }

      // Receive initial value
      channel.receive()
      assertTrue(channel.isEmpty)

      // 3 x publish event & await URL flow emits
      client.sendDevelopmentServerUpdate(wsUrl)
      channel.receive()
      assertTrue(channel.isEmpty)
      client.sendDevelopmentServerUpdate(wsUrl)
      channel.receive()
      assertTrue(channel.isEmpty)
      client.sendDevelopmentServerUpdate(wsUrl)
      channel.receive()
      assertTrue(channel.isEmpty)

      job.cancel()
      client.log.close()
    }

    // confirm < 500 ms wall clock time has elapsed
    assertTrue(testDuration.inWholeMilliseconds < 500)
    assertEquals(listOf("open socket http://localhost:8080/ws"), client.log.consumeAsFlow().toList())
  }

  @Test
  internal fun webSocketAlreadyClosedFallsBackToPolling() = runTest {
    val testDuration = testTimeSource.measureTime {
      val manifestUrl = "http://localhost:8080/manifest.json"
      val wsUrl = "http://localhost:8080/ws"
      val manifestUrlFlow = flowOf(manifestUrl)
      val fastCodeFlow = manifestUrlFlow.withDevelopmentServerPush(client, 500.milliseconds)

      // close channel
      client.closeDevelopmentServerChannel(wsUrl)

      // await URL flow emitting twice: once at time 0, once at 500ms on polling fallback
      //    from socket failure
      fastCodeFlow.take(2).toList()
      client.log.close()
    }

    // Confirm 500 ms has elapsed.
    assertTrue(testDuration.inWholeMilliseconds >= 500)
    assertEquals(
      listOf(
        "open socket http://localhost:8080/ws",
        "open socket http://localhost:8080/ws",
        "close socket http://localhost:8080/ws",
      ),
      client.log.consumeAsFlow().toList().reversed(),
    )
  }

  @Test
  internal fun webSocketClosedForOldUrl() = runTest {
    val manifestUrlA = "http://a/manifest.json"
    val manifestUrlB = "http://b/manifest.json"
    val manifestUrlC = "http://c/manifest.json"
    val wsUrlA = "http://a/ws"
    val wsUrlB = "http://b/ws"
    val wsUrlC = "http://c/ws"
    val testDuration = testTimeSource.measureTime {
      val manifestUrlFlow = flowOf(
        manifestUrlA,
        manifestUrlB,
        manifestUrlC,
      )
      val fastCodeFlow = manifestUrlFlow.withDevelopmentServerPush(client, 500.milliseconds)

      // close channel
      client.closeDevelopmentServerChannel(wsUrlA)
      client.closeDevelopmentServerChannel(wsUrlB)
      client.closeDevelopmentServerChannel(wsUrlC)

      // await URL flow emitting twice: once at time 0, once at 500ms on polling fallback
      //    from socket failure
      fastCodeFlow.take(6).toList()
      client.log.close()
    }

    // confirm >= 500 ms has elapsed
    assertTrue(testDuration.inWholeMilliseconds >= 500)
    assertEquals(
      listOf(
      "close socket $wsUrlA",
      "close socket $wsUrlB",
      "close socket $wsUrlC",
      "open socket $wsUrlA",
      "open socket $wsUrlB",
      "open socket $wsUrlC",
    ),
      client.log.consumeAsFlow().toList().take(6),
    )
  }

  @Test
  internal fun reconnectsToWebSocketAfterFailure() = runTest {
    val wsUrl = "http://localhost:8080/ws"
    val manifestUrl = "http://localhost:8080/manifest.json"
    val manifestUrlFlow = flowOf(manifestUrl)
    val fastCodeFlow = manifestUrlFlow.withDevelopmentServerPush(client, 500.milliseconds)

    val channel = Channel<String>(capacity = Int.MAX_VALUE)
    val job = launch {
      fastCodeFlow.collect {
        channel.send(it)
      }
      try {
        channel.close()
      } catch (ignored: Exception) {
      }
    }

    // Receive initial value.
    channel.receive()
    assertTrue(channel.isEmpty)

    // Receive web socket value.
    client.sendDevelopmentServerUpdate(wsUrl)
    channel.receive()

    // Break the web socket, it should disconnect, poll once, and then re-open the socket.
    client.sendDevelopmentServerError(wsUrl)
    channel.receive()

    job.cancel()
    client.log.close()

    assertEquals(
      listOf(
      "open socket $wsUrl",
      "open socket $wsUrl",
    ),
      client.log.consumeAsFlow().toList().take(6),
    )
  }
}
