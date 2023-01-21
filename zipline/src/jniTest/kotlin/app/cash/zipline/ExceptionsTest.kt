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
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ExceptionsTest {
  @Rule @JvmField val ziplineTestRule = ZiplineTestRule()
  private val dispatcher = ziplineTestRule.dispatcher
  private val zipline = Zipline.create(dispatcher)

  @Before fun setUp(): Unit = runBlocking(dispatcher) {
    zipline.loadTestingJs()
  }

  @After fun tearDown() = runBlocking(dispatcher) {
    zipline.close()
  }

  @Test fun jvmCallJsServiceThatThrows(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareThrowingJsBridges()")

    val service = zipline.take<EchoService>("throwingService")

    assertThat(assertFailsWith<ZiplineException> {
      service.echo(EchoRequest("Jake"))
    }.stackTraceToString()).apply {
      matches(
        """(?s).*IllegalStateException: boom!""" +
          """.*at goBoom1""" +
          """.*at goBoom2""" +
          """.*at goBoom3""" +
          """.*"""
      )
    }
  }

  @Test fun jsCallJvmServiceThatThrows(): Unit = runBlocking(dispatcher) {
    zipline.bind<EchoService>("throwingService", JvmThrowingEchoService())

    assertThat(assertFailsWith<QuickJsException> {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callThrowingService('homie')")
    }.stackTraceToString()).apply {
      matches(
        """(?s).*java\.lang\.IllegalStateException: boom!""" +
          """.*JvmThrowingEchoService\.goBoom1""" +
          """.*JvmThrowingEchoService\.goBoom2""" +
          """.*JvmThrowingEchoService\.goBoom3""" +
          """.*JvmThrowingEchoService\.echo""" +
          """.*"""
      )
    }
  }

  @Test fun jvmCallJsCallsJvmServiceThatThrows(): Unit = runBlocking(dispatcher) {
    zipline.bind<EchoService>("throwingService", JvmThrowingEchoService())
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareDelegatingService()")

    val service = zipline.take<EchoService>("delegatingService")

    assertThat(assertFailsWith<ZiplineException> {
      service.echo(EchoRequest("Jake"))
    }.stackTraceToString()).apply {
      matches(
        """(?s).*IllegalStateException: boom!""" +
          """.*at .*JvmThrowingEchoService\.goBoom1""" +
          """.*at .*JvmThrowingEchoService\.goBoom2""" +
          """.*at .*JvmThrowingEchoService\.goBoom3""" +
          """.*at .*JvmThrowingEchoService\.echo""" +
          """.*at delegate1""" +
          """.*at delegate2""" +
          """.*at delegate3""" +
          """.*"""
      )
    }
  }

  private class JvmThrowingEchoService : EchoService {
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
