/*
 * Copyright (C) 2022 Block, Inc.
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
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.newEndpointPair
import app.cash.zipline.testing.signatureHash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

/**
 * White box tests that confirm implementation details of call encoding work as expected on input
 * that can't be produced through end-to-end tests.
 */
internal class ManualCallEncodingTest {

  @Test
  fun happyPath() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    val requests = ArrayDeque<String>()
    val service = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        requests.addFirst("received '${request.message}'")
        return EchoResponse("received '${request.message}'")
      }
    }
    endpointA.bind<EchoService>("helloService", service)

    val responseJson = endpointB.outboundChannel.call(
      """
      |{
      |  "service": "helloService",
      |  "function": "${"fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse".signatureHash()}",
      |  "args": [
      |    {
      |      "message": "hello"
      |    }
      |  ]
      |}
      """.trimMargin(),
    )

    assertEquals(
      """
      |{
      |  "success": {
      |    "message": "received 'hello'"
      |  }
      |}
      """.trimMargin(),
      prettyPrint(responseJson),
    )

    assertEquals("received 'hello'", requests.removeFirst())
  }

  @Test
  fun absentValuesAreDefaulted() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    val requests = ArrayDeque<String>()
    val service = object : SendService {
      override fun send(message: MessageWithDefaults) {
        requests.addFirst("received $message")
      }
    }
    endpointA.bind<SendService>("service", service)

    val responseJson = endpointB.outboundChannel.call(
      """
      |{
      |  "service": "service",
      |  "function": "${"fun send(app.cash.zipline.ManualCallEncodingTest.MessageWithDefaults): kotlin.Unit".signatureHash()}",
      |  "args": [
      |    {}
      |  ]
      |}
      """.trimMargin(),
    )

    assertEquals(
      """
      |{
      |  "success": {}
      |}
      """.trimMargin(),
      prettyPrint(responseJson),
    )

    assertEquals(
      "received MessageWithDefaults(name=null, color=blue)",
      requests.removeFirst(),
    )
  }

  @Test
  fun defaultValuesArePresent() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : ReceiveService {
      override fun receive(): MessageWithDefaults {
        return MessageWithDefaults()
      }
    }
    endpointA.bind<ReceiveService>("service", service)

    val responseJson = endpointB.outboundChannel.call(
      """
      |{
      |  "service": "service",
      |  "function": "${"fun receive(): app.cash.zipline.ManualCallEncodingTest.MessageWithDefaults".signatureHash()}",
      |  "args": []
      |}
      """.trimMargin(),
    )

    assertEquals(
      """
      |{
      |  "success": {
      |    "name": null,
      |    "color": "blue"
      |  }
      |}
      """.trimMargin(),
      prettyPrint(responseJson),
    )
  }

  @Test
  fun mapsAreStructured() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    val requests = ArrayDeque<String>()
    val service = object : MapService {
      override fun flip(map: Map<List<String>, List<Int>>): Map<List<Int>, List<String>> {
        requests.addFirst("received $map")
        return map.entries.associate { (key, value) -> value to key }
      }
    }
    endpointA.bind<MapService>("service", service)

    val responseJson = endpointB.outboundChannel.call(
      """
      |{
      |  "service": "service",
      |  "function": "${"fun flip(kotlin.collections.Map<kotlin.collections.List<kotlin.String>,kotlin.collections.List<kotlin.Int>>): kotlin.collections.Map<kotlin.collections.List<kotlin.Int>,kotlin.collections.List<kotlin.String>>".signatureHash()}",
      |  "args": [
      |    [
      |      [
      |        "a"
      |      ],
      |      [
      |        1
      |      ],
      |      [
      |        "b"
      |      ],
      |      [
      |        5
      |      ]
      |    ]
      |  ]
      |}
      """.trimMargin(),
    )

    assertEquals(
      """
      |{
      |  "success": [
      |    [
      |      1
      |    ],
      |    [
      |      "a"
      |    ],
      |    [
      |      5
      |    ],
      |    [
      |      "b"
      |    ]
      |  ]
      |}
      """.trimMargin(),
      prettyPrint(responseJson),
    )

    assertEquals("received {[a]=[1], [b]=[5]}", requests.removeFirst())
  }

  @Serializable
  data class MessageWithDefaults(
    val name: String? = null,
    val color: String = "blue",
  )

  interface SendService : ZiplineService {
    fun send(message: MessageWithDefaults)
  }

  interface ReceiveService : ZiplineService {
    fun receive(): MessageWithDefaults
  }

  interface MapService : ZiplineService {
    fun flip(map: Map<List<String>, List<Int>>): Map<List<Int>, List<String>>
  }
}
