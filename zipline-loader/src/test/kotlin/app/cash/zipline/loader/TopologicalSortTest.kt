package app.cash.zipline.loader

import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test

class TopologicalSortTest {
  @Test
  fun happyPath() {
    assertThat(listOf("a", "b", "c")
      .topologicalSort(edges()))
      .containsExactly("a", "b", "c")
      .inOrder()
    assertThat(listOf("a", "b")
      .topologicalSort(edges("ba")))
      .containsExactly("a", "b")
      .inOrder()
    assertThat(listOf("a", "b")
      .topologicalSort(edges("ab")))
      .containsExactly("b", "a")
      .inOrder()
    assertThat(listOf("a", "b", "c", "d")
      .topologicalSort(edges("ba", "ca", "db", "dc")))
      .containsExactly("a", "b", "c", "d")
      .inOrder()
    assertThat(listOf("d", "b", "c", "a")
      .topologicalSort(edges("ba", "ca", "db", "dc")))
      .containsExactly("a", "b", "c", "d")
      .inOrder()
    assertThat(listOf("a", "b", "c", "d", "e")
      .topologicalSort(edges("be", "bc", "ec", "ac", "cd", "ad")))
      .containsExactly("d", "c", "a", "e", "b")
      .inOrder()
  }

  @Test
  fun cycleCrashes() {
    assertFailsWith<IllegalArgumentException> {
      listOf("a", "b")
        .topologicalSort(edges("ab", "ba"))
    }
  }

  /** Each string is two characters, source and destination of an edge. */
  fun edges(vararg edges: String): (String) -> List<String> {
    return { node: String ->
      edges.filter { it.startsWith(node) }.map { it.substring(1) }
    }
  }
}
