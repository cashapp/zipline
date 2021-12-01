package app.cash.zipline.loader

import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test

class TopologicalSortTest {
  @Test
  fun emptyEdges() {
    val unsorted = listOf("a", "b", "c")
    val sorted = listOf("a", "b", "c")
    val sourceToTarget = edges()
    val actual = unsorted.topologicalSort(sourceToTarget)
    assertThat(sorted.isTopologicallySorted(sourceToTarget)).isTrue()
    assertThat(actual).isEqualTo(sorted)
  }

  @Test
  fun alreadySorted() {
    val sourceToTarget = edges("ba", "cb")
    val unsorted = listOf("a", "b", "c")
    val sorted = listOf("a", "b", "c")
    val actual = unsorted.topologicalSort(sourceToTarget)
    assertThat(sorted.isTopologicallySorted(sourceToTarget)).isTrue()
    assertThat(actual).isEqualTo(sorted)
  }

  @Test
  fun happyPath() {
    assertTopologicalSort(
      unsorted = listOf("a", "b"),
      sorted = listOf("b", "a"),
      "ab"
    )
    assertTopologicalSort(
      unsorted = listOf("b", "c", "d", "a"),
      sorted = listOf("a", "b", "c", "d"),
      "ba", "ca", "db", "dc"
    )
    assertTopologicalSort(
      unsorted = listOf("d", "b", "c", "a"),
      sorted = listOf("a", "b", "c", "d"),
      "ba", "ca", "db", "dc"
    )
    assertTopologicalSort(
      unsorted = listOf("a", "b", "c", "d", "e"),
      sorted = listOf("d", "c", "a", "e", "b"),
      "be", "bc", "ec", "ac", "cd", "ad"
    )
  }

  @Test
  fun cycleCrashes() {
    assertFailsWith<IllegalArgumentException> {
      listOf("a", "b")
        .topologicalSort(edges("ab", "ba"))
    }
  }

  private fun assertTopologicalSort(unsorted: List<String>, sorted: List<String>, vararg edges: String) {
    val sourceToTarget = edges(*edges)
    assertThat(unsorted.isTopologicallySorted(sourceToTarget)).isFalse()
    assertThat(sorted.isTopologicallySorted(sourceToTarget)).isTrue()

    val actual = unsorted
      .topologicalSort(sourceToTarget)

    assertThat(actual.isTopologicallySorted(sourceToTarget)).isTrue()
    assertThat(actual).isEqualTo(sorted)
  }

  /** Each string is two characters, source and destination of an edge. */
  private fun edges(vararg edges: String): (String) -> List<String> {
    return { node: String ->
      edges.filter { it.startsWith(node) }.map { it.substring(1) }
    }
  }
}
