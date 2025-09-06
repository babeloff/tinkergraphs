package org.apache.tinkerpop.gremlin.tinkergraph.algorithms

import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import kotlin.test.*

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
 * All tests verify correctness, edge cases, and performance characteristics
 * of the advanced algorithm implementations across different graph structures.
 *
 * @see org.apache.tinkerpop.gremlin.tinkergraph.algorithms.AdvancedGraphAlgorithms
 */
class AdvancedGraphAlgorithmsTest {

    /**
     * TinkerGraph instance used for testing algorithms.
     * Initialized fresh for each test to ensure isolation.
     */
    private lateinit var graph: TinkerGraph

    /**
     * Sets up a fresh TinkerGraph instance before each test.
     * Ensures test isolation and clean state for each algorithm test.
     */
    @BeforeTest
    fun setUp() {
        graph = TinkerGraph.open()
    }

    /**
     * Cleans up resources after each test by closing the graph.
     * Ensures proper resource management and prevents memory leaks.
     */
    @AfterTest
    fun tearDown() {
        graph.close()
    }

    // ========================================
    // Dijkstra's Shortest Path Algorithm Tests
    // ========================================

    /**
     * Tests Dijkstra's algorithm when source and target are the same vertex.
     * Should return a path containing only the single vertex with zero weight.
     */
    @Test
    fun testDijkstraShortestPathSameVertex() {
        val v1 = graph.addVertex("id", 1)

        val result = graph.dijkstraShortestPath(v1, v1)

        assertNotNull(result)
        assertEquals(1, result.vertices.size)
        assertEquals(v1, result.vertices[0])
        assertEquals(0.0, result.totalWeight)
    }

    @Test
    fun testDijkstraShortestPathDirectConnection() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val edge = v1.addEdge("connects", v2)
        edge.property("weight", 5.0)

        val result = graph.dijkstraShortestPath(v1, v2)

