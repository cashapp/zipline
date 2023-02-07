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
      client.sendDevelopmentServerUpdate()
      channel.receive()
      assertTrue(channel.isEmpty)
      client.sendDevelopmentServerUpdate()
      channel.receive()
      assertTrue(channel.isEmpty)
      client.sendDevelopmentServerUpdate()
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
      val manifestUrlFlow = flowOf("http://localhost:8080/manifest.json")
      val fastCodeFlow = manifestUrlFlow.withDevelopmentServerPush(client, 500.milliseconds)

      // close channel
      client.closeDevelopmentServerChannel("http://localhost:8080/manifest.json")

      // await URL flow emitting twice: once at time 0, once at 500ms on polling fallback
      //    from socket failure
      fastCodeFlow.take(2).toList()
      client.log.close()
    }

    // Confirm 500 ms has elapsed.
    assertTrue(testDuration.inWholeMilliseconds >= 500)
    assertEquals(listOf("open socket http://localhost:8080/ws", "close socket http://localhost:8080/manifest.json"), client.log.consumeAsFlow().toList().reversed())
  }

  @Test
  internal fun webSocketClosedForOldUrl() = runTest {
    val testDuration = testTimeSource.measureTime {
      val manifestUrlFlow = flowOf(
        "http://localhost:1/manifest.json",
        "http://localhost:2/manifest.json",
        "http://localhost:3/manifest.json",
      )
      val fastCodeFlow = manifestUrlFlow.withDevelopmentServerPush(client, 500.milliseconds)

      // close channel
      client.closeDevelopmentServerChannel("http://localhost:1/manifest.json")
      client.closeDevelopmentServerChannel("http://localhost:2/manifest.json")
      client.closeDevelopmentServerChannel("http://localhost:3/manifest.json")

      // await URL flow emitting twice: once at time 0, once at 500ms on polling fallback
      //    from socket failure
      fastCodeFlow.take(6).toList()
      client.log.close()
    }

    // confirm >= 500 ms has elapsed
    assertTrue(testDuration.inWholeMilliseconds >= 500)
    assertEquals(listOf(
      "open socket http://localhost:3/ws",
      "open socket http://localhost:2/ws",
      "open socket http://localhost:1/ws",
      "close socket http://localhost:3/manifest.json",
      "close socket http://localhost:2/manifest.json",
      "close socket http://localhost:1/manifest.json",
    ), client.log.consumeAsFlow().toList().reversed())
  }
}
