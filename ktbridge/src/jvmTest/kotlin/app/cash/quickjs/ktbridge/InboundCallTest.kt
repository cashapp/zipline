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
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingDeque
import org.junit.Test

/**
 * Manually call [createJsService] the way generated code does and confirm things work as expected.
 * This is intended to help with iterating on that API without working through the generator.
 */
class InboundCallTest {
  private val requests = LinkedBlockingDeque<String>()
  private val responses = LinkedBlockingDeque<String>()
  private val inboundCallEchoService = object : EchoService {
    override fun echo(request: EchoRequest): EchoResponse {
      requests += request.message
      return EchoResponse(responses.take())
    }
  }

  private val testService = createJsService(
    jsAdapter = EchoJsAdapter,
    service = inboundCallEchoService,
    block = fun (inboundCall: InboundCall<EchoService>): ByteArray {
      return when {
        inboundCall.funName == "echo" -> {
          inboundCall.result(
            inboundCall.service.echo(
              inboundCall.parameter()
            )
          )
        }
        else -> inboundCall.unexpectedFunction()
      }
    }
  ) as InternalBridge

  @Test
  fun inboundCallRequestAndResponse() {
    responses += "this is a curt response"
    val echoService = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        val outboundCall = OutboundCall(EchoJsAdapter, testService, "echo", 1)
        outboundCall.parameter(request)
        return outboundCall.invoke()
      }
    }
    val response = echoService.echo(EchoRequest("this is a happy request"))
    assertThat(response.message).isEqualTo("this is a curt response")
    assertThat(requests.poll()).isEqualTo("this is a happy request")
    assertThat(requests.poll()).isNull()
  }
}
