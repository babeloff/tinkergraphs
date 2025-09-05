package org.apache.tinkerpop.gremlin.tinkergraph.algorithms

import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.structure.Graph
import org.apache.tinkerpop.gremlin.structure.Vertex

/**
 * Graph algorithms implemented as extension functions on Graph.
 *
 * This module provides basic graph algorithms including traversal, pathfinding,
 * and connectivity analysis. All algorithms are implemented to work efficiently
 * with the TinkerGraph structure and its iterator implementations.
 */

/**
 * Performs a breadth-first search (BFS) traversal starting from the given vertex.
 *
 * BFS explores vertices level by level, visiting all vertices at distance k before
 * visiting any vertices at distance k+1. This algorithm is useful for finding
 * shortest paths in unweighted graphs and for level-order traversal.
 *
 * @param startVertex The vertex to start the search from
 * @return A sequence of vertices in BFS order
 *
 * @see [Breadth-first search on Wikipedia](https://en.wikipedia.org/wiki/Breadth-first_search)
 */
fun Graph.breadthFirstSearch(startVertex: Vertex): Sequence<Vertex> = sequence {
    val visited = mutableSetOf<Any?>()
    val queue = ArrayDeque<Vertex>()

    queue.addLast(startVertex)
    visited.add(startVertex.id())

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        yield(current)

        current.edges(Direction.BOTH).forEach { edge ->
            val neighbor = edge.inVertex().takeIf { it.id() != current.id() }
                ?: edge.outVertex()

            if (neighbor.id() !in visited) {
                visited.add(neighbor.id())
                queue.addLast(neighbor)
            }
        }
    }
}

/**
 * Performs a depth-first search (DFS) traversal starting from the given vertex.
 *
 * DFS explores as far as possible along each branch before backtracking. This algorithm
 * is useful for topological sorting, detecting cycles, and solving maze-like problems.
 *
 * @param startVertex The vertex to start the search from
 * @return A sequence of vertices in DFS order
 *
 * @see [Depth-first search on Wikipedia](https://en.wikipedia.org/wiki/Depth-first_search)
 */
fun Graph.depthFirstSearch(startVertex: Vertex): Sequence<Vertex> = sequence {
    val visited = mutableSetOf<Any?>()
    val stack = ArrayDeque<Vertex>()

    stack.addLast(startVertex)

    while (stack.isNotEmpty()) {
        val current = stack.removeLast()

        if (current.id() !in visited) {
            visited.add(current.id())
            yield(current)

            current.edges(Direction.BOTH).forEach { edge ->
                val neighbor = edge.inVertex().takeIf { it.id() != current.id() }
                    ?: edge.outVertex()

                if (neighbor.id() !in visited) {
                    stack.addLast(neighbor)
                }
            }
        }
    }
}

/**
 * Finds the shortest path between two vertices using BFS.
 *
 * This implementation uses breadth-first search to find the shortest path in terms
 * of number of edges (unweighted shortest path). For weighted graphs, consider
 * implementing Dijkstra's algorithm.
 *
 * @param from The starting vertex
 * @param to The target vertex
 * @return A list of vertices representing the shortest path, or null if no path exists
 *
 * @see [Shortest path problem on Wikipedia](https://en.wikipedia.org/wiki/Shortest_path_problem)
 */
fun Graph.shortestPath(from: Vertex, to: Vertex): List<Vertex>? {
    if (from.id() == to.id()) return listOf(from)

    val visited = mutableSetOf<Any?>()
    val queue = ArrayDeque<Pair<Vertex, List<Vertex>>>()

    queue.addLast(from to listOf(from))
    visited.add(from.id())

    while (queue.isNotEmpty()) {
        val (current, path) = queue.removeFirst()

        current.edges(Direction.BOTH).forEach { edge ->
            val neighbor = edge.inVertex().takeIf { it.id() != current.id() }
                ?: edge.outVertex()

            if (neighbor.id() == to.id()) {
                return path + neighbor
            }

            if (neighbor.id() !in visited) {
                visited.add(neighbor.id())
                queue.addLast(neighbor to (path + neighbor))
            }
        }
    }

    return null // No path found
}

/**
 * Finds all connected components in the graph.
 *
 * A connected component is a maximal set of vertices such that there is a path
 * between every pair of vertices. This algorithm identifies all such components
 * in the graph using depth-first search.
 *
 * @return A list of sets, where each set contains the vertices of one connected component
 *
 * @see [Connected component on Wikipedia](https://en.wikipedia.org/wiki/Connected_component)
 */
