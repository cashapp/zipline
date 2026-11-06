/*
 * Copyright (C) 2026 Block, Inc.
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

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class QuickJsNativeMemoryTest {
  @Test fun returningStringsReleasesCopiedJniBuffers() {
    val server = ManagementFactory.getPlatformMBeanServer()
    val diagnosticCommand = ObjectName("com.sun.management:type=DiagnosticCommand")
    assumeTrue("Requires HotSpot native memory tracking", server.isRegistered(diagnosticCommand))

    fun internalMemoryKiB(): Long {
      val summary = server.invoke(
        diagnosticCommand,
        "vmNativeMemory",
        arrayOf(arrayOf("summary", "scale=KB")),
        arrayOf("[Ljava.lang.String;"),
      ) as String
      // HotSpot accounts for copies from GetByteArrayElements in the Internal category.
      return Regex("Internal \\(reserved=\\d+KB, committed=(\\d+)KB\\)")
        .find(summary)?.groupValues?.get(1)?.toLong()
        ?: error("Native memory tracking must be enabled: $summary")
    }

    QuickJs.create().use { quickjs ->
      val expected = "x".repeat(256 * 1024)
      val script = "'x'.repeat(262144)"
      repeat(32) {
        assertEquals(expected, quickjs.evaluate(script))
        internalMemoryKiB() // Warm up both string conversion and the diagnostic command.
      }
      val beforeKiB = internalMemoryKiB()
      repeat(256) {
        assertEquals(expected, quickjs.evaluate(script))
      }
      val growthKiB = internalMemoryKiB() - beforeKiB

      // JNI_COMMIT without a final release retains 64 MiB over these calls.
      // Allow 8 MiB of unrelated JVM allocation noise with the correct release mode.
      assertTrue("JNI string conversion retained ${growthKiB} KiB", growthKiB < 8 * 1024)
    }
  }
}
