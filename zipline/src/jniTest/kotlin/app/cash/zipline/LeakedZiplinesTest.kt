/*
 * Copyright (C) 2024 Cash App
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
import app.cash.zipline.testing.loadTestingJs
import assertk.assertThat
import assertk.assertions.isSameInstanceAs
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Confirm that when we close a Zipline, it is no longer reachable by any GC roots.
 *
 * We had a bug in our leak detector (!) where the phantom references used to catch unclosed
 * services were holding the Zipline itself longer than necessary.
 *
 * https://github.com/cashapp/zipline/issues/1287
 */
class LeakedZiplinesTest {
  private val dispatcher = StandardTestDispatcher()

  @Test fun ziplineDoesntLeak() = runTest(dispatcher) {
    val referenceQueue = ReferenceQueue<Any>()
    val reference = createUseAndCloseZipline(referenceQueue)
    awaitGarbageCollection()
    assertThat(referenceQueue.poll()).isSameInstanceAs(reference) // Successfully released.
  }

  private fun createUseAndCloseZipline(
    referenceQueue: ReferenceQueue<Any>,
  ): PhantomReference<Any> {
    val zipline = Zipline.create(dispatcher)
    zipline.loadTestingJs()
    zipline.take<EchoService>("echoService")
    zipline.close()
    return PhantomReference(zipline, referenceQueue)
  }
}
