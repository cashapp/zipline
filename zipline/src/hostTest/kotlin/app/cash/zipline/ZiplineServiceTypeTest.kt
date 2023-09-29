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

import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.GenericEchoService
import app.cash.zipline.testing.kotlinBuiltInSerializersModule
import app.cash.zipline.testing.newEndpointPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.runBlocking

internal class ZiplineServiceTypeTest {
  @Test
  fun targetTypeHappyPath() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        error("unexpected call")
      }
    }

    endpointA.bind<EchoService>("helloService", service)
    val client = endpointB.take<EchoService>("helloService")

    val type = client.targetType!!
    assertEquals("app.cash.zipline.testing.EchoService", type.name)

    assertEquals(2, type.functions.size)

    val close = type.functions[0]
    assertEquals("fun close(): kotlin.Unit", close.signature)
    assertEquals(
      // close.name.signatureHash().
      "moYx+T3e",
      close.id,
    )
    assertFalse(close.isSuspending)
    assertTrue(close.isClose)

    val echo = type.functions[1]
    assertEquals(
      "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse",
      echo.signature,
    )
    assertEquals(
      // echo.name.signatureHash().
      "tksid3cr",
      echo.id,
    )
    assertFalse(echo.isSuspending)
    assertFalse(echo.isClose)
  }

  @Test
  fun targetTypeNotAnOutboundService() = runBlocking(Unconfined) {
    val service = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        error("unexpected call")
      }
    }

    assertNull(service.targetType)
  }

  @Test
  fun targetTypeWithGenerics() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this, kotlinBuiltInSerializersModule)

    val service = object : GenericEchoService<String> {
      override fun genericEcho(request: String): List<String> {
        error("unexpected call")
      }
    }

    endpointA.bind<GenericEchoService<String>>("helloService", service)
    val client = endpointB.take<GenericEchoService<String>>("helloService")

    val type = client.targetType!!
    assertEquals("app.cash.zipline.testing.GenericEchoService<kotlin.String>", type.name)

    assertEquals(2, type.functions.size)
    assertEquals("fun close(): kotlin.Unit", type.functions[0].signature)
    assertEquals("fun genericEcho(T): kotlin.collections.List<T>", type.functions[1].signature)
  }

  @Test
  fun targetTypeNotKnown(): Unit = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    // Don't bind the service yet. This just returns null.
    val client = endpointB.take<EchoService>("helloService")
    assertNull(client.targetType)

    // When we bind it later, things begin to work.
    val service = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        error("unexpected call")
      }
    }
    endpointA.bind<EchoService>("helloService", service)
    assertNotNull(
      "app.cash.zipline.testing.EchoService",
      client.targetType?.name,
    )
  }

  /**
   * Zipline doesn't actually enforce that the types on either end of a binding are identical. This
   * is essential for forwards-compatibility. This test does an extreme version of that, where the
   * types don't share anything.
   */
  @Test
  fun sourceTypeAndTargetTypeAreDifferent() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : ReverseEchoService {
      override fun reverseEcho(response: String): String {
        error("unexpected call")
      }
    }

    endpointA.bind<ReverseEchoService>("helloService", service)
    val client = endpointB.take<EchoService>("helloService")

    val type = client.targetType!!
    assertEquals("app.cash.zipline.ZiplineServiceTypeTest.ReverseEchoService", type.name)

    assertEquals(2, type.functions.size)

    assertEquals(
      "fun close(): kotlin.Unit",
      type.functions[0].signature,
    )
    assertEquals(
      "fun reverseEcho(kotlin.String): kotlin.String",
      type.functions[1].signature,
    )
  }

  interface ReverseEchoService : ZiplineService {
    fun reverseEcho(response: String): String
  }
}
