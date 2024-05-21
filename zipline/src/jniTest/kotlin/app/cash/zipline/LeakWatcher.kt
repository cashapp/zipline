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

import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue

/**
 * Use this to confirm that potentially leaked objects are eligible for garbage collection when they
 * should be.
 *
 * Be careful to not retain a reference to the allocated object in the calling code. The runtime is
 * known to not collect objects that are referenced in the current stack frame, even if they are no
 * longer needed by subsequent code in that stack frame. We support this here by accepting a
 * function to allocate an instance, rather than accepting an instance directly.
 */
class LeakWatcher<T>(
  allocate: () -> T,
) {
  private val referenceQueue = ReferenceQueue<T>()
  private val phantomReference = PhantomReference(allocate(), referenceQueue)
  private var released = false

  /**
   * Asserts that the subject is not strongly reachable from any garbage collection roots.
   *
   * This function works by requesting a garbage collection and confirming that the object is
   * collected in the process. An alternate, more robust implementation could do a heap dump and
   * report the shortest paths from GC roots if any exist.
   */
  fun assertNotLeaked() {
    if (released) return
    awaitGarbageCollection()
    released = referenceQueue.poll() == phantomReference
    if (!released) throw AssertionError("object was not garbage collected")
  }

  /**
   * See FinalizationTester for discussion on how to best trigger GC in tests.
   * https://android.googlesource.com/platform/libcore/+/master/support/src/test/java/libcore/
   * java/lang/ref/FinalizationTester.java
   */
  private fun awaitGarbageCollection() {
    Runtime.getRuntime().gc()
    Thread.sleep(100)
    System.runFinalization()
  }
}
