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

import app.cash.zipline.internal.bridge.Endpoint
import app.cash.zipline.testing.newEndpointPair
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class FlowTest {
  private val dispatcher = TestCoroutineDispatcher()
  private val endpointA: Endpoint
  private val endpointB: Endpoint

  init {
    val (endpointA, endpointB) = newEndpointPair(CoroutineScope(dispatcher))
    this.endpointA = endpointA
    this.endpointB = endpointB
  }

  interface FlowEchoService {
    fun createFlow(message: String, count: Int): FlowReference<String>
    suspend fun flowParameter(flowReference: FlowReference<String>): Int
  }

  class RealFlowEchoService : FlowEchoService {
    override fun createFlow(message: String, count: Int): FlowReference<String> {
      val flow = flow {
        for (i in 0 until count) {
          emit("$i $message")
        }
      }
      return flow.asFlowReference()
    }

    override suspend fun flowParameter(flowReference: FlowReference<String>): Int {
      val flow = flowReference.get()
      return flow.count()
    }
  }

  @Test
  fun flowReturnValueWorks(): Unit = runBlocking(dispatcher) {
    val service = RealFlowEchoService()

    endpointA.set<FlowEchoService>("service", service)
    val client = endpointB.get<FlowEchoService>("service")
    val initialServiceNames = endpointA.serviceNames
    val initialClientNames = endpointA.clientNames

    val flowReference = client.createFlow("hello", 3)
    val flow = flowReference.get()
    assertThat(flow.toList()).containsExactly("0 hello", "1 hello", "2 hello")

    // Confirm that no services or clients were leaked.
    assertThat(endpointA.serviceNames).isEqualTo(initialServiceNames)
    assertThat(endpointA.clientNames).isEqualTo(initialClientNames)
  }

  @Test
  fun flowParameterWorks(): Unit = runBlocking(dispatcher) {
    val service = RealFlowEchoService()

    endpointA.set<FlowEchoService>("service", service)
    val client = endpointB.get<FlowEchoService>("service")
    val initialServiceNames = endpointA.serviceNames
    val initialClientNames = endpointA.clientNames

    val sharedFlow = MutableSharedFlow<String?>()
    val flow = sharedFlow.takeWhile { it != null }.filterIsInstance<String>()

    val deferredCount = async {
      client.flowParameter(flow.asFlowReference())
    }

    sharedFlow.emit("a")
    sharedFlow.emit("b")
    sharedFlow.emit("c")
    sharedFlow.emit(null)

    assertThat(deferredCount.await()).isEqualTo(3)

    // Confirm that no services or clients were leaked.
    assertThat(endpointA.serviceNames).isEqualTo(initialServiceNames)
    assertThat(endpointA.clientNames).isEqualTo(initialClientNames)
  }
}