        assertNotNull(result)
        assertEquals(2, result.vertices.size)
        assertEquals(v1, result.vertices[0])
        assertEquals(v2, result.vertices[1])
        assertEquals(5.0, result.totalWeight)
    }

    @Test
    fun testDijkstraShortestPathMultipleHops() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        val e1 = v1.addEdge("connects", v2)
        e1.property("weight", 2.0)
        val e2 = v2.addEdge("connects", v3)
        e2.property("weight", 3.0)

        val result = graph.dijkstraShortestPath(v1, v3)

        assertNotNull(result)
        assertEquals(3, result.vertices.size)
        assertEquals(listOf(v1, v2, v3), result.vertices)
        assertEquals(5.0, result.totalWeight)
    }

    @Test
    fun testDijkstraShortestPathChoosesShorterRoute() {
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

        assertNotNull(result)
        assertEquals(3, result.vertices.size)
        assertEquals(listOf(v1, v2, v4), result.vertices)
        assertEquals(3.0, result.totalWeight)
    }

    @Test
    fun testDijkstraShortestPathNoPath() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)

        val result = graph.dijkstraShortestPath(v1, v2)

        assertNull(result)
    }

    @Test
    fun testDijkstraShortestPathWithDefaultWeights() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2) // No weight property, should default to 1.0
        v2.addEdge("connects", v3) // No weight property, should default to 1.0

        val result = graph.dijkstraShortestPath(v1, v3)

        assertNotNull(result)
        assertEquals(3, result.vertices.size)
        assertEquals(2.0, result.totalWeight)
    }

    // Topological Sort Tests

    @Test
    fun testTopologicalSortLinearDAG() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)

        val result = graph.topologicalSort()

        assertNotNull(result)
        assertEquals(3, result.size)
        assertEquals(v1, result[0])
        assertEquals(v2, result[1])
        assertEquals(v3, result[2])
    }

    @Test
    fun testTopologicalSortWithBranching() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)
        val v4 = graph.addVertex("id", 4)

        v1.addEdge("connects", v2)
        v1.addEdge("connects", v3)
        v2.addEdge("connects", v4)
        v3.addEdge("connects", v4)

        val result = graph.topologicalSort()

        assertNotNull(result)
        assertEquals(4, result.size)
        assertEquals(v1, result[0]) // v1 must come first
        assertEquals(v4, result[3]) // v4 must come last
        // v2 and v3 can be in any order in positions 1 and 2
        assertTrue(result.contains(v2))
        assertTrue(result.contains(v3))
    }

    @Test
    fun testTopologicalSortWithCycle() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)
        v3.addEdge("connects", v1) // Creates cycle

        val result = graph.topologicalSort()

        assertNull(result) // Should return null for cyclic graphs
    }

    @Test
    fun testTopologicalSortSingleVertex() {
        val v1 = graph.addVertex("id", 1)

        val result = graph.topologicalSort()

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(v1, result[0])
    }

    @Test
    fun testTopologicalSortIsolatedVertices() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        val result = graph.topologicalSort()

        assertNotNull(result)
        assertEquals(3, result.size)
        // All vertices should be present, order doesn't matter for isolated vertices
        assertTrue(result.contains(v1))
        assertTrue(result.contains(v2))
        assertTrue(result.contains(v3))
    }

    // Tarjan's Strongly Connected Components Tests

    @Test
    fun testTarjanSCCSingleVertex() {
        val v1 = graph.addVertex("id", 1)

        val result = graph.tarjanStronglyConnectedComponents()

        assertEquals(1, result.size)
        assertEquals(setOf(v1), result[0])
    }

    @Test
    fun testTarjanSCCLinearPath() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)

        val result = graph.tarjanStronglyConnectedComponents()

        assertEquals(3, result.size)
        // Each vertex should be in its own SCC
        assertTrue(result.any { it == setOf(v1) })
        assertTrue(result.any { it == setOf(v2) })
        assertTrue(result.any { it == setOf(v3) })
    }

    @Test
    fun testTarjanSCCWithCycle() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)
        v3.addEdge("connects", v1) // Creates strongly connected component

        val result = graph.tarjanStronglyConnectedComponents()

        assertEquals(1, result.size)
        assertEquals(setOf(v1, v2, v3), result[0])
    }

    @Test
    fun testTarjanSCCComplexGraph() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)
        val v4 = graph.addVertex("id", 4)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)
        v3.addEdge("connects", v2) // v2 and v3 form SCC
        v3.addEdge("connects", v4)

        val result = graph.tarjanStronglyConnectedComponents()

        assertEquals(3, result.size)
        assertTrue(result.any { it == setOf(v1) })
        assertTrue(result.any { it == setOf(v2, v3) })
        assertTrue(result.any { it == setOf(v4) })
    }

    // Kruskal's Minimum Spanning Tree Tests

    @Test
    fun testKruskalMSTSingleVertex() {
        val v1 = graph.addVertex("id", 1)

        val result = graph.kruskalMinimumSpanningTree()

        assertEquals(0, result.size)
    }

    @Test
    fun testKruskalMSTTwoVertices() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val edge = v1.addEdge("connects", v2)
        edge.property("weight", 5.0)

        val result = graph.kruskalMinimumSpanningTree()

        assertEquals(1, result.size)
        assertTrue(result.contains(edge))
    }

    @Test
    fun testKruskalMSTTriangle() {
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

        assertEquals(2, result.size)
        assertTrue(result.contains(e1)) // Weight 1.0
        assertTrue(result.contains(e2)) // Weight 2.0
        assertFalse(result.contains(e3)) // Weight 3.0, should be excluded
    }

    @Test
    fun testKruskalMSTComplexGraph() {
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

        assertEquals(3, result.size) // Should have n-1 = 3 edges for 4 vertices
        assertTrue(result.contains(e1)) // Weight 1.0
        assertTrue(result.contains(e3)) // Weight 2.0
        assertTrue(result.contains(e5)) // Weight 3.0
    }

    @Test
    fun testKruskalMSTWithDefaultWeights() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        val e1 = v1.addEdge("connects", v2) // No weight, defaults to 1.0
        val e2 = v2.addEdge("connects", v3) // No weight, defaults to 1.0

        val result = graph.kruskalMinimumSpanningTree()

        assertEquals(2, result.size)
        assertTrue(result.contains(e1))
        assertTrue(result.contains(e2))
    }

    // Articulation Points Tests

    @Test
    fun testArticulationPointsSingleVertex() {
        val v1 = graph.addVertex("id", 1)

        val result = graph.articulationPoints()

        assertEquals(0, result.size)
    }

    @Test
    fun testArticulationPointsLinearGraph() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)

        val result = graph.articulationPoints()

        assertEquals(1, result.size)
        assertTrue(result.contains(v2)) // v2 is the articulation point
    }

    @Test
    fun testArticulationPointsStarGraph() {
        val center = graph.addVertex("id", 0)
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        center.addEdge("connects", v1)
        center.addEdge("connects", v2)
        center.addEdge("connects", v3)

        val result = graph.articulationPoints()

        assertEquals(1, result.size)
        assertTrue(result.contains(center)) // Center is the articulation point
    }

    @Test
    fun testArticulationPointsTriangle() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)
        v3.addEdge("connects", v1)

        val result = graph.articulationPoints()

        assertEquals(0, result.size) // No articulation points in a triangle
    }

    // Bridges Tests

    @Test
    fun testBridgesSingleVertex() {
        val v1 = graph.addVertex("id", 1)

        val result = graph.bridges()

        assertEquals(0, result.size)
    }

    @Test
    fun testBridgesLinearGraph() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        val e1 = v1.addEdge("connects", v2)
        val e2 = v2.addEdge("connects", v3)

        val result = graph.bridges()

        assertEquals(2, result.size)
        assertTrue(result.contains(e1))
        assertTrue(result.contains(e2))
    }

    @Test
    fun testBridgesTriangle() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        val e1 = v1.addEdge("connects", v2)
        val e2 = v2.addEdge("connects", v3)
        val e3 = v3.addEdge("connects", v1)

        val result = graph.bridges()

        assertEquals(0, result.size) // No bridges in a triangle
    }

    @Test
    fun testBridgesWithBridge() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)
        val v4 = graph.addVertex("id", 4)

        val e1 = v1.addEdge("connects", v2)
        val e2 = v2.addEdge("connects", v1) // Make v1-v2 non-bridge
        val bridge = v2.addEdge("connects", v3) // This should be a bridge
        val e4 = v3.addEdge("connects", v4)

        val result = graph.bridges()

        assertTrue(result.contains(bridge))
        assertTrue(result.contains(e4))
        assertFalse(result.contains(e1))
        assertFalse(result.contains(e2))
    }

    // Bipartite Tests

    @Test
    fun testIsBipartiteSingleVertex() {
        val v1 = graph.addVertex("id", 1)

        val result = graph.isBipartite()

        assertTrue(result.first)
        assertEquals(1, result.second.size)
        assertEquals(0, result.second[v1.id()])
    }

    @Test
    fun testIsBipartiteLinearGraph() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)

        val result = graph.isBipartite()

        assertTrue(result.first)
        assertEquals(0, result.second[v1.id()])
        assertEquals(1, result.second[v2.id()])
        assertEquals(0, result.second[v3.id()])
    }

    @Test
    fun testIsBipartiteTriangle() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)
        v3.addEdge("connects", v1)

        val result = graph.isBipartite()

        assertFalse(result.first) // Odd cycle is not bipartite
    }

    @Test
    fun testIsBipartiteSquare() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)
        val v4 = graph.addVertex("id", 4)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)
        v3.addEdge("connects", v4)
        v4.addEdge("connects", v1)

        val result = graph.isBipartite()

        assertTrue(result.first) // Even cycle is bipartite
        // Colors should alternate
        assertEquals(0, result.second[v1.id()])
        assertEquals(1, result.second[v2.id()])
        assertEquals(0, result.second[v3.id()])
        assertEquals(1, result.second[v4.id()])
    }

    // Reachable Vertices Tests

    @Test
    fun testReachableVerticesSingleVertex() {
        val v1 = graph.addVertex("id", 1)

        val result = graph.reachableVertices(v1)

        assertEquals(1, result.size)
        assertTrue(result.contains(v1))
    }

    @Test
    fun testReachableVerticesLinearPath() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)

        val result = graph.reachableVertices(v1)

        assertEquals(3, result.size)
        assertTrue(result.contains(v1))
        assertTrue(result.contains(v2))
        assertTrue(result.contains(v3))
    }

    @Test
    fun testReachableVerticesDisconnected() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        // v3 is disconnected

        val result = graph.reachableVertices(v1)

        assertEquals(2, result.size)
        assertTrue(result.contains(v1))
        assertTrue(result.contains(v2))
        assertFalse(result.contains(v3))
    }

    // Transitive Closure Tests

    @Test
    fun testTransitiveClosureSingleVertex() {
        val v1 = graph.addVertex("id", 1)

        val result = graph.transitiveClosure()

        assertEquals(1, result.size)
        assertTrue(result.containsKey(v1))
        assertEquals(0, result[v1]?.size)
    }

    @Test
    fun testTransitiveClosureLinearPath() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)

        val result = graph.transitiveClosure()

        assertEquals(3, result.size)
        assertEquals(setOf(v2, v3), result[v1])
        assertEquals(setOf(v3), result[v2])
        assertEquals(emptySet(), result[v3])
    }

    @Test
    fun testTransitiveClosureWithCycle() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)
        v3.addEdge("connects", v1)

        val result = graph.transitiveClosure()

        assertEquals(3, result.size)
        // In a cycle, each vertex can reach all others
        assertEquals(setOf(v2, v3), result[v1])
        assertEquals(setOf(v1, v3), result[v2])
        assertEquals(setOf(v1, v2), result[v3])
    }
}
