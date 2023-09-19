/*
 * Copyright (C) 2023 Square, Inc.
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
import assertk.assertions.isEqualTo
import java.util.concurrent.Executors
import kotlin.test.assertFailsWith
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class ZiplineStackSizeTest {
  /** An executor service that uses 8 MiB thread stacks. */
  private val executorService = Executors.newSingleThreadExecutor { runnable ->
     Thread(null, runnable, "Treehouse", 8 * 1024 * 1024)
  }
  private val dispatcher = executorService.asCoroutineDispatcher()
  private lateinit var zipline: Zipline

  @Before
  fun setUp() = runBlocking(dispatcher) {
    zipline = Zipline.create(dispatcher)
    zipline.loadTestingJs()
  }

  @After
  fun tearDown() = runBlocking(dispatcher) {
    zipline.close()
  }

  @Test
  fun deepRecursionDoesntCrash() {
    runBlocking(dispatcher) {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareRecursingService()")

      val recurseCount = 500
      val service = zipline.take<EchoService>("recursingService")
      val echoResponse = service.echo(EchoRequest("$recurseCount"))
      assertThat(echoResponse).isEqualTo(EchoResponse("recursed $recurseCount times!"))
    }
  }

  @Test
  @Ignore("https://github.com/cashapp/zipline/issues/1130")
  fun veryDeepRecursionFailsGracefully() {
    runBlocking(dispatcher) {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareRecursingService()")

      val recurseCount = 2000
      val service = zipline.take<EchoService>("recursingService")
      val e = assertFailsWith<QuickJsException> {
        service.echo(EchoRequest("$recurseCount"))
      }
      assertThat(e.message).isEqualTo("stack overflow")
    }
  }
}
