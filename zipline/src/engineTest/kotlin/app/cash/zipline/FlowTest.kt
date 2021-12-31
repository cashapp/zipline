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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

internal class FlowTest {
  interface FlowEchoService : ZiplineService {
    fun createFlow(message: String, count: Int): FlowReference<String>
    suspend fun flowParameter(flowReference: FlowReference<String>): Int
  }

  class RealFlowEchoService : FlowEchoService {
    override fun createFlow(message: String, count: Int): FlowReference<String> {
      val flow = flow {
        for (i in 0 until count) {
          delay(10) // Ensure we can send async through the reference.
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
  fun flowReturnValueWorks() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)
    val service = RealFlowEchoService()

    endpointA.set<FlowEchoService>("service", service)
    val client = endpointB.get<FlowEchoService>("service")
    val initialServiceNames = endpointA.serviceNames
    val initialClientNames = endpointA.clientNames

    val flowReference = client.createFlow("hello", 3)
    val flow = flowReference.get()
    assertEquals(listOf("0 hello", "1 hello", "2 hello"), flow.toList())

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
      client.flowParameter(flow.asFlowReference())
    }

    assertEquals(3, deferredCount.await())

    // Confirm that no services or clients were leaked.
    assertEquals(initialServiceNames, endpointA.serviceNames)
    assertEquals(initialClientNames, endpointA.clientNames)
  }

  @Test
  fun flowCanBeUsedWithoutPassingThroughZipline() = runBlocking {
    val service = RealFlowEchoService()
    val flowReference = service.createFlow("hello", 3)
    val flow = flowReference.get()
    assertEquals(listOf("0 hello", "1 hello", "2 hello"), flow.toList())
  }
}
