package org.apache.tinkerpop.gremlin.tinkergraph.algorithms

import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.structure.Graph
import org.apache.tinkerpop.gremlin.structure.Vertex

/**
 * Advanced graph algorithms implemented as extension functions on Graph.
 *
 * This module provides sophisticated graph algorithms including weighted pathfinding,
 * minimum spanning trees, strongly connected components, and connectivity analysis.
 * All algorithms are optimized for TinkerGraph and support multiplatform deployment.
 */

/**
 * Represents a weighted edge for algorithms that require edge weights.
 */
data class WeightedEdge(
    val edge: Edge,
    val weight: Double,
    val from: Vertex,
    val to: Vertex
)

/**
 * Represents a path with its total weight.
 */
data class WeightedPath(
    val vertices: List<Vertex>,
    val totalWeight: Double
)

/**
 * Finds the shortest path between two vertices using Dijkstra's algorithm.
 *
 * This algorithm finds the shortest path in a weighted graph by exploring vertices
 * in order of their distance from the source. Edge weights are extracted from the
 * specified property key.
 *
 * @param from The starting vertex
 * @param to The target vertex
 * @param weightProperty The property key containing edge weights (default: "weight")
 * @param direction The direction to traverse edges (default: Direction.OUT for directed graph)
 * @return WeightedPath containing the shortest path and total weight, or null if no path exists
 *
 * @see [Dijkstra's algorithm on Wikipedia](https://en.wikipedia.org/wiki/Dijkstra%27s_algorithm)
 */
fun Graph.dijkstraShortestPath(
    from: Vertex,
    to: Vertex,
    weightProperty: String = "weight",
    direction: Direction = Direction.OUT
): WeightedPath? {
    if (from.id() == to.id()) return WeightedPath(listOf(from), 0.0)

    val distances = mutableMapOf<Any?, Double>()
    val previous = mutableMapOf<Any?, Vertex>()
    val visited = mutableSetOf<Any?>()
    val unvisited = mutableSetOf<Vertex>()

    // Initialize distances
    vertices().forEach { vertex ->
        distances[vertex.id()] = if (vertex.id() == from.id()) 0.0 else Double.POSITIVE_INFINITY
        unvisited.add(vertex)
    }

    while (unvisited.isNotEmpty()) {
        // Find unvisited vertex with minimum distance
        val current = unvisited.minByOrNull { distances[it.id()] ?: Double.POSITIVE_INFINITY }
            ?: break

        if (current.id() == to.id()) {
            // Reconstruct path
            val path = mutableListOf<Vertex>()
            var curr: Vertex? = current
            while (curr != null) {
                path.add(0, curr)
                curr = previous[curr.id()]
            }
            val finalDistance = distances[to.id()] ?: Double.POSITIVE_INFINITY
            return if (finalDistance == Double.POSITIVE_INFINITY) null else WeightedPath(path, finalDistance)
        }

        unvisited.remove(current)
        visited.add(current.id())

        val currentDistance = distances[current.id()] ?: Double.POSITIVE_INFINITY
        if (currentDistance == Double.POSITIVE_INFINITY) break

        // Update distances to neighbors
        current.edges(direction).forEach { edge ->
            val neighbor = when (direction) {
                Direction.OUT -> edge.inVertex()
                Direction.IN -> edge.outVertex()
                Direction.BOTH -> if (edge.outVertex().id() == current.id()) edge.inVertex() else edge.outVertex()
            }

            if (neighbor.id() !in visited) {
                val weight = (edge.value<Any?>(weightProperty) as? Number)?.toDouble() ?: 1.0
                val alt = currentDistance + weight

                if (alt < (distances[neighbor.id()] ?: Double.POSITIVE_INFINITY)) {
                    distances[neighbor.id()] = alt
                    previous[neighbor.id()] = current
                }
            }
        }
    }

    return null // No path found
}

/**
 * Performs topological sorting of a directed acyclic graph (DAG).
 *
 * Topological sorting produces a linear ordering of vertices such that for every
 * directed edge (u, v), vertex u appears before v in the ordering. This is only
 * possible for directed acyclic graphs.
 *
 * @param direction The direction to consider for edges (default: Direction.OUT)
 * @return List of vertices in topological order, or null if the graph contains cycles
 *
 * @see [Topological sorting on Wikipedia](https://en.wikipedia.org/wiki/Topological_sorting)
 */