fun Graph.connectedComponents(): List<Set<Vertex>> {
    val visited = mutableSetOf<Any?>()
    val components = mutableListOf<Set<Vertex>>()

    vertices().forEach { vertex ->
        if (vertex.id() !in visited) {
            val component = mutableSetOf<Vertex>()

            // DFS to find all vertices in this component
            val stack = ArrayDeque<Vertex>()
            stack.addLast(vertex)

            while (stack.isNotEmpty()) {
                val current = stack.removeLast()

                if (current.id() !in visited) {
                    visited.add(current.id())
                    component.add(current)

                    current.edges(Direction.BOTH).forEach { edge ->
                        val neighbor = edge.inVertex().takeIf { it.id() != current.id() }
                            ?: edge.outVertex()

                        if (neighbor.id() !in visited) {
                            stack.addLast(neighbor)
                        }
                    }
                }
            }

            if (component.isNotEmpty()) {
                components.add(component)
            }
        }
    }

    return components
}

/**
 * Detects if the graph contains any cycles.
 *
 * A cycle is a path that starts and ends at the same vertex, with no repeated
 * edges or vertices (except for the start/end vertex). This implementation uses
 * DFS with parent tracking to detect back edges in undirected graphs.
 *
 * @return true if the graph contains at least one cycle, false otherwise
 *
 * @see [Cycle detection on Wikipedia](https://en.wikipedia.org/wiki/Cycle_(graph_theory))
 */
fun Graph.hasCycle(): Boolean {
    val visited = mutableSetOf<Any?>()

    fun dfsVisit(vertex: Vertex, parent: Vertex?): Boolean {
        visited.add(vertex.id())

        vertex.edges(Direction.BOTH).forEach { edge ->
            val neighbor = edge.inVertex().takeIf { it.id() != vertex.id() }
                ?: edge.outVertex()

            when {
                neighbor.id() !in visited -> {
                    // Recursively visit unvisited neighbor
                    if (dfsVisit(neighbor, vertex)) return true
                }
                parent == null || neighbor.id() != parent.id() -> {
                    // Found a visited vertex that is not the parent - cycle detected
                    return true
                }
                // If neighbor is the parent, it's just the edge we came from - ignore
            }
        }

        return false
    }

    // Check each unvisited vertex
    vertices().forEach { vertex ->
        if (vertex.id() !in visited) {
            if (dfsVisit(vertex, null)) return true
        }
    }

    return false
}

/**
 * Performs a breadth-first search and returns vertices at a specific distance.
 *
 * @param startVertex The vertex to start the search from
 * @param distance The exact distance from the start vertex
 * @return A set of vertices at the specified distance
 */
fun Graph.verticesAtDistance(startVertex: Vertex, distance: Int): Set<Vertex> {
    if (distance < 0) return emptySet()
    if (distance == 0) return setOf(startVertex)

    val visited = mutableSetOf<Any?>()
    val currentLevel = mutableSetOf<Vertex>()
    val nextLevel = mutableSetOf<Vertex>()

    currentLevel.add(startVertex)
    visited.add(startVertex.id())

    repeat(distance) {
        nextLevel.clear()

        currentLevel.forEach { vertex ->
            vertex.edges(Direction.BOTH).forEach { edge ->
                val neighbor = edge.inVertex().takeIf { it.id() != vertex.id() }
                    ?: edge.outVertex()

                if (neighbor.id() !in visited) {
                    visited.add(neighbor.id())
                    nextLevel.add(neighbor)
                }
            }
        }

        currentLevel.clear()
        currentLevel.addAll(nextLevel)
    }

    return currentLevel.toSet()
}

/**
 * Checks if the graph is connected (i.e., has only one connected component).
 *
 * @return true if the graph is connected, false otherwise
 */
fun Graph.isConnected(): Boolean {
    val components = connectedComponents()
    return components.size <= 1
}

/**
 * Calculates the diameter of the graph (longest shortest path between any two vertices).
 * This operation can be expensive for large graphs as it requires computing shortest
 * paths between all pairs of vertices.
 *
 * @return The diameter of the graph, or -1 if the graph is not connected
 */
fun Graph.diameter(): Int {
    if (!isConnected()) return -1

    var maxDistance = 0
    val vertexList = vertices().asSequence().toList()

    for (i in vertexList.indices) {
        for (j in i + 1 until vertexList.size) {
            val path = shortestPath(vertexList[i], vertexList[j])
            if (path != null) {
                maxDistance = maxOf(maxDistance, path.size - 1)
            }
        }
    }

    return maxDistance
}
