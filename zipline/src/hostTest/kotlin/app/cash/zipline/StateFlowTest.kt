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
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

internal class StateFlowTest {
  interface StateFlowEchoService : ZiplineService {
    val flow: StateFlow<String>
    suspend fun createFlow(initialValue: String): StateFlow<String>
    suspend fun createFlow(initialValue: String, values: List<String>): StateFlow<String>
    suspend fun take(flow: StateFlow<String>, count: Int): List<String>
  }

  class RealStateFlowEchoService(initialValue: String = "") :
    StateFlowEchoService,
    ZiplineScoped {
    override val scope = ZiplineScope()
    val mutableFlow = MutableStateFlow(initialValue)
    override val flow: StateFlow<String> get() = mutableFlow

    override suspend fun createFlow(initialValue: String): StateFlow<String> {
      return MutableStateFlow(initialValue)
    }

    lateinit var coroutineScope: CoroutineScope

    override suspend fun createFlow(initialValue: String, values: List<String>): StateFlow<String> {
      return flow {
        for (value in values) {
          forceSuspend()
          emit(value)
        }
      }.stateIn(coroutineScope, SharingStarted.Lazily, initialValue)
    }

    override suspend fun take(flow: StateFlow<String>, count: Int): List<String> {
      return flow.take(count).toList()
    }

    override fun close() {
      scope.close()
    }
  }

  @Test
  fun stateFlowValueWorks() = runBlocking(Unconfined) {
    val scope = ZiplineScope()
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealStateFlowEchoService(initialValue = "first")

    endpointA.bind<StateFlowEchoService>("service", service)
    val client = endpointB.take<StateFlowEchoService>("service", scope)

    assertEquals("first", client.flow.value)

    service.mutableFlow.value = "second"
    assertEquals("second", client.flow.value)

    service.mutableFlow.emit("third")
    assertEquals("third", client.flow.value)

    service.mutableFlow.tryEmit("fourth")
    assertEquals("fourth", client.flow.value)

    // Confirm that no services or clients were leaked.
    scope.close()
    assertEquals(setOf(), endpointA.serviceNames)
    assertEquals(setOf(), endpointB.serviceNames)
    assertEquals(0, service.mutableFlow.subscriptionCount.first())
  }

  @Test
  fun stateFlowReplayCacheWorks() = runBlocking(Unconfined) {
    val scope = ZiplineScope()
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealStateFlowEchoService(initialValue = "first")

    endpointA.bind<StateFlowEchoService>("service", service)
    val client = endpointB.take<StateFlowEchoService>("service", scope)

    assertEquals(listOf("first"), client.flow.replayCache)

    service.mutableFlow.value = "second"
    assertEquals(listOf("second"), client.flow.replayCache)

    service.mutableFlow.emit("third")
    assertEquals(listOf("third"), client.flow.replayCache)

    service.mutableFlow.tryEmit("fourth")
    assertEquals(listOf("fourth"), client.flow.replayCache)

    // Confirm that no services or clients were leaked.
    scope.close()
    assertEquals(setOf(), endpointA.serviceNames)
    assertEquals(setOf(), endpointB.serviceNames)
    assertEquals(0, service.mutableFlow.subscriptionCount.first())
  }

  @Test
  fun stateFlowAbortWorks() = runBlocking(Unconfined) {
    val scope = ZiplineScope()
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealStateFlowEchoService(initialValue = "first")

    endpointA.bind<StateFlowEchoService>("service", service)
    val client = endpointB.take<StateFlowEchoService>("service", scope)

    // 'Flow.take' throws AbortFlowException internally. Make sure we handle it gracefully.
    assertEquals(listOf("first"), client.flow.take(1).toList())

    // Confirm that no services or clients were leaked.
    scope.close()
    assertEquals(setOf(), endpointA.serviceNames)
    assertEquals(setOf(), endpointB.serviceNames)
    assertEquals(0, service.mutableFlow.subscriptionCount.first())
  }

  @Test
  fun stateFlowDoesNotEmitUnlessValueChanges() = runTest {
    val scope = ZiplineScope()
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealStateFlowEchoService()
    service.coroutineScope = backgroundScope

    endpointA.bind<StateFlowEchoService>("service", service)
    val client = endpointB.take<StateFlowEchoService>("service", scope)

    val flow = client.createFlow("1", listOf("1", "1", "2", "3", "3", "3", "4"))
    assertEquals(listOf("1", "2", "3", "4"), client.take(flow, 4))

    // Confirm that no services or clients were leaked.
    scope.close()
    assertEquals(setOf(), endpointA.serviceNames)
    assertEquals(setOf(), endpointB.serviceNames)
    assertEquals(0, service.mutableFlow.subscriptionCount.first())
  }

  @Test
  fun flowParameterWorks() = runTest {
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealStateFlowEchoService()

    endpointA.bind<StateFlowEchoService>("service", service)
    val client = endpointB.take<StateFlowEchoService>("service")

    val flow = flow {
      for (i in 1..3) {
        forceSuspend()
        emit("$i")
      }
    }.stateIn(backgroundScope, SharingStarted.Lazily, "0")

    val deferredItems = async {
      client.take(flow, 4)
    }

    assertEquals(listOf("0", "1", "2", "3"), deferredItems.await())

    // Confirm that no services or clients were leaked.
    client.close()
    assertEquals(setOf(), endpointA.serviceNames)
    assertEquals(setOf(), endpointB.serviceNames)
    assertEquals(0, service.mutableFlow.subscriptionCount.first())
  }

  @Test
  fun flowCanBeUsedWithoutPassingThroughZipline() = runBlocking(Unconfined) {
    val service = RealStateFlowEchoService()
    val flow = service.createFlow("hello")
    assertEquals(listOf("hello"), service.take(flow, 1))
  }

  @Test
  fun collectFlowOneTime() = runBlocking(Unconfined) {
    val scope = ZiplineScope()
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealStateFlowEchoService()

    endpointA.bind<StateFlowEchoService>("service", service)
    val client = endpointB.take<StateFlowEchoService>("service", scope)

    val flow = client.createFlow("hello")
    assertEquals(listOf("hello"), client.take(flow, 1))

    // Confirm that no services or clients were leaked.
    scope.close()
    assertEquals(setOf(), endpointA.serviceNames)
    assertEquals(setOf(), endpointB.serviceNames)
    assertEquals(0, service.mutableFlow.subscriptionCount.first())
  }

  @Test
  fun collectFlowZeroTimes() = runBlocking(Unconfined) {
    val scope = ZiplineScope()
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealStateFlowEchoService()

    endpointA.bind<StateFlowEchoService>("service", service)
    val client = endpointB.take<StateFlowEchoService>("service", scope)

    client.createFlow("hello")

    // Confirm that no services or clients were leaked.
    scope.close()
    assertEquals(setOf(), endpointA.serviceNames)
    assertEquals(setOf(), endpointB.serviceNames)
    assertEquals(0, service.mutableFlow.subscriptionCount.first())
  }
}
