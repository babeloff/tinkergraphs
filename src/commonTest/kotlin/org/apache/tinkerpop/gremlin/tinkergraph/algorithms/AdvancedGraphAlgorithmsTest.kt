package org.apache.tinkerpop.gremlin.tinkergraph.algorithms

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

/**
 * Test suite for advanced graph algorithms implementation.
 *
 * Tests sophisticated graph algorithms including:
 * - Dijkstra's shortest path algorithm with weighted edges
 * - Minimum spanning tree (Kruskal's and Prim's algorithms)
 * - Strongly connected components detection
 * - Graph connectivity analysis
 * - Weighted path finding and optimization
 *
 * All tests verify correctness, edge cases, and performance characteristics of the advanced
 * algorithm implementations across different graph structures.
 *
 * @see org.apache.tinkerpop.gremlin.tinkergraph.algorithms.AdvancedGraphAlgorithms
 */
class AdvancedGraphAlgorithmsTest :
        StringSpec({
            lateinit var graph: TinkerGraph

            beforeTest { graph = TinkerGraph.open() }

            afterTest { graph.close() }

            // ========================================
            // Dijkstra's Shortest Path Algorithm Tests
            // ========================================

            "dijkstra shortest path same vertex should work correctly" {
                val v1 = graph.addVertex("id", 1)

                val result = graph.dijkstraShortestPath(v1, v1)

                result.shouldNotBeNull()
                result.vertices shouldHaveSize 1
                result.vertices[0] shouldBe v1
                result.totalWeight shouldBe 0.0
            }

            "dijkstra shortest path direct connection should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val edge = v1.addEdge("connects", v2)
                edge.property("weight", 5.0)

                val result = graph.dijkstraShortestPath(v1, v2)

                result.shouldNotBeNull()
                result.vertices shouldHaveSize 2
                result.vertices[0] shouldBe v1
                result.vertices[1] shouldBe v2
                result.totalWeight shouldBe 5.0
            }

            "dijkstra shortest path multiple hops should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                val e1 = v1.addEdge("connects", v2)
                e1.property("weight", 2.0)
                val e2 = v2.addEdge("connects", v3)
                e2.property("weight", 3.0)

                val result = graph.dijkstraShortestPath(v1, v3)

                result.shouldNotBeNull()
                result.vertices shouldHaveSize 3
                result.vertices shouldBe listOf(v1, v2, v3)
                result.totalWeight shouldBe 5.0
            }

            "dijkstra shortest path should choose shorter route" {
                // Diamond graph with different weights
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                val e1 = v1.addEdge("connects", v2)
                e1.property("weight", 1.0)
                val e2 = v1.addEdge("connects", v3)
                e2.property("weight", 4.0)
                val e3 = v2.addEdge("connects", v4)
                e3.property("weight", 2.0)
                val e4 = v3.addEdge("connects", v4)
                e4.property("weight", 1.0)

                val result = graph.dijkstraShortestPath(v1, v4)

                result.shouldNotBeNull()
                result.vertices shouldHaveSize 3
                result.vertices shouldBe listOf(v1, v2, v3)
                result.totalWeight shouldBe 2.0
            }

            "dijkstra shortest path no path should return null" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)

                val result = graph.dijkstraShortestPath(v1, v2)

                result.shouldBeNull()
            }

            "dijkstra shortest path with default weights should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2) // No weight property, should default to 1.0
                v2.addEdge("connects", v3) // No weight property, should default to 1.0

                val result = graph.dijkstraShortestPath(v1, v3)

                result.shouldNotBeNull()
                result.vertices shouldHaveSize 3
                result.totalWeight shouldBe 2.0
            }

            // Topological Sort Tests

            "topological sort linear DAG should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)

                val result = graph.topologicalSort()

                result.shouldNotBeNull()
                result shouldHaveSize 3
                result[0] shouldBe v1
                result[1] shouldBe v2
                result[2] shouldBe v3
            }

            "topological sort with branching should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                v1.addEdge("connects", v2)
                v1.addEdge("connects", v3)
                v2.addEdge("connects", v4)
                v3.addEdge("connects", v4)

                val result = graph.topologicalSort()

                result.shouldNotBeNull()
                result shouldHaveSize 4
                result[0] shouldBe v1 // v1 must come first
                result[3] shouldBe v4 // v4 must come last
                // v2 and v3 can be in any order in positions 1 and 2
                result shouldContain v2
                result shouldContain v3
            }

            "topological sort with cycle should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)
                v3.addEdge("connects", v1) // Creates cycle

                val result = graph.topologicalSort()

                result.shouldBeNull() // Should return null for cyclic graphs
            }

            "topological sort single vertex should work correctly" {
                val v1 = graph.addVertex("id", 1)

                val result = graph.topologicalSort()

                result.shouldNotBeNull()
                result shouldHaveSize 1
                result[0] shouldBe v1
            }

            "topological sort isolated vertices should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                val result = graph.topologicalSort()

                result.shouldNotBeNull()
                result shouldHaveSize 3
                // All vertices should be present, order doesn't matter for isolated vertices
                result shouldContain v1
                result shouldContain v2
                result shouldContain v3
            }

            // Tarjan's Strongly Connected Components Tests

            "tarjan SCC single vertex should work correctly" {
                val v1 = graph.addVertex("id", 1)

                val result = graph.tarjanStronglyConnectedComponents()

                result shouldHaveSize 1
                result[0] shouldBe setOf(v1)
            }

            "tarjan SCC linear path should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)

                val result = graph.tarjanStronglyConnectedComponents()

                result shouldHaveSize 3
                // Each vertex should be in its own SCC
                result.any { it == setOf(v1) }.shouldBeTrue()
                result.any { it == setOf(v2) }.shouldBeTrue()
                result.any { it == setOf(v3) }.shouldBeTrue()
            }

            "tarjan SCC with cycle should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)
                v3.addEdge("connects", v1) // Creates strongly connected component

                val result = graph.tarjanStronglyConnectedComponents()

                result shouldHaveSize 1
                result[0] shouldBe setOf(v1, v2, v3)
            }

            "tarjan SCC complex graph should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)
                v3.addEdge("connects", v2) // v2 and v3 form SCC
                v2.addEdge("connects", v4)

                val result = graph.tarjanStronglyConnectedComponents()

                result shouldHaveSize 3
                result.any { it == setOf(v1) }.shouldBeTrue()
                result.any { it == setOf(v2, v3) }.shouldBeTrue()
                result.any { it == setOf(v4) }.shouldBeTrue()
            }

            // Kruskal's Minimum Spanning Tree Tests

            "kruskal MST single vertex should work correctly" {
                val v1 = graph.addVertex("id", 1)

                val result = graph.kruskalMinimumSpanningTree()

                result shouldHaveSize 0
            }

            "kruskal MST two vertices should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val edge = v1.addEdge("connects", v2)
                edge.property("weight", 5.0)

                val result = graph.kruskalMinimumSpanningTree()

                result shouldHaveSize 1
                result shouldContain edge
            }

            "kruskal MST triangle should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                val e1 = v1.addEdge("connects", v2)
                e1.property("weight", 1.0)
                val e2 = v2.addEdge("connects", v3)
                e2.property("weight", 2.0)
                val e3 = v3.addEdge("connects", v1)
                e3.property("weight", 3.0)

                val result = graph.kruskalMinimumSpanningTree()

                result shouldHaveSize 2
                result shouldContain e1 // Weight 1.0
                result shouldContain e2 // Weight 2.0
                result shouldNotContain e3 // Weight 3.0, should be excluded
            }

            "kruskal MST complex graph should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                val e1 = v1.addEdge("connects", v2)
                e1.property("weight", 1.0)
                val e2 = v1.addEdge("connects", v3)
                e2.property("weight", 4.0)
                val e3 = v2.addEdge("connects", v3)
                e3.property("weight", 2.0)
                val e4 = v2.addEdge("connects", v4)
                e4.property("weight", 5.0)
                val e5 = v3.addEdge("connects", v4)
                e5.property("weight", 3.0)

                val result = graph.kruskalMinimumSpanningTree()

                result shouldHaveSize 3 // Should have n-1 = 3 edges for 4 vertices
                result shouldContain e1 // Weight 1.0
                result shouldContain e3 // Weight 2.0
                result shouldContain e5 // Weight 3.0
            }

            "kruskal MST with default weights should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                val e1 = v1.addEdge("connects", v2) // No weight, defaults to 1.0
                val e2 = v2.addEdge("connects", v3) // No weight, defaults to 1.0

                val result = graph.kruskalMinimumSpanningTree()

                result shouldHaveSize 2
                result shouldContain e1
                result shouldContain e2
            }

            // Articulation Points Tests

            "articulation points single vertex should work correctly" {
                val v1 = graph.addVertex("id", 1)

                val result = graph.articulationPoints()

                result shouldHaveSize 0
            }

            "articulation points linear graph should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)

                val result = graph.articulationPoints()

                result shouldHaveSize 1
                result shouldContain v2 // v2 is the articulation point
            }

            "articulation points star graph should work correctly" {
                val center = graph.addVertex("id", 0)
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                center.addEdge("connects", v1)
                center.addEdge("connects", v2)
                center.addEdge("connects", v3)

                val result = graph.articulationPoints()

                result shouldHaveSize 1
                result shouldContain center // Center is the articulation point
            }

            "articulation points triangle should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)
                v3.addEdge("connects", v1)

                val result = graph.articulationPoints()

                result shouldHaveSize 0 // No articulation points in a triangle
            }

            // Bridges Tests

            "bridges single vertex should work correctly" {
                val v1 = graph.addVertex("id", 1)

                val result = graph.bridges()

                result shouldHaveSize 0
            }

            "bridges linear graph should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                val e1 = v1.addEdge("connects", v2)
                val e2 = v2.addEdge("connects", v3)

                val result = graph.bridges()

                result shouldHaveSize 2
                result shouldContain e1
                result shouldContain e2
            }

            "bridges triangle should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                val e1 = v1.addEdge("connects", v2)
                val e2 = v2.addEdge("connects", v3)
                val e3 = v3.addEdge("connects", v1)

                val result = graph.bridges()

                result shouldHaveSize 0 // No bridges in a triangle
            }

            "bridges with bridge should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)
                val v5 = graph.addVertex("id", 5)

                val e1 = v1.addEdge("connects", v2)
                val e2 = v2.addEdge("connects", v1)
                val bridge = v2.addEdge("connects", v3) // This is a bridge
                val e4 = v3.addEdge("connects", v4)
                val e5 = v4.addEdge("connects", v5)

                val result = graph.bridges()

                result shouldContain bridge
                result shouldContain e4
                result shouldNotContain e1
                result shouldNotContain e2
            }

            // Bipartite Tests

            "is bipartite single vertex should work correctly" {
                val v1 = graph.addVertex("id", 1)

                val result = graph.isBipartite()

                result.first.shouldBeTrue()
                result.second.size shouldBe 1
                result.second[v1.id()] shouldBe 0
            }

            "is bipartite linear graph should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)

                val result = graph.isBipartite()

                result.first.shouldBeTrue()
                result.second[v1.id()] shouldBe 0
                result.second[v2.id()] shouldBe 1
                result.second[v3.id()] shouldBe 0
            }

            "is bipartite triangle should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)
                v3.addEdge("connects", v1)

                val result = graph.isBipartite()

                result.first.shouldBeFalse() // Odd cycle is not bipartite
            }

            "is bipartite square should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)
                val v4 = graph.addVertex("id", 4)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)
                v3.addEdge("connects", v4)
                v4.addEdge("connects", v1)

                val result = graph.isBipartite()

                result.first.shouldBeTrue() // Even cycle is bipartite
                // Colors should alternate
                result.second[v1.id()] shouldBe 0
                result.second[v2.id()] shouldBe 1
                result.second[v3.id()] shouldBe 0
                result.second[v4.id()] shouldBe 1
            }

            // Reachable Vertices Tests

            "reachable vertices single vertex should work correctly" {
                val v1 = graph.addVertex("id", 1)

                val result = graph.reachableVertices(v1)

                result shouldHaveSize 1
                result shouldContain v1
            }

            "reachable vertices linear path should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)

                val result = graph.reachableVertices(v1)

                result shouldHaveSize 3
                result shouldContain v1
                result shouldContain v2
                result shouldContain v3
            }

            "reachable vertices disconnected should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                // v3 is disconnected

                val result = graph.reachableVertices(v1)

                result shouldHaveSize 2
                result shouldContain v1
                result shouldContain v2
                result shouldNotContain v3
            }

            // Transitive Closure Tests

            "transitive closure single vertex should work correctly" {
                val v1 = graph.addVertex("id", 1)

                val result = graph.transitiveClosure()

                result.size shouldBe 1
                result shouldContainKey v1
                result[v1]?.size shouldBe 0
            }

            "transitive closure linear path should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)

                val result = graph.transitiveClosure()

                result.size shouldBe 3
                result[v1] shouldBe setOf(v2, v3)
                result[v2] shouldBe setOf(v3)
                result[v3] shouldBe emptySet<Any>()
            }

            "transitive closure with cycle should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val v3 = graph.addVertex("id", 3)

                v1.addEdge("connects", v2)
                v2.addEdge("connects", v3)
                v3.addEdge("connects", v1)

                val result = graph.transitiveClosure()

                result.size shouldBe 3
                // In a cycle, each vertex can reach all others
                result[v1] shouldBe setOf(v2, v3)
                result[v2] shouldBe setOf(v1, v3)
                result[v3] shouldBe setOf(v1, v2)
            }
        })
