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
import app.cash.zipline.testing.LoggingEventListener
import app.cash.zipline.testing.loadTestingJs
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class LeakedServicesTest {
  private val dispatcher = StandardTestDispatcher()
  private val eventListener = LoggingEventListener()
  private val zipline = Zipline.create(dispatcher, eventListener = eventListener)

  @Before fun setUp() {
    zipline.loadTestingJs()
    eventListener.takeAll() // Skip events created by loadTestingJs().
  }

  @After fun tearDown() {
    zipline.close()
  }

  @Test fun jvmLeaksService() = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")
    val name = "helloService"
    val leakWatcher = LeakWatcher<EchoService> {
      zipline.take(name) // Deliberately not closed for testing.
    }
    leakWatcher.assertNotLeaked()
    triggerJvmLeakDetection()
    assertThat(eventListener.take()).isEqualTo("takeService $name")
    assertThat(eventListener.take()).isEqualTo("serviceLeaked $name")
  }

  @Test fun jsLeaksService() = runTest(dispatcher) {
    val supService = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse = error("unexpected call")
    }

    val name = "supService"
    zipline.bind<EchoService>(name, supService)
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.allocateAndLeakService()")
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.triggerLeakDetection()")
    assertThat(eventListener.take()).isEqualTo("bindService $name")
    assertThat(eventListener.take()).isEqualTo("serviceLeaked $name")
  }

  /** Just attempting to take a service causes Zipline to process leaked services. */
  private fun triggerJvmLeakDetection() {
    zipline.take<EchoService>("noSuchService")
  }

  /**
   * Confirm that [Zipline.close] clears the index of services. This isn't useful in fully
   * garbage-collected platforms like Kotlin/JVM, but it prevents retain cycle on Kotlin/Native
   * where Kotlin objects are garbage collected and native objects are reference counted.
   */
  @Test fun servicesCollectedAfterZiplineClose() = runTest(dispatcher) {
    val leakWatcher = LeakWatcher {
      val helloService = object : EchoService {
        override fun echo(request: EchoRequest): EchoResponse = error("unexpected call")
      }
      zipline.bind<EchoService>("helloService", helloService)
      return@LeakWatcher helloService
    }
    zipline.close()
    leakWatcher.assertNotLeaked()
  }
}