fun Graph.topologicalSort(direction: Direction = Direction.OUT): List<Vertex>? {
    val inDegree = mutableMapOf<Any?, Int>()
    val vertices = vertices().asSequence().toList()

    // Initialize in-degrees
    vertices.forEach { vertex ->
        inDegree[vertex.id()] = 0
    }

    // Calculate in-degrees
    vertices.forEach { vertex ->
        vertex.edges(direction).forEach { edge ->
            val target = when (direction) {
                Direction.OUT -> edge.inVertex()
                Direction.IN -> edge.outVertex()
                Direction.BOTH -> if (edge.outVertex().id() == vertex.id()) edge.inVertex() else edge.outVertex()
            }
            inDegree[target.id()] = (inDegree[target.id()] ?: 0) + 1
        }
    }

    // Find vertices with no incoming edges
    val queue = ArrayDeque<Vertex>()
    vertices.forEach { vertex ->
        if (inDegree[vertex.id()] == 0) {
            queue.addLast(vertex)
        }
    }

    val result = mutableListOf<Vertex>()

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        result.add(current)

        current.edges(direction).forEach { edge ->
            val target = when (direction) {
                Direction.OUT -> edge.inVertex()
                Direction.IN -> edge.outVertex()
                Direction.BOTH -> if (edge.outVertex().id() == current.id()) edge.inVertex() else edge.outVertex()
            }

            inDegree[target.id()] = (inDegree[target.id()] ?: 1) - 1
            if (inDegree[target.id()] == 0) {
                queue.addLast(target)
            }
        }
    }

    // Check for cycles
    return if (result.size == vertices.size) result else null
}

/**
 * Finds strongly connected components using Tarjan's algorithm.
 *
 * A strongly connected component is a maximal set of vertices such that there is
 * a path from each vertex to every other vertex in the component. Tarjan's algorithm
 * uses a single DFS traversal to find all SCCs in linear time.
 *
 * @param direction The direction to consider for edges (default: Direction.OUT)
 * @return List of strongly connected components, each represented as a set of vertices
 *
 * @see [Tarjan's strongly connected components algorithm on Wikipedia](https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm)
 */
fun Graph.tarjanStronglyConnectedComponents(direction: Direction = Direction.OUT): List<Set<Vertex>> {
    var index = 0
    val indices = mutableMapOf<Any?, Int>()
    val lowLinks = mutableMapOf<Any?, Int>()
    val onStack = mutableSetOf<Any?>()
    val stack = ArrayDeque<Vertex>()
    val components = mutableListOf<Set<Vertex>>()

    fun strongConnect(vertex: Vertex) {
        indices[vertex.id()] = index
        lowLinks[vertex.id()] = index
        index++
        stack.addLast(vertex)
        onStack.add(vertex.id())

        vertex.edges(direction).forEach { edge ->
            val neighbor = when (direction) {
                Direction.OUT -> edge.inVertex()
                Direction.IN -> edge.outVertex()
                Direction.BOTH -> if (edge.outVertex().id() == vertex.id()) edge.inVertex() else edge.outVertex()
            }

            when {
                neighbor.id() !in indices -> {
                    strongConnect(neighbor)
                    lowLinks[vertex.id()] = minOf(
                        lowLinks[vertex.id()] ?: Int.MAX_VALUE,
                        lowLinks[neighbor.id()] ?: Int.MAX_VALUE
                    )
                }
                neighbor.id() in onStack -> {
                    lowLinks[vertex.id()] = minOf(
                        lowLinks[vertex.id()] ?: Int.MAX_VALUE,
                        indices[neighbor.id()] ?: Int.MAX_VALUE
                    )
                }
            }
        }

        // If vertex is a root node, pop the stack and create SCC
        if (lowLinks[vertex.id()] == indices[vertex.id()]) {
            val component = mutableSetOf<Vertex>()
            do {
                val w = stack.removeLast()
                onStack.remove(w.id())
                component.add(w)
            } while (w.id() != vertex.id())
            components.add(component)
        }
    }

    vertices().forEach { vertex ->
        if (vertex.id() !in indices) {
            strongConnect(vertex)
        }
    }

    return components
}

/**
 * Finds the minimum spanning tree using Kruskal's algorithm.
 *
 * A minimum spanning tree is a subset of edges that connects all vertices with
 * the minimum total edge weight. Kruskal's algorithm builds the MST by sorting
 * edges by weight and adding them if they don't create a cycle.
 *
 * @param weightProperty The property key containing edge weights (default: "weight")
 * @return Set of edges forming the minimum spanning tree
 *
 * @see [Kruskal's algorithm on Wikipedia](https://en.wikipedia.org/wiki/Kruskal%27s_algorithm)
 */
