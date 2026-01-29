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
import assertk.assertThat
import assertk.assertions.containsExactly
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope

internal class FlowTest {
  interface FlowEchoService : ZiplineService {
    fun createFlow(message: String, count: Int): Flow<String>
    suspend fun flowParameter(flow: Flow<String>): Int
  }

  class RealFlowEchoService :
    FlowEchoService,
    ZiplineScoped {
    override val scope = ZiplineScope()

    override fun createFlow(message: String, count: Int): Flow<String> {
      return flow {
        for (i in 0 until count) {
          forceSuspend() // Ensure we can send async through the reference.
          emit("$i $message")
        }
      }
    }

    override suspend fun flowParameter(flow: Flow<String>): Int {
      return flow.count()
    }

    override fun close() {
      scope.close()
    }
  }

  @Test
  fun flowReturnValueWorks() = runBlocking(Unconfined) {
    val scope = ZiplineScope()
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealFlowEchoService()

    endpointA.bind<FlowEchoService>("service", service)
    val client = endpointB.take<FlowEchoService>("service", scope)

    val flow = client.createFlow("hello", 3)
    assertEquals(listOf("0 hello", "1 hello", "2 hello"), flow.toList())

    // Confirm that no services or clients were leaked.
    scope.close()
    assertEquals(setOf(), endpointA.serviceNames)
    assertEquals(setOf(), endpointB.serviceNames)
  }

  @Test
  fun flowParameterWorks() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealFlowEchoService()

    endpointA.bind<FlowEchoService>("service", service)
    val client = endpointB.take<FlowEchoService>("service")

    val flow = flow {
      for (i in 1..3) {
        forceSuspend() // Ensure we can send async through the reference.
        emit("$i")
      }
    }

    val deferredCount = async {
      client.flowParameter(flow)
    }

    assertEquals(3, deferredCount.await())

    // Confirm that no services or clients were leaked.
    client.close()
    assertEquals(setOf(), endpointA.serviceNames)
    assertEquals(setOf(), endpointB.serviceNames)
  }

  @Test
  fun flowCanBeUsedWithoutPassingThroughZipline() = runBlocking(Unconfined) {
    val service = RealFlowEchoService()
    val flow = service.createFlow("hello", 3)
    assertEquals(listOf("0 hello", "1 hello", "2 hello"), flow.toList())
  }

  @Test
  fun receivingEndpointCancelsFlow() = runBlocking(Unconfined) {
    val scope = ZiplineScope()
    val channel = Channel<String>(Int.MAX_VALUE)

    val (endpointA, endpointB) = newEndpointPair(this)
    val service = object : FlowEchoService {
      override fun createFlow(message: String, count: Int): Flow<String> {
        return channel.consumeAsFlow()
      }

      override suspend fun flowParameter(flow: Flow<String>): Int = error("unexpected call")
    }

    endpointA.bind<FlowEchoService>("service", service)
    val client = endpointB.take<FlowEchoService>("service", scope)

    val flow = client.createFlow("", 0)
    val deferred = async {
      channel.send("A")
      forceSuspend()
      channel.send("B")
      forceSuspend()
      channel.send("C")
      forceSuspend()
    }

    val received = mutableListOf<String>()
    val e = assertFailsWith<CancellationException> {
      flow.collect {
        received += it
        if (received.size == 2) channel.cancel()
      }
    }
    assertContains(e.toString(), "CancellationException")
    deferred.join()
    assertEquals(listOf("A", "B"), received)

    // Confirm that no services or clients were leaked.
    scope.close()
    assertEquals(setOf(), endpointA.serviceNames)
    assertEquals(setOf(), endpointB.serviceNames)
  }

  @Test
  fun callingEndpointCancelsFlow() = runBlocking(Unconfined) {
    val scope = ZiplineScope()
    val channel = Channel<String>(Int.MAX_VALUE)

    val (endpointA, endpointB) = newEndpointPair(this)
    val service = object : FlowEchoService {
      override fun createFlow(message: String, count: Int): Flow<String> {
        return channel.consumeAsFlow()
      }

      override suspend fun flowParameter(flow: Flow<String>): Int = error("unexpected call")
    }

    endpointA.bind<FlowEchoService>("service", service)
    val client = endpointB.take<FlowEchoService>("service", scope)

    val flow = client.createFlow("", 0)
    val deferred = async {
      channel.send("A")
      forceSuspend()
      channel.send("B")
      forceSuspend()
      channel.send("C")
      forceSuspend()
    }

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
    deferred.join()
    assertEquals(listOf("A", "B"), received)

    // Confirm that no services or clients were leaked.
    scope.close()
    assertEquals(setOf(), endpointA.serviceNames)
    assertEquals(setOf(), endpointB.serviceNames)
  }

  @Test
  fun collectFlowMultipleTimes() = runBlocking(Unconfined) {
    val scope = ZiplineScope()
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealFlowEchoService()

    endpointA.bind<FlowEchoService>("service", service)
    val client = endpointB.take<FlowEchoService>("service", scope)

    val flow = client.createFlow("hello", 3)
    assertEquals(listOf("0 hello", "1 hello", "2 hello"), flow.toList())
    assertEquals(listOf("0 hello", "1 hello", "2 hello"), flow.toList())

    // Confirm that no services or clients were leaked.
    scope.close()
    assertEquals(setOf(), endpointA.serviceNames)
    assertEquals(setOf(), endpointB.serviceNames)
  }

  @Test
  fun collectFlowZeroTimes() = runBlocking(Unconfined) {
    val scope = ZiplineScope()
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealFlowEchoService()

    endpointA.bind<FlowEchoService>("service", service)
    val client = endpointB.take<FlowEchoService>("service", scope)

    client.createFlow("hello", 3)

    // Confirm that no services or clients were leaked.
    scope.close()
    assertEquals(setOf(), endpointA.serviceNames)
    assertEquals(setOf(), endpointB.serviceNames)
  }

  /** https://github.com/cashapp/zipline/issues/1039 */
  @Test
  fun differentFlowTypes() = runBlocking(Unconfined) {
    val scope = ZiplineScope()
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = object : DifferentFlowTypesService {
      override fun stringFlow(): Flow<String> = flowOf("a")
      override fun intFlow(): Flow<Int> = flowOf(1)
      override fun stringListFlow(): Flow<List<String>> = flowOf(listOf("a"))
      override fun intListFlow(): Flow<List<Int>> = flowOf(listOf(1))
    }

    endpointA.bind<DifferentFlowTypesService>("service", service)
    val client = endpointB.take<DifferentFlowTypesService>("service", scope)

    assertThat(client.stringFlow().toList()).containsExactly("a")
    assertThat(client.intFlow().toList()).containsExactly(1)

    assertThat(client.stringListFlow().toList()).containsExactly(listOf("a"))
    assertThat(client.intListFlow().toList()).containsExactly(listOf(1))

    scope.close()
  }

  interface DifferentFlowTypesService : ZiplineService {
    fun stringFlow(): Flow<String>
    fun intFlow(): Flow<Int>
    fun stringListFlow(): Flow<List<String>>
    fun intListFlow(): Flow<List<Int>>
  }
}
