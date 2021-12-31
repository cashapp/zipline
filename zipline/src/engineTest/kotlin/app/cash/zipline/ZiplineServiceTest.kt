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

import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoZiplineService
import app.cash.zipline.testing.newEndpointPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

internal class ZiplineServiceTest {
  @Test
  fun requestAndResponse() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val events = ArrayDeque<String>()
    val responses = ArrayDeque<String>()
    val service = object : EchoZiplineService {
      override fun echo(request: EchoRequest): EchoResponse {
        events += "request message='${request.message}'"
        return EchoResponse(responses.removeFirst())
      }

      override fun close() {
        events += "close"
      }
    }

    endpointA.set<EchoZiplineService>("helloService", service)
    assertEquals(setOf("helloService"), endpointA.serviceNames)

    val client = endpointB.get<EchoZiplineService>("helloService")
    assertEquals(setOf("helloService"), endpointB.clientNames)

    responses += "this is a curt response"
    val response = client.echo(EchoRequest("this is a happy request"))
    assertEquals("this is a curt response", response.message)
    assertEquals("request message='this is a happy request'", events.removeFirst())

    client.close()
    assertEquals("close", events.removeFirst())
    assertEquals(setOf(), endpointB.clientNames)
    assertEquals(setOf(), endpointA.serviceNames)

    assertNull(events.removeFirstOrNull())
  }
}