fun Graph.kruskalMinimumSpanningTree(weightProperty: String = "weight"): Set<Edge> {
    val mst = mutableSetOf<Edge>()
    val vertexIds = vertices().asSequence().map { it.id() }.toSet()

    // Union-Find data structure
    val parent = mutableMapOf<Any?, Any?>()
    val rank = mutableMapOf<Any?, Int>()

    // Initialize Union-Find
    vertexIds.forEach { id ->
        parent[id] = id
        rank[id] = 0
    }

    fun find(x: Any?): Any? {
        if (parent[x] != x) {
            parent[x] = find(parent[x])
        }
        return parent[x]
    }

    fun union(x: Any?, y: Any?): Boolean {
        val rootX = find(x)
        val rootY = find(y)

        if (rootX == rootY) return false

        val rankX = rank[rootX] ?: 0
        val rankY = rank[rootY] ?: 0

        when {
            rankX < rankY -> parent[rootX] = rootY
            rankX > rankY -> parent[rootY] = rootX
            else -> {
                parent[rootY] = rootX
                rank[rootX] = rankX + 1
            }
        }
        return true
    }

    // Get all edges and sort by weight
    val sortedEdges = edges().asSequence().map { edge ->
        val weight = (edge.value<Any?>(weightProperty) as? Number)?.toDouble() ?: 1.0
        WeightedEdge(edge, weight, edge.outVertex(), edge.inVertex())
    }.sortedBy { it.weight }

    // Kruskal's algorithm
    for (weightedEdge in sortedEdges) {
        if (union(weightedEdge.from.id(), weightedEdge.to.id())) {
            mst.add(weightedEdge.edge)
            if (mst.size == vertexIds.size - 1) break
        }
    }

    return mst
}

/**
 * Finds all articulation points (cut vertices) in the graph.
 *
 * An articulation point is a vertex whose removal increases the number of
 * connected components. These are critical vertices for graph connectivity.
 *
 * @return Set of articulation points
 *
 * @see [Biconnected component on Wikipedia](https://en.wikipedia.org/wiki/Biconnected_component)
 */
fun Graph.articulationPoints(): Set<Vertex> {
    val visited = mutableSetOf<Any?>()
    val articulationPoints = mutableSetOf<Vertex>()
    val disc = mutableMapOf<Any?, Int>()
    val low = mutableMapOf<Any?, Int>()
    val parent = mutableMapOf<Any?, Vertex?>()
    var time = 0

    fun bridgeUtil(u: Vertex) {
        var children = 0
        visited.add(u.id())
        disc[u.id()] = time
        low[u.id()] = time
        time++

        u.edges(Direction.BOTH).forEach { edge ->
            val v = if (edge.outVertex().id() == u.id()) edge.inVertex() else edge.outVertex()

            if (v.id() !in visited) {
                children++
                parent[v.id()] = u
                bridgeUtil(v)

                low[u.id()] = minOf(low[u.id()] ?: Int.MAX_VALUE, low[v.id()] ?: Int.MAX_VALUE)

                // u is articulation point in following cases:
                // 1) u is root of DFS tree and has two or more children
                if (parent[u.id()] == null && children > 1) {
                    articulationPoints.add(u)
                }
                // 2) u is not root and low value of one of its children is >= disc[u]
                if (parent[u.id()] != null && (low[v.id()] ?: 0) >= (disc[u.id()] ?: 0)) {
                    articulationPoints.add(u)
                }
            } else if (v.id() != parent[u.id()]?.id()) {
                low[u.id()] = minOf(low[u.id()] ?: Int.MAX_VALUE, disc[v.id()] ?: Int.MAX_VALUE)
            }
        }
    }

    vertices().forEach { vertex ->
        if (vertex.id() !in visited) {
            bridgeUtil(vertex)
        }
    }

    return articulationPoints
}

/**
 * Finds all bridges (cut edges) in the graph.
 *
 * A bridge is an edge whose removal increases the number of connected components.
 * These are critical edges for graph connectivity.
 *
 * @return Set of bridge edges
 *
 * @see [Bridge in graph theory on Wikipedia](https://en.wikipedia.org/wiki/Bridge_(graph_theory))
 */
