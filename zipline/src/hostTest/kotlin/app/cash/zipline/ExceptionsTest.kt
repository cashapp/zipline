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
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.loadTestingJs
import assertk.assertThat
import assertk.assertions.matches
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher

class ExceptionsTest {
  @OptIn(ExperimentalCoroutinesApi::class)
  private val dispatcher = UnconfinedTestDispatcher()
  private val zipline = Zipline.create(dispatcher)

  @BeforeTest fun setUp(): Unit = runBlocking(dispatcher) {
    zipline.loadTestingJs()
  }

  @AfterTest fun tearDown() = runBlocking(dispatcher) {
    zipline.close()
  }

  @Test fun hostCallGuestServiceThatThrows(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareThrowingJsBridges()")

    val service = zipline.take<EchoService>("throwingService")

    val e = assertFailsWith<ZiplineException> {
      service.echo(EchoRequest("Jake"))
    }
    assertThat(e.stackTraceToString()).matches(
      Regex(
        """(?s).*IllegalStateException: boom!""" +
        """.*at goBoom1""" +
        """.*at goBoom2""" +
        """.*at goBoom3""" +
        """.*""",
      ),
    )
  }

  @Test fun guestCallsHostServiceThatThrows(): Unit = runBlocking(dispatcher) {
    zipline.bind<EchoService>("throwingService", HostThrowingEchoService())

    val e = assertFailsWith<QuickJsException> {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callThrowingService('homie')")
    }
    assertThat(e.stackTraceToString()).matches(
      Regex(
        """(?s).*(java\.lang|kotlin)\.IllegalStateException: boom!""" +
        """.*HostThrowingEchoService\.goBoom1""" +
        """.*HostThrowingEchoService\.goBoom2""" +
        """.*HostThrowingEchoService\.goBoom3""" +
        """.*HostThrowingEchoService\.echo""" +
        """.*""",
      ),
    )
  }

  @Test
  fun hostCallsGuestCallsHostServiceThatThrows(): Unit = runBlocking(dispatcher) {
    zipline.bind<EchoService>("throwingService", HostThrowingEchoService())
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareDelegatingService()")

    val service = zipline.take<EchoService>("delegatingService")

    val e = assertFailsWith<ZiplineException> {
      service.echo(EchoRequest("Jake"))
    }
    assertThat(e.stackTraceToString()).matches(
      Regex(
        """(?s).*IllegalStateException: boom!""" +
        """.*at .*HostThrowingEchoService\.goBoom1""" +
        """.*at .*HostThrowingEchoService\.goBoom2""" +
        """.*at .*HostThrowingEchoService\.goBoom3""" +
        """.*at .*HostThrowingEchoService\.echo""" +
        """.*at delegate1""" +
        """.*at delegate2""" +
        """.*at delegate3""" +
        """.*""",
      ),
    )
  }

  private class HostThrowingEchoService : EchoService {
    override fun echo(request: EchoRequest): EchoResponse {
      goBoom3()
    }
    private fun goBoom3(): Nothing {
      goBoom2()
    }
    private fun goBoom2(): Nothing {
      goBoom1()
    }
    private fun goBoom1(): Nothing {
      throw IllegalStateException("boom!")
    }
  }
}
