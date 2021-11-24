package app.cash.zipline.loader

/**
 * @param upstreamEdges a function that returns nodes upstream from the input node connected by an edge.
 *
 * https://www.interviewcake.com/concept/java/topological-sort
 */
fun <T> List<T>.topologicalSort(upstreamEdges: (T) -> Iterable<T>): List<T> {
  // map of node to nodes that depend on it
  val downstreamMap = mutableMapOf<T, MutableSet<T>>()
  // map of node to nodes that it depends on
  val upstreamMap = mutableMapOf<T, Set<T>>()

  // populate downstreamMap and upstreamMap
  forEach { node ->
    val upstreamNodes = upstreamEdges(node).toSet()
    upstreamMap[node] = upstreamNodes
    upstreamNodes.forEach { upstream ->
      if (!downstreamMap.containsKey(upstream)) {
        downstreamMap[upstream] = mutableSetOf()
      }
      downstreamMap[upstream]!!.add(node)
    }




//    val downstreamNodes = upstreamEdges(node)
//    downstreamMap[node] = downstreamNodes.toSet()
//    downstreamNodes.forEach { downstream ->
//      if (!upstreamMap.containsKey(downstream)) {
//        upstreamMap[downstream] = mutableSetOf()
//      }
//      upstreamMap[downstream]!!.add(node)
//    }
  }

  val nodesWithNoIncomingEdges = mutableListOf<T>()
  nodesWithNoIncomingEdges.addAll(filter { upstreamMap[it]?.isEmpty() ?: false })

 // nodesWithNoIncomingEdges: [b,c]
 //


  val topologicalOrdering = mutableListOf<T>()
  while (nodesWithNoIncomingEdges.isNotEmpty()) {
    // TODO consider if removeLast is better match to .pop()
    val node = nodesWithNoIncomingEdges.removeFirst()
    if (!topologicalOrdering.contains(node)) topologicalOrdering.add(node)

    downstreamMap[node]?.let { nodesWithNoIncomingEdges.addAll(it.filter { downstream ->
      downstream !in nodesWithNoIncomingEdges
        // Don't add all downstream nodes unless their dependencies have already been included in ordering
        && (topologicalOrdering + nodesWithNoIncomingEdges).containsAll(upstreamMap[downstream] ?: setOf())
    }) }
  }

  require(topologicalOrdering.size == this.size) {
    "All elements aren't included in  Topological Ordering [outputSize=${topologicalOrdering.size}] does not include all elements to be ordered [inputSize=${this.size}]"
  }

  return topologicalOrdering
}
