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
import org.junit.Before
import org.junit.Test

internal class CallBridgesTest {
  private val requests = LinkedBlockingDeque<String>()
  private val responses = LinkedBlockingDeque<String>()

  private val bridges = KtBridgePair()

  private val echoService = object : EchoService {
    override fun echo(request: EchoRequest): EchoResponse {
      requests += request.message
      return EchoResponse(responses.take())
    }
  }

  private lateinit var echoClient: EchoService

  @Before
  fun setUp() {
    bridges.a.set<EchoService>("helloService", EchoJsAdapter, echoService)
    echoClient = bridges.b.get("helloService", EchoJsAdapter)
  }

  @Test
  fun `inbound call request and response`() {
    responses += "this is a curt response"
    val response = echoClient.echo(EchoRequest("this is a happy request"))
    assertThat(response.message).isEqualTo("this is a curt response")
    assertThat(requests.poll()).isEqualTo("this is a happy request")
    assertThat(requests.poll()).isNull()
  }
}
