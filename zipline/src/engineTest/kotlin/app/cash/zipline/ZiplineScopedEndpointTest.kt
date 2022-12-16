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

import app.cash.zipline.internal.bridge.OutboundService
import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.newEndpointPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.runBlocking

internal class ZiplineScopedEndpointTest {
  @Test
  fun eachTakeGetsAFreshScopeIfNoneProvided() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val log = ArrayDeque<String>()

    endpointA.bind<EchoService>("serviceA", RealEchoService(log, "serviceA"))
    endpointA.bind<EchoService>("serviceB", RealEchoService(log, "serviceB"))
    val clientA = endpointB.take<EchoService>("serviceA")
    val scopeA = (clientA as OutboundService).scope

    val clientB = endpointB.take<EchoService>("serviceB")
    val scopeB = (clientB as OutboundService).scope

    assertNotSame(scopeA, scopeB)
    clientA.echo(EchoRequest("hello"))
    clientB.echo(EchoRequest("hello"))
    assertEquals("serviceA request", log.removeFirst())
    assertEquals("serviceB request", log.removeFirst())
    assertNull(log.removeFirstOrNull())

    scopeA.close()
    assertEquals("serviceA closed", log.removeFirst())
    assertNull(log.removeFirstOrNull())

    scopeB.close()
    assertEquals("serviceB closed", log.removeFirst())
    assertNull(log.removeFirstOrNull())
  }

  @Test
  fun takeHonorsCallerProvidedScope() = runBlocking {
    val scope = ZiplineScope()

    val (endpointA, endpointB) = newEndpointPair(this)

    val log = ArrayDeque<String>()

    endpointA.bind<EchoService>("serviceA", RealEchoService(log, "serviceA"))
    endpointA.bind<EchoService>("serviceB", RealEchoService(log, "serviceB"))
    val clientA = endpointB.take<EchoService>("serviceA", scope)
    assertSame(scope, (clientA as OutboundService).scope)

    val clientB = endpointB.take<EchoService>("serviceB", scope)
    assertSame(scope, (clientB as OutboundService).scope)

    clientA.echo(EchoRequest("hello"))
    clientB.echo(EchoRequest("hello"))
    assertEquals("serviceA request", log.removeFirst())
    assertEquals("serviceB request", log.removeFirst())
    assertNull(log.removeFirstOrNull())

    scope.close()
    assertEquals("serviceA closed", log.removeFirst())
    assertEquals("serviceB closed", log.removeFirst())
    assertNull(log.removeFirstOrNull())
  }

  @Test
  fun eachPassedInServiceGetsAFreshScopeIfReceiverIsNotZiplineScoped() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val log = ArrayDeque<String>()
    val receivedServices = ArrayDeque<EchoService>()

    val service = object : EchoServiceChecker {
      override fun check(echoService: EchoService) {
        log += "received service"
        receivedServices += echoService
      }
    }

    endpointA.bind<EchoServiceChecker>("service", service)
    val client = endpointB.take<EchoServiceChecker>("service")

    client.check(RealEchoService(log, "a"))
    assertEquals("received service", log.removeFirst())
    assertNull(log.removeFirstOrNull())
    val receivedA = receivedServices.removeFirst()
    val receivedAScope = (receivedA as OutboundService).scope

    client.check(RealEchoService(log, "b"))
    assertEquals("received service", log.removeFirst())
    assertNull(log.removeFirstOrNull())
    val receivedB = receivedServices.removeFirst()
    val receivedBScope = (receivedB as OutboundService).scope

    assertNotSame(receivedAScope, receivedBScope)

    receivedAScope.close()
    assertEquals("a closed", log.removeFirst())
    assertNull(log.removeFirstOrNull())

    receivedBScope.close()
    assertEquals("b closed", log.removeFirst())
    assertNull(log.removeFirstOrNull())
  }

  @Test
  fun eachServiceParameterHonorsZiplineScopedReceiver() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val serviceScope = ZiplineScope()
    val log = ArrayDeque<String>()
    val receivedServices = ArrayDeque<EchoService>()

    val service = object : EchoServiceChecker, ZiplineScoped {
      override val scope = serviceScope

      override fun check(echoService: EchoService) {
        log += "received service"
        receivedServices += echoService
      }
    }

    endpointA.bind<EchoServiceChecker>("service", service)
    val client = endpointB.take<EchoServiceChecker>("service")

    client.check(RealEchoService(log, "a"))
    assertEquals("received service", log.removeFirst())
    assertNull(log.removeFirstOrNull())
    val receivedA = receivedServices.removeFirst()
    assertSame(serviceScope, (receivedA as OutboundService).scope)

    client.check(RealEchoService(log, "b"))
    assertEquals("received service", log.removeFirst())
    assertNull(log.removeFirstOrNull())
    val receivedB = receivedServices.removeFirst()
    assertSame(serviceScope, (receivedB as OutboundService).scope)

    serviceScope.close()
    assertEquals("a closed", log.removeFirst())
    assertEquals("b closed", log.removeFirst())
    // Note that the EchoServiceChecker isn't closed here. In practice, it'll do that itself by
    // overriding close() and calling scope.close().
    assertNull(log.removeFirstOrNull())
  }

  @Test
  fun eachServiceResultSharesSubjectsScope() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val scope = ZiplineScope()
    val log = ArrayDeque<String>()

    val service = object : EchoServiceMaker {
      override fun newEchoService(name: String): EchoService {
        log += "making $name"
        return RealEchoService(log, name)
      }

      override fun close() {
        log += "maker closed"
      }
    }

    endpointA.bind<EchoServiceMaker>("service", service)
    val client = endpointB.take<EchoServiceMaker>("service", scope)

    val serviceA = client.newEchoService("a")
    assertSame(scope, (serviceA as OutboundService).scope)
    assertEquals("making a", log.removeFirst())
    assertNull(log.removeFirstOrNull())

    val serviceB = client.newEchoService("b")
    assertSame(scope, (serviceB as OutboundService).scope)
    assertEquals("making b", log.removeFirst())
    assertNull(log.removeFirstOrNull())

    scope.close()
    assertEquals("maker closed", log.removeFirst())
    assertEquals("a closed", log.removeFirst())
    assertEquals("b closed", log.removeFirst())
    assertNull(log.removeFirstOrNull())
  }

  @Test
  fun eachServiceResultSharesSubjectsScopeWhenSuspending() = runBlocking {
    val (endpointA, endpointB) = newEndpointPair(this)

    val scope = ZiplineScope()
    val log = ArrayDeque<String>()

    val service = object : SuspendingEchoServiceMaker {
      override suspend fun newEchoService(name: String): EchoService {
        log += "making $name"
        return RealEchoService(log, name)
      }

      override fun close() {
        log += "maker closed"
      }
    }

    endpointA.bind<SuspendingEchoServiceMaker>("service", service)
    val client = endpointB.take<SuspendingEchoServiceMaker>("service", scope)

    val serviceA = client.newEchoService("a")
    assertSame(scope, (serviceA as OutboundService).scope)
    assertEquals("making a", log.removeFirst())
    assertNull(log.removeFirstOrNull())

    val serviceB = client.newEchoService("b")
    assertSame(scope, (serviceB as OutboundService).scope)
    assertEquals("making b", log.removeFirst())
    assertNull(log.removeFirstOrNull())

    scope.close()
    assertEquals("maker closed", log.removeFirst())
    assertEquals("a closed", log.removeFirst())
    assertEquals("b closed", log.removeFirst())
    assertNull(log.removeFirstOrNull())
  }

  class RealEchoService(
    val log: ArrayDeque<String>,
    val name: String,
  ) : EchoService {
    override fun echo(request: EchoRequest): EchoResponse {
      log += "$name request"
      return EchoResponse("hello")
    }

    override fun close() {
      log += "$name closed"
    }
  }

  interface EchoServiceChecker : ZiplineService {
    fun check(echoService: EchoService)
  }

  interface EchoServiceMaker : ZiplineService {
    fun newEchoService(name: String): EchoService
  }

  interface SuspendingEchoServiceMaker : ZiplineService {
    suspend fun newEchoService(name: String): EchoService
  }
}
