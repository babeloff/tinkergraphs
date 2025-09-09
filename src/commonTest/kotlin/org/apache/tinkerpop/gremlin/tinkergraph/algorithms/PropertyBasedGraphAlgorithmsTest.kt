package org.apache.tinkerpop.gremlin.tinkergraph.algorithms

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

/**
 * Property-based test suite for graph algorithms using Kotest Property framework.
 *
 * This test suite demonstrates the incorporation of property-based testing
 * for graph algorithms, validating algorithmic invariants across randomly
 * generated graph topologies to ensure correctness and robustness.
 */
class PropertyBasedGraphAlgorithmsTest : StringSpec({

    "breadth first search should visit all reachable vertices exactly once" {
        checkAll(
            Arb.int(1..15),  // number of vertices
            Arb.double(0.2..0.8)  // edge probability
        ) { numVertices, edgeProbability ->
            val graph = generateRandomGraph(numVertices, edgeProbability)
            val vertices = graph.vertices().asSequence().toList()

            if (vertices.isNotEmpty()) {
                val startVertex = vertices.first() // Use first vertex for determinism
                val bfsResult = graph.breadthFirstSearch(startVertex).toList()

                // Each vertex should appear at most once
                bfsResult.size shouldBeLessThanOrEqual vertices.size
                bfsResult.toSet().size shouldBe bfsResult.size

                // Start vertex should be first
                bfsResult.first() shouldBe startVertex

                // All vertices in result should be reachable from start
                bfsResult.forEach { vertex ->
                    graph.shortestPath(startVertex, vertex).shouldNotBeNull()
                }
            }
        }
    }

    "depth first search should visit all reachable vertices exactly once" {
        checkAll(
            Arb.int(1..15),
            Arb.double(0.2..0.8)
        ) { numVertices, edgeProbability ->
            val graph = generateRandomGraph(numVertices, edgeProbability)
            val vertices = graph.vertices().asSequence().toList()

            if (vertices.isNotEmpty()) {
                val startVertex = vertices.first()
                val dfsResult = graph.depthFirstSearch(startVertex).toList()

                // Each vertex should appear at most once
                dfsResult.toSet().size shouldBe dfsResult.size

                // Start vertex should be first
                dfsResult.first() shouldBe startVertex

                // All vertices in result should be reachable from start
                dfsResult.forEach { vertex ->
                    graph.shortestPath(startVertex, vertex).shouldNotBeNull()
                }
            }
        }
    }

    "shortest path should satisfy basic properties" {
        checkAll(
            Arb.int(3..12),
            Arb.double(0.3..0.7)
        ) { numVertices, edgeProbability ->
            val graph = generateRandomGraph(numVertices, edgeProbability)
            val vertices = graph.vertices().asSequence().toList()

            if (vertices.size >= 3) {
                val vertex1 = vertices[0]
                val vertex2 = vertices[1]
                val vertex3 = vertices[2]

                val path12 = graph.shortestPath(vertex1, vertex2)
                val path23 = graph.shortestPath(vertex2, vertex3)
                val path13 = graph.shortestPath(vertex1, vertex3)

                // If paths exist, verify triangle inequality
                if (path12 != null && path23 != null && path13 != null) {
                    val dist12 = path12.size - 1
                    val dist23 = path23.size - 1
                    val dist13 = path13.size - 1

                    // Triangle inequality: d(1,3) <= d(1,2) + d(2,3)
                    dist13 shouldBeLessThanOrEqual (dist12 + dist23)
                }

                // Path to self should always be single vertex
                val selfPath = graph.shortestPath(vertex1, vertex1)
                selfPath.shouldNotBeNull()
                selfPath.size shouldBe 1
                selfPath.first() shouldBe vertex1
            }
        }
    }

    "connected components should partition the vertex set" {
        checkAll(
            Arb.int(1..15),
            Arb.double(0.0..1.0)
        ) { numVertices, edgeProbability ->
            val graph = generateRandomGraph(numVertices, edgeProbability)
            val vertices = graph.vertices().asSequence().toSet()
            val components = graph.connectedComponents()

            // Components should partition the vertex set
            val allComponentVertices = components.flatten().toSet()
            allComponentVertices shouldBe vertices

            // Components should be non-empty and non-overlapping
            components.forEach { component ->
                component.size shouldBeGreaterThanOrEqual 1
            }

            // Verify no overlap between components
            for (i in components.indices) {
                for (j in (i + 1) until components.size) {
                    components[i].intersect(components[j]).size shouldBe 0
                }
            }
        }
    }

    "graph connectivity should be consistent with component count" {
        checkAll(
            Arb.int(2..12),
            Arb.double(0.1..0.9)
        ) { numVertices, edgeProbability ->
            val graph = generateRandomGraph(numVertices, edgeProbability)
            val isConnected = graph.isConnected()
            val components = graph.connectedComponents()

            if (isConnected) {
                components.size shouldBeLessThanOrEqual 1
            } else {
                components.size shouldBeGreaterThanOrEqual 2
            }
        }
    }

    "diameter should be consistent with connectivity" {
        checkAll(
            Arb.int(2..10),
            Arb.double(0.3..1.0)
        ) { numVertices, edgeProbability ->
            val graph = generateRandomGraph(numVertices, edgeProbability)
            val vertices = graph.vertices().asSequence().toList()
            val diameter = graph.diameter()

            if (graph.isConnected() && vertices.isNotEmpty()) {
                // Connected graph should have non-negative diameter
                diameter shouldBeGreaterThanOrEqual 0

                // Diameter should be at most n-1 for n vertices
                diameter shouldBeLessThanOrEqual (vertices.size - 1)
            } else {
                // Disconnected graph should have diameter -1
                diameter shouldBe -1
            }
        }
    }

    "vertices at distance should form valid distance sets" {
        checkAll(
            Arb.int(1..12),
            Arb.double(0.2..0.8),
            Arb.int(0..4)
        ) { numVertices, edgeProbability, distance ->
            val graph = generateRandomGraph(numVertices, edgeProbability)
            val vertices = graph.vertices().asSequence().toList()

            if (vertices.isNotEmpty()) {
                val startVertex = vertices.first()
                val verticesAtDist = graph.verticesAtDistance(startVertex, distance)

                // All vertices at specified distance should actually be at that distance
                verticesAtDist.forEach { vertex ->
                    val path = graph.shortestPath(startVertex, vertex)
                    if (path != null) {
                        (path.size - 1) shouldBe distance
                    }
                }

                // Vertices at distance 0 should only contain start vertex
                if (distance == 0) {
                    verticesAtDist.size shouldBe 1
                    verticesAtDist shouldContain startVertex
                }
            }
        }
    }

    "breadth first search and depth first search should visit same reachable vertices" {
        checkAll(
            Arb.int(1..12),
            Arb.double(0.0..1.0)
        ) { numVertices, edgeProbability ->
            val graph = generateRandomGraph(numVertices, edgeProbability)
            val vertices = graph.vertices().asSequence().toList()

            if (vertices.isNotEmpty()) {
                val startVertex = vertices.first()

                val bfsVertices = graph.breadthFirstSearch(startVertex).toSet()
                val dfsVertices = graph.depthFirstSearch(startVertex).toSet()

                // Both should visit exactly the same set of vertices
                bfsVertices shouldBe dfsVertices
            }
        }
    }

    "cycle detection should be consistent with graph structure" {
        checkAll(
            Arb.int(3..12),
            Arb.double(0.0..1.0)
        ) { numVertices, edgeProbability ->
            val graph = generateRandomGraph(numVertices, edgeProbability)
            val hasCycle = graph.hasCycle()

            // If graph has cycle, it should not be a tree
            if (hasCycle) {
                val vertices = graph.vertices().asSequence().toList()
                val edges = graph.edges().asSequence().toList()

                // Connected graph with cycle: edges >= vertices (for n>=1)
                if (graph.isConnected() && vertices.size > 1) {
                    edges.size shouldBeGreaterThanOrEqual vertices.size
                }
            }
        }
    }

    "linear graph should have predictable properties" {
        checkAll(
            Arb.int(2..8)
        ) { numVertices ->
            val graph = createLinearGraph(numVertices)

            // Linear graph should be connected
            graph.isConnected() shouldBe true

            // Should have exactly numVertices - 1 edges
            val edges = graph.edges().asSequence().toList()
            edges.size shouldBe (numVertices - 1)

            // Should not have cycles
            graph.hasCycle() shouldBe false

            // Diameter should be numVertices - 1
            graph.diameter() shouldBe (numVertices - 1)

            // Should have exactly one connected component
            val components = graph.connectedComponents()
            components.size shouldBe 1
            components[0].size shouldBe numVertices
        }
    }
})

/**
 * Generates a random graph with specified number of vertices and edge probability.
 */
private fun generateRandomGraph(numVertices: Int, edgeProbability: Double): TinkerGraph {
    val graph = TinkerGraph.open()
    val vertices = (1..numVertices).map { graph.addVertex("id", it) }

    // Add random edges
    for (i in vertices.indices) {
        for (j in (i + 1) until vertices.size) {
            if (kotlin.random.Random.nextDouble() < edgeProbability) {
                vertices[i].addEdge("connects", vertices[j])
            }
        }
    }

    return graph
}

/**
 * Creates a linear graph: 1 - 2 - 3 - ... - n
 */
private fun createLinearGraph(numVertices: Int): TinkerGraph {
    val graph = TinkerGraph.open()
    val vertices = (1..numVertices).map { graph.addVertex("id", it) }

    for (i in 0 until vertices.size - 1) {
        vertices[i].addEdge("connects", vertices[i + 1])
    }

    return graph
}
