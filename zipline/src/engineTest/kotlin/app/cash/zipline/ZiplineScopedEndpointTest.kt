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
import app.cash.zipline.testing.SuspendingEchoService
import app.cash.zipline.testing.newEndpointPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

internal class ZiplineScopedEndpointTest {
  @Test
  fun eachTakeGetsAFreshScopeIfNoneProvided() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    val log = ArrayDeque<String>()

    endpointA.bind<EchoService>("serviceA", RealEchoService(log, "serviceA"))
    endpointA.bind<EchoService>("serviceB", RealEchoService(log, "serviceB"))
    val clientA = endpointB.take<EchoService>("serviceA")
    val scopeA = (clientA as OutboundService).callHandler.scope

    val clientB = endpointB.take<EchoService>("serviceB")
    val scopeB = (clientB as OutboundService).callHandler.scope

    assertNotSame(scopeA, scopeB)
    clientA.echo(EchoRequest("hello"))
    clientB.echo(EchoRequest("hello"))
    log.takeAndAssertContents(
      "serviceA request",
      "serviceB request",
    )

    scopeA.close()
    log.takeAndAssertContents(
      "serviceA closed",
    )

    scopeB.close()
    log.takeAndAssertContents(
      "serviceB closed",
    )
  }

  @Test
  fun takeHonorsCallerProvidedScope() = runBlocking(Unconfined) {
    val scope = ZiplineScope()

    val (endpointA, endpointB) = newEndpointPair(this)

    val log = ArrayDeque<String>()

    endpointA.bind<EchoService>("serviceA", RealEchoService(log, "serviceA"))
    endpointA.bind<EchoService>("serviceB", RealEchoService(log, "serviceB"))
    val clientA = endpointB.take<EchoService>("serviceA", scope)
    assertSame(scope, (clientA as OutboundService).callHandler.scope)

    val clientB = endpointB.take<EchoService>("serviceB", scope)
    assertSame(scope, (clientB as OutboundService).callHandler.scope)

    clientA.echo(EchoRequest("hello"))
    clientB.echo(EchoRequest("hello"))
    log.takeAndAssertContents(
      "serviceA request",
      "serviceB request",
    )

    scope.close()
    log.takeAndAssertContents(
      "serviceA closed",
      "serviceB closed",
    )
  }

  @Test
  fun eachPassedInServiceGetsAFreshScopeIfReceiverIsNotZiplineScoped() = runBlocking(Unconfined) {
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
    log.takeAndAssertContents(
      "received service",
    )
    val receivedA = receivedServices.removeFirst()
    val receivedAScope = (receivedA as OutboundService).callHandler.scope

    client.check(RealEchoService(log, "b"))
    log.takeAndAssertContents(
      "received service",
    )
    val receivedB = receivedServices.removeFirst()
    val receivedBScope = (receivedB as OutboundService).callHandler.scope

    assertNotSame(receivedAScope, receivedBScope)

    receivedAScope.close()
    log.takeAndAssertContents(
      "a closed",
    )

    receivedBScope.close()
    log.takeAndAssertContents(
      "b closed",
    )
  }

  @Test
  fun eachServiceParameterHonorsZiplineScopedReceiver() = runBlocking(Unconfined) {
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
    log.takeAndAssertContents(
      "received service",
    )
    val receivedA = receivedServices.removeFirst()
    assertSame(serviceScope, (receivedA as OutboundService).callHandler.scope)

    client.check(RealEchoService(log, "b"))
    log.takeAndAssertContents(
      "received service",
    )
    val receivedB = receivedServices.removeFirst()
    assertSame(serviceScope, (receivedB as OutboundService).callHandler.scope)

    serviceScope.close()
    log.takeAndAssertContents(
      "a closed",
      "b closed",
      // Note that the EchoServiceChecker isn't closed here. In practice, it'll do that itself by
      // overriding close() and calling scope.close().
    )
  }

  @Test
  fun eachServiceResultSharesSubjectsScope() = runBlocking(Unconfined) {
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
    assertSame(scope, (serviceA as OutboundService).callHandler.scope)
    log.takeAndAssertContents(
      "making a",
    )

    val serviceB = client.newEchoService("b")
    assertSame(scope, (serviceB as OutboundService).callHandler.scope)
    log.takeAndAssertContents(
      "making b",
    )

    scope.close()
    log.takeAndAssertContents(
      "maker closed",
      "a closed",
      "b closed",
    )
  }

  @Test
  fun eachServiceResultSharesSubjectsScopeWhenSuspending() = runBlocking(Unconfined) {
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
    assertSame(scope, (serviceA as OutboundService).callHandler.scope)
    log.takeAndAssertContents(
      "making a",
    )

    val serviceB = client.newEchoService("b")
    assertSame(scope, (serviceB as OutboundService).callHandler.scope)
    log.takeAndAssertContents(
      "making b",
    )

    scope.close()
    log.takeAndAssertContents(
      "maker closed",
      "a closed",
      "b closed",
    )
  }

  /**
   * `SuspendCallback` is a special service that closes itself. Confirm that closing the scope
   * while this is active doesn't crash.
   */
  @Test
  fun suspendCallbacksNotScoped() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    val serviceScope = ZiplineScope()

    val service = object : SuspendingEchoService, ZiplineScoped {
      override val scope = serviceScope

      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        scope.close()
        return EchoResponse("response")
      }
    }

    endpointA.bind<SuspendingEchoService>("service", service)
    val client = endpointB.take<SuspendingEchoService>("service")

    val response = client.suspendingEcho(EchoRequest("request"))
    assertEquals("response", response.message)
  }

  /**
   * `CancelCallback` is another special service that closes itself. Confirm that closing the scope
   * while this is active doesn't crash.
   */
  @Test
  fun cancelCallbacksNotScoped() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    val scope = ZiplineScope()
    val channel = Channel<String>()

    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        channel.send("suspend call received")
        assertEquals("scope canceled", channel.receive())
        return EchoResponse("response")
      }
    }

    endpointA.bind<SuspendingEchoService>("service", service)
    val client = endpointB.take<SuspendingEchoService>("service", scope)

    val deferred = async {
      client.suspendingEcho(EchoRequest("request"))
    }
    assertEquals("suspend call received", channel.receive())
    scope.close()
    channel.send("scope canceled")
    assertEquals("response", deferred.await().message)
  }

  @Test
  fun withScopeForShorterLifetime() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    val scope1 = ZiplineScope()
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
    val clientWithScope1 = endpointB.take<EchoServiceMaker>("service", scope1)

    val serviceA = clientWithScope1.newEchoService("a")
    assertSame(scope1, (serviceA as OutboundService).callHandler.scope)
    log.takeAndAssertContents(
      "making a",
    )

    val scope2 = ZiplineScope()
    val clientWithScope2 = clientWithScope1.withScope(scope2)
    val serviceB = clientWithScope2.newEchoService("b")
    assertSame(scope2, (serviceB as OutboundService).callHandler.scope)
    log.takeAndAssertContents(
      "making b",
    )

    scope2.close()
    log.takeAndAssertContents(
      "b closed",
    )

    scope1.close()
    log.takeAndAssertContents(
      "maker closed",
      "a closed",
    )
  }

  /** Confirm there's no nesting relationship between scopes. */
  @Test
  fun withScopeForLongerLifetime() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    val scope1 = ZiplineScope()
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
    val clientWithScope1 = endpointB.take<EchoServiceMaker>("service", scope1)

    val serviceA = clientWithScope1.newEchoService("a")
    assertSame(scope1, (serviceA as OutboundService).callHandler.scope)
    log.takeAndAssertContents(
      "making a",
    )

    val scope2 = ZiplineScope()
    val clientWithScope2 = clientWithScope1.withScope(scope2)
    val serviceB = clientWithScope2.newEchoService("b")
    assertSame(scope2, (serviceB as OutboundService).callHandler.scope)
    log.takeAndAssertContents(
      "making b",
    )

    scope1.close()
    log.takeAndAssertContents(
      "maker closed",
      "a closed",
    )

    scope2.close()
    log.takeAndAssertContents(
      "b closed",
    )
  }

  @Test
  fun closingResultOfWithScopeClosesOriginal() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    val scope1 = ZiplineScope()
    val scope2 = ZiplineScope()

    val log = ArrayDeque<String>()

    val service = object : EchoServiceMaker {
      override fun newEchoService(name: String) = error("unexpected call")

      override fun close() {
        log += "maker closed"
      }
    }

    endpointA.bind<EchoServiceMaker>("service", service)
    val clientWithScope1 = endpointB.take<EchoServiceMaker>("service", scope1)

    val clientWithScope2 = clientWithScope1.withScope(scope2)

    clientWithScope2.close()
    log.takeAndAssertContents(
      "maker closed",
    )

    clientWithScope1.close()
    log.takeAndAssertContents(
      // Nothing to do: close() is idempotent.
    )
  }

  private fun ArrayDeque<String>.takeAndAssertContents(vararg expected: String) {
    assertEquals(expected.toList(), toList())
    clear()
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
