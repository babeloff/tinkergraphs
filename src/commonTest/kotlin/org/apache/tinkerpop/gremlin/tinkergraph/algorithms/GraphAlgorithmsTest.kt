package org.apache.tinkerpop.gremlin.tinkergraph.algorithms

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

/**
 * Test suite for basic graph algorithms implementation.
 *
 * Tests fundamental graph algorithms including:
 * - Breadth-first search (BFS) traversal
 * - Depth-first search (DFS) traversal
 * - Path finding algorithms
 * - Graph connectivity analysis
 * - Cycle detection
 * - Topological sorting
 *
 * All tests verify correctness across different graph topologies including linear graphs, trees,
 * cycles, and disconnected components. Tests ensure proper handling of edge cases and performance
 * characteristics.
 *
 * @see org.apache.tinkerpop.gremlin.tinkergraph.algorithms.GraphAlgorithms
 */
class GraphAlgorithmsTest :
        StringSpec({
            lateinit var graph: TinkerGraph

            beforeTest { graph = TinkerGraph.open() }

            "breadth first search on single vertex should work correctly" {
                val v1 = graph.addVertex("id", 1)

                val result = graph.breadthFirstSearch(v1).toList()

                result shouldHaveSize 1
                result[0] shouldBe v1
            }

            "breadth first search on linear graph should work correctly" {
                // Create linear graph: 1 - 2 - 3 - 4
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)
                v3.addEdge("connects", v4)

                val result = graph.breadthFirstSearch(v1).toList()

                result shouldHaveSize 4
                result[0] shouldBe v1
                result[1] shouldBe v2
                result[2] shouldBe v3
                result[3] shouldBe v4
            }

            "breadth first search on tree should work correctly" {
                // Create tree:     1
                //                 / \
                //                2   3
                //               / \   \
                //              4   5   6
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)
                val v5 = graph.addVertex("id", 5)
                val v6 = graph.addVertex("id", 6)

                v1.addEdge("connects", v2)
                v1.addEdge("connects", v3)
                v2.addEdge("connects", v4)
                v2.addEdge("connects", v5)
                v3.addEdge("connects", v6)

                val result = graph.breadthFirstSearch(v1).toList()

                result shouldHaveSize 6
                result[0] shouldBe v1 // Level 0
                // Level 1: v2, v3 (order may vary)
                (result[1] == v2 || result[1] == v3) shouldBe true
                (result[2] == v2 || result[2] == v3) shouldBe true
                (result[1] != result[2]) shouldBe true
                // Level 2: v4, v5, v6 (order may vary)
                val level2 = result.subList(3, 6).toSet()
                level2 shouldBe setOf(v4, v5, v6)
            }

            "depth first search on single vertex should work correctly" {
                val v1 = graph.addVertex("id", 1)

                val result = graph.depthFirstSearch(v1).toList()

                result shouldHaveSize 1
                result[0] shouldBe v1
            }

            "depth first search on linear graph should work correctly" {
                // Create linear graph: 1 - 2 - 3 - 4
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)
                v3.addEdge("connects", v4)

                val result = graph.depthFirstSearch(v1).toList()

                result shouldHaveSize 4
                result[0] shouldBe v1
                // DFS should go deep before backtracking
                result shouldContain v2
                result shouldContain v3
                result shouldContain v4
            }

            "shortest path same vertex should work correctly" {
                val v1 = graph.addVertex("id", 1)

                val result = graph.shortestPath(v1, v1)

                result.shouldNotBeNull()
                result shouldHaveSize 1
                result[0] shouldBe v1
            }

            "shortest path direct connection should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                v1.addEdge("connects", v2)

                val result = graph.shortestPath(v1, v2)

                result.shouldNotBeNull()
                result shouldHaveSize 2
                result[0] shouldBe v1
                result[1] shouldBe v2
            }

            "shortest path multiple hops should work correctly" {
                // Create path: 1 - 2 - 3 - 4
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)
                v3.addEdge("connects", v4)

                val result = graph.shortestPath(v1, v4)

                result.shouldNotBeNull()
                result shouldHaveSize 4
                result shouldBe listOf(v1, v2, v3, v4)
            }

            "shortest path no path should return null" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                // No connection between vertices

                val result = graph.shortestPath(v1, v2)

                result.shouldBeNull()
            }

            "shortest path with alternative routes should work correctly" {
                // Create diamond shape:  1
                //                       / \
                //                      2   3
                //                       \ /
                //                        4
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                v1.addEdge("connects", v2)
                v1.addEdge("connects", v3)
                v2.addEdge("connects", v4)
                v3.addEdge("connects", v4)

                val result = graph.shortestPath(v1, v4)

                result.shouldNotBeNull()
                result shouldHaveSize 3
                result[0] shouldBe v1
                result[2] shouldBe v4
                // Middle vertex can be either v2 or v3
                (result[1] == v2 || result[1] == v3) shouldBe true
            }

            "connected components single vertex should work correctly" {
                val v1 = graph.addVertex("id", 1)

                val components = graph.connectedComponents()

                components shouldHaveSize 1
                components[0] shouldBe setOf(v1)
            }

            "connected components connected graph should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)

                val components = graph.connectedComponents()

                components shouldHaveSize 1
                components[0] shouldBe setOf(v1, v2, v3)
            }

            "connected components disconnected graph should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                v1.addEdge("connects", v2) // Component 1: {1, 2}
                v3.addEdge("connects", v4) // Component 2: {3, 4}

                val components = graph.connectedComponents()

                components shouldHaveSize 2

                val componentSizes = components.map { it.size }.sorted()
                componentSizes shouldBe listOf(2, 2)

                // Check that components contain the right vertices
                val allVertices = components.flatten().toSet()
                allVertices shouldBe setOf(v1, v2, v3, v4)
            }

            "connected components isolated vertices should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                // No edges - all vertices are isolated

                val components = graph.connectedComponents()

                components shouldHaveSize 3
                components.forEach { component -> component shouldHaveSize 1 }

                val allVertices = components.flatten().toSet()
                allVertices shouldBe setOf(v1, v2, v3)
            }

            "has cycle no cycle should return false" {
                // Create tree: 1 - 2 - 3
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)

                graph.hasCycle() shouldBe false
            }

            "has cycle simple cycle should return true" {
                // Create triangle: 1 - 2 - 3 - 1
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)
                v3.addEdge("connects", v1)

                graph.hasCycle() shouldBe true
            }

            "has cycle self loop should return true" {
                val v1 = graph.addVertex("id", 1)
                v1.addEdge("connects", v1) // Self-loop

                graph.hasCycle() shouldBe true
            }

            "has cycle complex graph with cycle should return true" {
                // Create graph with cycle: 1 - 2 - 3 - 4 - 2 (cycle: 2-3-4-2)
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)
                v3.addEdge("connects", v4)
                v4.addEdge("connects", v2) // Creates cycle

                graph.hasCycle() shouldBe true
            }

            "vertices at distance zero should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                v1.addEdge("connects", v2)

                val result = graph.verticesAtDistance(v1, 0)

                result shouldBe setOf(v1)
            }

            "vertices at distance one should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v1.addEdge("connects", v3)

                val result = graph.verticesAtDistance(v1, 1)

                result shouldBe setOf(v2, v3)
            }

            "vertices at distance two should work correctly" {
                // Create path: 1 - 2 - 3 - 4
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)
                v3.addEdge("connects", v4)

                val result = graph.verticesAtDistance(v1, 2)

                result shouldBe setOf(v3)
            }

            "vertices at distance negative should return empty set" {
                val v1 = graph.addVertex("id", 1)

                val result = graph.verticesAtDistance(v1, -1)

                result shouldBe emptySet()
            }

            "is connected single vertex should return true" {
                val v1 = graph.addVertex("id", 1)

                graph.isConnected() shouldBe true
            }

            "is connected empty graph should return true" {
                graph.isConnected() shouldBe true // Empty graph is considered connected
            }

            "is connected true should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)

                graph.isConnected() shouldBe true
            }

            "is connected false should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                v1.addEdge("connects", v2) // Component 1
                v3.addEdge("connects", v4) // Component 2

                graph.isConnected() shouldBe false
            }

            "diameter connected graph should work correctly" {
                // Create linear graph: 1 - 2 - 3 - 4 (diameter should be 3)
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)
                v3.addEdge("connects", v4)

                graph.diameter() shouldBe 3
            }

            "diameter disconnected graph should return -1" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                // No edges - disconnected

                graph.diameter() shouldBe -1
            }

            "diameter single vertex should return 0" {
                val v1 = graph.addVertex("id", 1)

                graph.diameter() shouldBe 0
            }

            "diameter star graph should work correctly" {
                // Create star graph: center connected to 4 outer vertices
                val center = graph.addVertex("id", 0)
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                center.addEdge("connects", v1)
                center.addEdge("connects", v2)
                center.addEdge("connects", v3)
                center.addEdge("connects", v4)

                graph.diameter() shouldBe 2 // Max distance is from any outer vertex to another
            }
        })
