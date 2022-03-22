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

import app.cash.zipline.testing.EchoService
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

class LeakedServicesTest {
  private val events = Channel<String>(capacity = Int.MAX_VALUE)
  private val eventListener = object : EventListener() {
    override fun instanceLeaked(name: String, stacktrace: Exception?) {
      val sent = events.trySend("instanceLeaked($name, $stacktrace)")
      check(sent.isSuccess)
    }
  }
  private val dispatcher = TestCoroutineDispatcher()
  private val zipline = Zipline.create(dispatcher, eventListener = eventListener)
  private val uncaughtExceptionHandler = TestUncaughtExceptionHandler()

  @Before fun setUp(): Unit = runBlocking(dispatcher) {
    zipline.loadTestingJs()
    uncaughtExceptionHandler.setUp()
  }

  @After fun tearDown(): Unit = runBlocking(dispatcher) {
    zipline.close()
    uncaughtExceptionHandler.tearDown()
  }

  @Test fun jvmLeaksService(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")
    allocateAndLeakService()
    awaitGarbageCollection()
    triggerLeakDetection()
    assertThat(events.receive()).isEqualTo("instanceLeaked(helloService, null)")
  }

  /** Just attempting to take a service causes Zipline to process leaked services. */
  private fun triggerLeakDetection() {
    try {
      zipline.take<EchoService>("noSuchService")
    } catch (ignored: Exception) {
    }
  }

  /** Use a separate method so there's no hidden reference remaining on the stack. */
  private fun allocateAndLeakService() {
    zipline.take<EchoService>("helloService")
  }
}
