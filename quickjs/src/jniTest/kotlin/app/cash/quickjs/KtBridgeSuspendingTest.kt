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
import app.cash.quickjs.testing.Logger
import app.cash.quickjs.testing.RecordingLogger
import app.cash.quickjs.testing.jsSuspendingEchoService
import app.cash.quickjs.testing.prepareSuspendingJvmBridges
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Need to coordinate mutual exclusion")
class KtBridgeSuspendingTest {
  private val quickjs = QuickJs.create()

  @Before
  fun setUp() {
    quickjs.loadTestingJs()
    quickjs.set("console", Console::class.java, object : Console {
      override fun error(message: String) {
        println(message)
      }

      override fun log(message: String) {
        println(message)
      }
    })
  }

  @After fun tearDown() {
    quickjs.close()
  }

  interface Console {
    fun error(message: String)
    fun log(message: String)
  }

  @Test fun jvmCallJsService() {
    quickjs.evaluate("testing.app.cash.quickjs.testing.prepareSuspendingJsBridges()")

    val ktBridge = quickjs.getKtBridge()

    runBlocking {
      assertThat(ktBridge.jsSuspendingEchoService.suspendingEcho(EchoRequest("Jake")))
        .isEqualTo(EchoResponse("hello from suspending JavaScript, Jake"))
    }
  }

  @Test fun jsCallJvmService() {
    val ktBridge = quickjs.getKtBridge()
    val recordingLogger = RecordingLogger()
    ktBridge.set<Logger>("logger", Logger.Adapter, recordingLogger)
    prepareSuspendingJvmBridges(ktBridge)

    quickjs.evaluate("testing.app.cash.quickjs.testing.callSuspendingEchoService('Eric')")
    assertThat(recordingLogger.log.take())
      .isEqualTo("JavaScript received 'sup from the suspending JVM, Eric' from the JVM")
  }
}
