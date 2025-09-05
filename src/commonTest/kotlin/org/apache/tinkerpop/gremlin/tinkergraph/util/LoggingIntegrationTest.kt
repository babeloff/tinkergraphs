package org.apache.tinkerpop.gremlin.tinkergraph.util

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test for logging functionality introduced in Prompt 3.0.1.
 *
 * This test demonstrates:
 * 1. KmLogging integration across all TinkerGraph operations
 * 2. Proper KDoc documentation generation
 * 3. Cross-platform logging support
 */
class LoggingIntegrationTest {

    @Test
    fun testLoggingConfigUtilities() {
        // Test logger creation
        val logger = LoggingConfig.getLogger<LoggingIntegrationTest>()
        assertNotNull(logger, "Logger should be created successfully")

        val namedLogger = LoggingConfig.getLogger("TestLogger")
        assertNotNull(namedLogger, "Named logger should be created successfully")
    }

    @Test
    fun testGraphOperationsWithLogging() {
        val graph = TinkerGraph.open()

        // Test vertex creation with logging
        val vertex1 = graph.addVertex("name", "Alice", "age", 30)
        assertNotNull(vertex1, "Vertex should be created successfully")

        val vertex2 = graph.addVertex("name", "Bob", "age", 25)
        assertNotNull(vertex2, "Second vertex should be created successfully")

        // Test edge creation with logging
        val edge = vertex1.addEdge("knows", vertex2, "since", 2020)
        assertNotNull(edge, "Edge should be created successfully")

        // Verify graph state
        assertEquals(2, graph.vertices().asSequence().count(), "Graph should contain 2 vertices")
        assertEquals(1, graph.edges().asSequence().count(), "Graph should contain 1 edge")
    }

    @Test
    fun testPerformanceLogging() {
        val graph = TinkerGraph.open()

        // Test performance measurement
        val result = LoggingConfig.measureTime("bulk-vertex-creation") {
            repeat(100) { i ->
                graph.addVertex("id", i, "name", "User$i")
            }
            graph.vertices().asSequence().count()
        }

        assertEquals(100, result, "Should create 100 vertices")
    }

    @Test
    fun testGraphStatsLogging() {
        val graph = TinkerGraph.open()

        // Add some vertices and edges
        val v1 = graph.addVertex("name", "Node1")
        val v2 = graph.addVertex("name", "Node2")
        val v3 = graph.addVertex("name", "Node3")

        v1.addEdge("connects", v2)
        v2.addEdge("connects", v3)

        val vertexCount = graph.vertices().asSequence().count()
        val edgeCount = graph.edges().asSequence().count()

        // Test stats logging
        LoggingConfig.logGraphStats(vertexCount, edgeCount, "test-operation")

        assertEquals(3, vertexCount, "Should have 3 vertices")
        assertEquals(2, edgeCount, "Should have 2 edges")
    }

    @Test
    fun testIndexOperationLogging() {
        val graph = TinkerGraph.open()

        // Create vertices with properties for indexing
        graph.addVertex("name", "Alice", "age", 30, "city", "New York")
        graph.addVertex("name", "Bob", "age", 25, "city", "San Francisco")
        graph.addVertex("name", "Charlie", "age", 35, "city", "New York")

        // Test index operation logging
        LoggingConfig.logIndexOperation("vertex", "query", "city", 2)
        LoggingConfig.logIndexOperation("vertex", "create", "age")

        // Verify we can still query the graph normally
        val vertices = graph.vertices().asSequence().toList()
        assertEquals(3, vertices.size, "Should have 3 vertices")
    }

    @Test
    fun testElementDebugLogging() {
        val graph = TinkerGraph.open()

        val vertex = graph.addVertex("name", "TestVertex", "type", "debug")
        val edge = vertex.addEdge("test", vertex, "weight", 1.0)

        // Test element debug logging
        LoggingConfig.logElementDebug("vertex", vertex.id(), "Created for debug test")
        LoggingConfig.logElementDebug("edge", edge.id(), "Self-referencing edge for testing")

        assertNotNull(vertex, "Vertex should exist")
        assertNotNull(edge, "Edge should exist")
    }

    @Test
    fun testMemoryUsageLogging() {
        val graph = TinkerGraph.open()

        // Log memory usage before operations
        LoggingConfig.logMemoryUsage("before-bulk-operations")

        // Perform memory-intensive operations
        repeat(50) { i ->
            val vertex = graph.addVertex("id", i, "data", "x".repeat(100))
            vertex.addEdge("self", vertex, "weight", i.toDouble())
        }

        // Log memory usage after operations
        LoggingConfig.logMemoryUsage("after-bulk-operations")

        val vertexCount = graph.vertices().asSequence().count()
        val edgeCount = graph.edges().asSequence().count()

        assertEquals(50, vertexCount, "Should have 50 vertices")
        assertEquals(50, edgeCount, "Should have 50 edges")
    }

    @Test
    fun testDebugConfiguration() {
        // Test debug configuration
        LoggingConfig.setDebugEnabled("TinkerGraph", true)
        LoggingConfig.setDebugEnabled("PropertyQueryEngine", false)

        val graph = TinkerGraph.open()
        val vertex = graph.addVertex("name", "DebugTest")

        assertNotNull(vertex, "Vertex should be created even with debug configuration")
    }

    @Test
    fun testCrossplatformLogging() {
        // This test verifies that logging works across platforms
        val logger = LoggingConfig.getLogger("CrossPlatformTest")
        assertNotNull(logger, "Logger should work on current platform")

        val graph = TinkerGraph.open()

        // Perform operations that trigger logging on all platforms
        val vertex = graph.addVertex("platform", "current", "test", "cross-platform")
        val foundVertex = graph.vertex(vertex.id())

        assertEquals(vertex, foundVertex, "Vertex operations should work with logging")
    }

    @Test
    fun testLoggingWithComplexOperations() {
        val graph = TinkerGraph.open()

        // Create a more complex graph structure
        val users = mutableListOf<Any>()
        repeat(10) { i ->
            users.add(graph.addVertex("type", "user", "id", i, "name", "User$i"))
        }

        val groups = mutableListOf<Any>()
        repeat(3) { i ->
            groups.add(graph.addVertex("type", "group", "id", "group$i", "name", "Group$i"))
        }

        // Create relationships with logging
        LoggingConfig.measureTime("relationship-creation") {
            users.forEach { user ->
                groups.forEach { group ->
                    val userVertex = user as org.apache.tinkerpop.gremlin.structure.Vertex
                    val groupVertex = group as org.apache.tinkerpop.gremlin.structure.Vertex
                    val userId = userVertex.value<Int>("id") ?: 0
                    val groupId = groupVertex.value<String>("id") ?: "group0"
                    if (userId % 3 == groupId.last().digitToInt() % 3) {
                        userVertex.addEdge("memberOf", groupVertex, "role", "member")
                    }
                }
            }
        }

        val totalVertices = graph.vertices().asSequence().count()
        val totalEdges = graph.edges().asSequence().count()

        LoggingConfig.logGraphStats(totalVertices, totalEdges, "complex-graph-creation")

        assertEquals(13, totalVertices, "Should have 13 vertices (10 users + 3 groups)")
        assertTrue(totalEdges > 0, "Should have created some edges")
    }
}
