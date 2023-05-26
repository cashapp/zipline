/*
 * Copyright (C) 2015 Square, Inc.
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

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TuningApisTest {
  private val quickjs = QuickJs.create()

  @AfterTest fun tearDown() {
    quickjs.close()
  }

  @Test fun defaults() {
    // TODO remove this test once jniMain and nativeMain share initial value config in hostMain.
    assertEquals(-1, quickjs.memoryLimit)
    assertEquals(256L * 1024L, quickjs.gcThreshold)
    assertEquals(512L * 1024L, quickjs.maxStackSize)
  }

  @Test fun setMemoryLimit() {
    val value = 1024L * 1024L + 1L
    quickjs.memoryLimit = value
    assertEquals(value, quickjs.memoryLimit)
    assertEquals(value, quickjs.memoryUsage.memoryAllocatedLimit)
  }

  @Test fun setGcThreshold() {
    val value = 1024L * 1024L + 2L
    quickjs.gcThreshold = value
    assertEquals(value, quickjs.gcThreshold)
  }

  @Test fun setMaxStackSize() {
    val value = 1024L * 1024L + 3L
    quickjs.maxStackSize = value
    assertEquals(value, quickjs.maxStackSize)
  }

  @Test fun initialMemoryUsage() {
    val usage = quickjs.memoryUsage
    assertTrue(usage.memoryAllocatedCount > 0L, usage.toString())
    assertTrue(usage.memoryAllocatedSize > 0L, usage.toString())
    assertTrue(usage.memoryAllocatedLimit != 0L, usage.toString())
    assertTrue(usage.memoryUsedCount > 0L, usage.toString())
    assertTrue(usage.memoryUsedSize in 1L..usage.memoryAllocatedSize, usage.toString())
  }

  @Test fun definePropertyIncreasesPropertiesCount() {
    val diff = diffMemoryUsage {
      quickjs.evaluate(
        """
        globalThis.hello = 'hello';
        """,
      )
    }
    assertEquals(diff.stringsCount, 0L) // Why isn't this 1?
    assertEquals(diff.propertiesCount, 1L)
  }

  @Test fun defineFunctionIncreasesFunctionsCount() {
    val diff = diffMemoryUsage {
      quickjs.evaluate(
        """
        globalThis.hypotenuse = function(a, b) {
          return Math.sqrt((a * a) + (b * b));
        };
        """,
      )
    }
    assertEquals(diff.jsFunctionsCount, 1L)
    assertTrue(diff.jsFunctionsSize > 0L)
    assertTrue(diff.jsFunctionsCodeSize > 0L)
    assertTrue(diff.jsFunctionsLineNumberTablesCount > 0L)
    assertTrue(diff.jsFunctionsLineNumberTablesSize > 0L)
  }

  @Test fun defineFastArrayIncreasesFastArraysCount() {
    val diff = diffMemoryUsage {
      quickjs.evaluate(
        """
        globalThis.buffer = new Uint8Array(1024 * 1024);
        """,
      )
    }
    assertTrue(diff.memoryAllocatedSize >= 1024L * 1024L)
    assertTrue(diff.memoryUsedSize >= 1024L * 1024L)
    assertEquals(diff.fastArraysCount, 0L) // Why isn't this 1?
    assertEquals(diff.fastArraysElementsCount, 0L) // Why isn't this (1024L * 1024L)?
  }

  private fun diffMemoryUsage(block: () -> Unit): MemoryUsage {
    val before = quickjs.memoryUsage
    block()
    val after = quickjs.memoryUsage

    return MemoryUsage(
      memoryAllocatedCount = after.memoryAllocatedCount - before.memoryAllocatedCount,
      memoryAllocatedSize = after.memoryAllocatedSize - before.memoryAllocatedSize,
      memoryAllocatedLimit = after.memoryAllocatedLimit - before.memoryAllocatedLimit,
      memoryUsedCount = after.memoryUsedCount - before.memoryUsedCount,
      memoryUsedSize = after.memoryUsedSize - before.memoryUsedSize,
      atomsCount = after.atomsCount - before.atomsCount,
      atomsSize = after.atomsSize - before.atomsSize,
      stringsCount = after.stringsCount - before.stringsCount,
      stringsSize = after.stringsSize - before.stringsSize,
      objectsCount = after.objectsCount - before.objectsCount,
      objectsSize = after.objectsSize - before.objectsSize,
      propertiesCount = after.propertiesCount - before.propertiesCount,
      propertiesSize = after.propertiesSize - before.propertiesSize,
      shapeCount = after.shapeCount - before.shapeCount,
      shapeSize = after.shapeSize - before.shapeSize,
      jsFunctionsCount = after.jsFunctionsCount - before.jsFunctionsCount,
      jsFunctionsSize = after.jsFunctionsSize - before.jsFunctionsSize,
      jsFunctionsCodeSize = after.jsFunctionsCodeSize - before.jsFunctionsCodeSize,
      jsFunctionsLineNumberTablesCount = after.jsFunctionsLineNumberTablesCount - before.jsFunctionsLineNumberTablesCount,
      jsFunctionsLineNumberTablesSize = after.jsFunctionsLineNumberTablesSize - before.jsFunctionsLineNumberTablesSize,
      cFunctionsCount = after.cFunctionsCount - before.cFunctionsCount,
      arraysCount = after.arraysCount - before.arraysCount,
      fastArraysCount = after.fastArraysCount - before.fastArraysCount,
      fastArraysElementsCount = after.fastArraysElementsCount - before.fastArraysElementsCount,
      binaryObjectsCount = after.binaryObjectsCount - before.binaryObjectsCount,
      binaryObjectsSize = after.binaryObjectsSize - before.binaryObjectsSize,
    )
  }
}