fun Graph.bridges(): Set<Edge> {
    val visited = mutableSetOf<Any?>()
    val bridges = mutableSetOf<Edge>()
    val disc = mutableMapOf<Any?, Int>()
    val low = mutableMapOf<Any?, Int>()
    val parent = mutableMapOf<Any?, Edge?>()
    var time = 0

    fun bridgeUtil(u: Vertex) {
        visited.add(u.id())
        disc[u.id()] = time
        low[u.id()] = time
        time++

        u.edges(Direction.BOTH).forEach { edge ->
            val v = if (edge.outVertex().id() == u.id()) edge.inVertex() else edge.outVertex()

            if (v.id() !in visited) {
                parent[v.id()] = edge
                bridgeUtil(v)

                low[u.id()] = minOf(low[u.id()] ?: Int.MAX_VALUE, low[v.id()] ?: Int.MAX_VALUE)

                // If the lowest vertex reachable from subtree under v is below u in DFS tree, then u-v is a bridge
                if ((low[v.id()] ?: 0) > (disc[u.id()] ?: 0)) {
                    bridges.add(edge)
                }
            } else if (edge != parent[u.id()]) {
                // Only update if this is not the same edge we came from
                low[u.id()] = minOf(low[u.id()] ?: Int.MAX_VALUE, disc[v.id()] ?: Int.MAX_VALUE)
            }
        }
    }

    vertices().forEach { vertex ->
        if (vertex.id() !in visited) {
            bridgeUtil(vertex)
        }
    }

    return bridges
}

/**
 * Checks if the graph is bipartite.
 *
 * A bipartite graph is one whose vertices can be divided into two disjoint sets
 * such that no two vertices within the same set are adjacent.
 *
 * @return Pair containing (isBipartite, colorMap) where colorMap assigns 0 or 1 to each vertex
 *
 * @see [Bipartite graph on Wikipedia](https://en.wikipedia.org/wiki/Bipartite_graph)
 */
fun Graph.isBipartite(): Pair<Boolean, Map<Any?, Int>> {
    val color = mutableMapOf<Any?, Int>()
    val vertices = vertices().asSequence().toList()

    fun dfs(vertex: Vertex, c: Int): Boolean {
        color[vertex.id()] = c

        vertex.edges(Direction.BOTH).forEach { edge ->
            val neighbor = if (edge.outVertex().id() == vertex.id()) edge.inVertex() else edge.outVertex()

            when {
                neighbor.id() !in color -> {
                    if (!dfs(neighbor, 1 - c)) return false
                }
                color[neighbor.id()] == color[vertex.id()] -> return false
            }
        }
        return true
    }

    // Check each component
    for (vertex in vertices) {
        if (vertex.id() !in color) {
            if (!dfs(vertex, 0)) {
                return Pair(false, color)
            }
        }
    }

    return Pair(true, color)
}

/**
 * Finds all vertices reachable from the given starting vertex.
 *
 * @param start The starting vertex
 * @param direction The direction to traverse edges (default: Direction.OUT)
 * @return Set of all reachable vertices including the start vertex
 */
fun Graph.reachableVertices(start: Vertex, direction: Direction = Direction.OUT): Set<Vertex> {
    val reachable = mutableSetOf<Vertex>()
    val visited = mutableSetOf<Any?>()
    val stack = ArrayDeque<Vertex>()

    stack.addLast(start)

    while (stack.isNotEmpty()) {
        val current = stack.removeLast()

        if (current.id() !in visited) {
            visited.add(current.id())
            reachable.add(current)

            current.edges(direction).forEach { edge ->
                val neighbor = when (direction) {
                    Direction.OUT -> edge.inVertex()
                    Direction.IN -> edge.outVertex()
                    Direction.BOTH -> if (edge.outVertex().id() == current.id()) edge.inVertex() else edge.outVertex()
                }

                if (neighbor.id() !in visited) {
                    stack.addLast(neighbor)
                }
            }
        }
    }

    return reachable
}

/**
 * Computes the transitive closure of the graph.
 *
 * The transitive closure contains an edge from vertex i to vertex j if there is
 * a directed path from i to j in the original graph.
 *
 * @return Map where each vertex maps to the set of vertices reachable from it
 *
 * @see [Transitive closure on Wikipedia](https://en.wikipedia.org/wiki/Transitive_closure)
 */
fun Graph.transitiveClosure(): Map<Vertex, Set<Vertex>> {
    return vertices().asSequence().associateWith { vertex ->
        reachableVertices(vertex, Direction.OUT) - vertex
    }
}
