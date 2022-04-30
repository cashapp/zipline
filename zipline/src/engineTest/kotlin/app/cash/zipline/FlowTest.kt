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
package app.cash.zipline

import app.cash.zipline.testing.newEndpointPair
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope

internal class FlowTest {
  interface FlowEchoService : ZiplineService {
    fun createFlow(message: String, count: Int): Flow<String>
    suspend fun flowParameter(flow: Flow<String>): Int
  }

  class RealFlowEchoService : FlowEchoService {
    override fun createFlow(message: String, count: Int): Flow<String> {
      return flow {
        for (i in 0 until count) {
          delay(10) // Ensure we can send async through the reference.
          emit("$i $message")
        }
      }
    }

    override suspend fun flowParameter(flow: Flow<String>): Int {
      return flow.count()
    }
  }

  @Test
  fun flowReturnValueWorks() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealFlowEchoService()

    endpointA.bind<FlowEchoService>("service", service)
    val client = endpointB.take<FlowEchoService>("service")
    val initialServiceNames = endpointA.serviceNames
    val initialClientNames = endpointA.clientNames

    val flow = client.createFlow("hello", 3)
    assertEquals(listOf("0 hello", "1 hello", "2 hello"), flow.toList())

    // Confirm that no services or clients were leaked.
    assertEquals(initialServiceNames, endpointA.serviceNames)
    assertEquals(initialClientNames, endpointA.clientNames)
  }

  @Test
  fun flowParameterWorks() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealFlowEchoService()

    endpointA.bind<FlowEchoService>("service", service)
    val client = endpointB.take<FlowEchoService>("service")
    val initialServiceNames = endpointA.serviceNames
    val initialClientNames = endpointA.clientNames

    val flow = flow {
      for (i in 1..3) {
        delay(10) // Ensure we can send async through the reference.
        emit("$i")
      }
    }

    val deferredCount = async {
      client.flowParameter(flow)
    }

    assertEquals(3, deferredCount.await())

    // Confirm that no services or clients were leaked.
    assertEquals(initialServiceNames, endpointA.serviceNames)
    assertEquals(initialClientNames, endpointA.clientNames)
  }

  @Test
  fun flowCanBeUsedWithoutPassingThroughZipline() = runBlocking {
    val service = RealFlowEchoService()
    val flow = service.createFlow("hello", 3)
    assertEquals(listOf("0 hello", "1 hello", "2 hello"), flow.toList())
  }

  @Test
  fun receivingEndpointCancelsFlow() = runBlocking {
    val channel = Channel<String>(Int.MAX_VALUE)

    val (endpointA, endpointB) = newEndpointPair(this)
    val service = object : FlowEchoService {
      override fun createFlow(message: String, count: Int): Flow<String> {
        return channel.consumeAsFlow()
      }

      override suspend fun flowParameter(flow: Flow<String>): Int = error("unexpected call")
    }

    endpointA.bind<FlowEchoService>("service", service)
    val client = endpointB.take<FlowEchoService>("service")
    val initialServiceNames = endpointA.serviceNames
    val initialClientNames = endpointA.clientNames

    val flow = client.createFlow("", 0)
    channel.send("A")
    channel.send("B")
    channel.send("C")

    val received = mutableListOf<String>()
    val e = assertFailsWith<Exception> {
      flow.collect {
        received += it
        if (received.size == 2) channel.cancel()
      }
    }
    assertContains(e.toString(), "CancellationException")
    assertEquals(listOf("A", "B"), received)

    // Confirm that no services or clients were leaked.
    assertEquals(initialServiceNames, endpointA.serviceNames)
    assertEquals(initialClientNames, endpointA.clientNames)
  }

  @Test
  fun callingEndpointCancelsFlow() = runBlocking {
    val channel = Channel<String>(Int.MAX_VALUE)

    val (endpointA, endpointB) = newEndpointPair(this)
    val service = object : FlowEchoService {
      override fun createFlow(message: String, count: Int): Flow<String> {
        return channel.consumeAsFlow()
      }

      override suspend fun flowParameter(flow: Flow<String>): Int = error("unexpected call")
    }

    endpointA.bind<FlowEchoService>("service", service)
    val client = endpointB.take<FlowEchoService>("service")
    val initialServiceNames = endpointA.serviceNames
    val initialClientNames = endpointA.clientNames

    val flow = client.createFlow("", 0)
    channel.send("A")
    channel.send("B")
    channel.send("C")

    val received = mutableListOf<String>()
    supervisorScope {
      assertFailsWith<CancellationException> {
        coroutineScope {
          flow.collect {
            received += it
            if (received.size == 2) {
              this@coroutineScope.cancel()
            }
          }
        }
      }
    }
    assertEquals(listOf("A", "B"), received)

    // Confirm that no services or clients were leaked.
    awaitCondition { initialServiceNames == endpointA.serviceNames }
    awaitCondition { initialClientNames == endpointA.clientNames }
  }
}
