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
      .containsExactly("b", "a")
      .inOrder()
    assertThat(listOf("a", "b")
      .topologicalSort(edges("ab")))
      .containsExactly("a", "b")
      .inOrder()
    assertThat(listOf("a", "b", "c", "d")
      .topologicalSort(edges("ba", "ca", "db", "dc")))
      .containsExactly("d", "b", "c", "a")
      .inOrder()
    assertThat(listOf("d", "b", "c", "a")
      .topologicalSort(edges("ba", "ca", "db", "dc")))
      .containsExactly("d", "b", "c", "a")
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
