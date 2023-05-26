/*
 * Copyright (C) 2023 Cash App
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

import app.cash.zipline.internal.EndpointService
import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.newEndpointPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.runBlocking

internal class ZiplineServiceTypeTest {
  @Test
  fun serviceTypesCrossBridge() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        error("unexpected call")
      }
    }

    endpointA.bind<EndpointService>("endpointService", endpointA)
    val endpointService = endpointB.take<EndpointService>("endpointService")

    endpointA.bind<EchoService>("helloService", service)

    assertEquals(
      setOf("endpointService", "helloService"),
      endpointService.serviceNames,
    )

    val type = endpointService.serviceType("helloService")
    assertEquals("app.cash.zipline.testing.EchoService", type.name)

    val functions = type.functions.sortedBy { it.name }
    assertEquals(2, functions.size)

    assertEquals("fun close(): kotlin.Unit", functions[0].name)
    assertFalse(functions[0].isSuspending)
    assertTrue(functions[0].isClose)

    assertEquals(
      "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse",
      functions[1].name,
    )
    assertFalse(functions[1].isSuspending)
    assertFalse(functions[1].isClose)
  }
}
