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

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FinalizationRegistryTest {
  private val quickJs = QuickJs.create()

  @BeforeTest
  fun setUp() {
    quickJs.evaluate(
      """
      globalThis.log = [];

      function takeLog() {
        const result = JSON.stringify(log);
        log.length = 0;
        return result;
      };
      """,
    )
  }

  @AfterTest
  fun tearDown() {
    quickJs.close()
  }

  @Test
  fun finalizerCalledImmediately() {
    quickJs.evaluate(
      """
      const registry = new FinalizationRegistry(heldValue => {
        log.push(heldValue);
      });

      function makeFinalizableObject() {
        const heavyObject = {
          data: 'this data is owned by the heavy object.'
        };
        registry.register(heavyObject, 'heavy object was finalized');
      }

      makeFinalizableObject();
      """.trimIndent(),
    )
    assertEquals(
      """["heavy object was finalized"]""",
      takeLog(),
    )
  }

  @Test
  fun valueCollectedOnceItGoesOutOfScope() {
    quickJs.evaluate(
      """
      const registry = new FinalizationRegistry(heldValue => {
        log.push('registry got ' + heldValue);
      });

      globalThis.heavyObject = {
        data: 'this data is owned by the heavy object.'
      };
      registry.register(heavyObject, 'heavy object');
      """.trimIndent(),
    )
    assertEquals(
      """[]""",
      takeLog(),
    )

    quickJs.evaluate(
      """
      globalThis.anotherProperty = globalThis.heavyObject;
      delete globalThis.heavyObject;
      """.trimIndent(),
    )
    assertEquals(
      """[]""",
      takeLog(),
    )

    quickJs.evaluate(
      """
      delete globalThis.anotherProperty;
      """.trimIndent(),
    )
    assertEquals(
      """["registry got heavy object"]""",
      takeLog(),
    )
  }

  @Test
  fun finalizerNotCalledUntilGcWhenThereIsAReferenceCycle() {
    quickJs.evaluate(
      """
      const registry = new FinalizationRegistry(heldValue => {
        log.push(heldValue);
      });

      function makeCycleContainingFinalizableObject() {
        const heavyObject = {
          data: 'this data is owned by the heavy object.'
        };
        registry.register(heavyObject, 'heavy object was finalized');
        heavyObject.cycle = {
          heavyObject: heavyObject
        };
      }

      makeCycleContainingFinalizableObject();
      """.trimIndent(),
    )
    assertEquals(
      """[]""",
      takeLog(),
    )

    quickJs.gc()
    assertEquals(
      """["heavy object was finalized"]""",
      takeLog(),
    )
  }

  @Test
  fun multipleValuesCollected() {
    quickJs.evaluate(
      """
      const registry = new FinalizationRegistry(heldValue => {
        log.push('registry got ' + heldValue);
      });

      function makeFinalizableObject(name) {
        const heavyObject = {
          data: 'this data is owned by the heavy object.'
        };
        registry.register(heavyObject, name);
      }

      makeFinalizableObject('red');
      makeFinalizableObject('green');
      makeFinalizableObject('blue');
      """.trimIndent(),
    )
    assertEquals(
      """["registry got red","registry got green","registry got blue"]""",
      takeLog(),
    )
  }

  @Test
  fun multipleRegistriesMayBeUsed() {
    quickJs.evaluate(
      """
      const registryA = new FinalizationRegistry(heldValue => {
        log.push('registry A got ' + heldValue);
      });

      const registryB = new FinalizationRegistry(heldValue => {
        log.push('registry B got ' + heldValue);
      });

      function makeFinalizableObject(registry, name) {
        const heavyObject = {
          data: 'this data is owned by the heavy object.'
        };
        registry.register(heavyObject, name);
      }

      makeFinalizableObject(registryA, 'green');
      makeFinalizableObject(registryB, 'blue');
      """.trimIndent(),
    )
    assertEquals(
      """["registry A got green","registry B got blue"]""",
      takeLog(),
    )
  }

  private fun takeLog() = quickJs.evaluate("takeLog()")
}
