package org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators

import kotlin.test.*
import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

/**
 * Simple verification test to validate iterator implementations work correctly.
 * This test focuses on core functionality without complex scenarios.
 */
class IteratorVerificationTest {

    @Test
    fun testBasicVertexIteration() {
        val graph = TinkerGraph.open()

        // Create some vertices
        val v1 = graph.addVertex("name", "alice")
        val v2 = graph.addVertex("name", "bob")
        val v3 = graph.addVertex("name", "charlie")

        // Test that basic vertex iteration works
        val allVertices = mutableListOf<org.apache.tinkerpop.gremlin.structure.Vertex>()
        val iterator = graph.vertices()

        while (iterator.hasNext()) {
            allVertices.add(iterator.next())
        }

        assertEquals(3, allVertices.size)
        assertTrue(allVertices.any { it.value<String>("name") == "alice" })
        assertTrue(allVertices.any { it.value<String>("name") == "bob" })
        assertTrue(allVertices.any { it.value<String>("name") == "charlie" })
    }

    @Test
    fun testBasicEdgeIteration() {
        val graph = TinkerGraph.open()

        val v1 = graph.addVertex("name", "alice")
        val v2 = graph.addVertex("name", "bob")
        val v3 = graph.addVertex("name", "charlie")

        // Create some edges
        val e1 = v1.addEdge("knows", v2)
        val e2 = v2.addEdge("knows", v3)

        // Test that basic edge iteration works
        val allEdges = mutableListOf<org.apache.tinkerpop.gremlin.structure.Edge>()
        val iterator = graph.edges()

        while (iterator.hasNext()) {
            allEdges.add(iterator.next())
        }

        assertEquals(2, allEdges.size)
        assertTrue(allEdges.any { it.label() == "knows" })
    }

    @Test
    fun testVertexTraversal() {
        val graph = TinkerGraph.open()

        val alice = graph.addVertex("name", "alice")
        val bob = graph.addVertex("name", "bob")
        val charlie = graph.addVertex("name", "charlie")

        alice.addEdge("knows", bob)
        bob.addEdge("knows", charlie)

        // Test outgoing vertex traversal from alice
        val outVertices = mutableListOf<org.apache.tinkerpop.gremlin.structure.Vertex>()
        val outIterator = alice.vertices(Direction.OUT, "knows")

        while (outIterator.hasNext()) {
            outVertices.add(outIterator.next())
        }

        assertEquals(1, outVertices.size)
        assertEquals("bob", outVertices.first().value<String>("name"))
    }

    @Test
    fun testEdgeTraversal() {
        val graph = TinkerGraph.open()

        val alice = graph.addVertex("name", "alice")
        val bob = graph.addVertex("name", "bob")
        val charlie = graph.addVertex("name", "charlie")

        alice.addEdge("knows", bob, "weight", 0.5)
        alice.addEdge("likes", charlie, "weight", 0.8)

        // Test outgoing edge traversal from alice
        val outEdges = mutableListOf<org.apache.tinkerpop.gremlin.structure.Edge>()
        val edgeIterator = alice.edges(Direction.OUT)

        while (edgeIterator.hasNext()) {
            outEdges.add(edgeIterator.next())
        }

        assertEquals(2, outEdges.size)
        assertTrue(outEdges.any { it.label() == "knows" })
        assertTrue(outEdges.any { it.label() == "likes" })
    }

    @Test
    fun testPropertyIteration() {
        val graph = TinkerGraph.open()

        val vertex = graph.addVertex("name", "alice", "age", 29, "city", "paris")

        // Test property iteration
        val properties = mutableListOf<org.apache.tinkerpop.gremlin.structure.VertexProperty<Any>>()
        val propIterator = vertex.properties<Any>()

        while (propIterator.hasNext()) {
            properties.add(propIterator.next())
        }

        assertEquals(3, properties.size)

        val keys = properties.map { it.key() }.toSet()
        assertTrue(keys.contains("name"))
        assertTrue(keys.contains("age"))
        assertTrue(keys.contains("city"))
    }

    @Test
    fun testLazyEvaluation() {
        val graph = TinkerGraph.open()

        // Create 10 vertices
        repeat(10) { i ->
            graph.addVertex("id", i, "type", if (i % 2 == 0) "even" else "odd")
        }

        // Test that we can stop iteration early (lazy evaluation)
        val iterator = graph.vertices()
        var count = 0

        while (iterator.hasNext() && count < 3) {
            iterator.next()
            count++
        }

        assertEquals(3, count)
        // If lazy evaluation works, we should still be able to continue
        assertTrue(iterator.hasNext())
    }

    @Test
    fun testEmptyIterators() {
        val graph = TinkerGraph.open()

        // Test empty graph iteration
        val vertexIterator = graph.vertices()
        val edgeIterator = graph.edges()

        assertFalse(vertexIterator.hasNext())
        assertFalse(edgeIterator.hasNext())

        // Test empty traversal
        val vertex = graph.addVertex("name", "lonely")
        val outIterator = vertex.vertices(Direction.OUT)

        assertFalse(outIterator.hasNext())
    }
}
