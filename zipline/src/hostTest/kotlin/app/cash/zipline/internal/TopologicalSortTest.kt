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

package app.cash.zipline.internal

import assertk.assertThat
import assertk.assertions.hasMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TopologicalSortTest {
  @Test
  fun emptyEdges() {
    val unsorted = listOf("a", "b", "c")
    val sorted = listOf("a", "b", "c")
    val sourceToTarget = edges()
    val actual = unsorted.topologicalSort(sourceToTarget)
    assertTrue(sorted.isTopologicallySorted(sourceToTarget))
    assertEquals(sorted, actual)
  }

  @Test
  fun alreadySorted() {
    val sourceToTarget = edges("ba", "cb")
    val unsorted = listOf("a", "b", "c")
    val sorted = listOf("a", "b", "c")
    val actual = unsorted.topologicalSort(sourceToTarget)
    assertTrue(sorted.isTopologicallySorted(sourceToTarget))
    assertEquals(sorted, actual)
  }

  @Test
  fun happyPath() {
    assertTopologicalSort(
      unsorted = listOf("a", "b"),
      sorted = listOf("b", "a"),
      "ab",
    )
    assertTopologicalSort(
      unsorted = listOf("b", "c", "d", "a"),
      sorted = listOf("a", "b", "c", "d"),
      "ba",
      "ca",
      "db",
      "dc",
    )
    assertTopologicalSort(
      unsorted = listOf("d", "b", "c", "a"),
      sorted = listOf("a", "b", "c", "d"),
      "ba",
      "ca",
      "db",
      "dc",
    )
    assertTopologicalSort(
      unsorted = listOf("a", "b", "c", "d", "e"),
      sorted = listOf("d", "c", "a", "e", "b"),
      "be",
      "bc",
      "ec",
      "ac",
      "cd",
      "ad",
    )
  }

  @Test
  fun cycleCrashes() {
    val exception = assertFailsWith<IllegalArgumentException> {
      listOf("a", "b")
        .topologicalSort(edges("ab", "ba"))
    }
    assertThat(exception).hasMessage(
      """
      |No topological ordering is possible for these items:
      |  a (b)
      |  b (a)
      """.trimMargin(),
    )
  }

  @Test
  fun elementConsumedButNotDeclaredCrashes() {
    val exception = assertFailsWith<IllegalArgumentException> {
      listOf("a", "b")
        .topologicalSort(edges("ab", "ac"))
    }
    assertThat(exception).hasMessage(
      """
      |No topological ordering is possible for these items:
      |  a (c)
      """.trimMargin(),
    )
  }

  @Test
  fun exceptionMessageOnlyIncludesProblematicItems() {
    val exception = assertFailsWith<IllegalArgumentException> {
      listOf("a", "b", "c", "d", "e")
        .topologicalSort(edges("ab", "bc", "da", "de", "db", "ed", "ef"))
    }
    assertThat(exception).hasMessage(
      """
      |No topological ordering is possible for these items:
      |  d (e)
      |  e (d, f)
      """.trimMargin(),
    )
  }

  private fun assertTopologicalSort(unsorted: List<String>, sorted: List<String>, vararg edges: String) {
    val sourceToTarget = edges(*edges)
    assertFalse(unsorted.isTopologicallySorted(sourceToTarget))
    assertTrue(sorted.isTopologicallySorted(sourceToTarget))

    val actual = unsorted
      .topologicalSort(sourceToTarget)

    assertTrue(actual.isTopologicallySorted(sourceToTarget))
    assertEquals(sorted, actual)
  }

  /** Each string is two characters, source and destination of an edge. */
  private fun edges(vararg edges: String): (String) -> List<String> {
    return { node: String ->
      edges.filter { it.startsWith(node) }.map { it.substring(1) }
    }
  }
}
