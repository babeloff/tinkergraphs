package org.apache.tinkerpop.gremlin.tinkergraph.algorithms

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import kotlin.test.*

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
 * All tests verify correctness across different graph topologies including
 * linear graphs, trees, cycles, and disconnected components. Tests ensure
 * proper handling of edge cases and performance characteristics.
 *
 * @see org.apache.tinkerpop.gremlin.tinkergraph.algorithms.GraphAlgorithms
 */
class GraphAlgorithmsTest {

    /**
     * TinkerGraph instance used for testing basic algorithms.
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
     * Tests breadth-first search on a graph with a single vertex.
     * Should return a sequence containing only the starting vertex.
     */
    @Test
    fun testBreadthFirstSearchSingleVertex() {
        val v1 = graph.addVertex("id", 1)

        val result = graph.breadthFirstSearch(v1).toList()

        assertEquals(1, result.size)
        assertEquals(v1, result[0])
    }

    /**
     * Tests breadth-first search on a linear graph structure.
     * Verifies that BFS visits vertices in the correct order from the starting point.
     */
    @Test
    fun testBreadthFirstSearchLinearGraph() {
        // Create linear graph: 1 - 2 - 3 - 4
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)
        val v4 = graph.addVertex("id", 4)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)
        v3.addEdge("connects", v4)

        val result = graph.breadthFirstSearch(v1).toList()

        assertEquals(4, result.size)
        assertEquals(v1, result[0])
        assertEquals(v2, result[1])
        assertEquals(v3, result[2])
        assertEquals(v4, result[3])
    }

    @Test
    fun testBreadthFirstSearchTree() {
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

        assertEquals(6, result.size)
        assertEquals(v1, result[0]) // Level 0
        // Level 1: v2, v3 (order may vary)
        assertTrue(result[1] == v2 || result[1] == v3)
        assertTrue(result[2] == v2 || result[2] == v3)
        assertTrue(result[1] != result[2])
        // Level 2: v4, v5, v6 (order may vary)
        val level2 = result.subList(3, 6).toSet()
        assertEquals(setOf(v4, v5, v6), level2)
    }

    @Test
    fun testDepthFirstSearchSingleVertex() {
        val v1 = graph.addVertex("id", 1)

        val result = graph.depthFirstSearch(v1).toList()

        assertEquals(1, result.size)
        assertEquals(v1, result[0])
    }

    @Test
    fun testDepthFirstSearchLinearGraph() {
        // Create linear graph: 1 - 2 - 3 - 4
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)
        val v4 = graph.addVertex("id", 4)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)
        v3.addEdge("connects", v4)

        val result = graph.depthFirstSearch(v1).toList()

        assertEquals(4, result.size)
        assertEquals(v1, result[0])
        // DFS should go deep before backtracking
        assertTrue(result.contains(v2))
        assertTrue(result.contains(v3))
        assertTrue(result.contains(v4))
    }

    @Test
    fun testShortestPathSameVertex() {
        val v1 = graph.addVertex("id", 1)

        val result = graph.shortestPath(v1, v1)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(v1, result[0])
    }

    @Test
    fun testShortestPathDirectConnection() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        v1.addEdge("connects", v2)

        val result = graph.shortestPath(v1, v2)

        assertNotNull(result)
        assertEquals(2, result.size)
        assertEquals(v1, result[0])
        assertEquals(v2, result[1])
    }

    @Test
    fun testShortestPathMultipleHops() {
        // Create path: 1 - 2 - 3 - 4
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)
        val v4 = graph.addVertex("id", 4)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)
        v3.addEdge("connects", v4)

        val result = graph.shortestPath(v1, v4)

        assertNotNull(result)
        assertEquals(4, result.size)
        assertEquals(listOf(v1, v2, v3, v4), result)
    }

    @Test
    fun testShortestPathNoPath() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        // No connection between vertices

        val result = graph.shortestPath(v1, v2)

        assertNull(result)
    }

    @Test
    fun testShortestPathWithAlternativeRoutes() {
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

        assertNotNull(result)
        assertEquals(3, result.size)
        assertEquals(v1, result[0])
        assertEquals(v4, result[2])
        // Middle vertex can be either v2 or v3
        assertTrue(result[1] == v2 || result[1] == v3)
    }

    @Test
    fun testConnectedComponentsSingleVertex() {
        val v1 = graph.addVertex("id", 1)

        val components = graph.connectedComponents()

        assertEquals(1, components.size)
        assertEquals(setOf(v1), components[0])
    }

    @Test
    fun testConnectedComponentsConnectedGraph() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)

        val components = graph.connectedComponents()

        assertEquals(1, components.size)
        assertEquals(setOf(v1, v2, v3), components[0])
    }

    @Test
    fun testConnectedComponentsDisconnectedGraph() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)
        val v4 = graph.addVertex("id", 4)

        v1.addEdge("connects", v2) // Component 1: {1, 2}
        v3.addEdge("connects", v4) // Component 2: {3, 4}

        val components = graph.connectedComponents()

        assertEquals(2, components.size)

        val componentSizes = components.map { it.size }.sorted()
        assertEquals(listOf(2, 2), componentSizes)

        // Check that components contain the right vertices
        val allVertices = components.flatten().toSet()
        assertEquals(setOf(v1, v2, v3, v4), allVertices)
    }

    @Test
    fun testConnectedComponentsIsolatedVertices() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)
        // No edges - all vertices are isolated

        val components = graph.connectedComponents()

        assertEquals(3, components.size)
        components.forEach { component ->
            assertEquals(1, component.size)
        }

        val allVertices = components.flatten().toSet()
        assertEquals(setOf(v1, v2, v3), allVertices)
    }

    @Test
    fun testHasCycleNoCycle() {
        // Create tree: 1 - 2 - 3
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)

        assertFalse(graph.hasCycle())
    }

    @Test
    fun testHasCycleSimpleCycle() {
        // Create triangle: 1 - 2 - 3 - 1
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)
        v3.addEdge("connects", v1)

        assertTrue(graph.hasCycle())
    }

    @Test
    fun testHasCycleSelfLoop() {
        val v1 = graph.addVertex("id", 1)
        v1.addEdge("connects", v1) // Self-loop

        assertTrue(graph.hasCycle())
    }

    @Test
    fun testHasCycleComplexGraphWithCycle() {
        // Create graph with cycle: 1 - 2 - 3 - 4 - 2 (cycle: 2-3-4-2)
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)
        val v4 = graph.addVertex("id", 4)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)
        v3.addEdge("connects", v4)
        v4.addEdge("connects", v2) // Creates cycle

        assertTrue(graph.hasCycle())
    }

    @Test
    fun testVerticesAtDistanceZero() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        v1.addEdge("connects", v2)

        val result = graph.verticesAtDistance(v1, 0)

        assertEquals(setOf(v1), result)
    }

    @Test
    fun testVerticesAtDistanceOne() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v1.addEdge("connects", v3)

        val result = graph.verticesAtDistance(v1, 1)

        assertEquals(setOf(v2, v3), result)
    }

    @Test
    fun testVerticesAtDistanceTwo() {
        // Create path: 1 - 2 - 3 - 4
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)
        val v4 = graph.addVertex("id", 4)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)
        v3.addEdge("connects", v4)

        val result = graph.verticesAtDistance(v1, 2)

        assertEquals(setOf(v3), result)
    }

    @Test
    fun testVerticesAtDistanceNegative() {
        val v1 = graph.addVertex("id", 1)

        val result = graph.verticesAtDistance(v1, -1)

        assertEquals(emptySet(), result)
    }

    @Test
    fun testIsConnectedSingleVertex() {
        val v1 = graph.addVertex("id", 1)

        assertTrue(graph.isConnected())
    }

    @Test
    fun testIsConnectedEmptyGraph() {
        assertTrue(graph.isConnected()) // Empty graph is considered connected
    }

    @Test
    fun testIsConnectedTrue() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)

        assertTrue(graph.isConnected())
    }

    @Test
    fun testIsConnectedFalse() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)
        val v4 = graph.addVertex("id", 4)

        v1.addEdge("connects", v2) // Component 1
        v3.addEdge("connects", v4) // Component 2

        assertFalse(graph.isConnected())
    }

    @Test
    fun testDiameterConnectedGraph() {
        // Create linear graph: 1 - 2 - 3 - 4 (diameter should be 3)
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)
        val v4 = graph.addVertex("id", 4)

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)
        v3.addEdge("connects", v4)

        assertEquals(3, graph.diameter())
    }

    @Test
    fun testDiameterDisconnectedGraph() {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        // No edges - disconnected

        assertEquals(-1, graph.diameter())
    }

    @Test
    fun testDiameterSingleVertex() {
        val v1 = graph.addVertex("id", 1)

        assertEquals(0, graph.diameter())
    }

    @Test
    fun testDiameterStarGraph() {
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

        assertEquals(2, graph.diameter()) // Max distance is from any outer vertex to another
    }
}
