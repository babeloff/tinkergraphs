package org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators

import kotlin.test.*
import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

/**
 * Simple test to verify basic iterator functionality works correctly.
 */
class SimpleIteratorTest {

    @Test
    fun testBasicVertexIteration() {
        val graph = TinkerGraph.open()

        // Create some vertices
        val v1 = graph.addVertex("name", "alice")
        val v2 = graph.addVertex("name", "bob")
        val v3 = graph.addVertex("name", "charlie")

        // Test that basic vertex iteration works
        val vertices = graph.vertices().asSequence().toList()

        println("Vertices found: ${vertices.size}")
        vertices.forEach { v -> println("  Vertex: ${v.id()} - ${v.value<String>("name")}") }

        assertEquals(3, vertices.size)

        val names = vertices.map { it.value<String>("name") }.toSet()
        println("Names: $names")
        assertTrue(names.contains("alice"))
        assertTrue(names.contains("bob"))
        assertTrue(names.contains("charlie"))
    }

    @Test
    fun testBasicEdgeIteration() {
        val graph = TinkerGraph.open()

        val alice = graph.addVertex("name", "alice")
        val bob = graph.addVertex("name", "bob")
        val charlie = graph.addVertex("name", "charlie")

        // Create some edges
        alice.addEdge("knows", bob, "weight", 0.5)
        bob.addEdge("knows", charlie, "weight", 0.8)

        // Test that basic edge iteration works
        val edges = graph.edges().asSequence().toList()

        assertEquals(2, edges.size)

        val labels = edges.map { it.label() }.toSet()
        assertTrue(labels.contains("knows"))

        val weights = edges.map { it.value<Double>("weight") }.toSet()
        assertTrue(weights.contains(0.5))
        assertTrue(weights.contains(0.8))
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
        val outVertices = alice.vertices(Direction.OUT, "knows").asSequence().toList()

        println("Alice out vertices: ${outVertices.size}")
        outVertices.forEach { v -> println("  Out vertex: ${v.id()} - ${v.value<String>("name")}") }

        assertEquals(1, outVertices.size)
        assertEquals("bob", outVertices.first().value<String>("name"))

        // Test incoming vertex traversal to charlie
        val inVertices = charlie.vertices(Direction.IN, "knows").asSequence().toList()

        assertEquals(1, inVertices.size)
        assertEquals("bob", inVertices.first().value<String>("name"))
    }

    @Test
    fun testEdgeTraversal() {
        val graph = TinkerGraph.open()

        val alice = graph.addVertex("name", "alice")
        val bob = graph.addVertex("name", "bob")
        val charlie = graph.addVertex("name", "charlie")

        alice.addEdge("knows", bob, "type", "friend")
        alice.addEdge("likes", charlie, "type", "romantic")

        // Test outgoing edge traversal from alice
        val outEdges = alice.edges(Direction.OUT).asSequence().toList()

        assertEquals(2, outEdges.size)

        val labels = outEdges.map { it.label() }.toSet()
        assertTrue(labels.contains("knows"))
        assertTrue(labels.contains("likes"))

        val types = outEdges.map { it.value<String>("type") }.toSet()
        assertTrue(types.contains("friend"))
        assertTrue(types.contains("romantic"))
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
    fun testEmptyGraph() {
        val graph = TinkerGraph.open()

        // Test empty graph iteration
        val vertices = graph.vertices().asSequence().toList()
        val edges = graph.edges().asSequence().toList()

        assertTrue(vertices.isEmpty())
        assertTrue(edges.isEmpty())
    }

    @Test
    fun testVertexPropertyIteration() {
        val graph = TinkerGraph.open()

        val vertex = graph.addVertex("name", "alice", "age", 29, "city", "paris")

        // Test vertex property iteration
        val vertexProperties = vertex.properties<Any>().asSequence().toList()

        assertEquals(3, vertexProperties.size)

        val keys = vertexProperties.map { it.key() }.toSet()
        assertTrue(keys.contains("name"))
        assertTrue(keys.contains("age"))
        assertTrue(keys.contains("city"))

        val values = vertexProperties.map { it.value() }.toSet()
        assertTrue(values.contains("alice"))
        assertTrue(values.contains(29))
        assertTrue(values.contains("paris"))
    }

    @Test
    fun testVertexDegree() {
        val graph = TinkerGraph.open()

        val alice = graph.addVertex("name", "alice")
        val bob = graph.addVertex("name", "bob")
        val charlie = graph.addVertex("name", "charlie")

        alice.addEdge("knows", bob)
        alice.addEdge("likes", charlie)
        bob.addEdge("knows", charlie)

        // Alice should have 2 outgoing edges
        val aliceOutEdges = alice.edges(Direction.OUT).asSequence().toList()
        assertEquals(2, aliceOutEdges.size)

        // Charlie should have 2 incoming edges
        val charlieInEdges = charlie.edges(Direction.IN).asSequence().toList()
        assertEquals(2, charlieInEdges.size)

        // Bob should have 1 outgoing and 1 incoming edge
        val bobOutEdges = bob.edges(Direction.OUT).asSequence().toList()
        val bobInEdges = bob.edges(Direction.IN).asSequence().toList()
        assertEquals(1, bobOutEdges.size)
        assertEquals(1, bobInEdges.size)
    }
}
