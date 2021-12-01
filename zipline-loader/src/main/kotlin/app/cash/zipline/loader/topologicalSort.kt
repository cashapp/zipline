package app.cash.zipline.loader

/**
 * Returns a new list where each element is preceded by its results in [sourceToTarget]. The first
 * element will return no values in [sourceToTarget].
 *
 * @param sourceToTarget a function that returns nodes that should precede the argument in the result.
 *
 * Implementation inspiration: https://www.interviewcake.com/concept/java/topological-sort
 */
fun <T> List<T>.topologicalSort(sourceToTarget: (T) -> Iterable<T>): List<T> {
  // Build a reverse index, from targets to sources.
  val targetToSources = mutableMapOf<T, MutableSet<T>>()
  val queue = ArrayDeque<T>()
  for (source in this) {
    var hasTargets = false
    for (target in sourceToTarget(source)) {
      val set = targetToSources.getOrPut(target) { mutableSetOf() }
      set += source
      hasTargets = true
    }

    // No targets means all this source's targets are satisfied, queue it up.
    if (!hasTargets) {
      queue += source
    }
  }

  val result = mutableListOf<T>()
  while (queue.isNotEmpty()) {
    val node = queue.removeFirst()
    result += node

    val potentiallySatisfied = targetToSources[node] ?: setOf()
    for (source in potentiallySatisfied) {
      // If all a source's targets are satisfied, queue up the source.
      if (source !in queue &&
        sourceToTarget(source).all { target -> target in result || target in queue }
      ) {
        queue += source
      }
    }
  }

  require(result.size == this.size) {
    "No topological ordering is possible for $this"
  }

  return result
}

fun <T> List<T>.isTopologicallySorted(sourceToTarget: (T) -> Iterable<T>): Boolean {
  val seenNodes = mutableSetOf<T>()
  for (node in this) {
    if (!seenNodes.containsAll(sourceToTarget(node).toSet())) return false
    seenNodes.add(node)
  }
  return true
}
