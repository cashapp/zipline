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

/**
 * Returns a new list where each element is preceded by its results in [sourceToTarget]. The first
 * element will return no values in [sourceToTarget].
 *
 * @param sourceToTarget a function that returns nodes that should precede the argument in the result.
 *
 * Implementation inspiration: https://www.interviewcake.com/concept/java/topological-sort
 */
internal fun <T> List<T>.topologicalSort(sourceToTarget: (T) -> Iterable<T>): List<T> {
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
    buildString {
      append("No topological ordering is possible for these items:")

      val unorderedItems = this@topologicalSort.toSet() - result.toSet()
      for (unorderedItem in unorderedItems) {
        append("\n  ")
        append(unorderedItem)
        val unsatisfiedDeps = sourceToTarget(unorderedItem).toSet() - result.toSet()
        unsatisfiedDeps.joinTo(this, separator = ", ", prefix = " (", postfix = ")")
      }
    }
  }

  return result
}

internal fun <T> List<T>.isTopologicallySorted(sourceToTarget: (T) -> Iterable<T>): Boolean {
  val seenNodes = mutableSetOf<T>()
  for (node in this) {
    if (sourceToTarget(node).any { it !in seenNodes }) return false
    seenNodes.add(node)
  }
  return true
}
