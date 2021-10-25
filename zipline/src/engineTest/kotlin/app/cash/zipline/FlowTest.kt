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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

internal class FlowTest {
  interface FlowEchoService {
    fun createFlow(message: String, count: Int): Flow<String>
    fun createTwoFlows(message: String, count: Int): TwoFlows<String>
    suspend fun flowParameter(flow: Flow<String>): Int
    suspend fun twoFlowsParameter(twoFlows: TwoFlows<String>): Int
  }

  @Serializable
  data class TwoFlows<T>(
    @Contextual val first: Flow<T>,
    @Contextual val second: Flow<T>,
  )

  class RealFlowEchoService : FlowEchoService {
    override fun createFlow(message: String, count: Int): Flow<String> {
      return flow {
        for (i in 0 until count) {
          delay(10) // Ensure we can send async through the reference.
          this.emit("$i $message")
        }
      }
    }

    override fun createTwoFlows(message: String, count: Int): TwoFlows<String> {
      val countingFlow = flow {
        for (i in 0 until count) {
          delay(10) // Ensure we can send async through the reference.
          emit(i)
        }
      }
      return TwoFlows(
        first = countingFlow.map { "$it $message" },
        second = countingFlow.map { "$message $it" },
      )
    }

    override suspend fun flowParameter(flow: Flow<String>): Int {
      return flow.count()
    }

    override suspend fun twoFlowsParameter(twoFlows: TwoFlows<String>): Int {
      return coroutineScope {
        val firstCount = async { twoFlows.first.count() }
        val secondCount = async { twoFlows.second.count() }
        firstCount.await() + secondCount.await()
      }
    }
  }

  @Test
  fun flowReturnValueWorks() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealFlowEchoService()

    endpointA.set<FlowEchoService>("service", service)
    val client = endpointB.get<FlowEchoService>("service")
    val initialServiceNames = endpointA.serviceNames
    val initialClientNames = endpointA.clientNames

    val flow = client.createFlow("hello", 3)
    assertEquals(listOf("0 hello", "1 hello", "2 hello"), flow.toList())

    // Confirm that no services or clients were leaked.
    assertEquals(initialServiceNames, endpointA.serviceNames)
    assertEquals(initialClientNames, endpointA.clientNames)
  }

  @Test
  fun twoFlowsReturnValueWorks() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealFlowEchoService()

    endpointA.set<FlowEchoService>("service", service)
    val client = endpointB.get<FlowEchoService>("service")
    val initialServiceNames = endpointA.serviceNames
    val initialClientNames = endpointA.clientNames

    val twoFlows = client.createTwoFlows("hello", 3)
    assertEquals(listOf("0 hello", "1 hello", "2 hello"), twoFlows.first.toList())
    assertEquals(listOf("hello 0", "hello 1", "hello 2"), twoFlows.second.toList())

    // Confirm that no services or clients were leaked.
    assertEquals(initialServiceNames, endpointA.serviceNames)
    assertEquals(initialClientNames, endpointA.clientNames)
  }

  @Test
  fun flowParameterWorks() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealFlowEchoService()

    endpointA.set<FlowEchoService>("service", service)
    val client = endpointB.get<FlowEchoService>("service")
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
  fun twoFlowsParameterWorks() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealFlowEchoService()

    endpointA.set<FlowEchoService>("service", service)
    val client = endpointB.get<FlowEchoService>("service")
    val initialServiceNames = endpointA.serviceNames
    val initialClientNames = endpointA.clientNames

    val countingFlow = flow {
      for (i in 1..3) {
        delay(10) // Ensure we can send async through the reference.
        emit("$i")
      }
    }
    val twoFlows = TwoFlows(
      first = countingFlow,
      second = countingFlow.take(2),
    )

    val deferredCount = async {
      client.twoFlowsParameter(twoFlows)
    }

    assertEquals(5, deferredCount.await())

    // Confirm that no services or clients were leaked.
    assertEquals(initialServiceNames, endpointA.serviceNames)
    assertEquals(initialClientNames, endpointA.clientNames)
  }
}
