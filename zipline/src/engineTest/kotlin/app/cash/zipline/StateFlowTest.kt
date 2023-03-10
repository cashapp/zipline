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

import app.cash.zipline.testing.awaitEquals
import app.cash.zipline.testing.newEndpointPair
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope

internal class StateFlowTest {
  interface StateFlowEchoService : ZiplineService {
    val flow: StateFlow<String>
    suspend fun createFlow(message: String, count: Int): StateFlow<String>
    suspend fun take(flow: StateFlow<String>, count: Int): List<String>
  }

  class RealStateFlowEchoService(initialValue: String = "") : StateFlowEchoService, ZiplineScoped {
    override val scope = ZiplineScope()
    val mutableFlow = MutableStateFlow(initialValue)
    override val flow: StateFlow<String> get() = mutableFlow

    override suspend fun createFlow(message: String, count: Int): StateFlow<String> = coroutineScope {
      flow {
        repeat(count) { index ->
          forceSuspend() // Ensure we can send async through the reference.
          emit("$index $message")
        }
      }.stateIn(this)
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
    val service = RealStateFlowEchoService("first")

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
    assertEquals(setOf(), endpointA.clientNames)
  }

  @Test
  fun stateFlowReplayCacheWorks() = runBlocking(Unconfined) {
    val scope = ZiplineScope()
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealStateFlowEchoService("first")

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
    assertEquals(setOf(), endpointA.clientNames)
  }

   @Test
   fun flowParameterWorks() = runBlocking(Unconfined) {
     val (endpointA, endpointB) = newEndpointPair(this)
     val service = RealStateFlowEchoService()

     endpointA.bind<StateFlowEchoService>("service", service)
     val client = endpointB.take<StateFlowEchoService>("service")

     val flow = flow {
       for (i in 1..3) {
         forceSuspend() // Ensure we can send async through the reference.
         emit("$i")
       }
     }.stateIn(this, SharingStarted.Lazily, "0")

     val deferredItems = async {
       client.take(flow, 3)
     }

     assertEquals(listOf("1", "2", "3"), deferredItems.await())

     // Confirm that no services or clients were leaked.
     client.close()
     assertEquals(setOf(), endpointA.serviceNames)
     assertEquals(setOf(), endpointA.clientNames)
   }

   @Test
   fun flowCanBeUsedWithoutPassingThroughZipline() = runBlocking(Unconfined) {
     val service = RealStateFlowEchoService()
     val flow = service.createFlow("hello", 3)
     assertEquals(listOf("0 hello", "1 hello", "2 hello"), listOf(flow.value))
   }

   @Test
   fun receivingEndpointCancelsFlow() = runBlocking(Unconfined) {
     val scope = ZiplineScope()
     val channel = Channel<String>(Int.MAX_VALUE)

     val (endpointA, endpointB) = newEndpointPair(this)
     val service = object : StateFlowEchoService {
       override val flow get() = error("unexpected call")

       override suspend fun createFlow(message: String, count: Int) = coroutineScope {
         channel.consumeAsFlow().stateIn(this)
       }

       override suspend fun take(flow: StateFlow<String>, count: Int) = error("unexpected call")
     }

     endpointA.bind<StateFlowEchoService>("service", service)
     val client = endpointB.take<StateFlowEchoService>("service", scope)

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
     val e = assertFailsWith<ZiplineException> {
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
     assertEquals(setOf(), endpointA.clientNames)
   }

   @Test
   fun callingEndpointCancelsFlow() = runBlocking(Unconfined) {
     val scope = ZiplineScope()
     val channel = Channel<String>(Int.MAX_VALUE)

     val (endpointA, endpointB) = newEndpointPair(this)
     val service = object : StateFlowEchoService {
       override val flow get() = error("unexpected call")

       override suspend fun createFlow(message: String, count: Int) = coroutineScope {
         channel.consumeAsFlow().stateIn(this)
       }

       override suspend fun take(flow: StateFlow<String>, count: Int) = error("unexpected call")
     }

     endpointA.bind<StateFlowEchoService>("service", service)
     val client = endpointB.take<StateFlowEchoService>("service", scope)

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
     awaitEquals(setOf<String>()) { endpointA.serviceNames }
     awaitEquals(setOf<String>()) { endpointA.clientNames }
   }

   @Test
   fun collectFlowMultipleTimes() = runBlocking(Unconfined) {
     val scope = ZiplineScope()
     val (endpointA, endpointB) = newEndpointPair(this)
     val service = RealStateFlowEchoService()

     endpointA.bind<StateFlowEchoService>("service", service)
     val client = endpointB.take<StateFlowEchoService>("service", scope)

     val flow = client.createFlow("hello", 3)
     assertEquals(listOf("0 hello", "1 hello", "2 hello"), flow.take(1).toList())
     assertEquals(listOf("0 hello", "1 hello", "2 hello"), flow.replayCache)

     // Confirm that no services or clients were leaked.
     scope.close()
     assertEquals(setOf(), endpointA.serviceNames)
     assertEquals(setOf(), endpointA.clientNames)
   }

   @Test
   fun collectFlowZeroTimes() = runBlocking(Unconfined) {
     val scope = ZiplineScope()
     val (endpointA, endpointB) = newEndpointPair(this)
     val service = RealStateFlowEchoService()

     endpointA.bind<StateFlowEchoService>("service", service)
     val client = endpointB.take<StateFlowEchoService>("service", scope)

     client.createFlow("hello", 3)

     // Confirm that no services or clients were leaked.
     scope.close()
     assertEquals(setOf(), endpointA.serviceNames)
     assertEquals(setOf(), endpointA.clientNames)
   }
}
