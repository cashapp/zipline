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
package app.cash.quickjs

import app.cash.quickjs.testing.EchoRequest
import app.cash.quickjs.testing.EchoResponse
import app.cash.quickjs.testing.jsSuspendingEchoService
import app.cash.quickjs.testing.prepareSuspendingJvmBridges
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KtBridgeSuspendingTest {
  private val dispatcher = TestCoroutineDispatcher()
  private val zipline = Zipline.create(dispatcher)

  @Before
  fun setUp(): Unit = runBlocking(dispatcher) {
    zipline.loadTestingJs()
  }

  @After fun tearDown(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.close()
  }

  @Test fun jvmCallJsService(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.quickjs.testing.prepareSuspendingJsBridges()")

    assertThat(zipline.jsSuspendingEchoService.suspendingEcho(EchoRequest("Jake")))
      .isEqualTo(EchoResponse("hello from suspending JavaScript, Jake"))
  }

  @Test fun jsCallJvmService(): Unit = runBlocking(dispatcher) {
    prepareSuspendingJvmBridges(zipline)

    zipline.quickJs.evaluate("testing.app.cash.quickjs.testing.callSuspendingEchoService('Eric')")
  }
}
