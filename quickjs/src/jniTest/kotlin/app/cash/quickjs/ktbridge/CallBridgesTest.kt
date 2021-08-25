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
package app.cash.quickjs.ktbridge

import app.cash.quickjs.ktbridge.testing.EchoJsAdapter
import app.cash.quickjs.ktbridge.testing.EchoRequest
import app.cash.quickjs.ktbridge.testing.EchoResponse
import app.cash.quickjs.ktbridge.testing.EchoService
import app.cash.quickjs.ktbridge.testing.KtBridgePair
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class CallBridgesTest {
  private val bridges = KtBridgePair()

  @Test
  fun inboundCallRequestAndResponse() {
    val requests = LinkedBlockingDeque<String>()
    val responses = LinkedBlockingDeque<String>()
    val service = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        requests += request.message
        return EchoResponse(responses.take())
      }
    }

    bridges.a.set<EchoService>("helloService", EchoJsAdapter, service)
    val client = bridges.b.get<EchoService>("helloService", EchoJsAdapter)

    responses += "this is a curt response"
    val response = client.echo(EchoRequest("this is a happy request"))
    assertEquals("this is a curt response", response.message)
    assertEquals("this is a happy request", requests.poll())
    assertNull(responses.poll())
    assertNull(requests.poll())
  }

  interface NullableEchoService {
    fun echo(request: EchoRequest?): EchoResponse?
  }

  @Test
  fun nullRequest() {
    val service = object : NullableEchoService {
      override fun echo(request: EchoRequest?): EchoResponse? {
        assertNull(request)
        return EchoResponse("received null")
      }
    }

    bridges.a.set<NullableEchoService>("helloService", EchoJsAdapter, service)
    val client = bridges.b.get<NullableEchoService>("helloService", EchoJsAdapter)

    val response = client.echo(null)
    assertThat(response?.message).isEqualTo("received null")
  }

  @Test
  fun nullResponse() {
    val service = object : NullableEchoService {
      override fun echo(request: EchoRequest?): EchoResponse? {
        assertEquals("send me null please?", request?.message)
        return null
      }
    }

    bridges.a.set<NullableEchoService>("helloService", EchoJsAdapter, service)
    val client = bridges.b.get<NullableEchoService>("helloService", EchoJsAdapter)

    val response = client.echo(EchoRequest("send me null please?"))
    assertNull(response)
  }
}
