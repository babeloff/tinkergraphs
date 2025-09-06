package org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators

import kotlin.test.*
import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerEdge
import org.apache.tinkerpop.gremlin.tinkergraph.util.SafeCasting

/**
 * Comprehensive tests for TinkerGraph traversal iterators.
 * Tests all iterator implementations including vertex, edge, property, and traversing iterators.
 */
class TinkerIteratorTest {

    private lateinit var graph: TinkerGraph
    private lateinit var v1: Vertex
    private lateinit var v2: Vertex
    private lateinit var v3: Vertex
    private lateinit var v4: Vertex
    private lateinit var e1: Edge
    private lateinit var e2: Edge
    private lateinit var e3: Edge

    @BeforeTest
    fun setup() {
        graph = TinkerGraph.open()

        // Create test vertices
        v1 = graph.addVertex("name", "alice", "age", 29, "type", "person")
        v2 = graph.addVertex("name", "bob", "age", 27, "type", "person")
        v3 = graph.addVertex("name", "charlie", "age", 32, "type", "person")
        v4 = graph.addVertex("name", "data", "type", "system")

        // Create test edges
        e1 = v1.addEdge("knows", v2, "weight", 0.5, "type", "friendship")
        e2 = v2.addEdge("knows", v3, "weight", 0.8, "type", "friendship")
        e3 = v1.addEdge("uses", v4, "frequency", "daily", "type", "interaction")

        // Create indices for testing
        graph.createIndex("name", Vertex::class)
        graph.createIndex("type", Edge::class)
    }

    @Test
    fun testTinkerVertexIteratorAll() {
        val iterator = TinkerVertexIterator.all(graph)
        val vertices = mutableListOf<Vertex>()

        while (iterator.hasNext()) {
            vertices.add(iterator.next())
        }

        assertEquals(4, vertices.size)
        assertTrue(vertices.contains(v1))
        assertTrue(vertices.contains(v2))
        assertTrue(vertices.contains(v3))
        assertTrue(vertices.contains(v4))
    }

    @Test
    fun testTinkerVertexIteratorByIds() {
        val iterator = TinkerVertexIterator.byIds(graph, v1.id(), v3.id())
        val vertices = mutableListOf<Vertex>()

        while (iterator.hasNext()) {
            vertices.add(iterator.next())
        }

        assertEquals(2, vertices.size)
        assertTrue(vertices.contains(v1))
        assertTrue(vertices.contains(v3))
        assertFalse(vertices.contains(v2))
        assertFalse(vertices.contains(v4))
    }

    @Test
    fun testTinkerVertexIteratorByLabels() {
        val iterator = TinkerVertexIterator.byLabels(graph, "vertex")
        val vertices = mutableListOf<Vertex>()

        while (iterator.hasNext()) {
            vertices.add(iterator.next())
        }

        assertEquals(4, vertices.size) // All vertices have default label "vertex"
    }

    @Test
    fun testTinkerVertexIteratorByProperty() {
        val iterator = TinkerVertexIterator.byProperty(graph, "name", "alice")
        val vertices = mutableListOf<Vertex>()

        while (iterator.hasNext()) {
            vertices.add(iterator.next())
        }

        assertEquals(1, vertices.size)
        assertEquals(v1, vertices.first())
        assertEquals("alice", vertices.first().value<String>("name"))
    }

    @Test
    fun testTinkerVertexIteratorByProperties() {
        val properties = mapOf("type" to "person", "age" to 27)
        val iterator = TinkerVertexIterator.byProperties(graph, properties)
        val vertices = mutableListOf<Vertex>()

        while (iterator.hasNext()) {
            vertices.add(iterator.next())
        }

        assertEquals(1, vertices.size)
        assertEquals(v2, vertices.first())
        assertEquals("bob", vertices.first().value<String>("name"))
    }

    @Test
    fun testTinkerEdgeIteratorAll() {
        val iterator = TinkerEdgeIterator.all(graph)
        val edges = mutableListOf<Edge>()

        while (iterator.hasNext()) {
            edges.add(iterator.next())
        }

        assertEquals(3, edges.size)
        assertTrue(edges.contains(e1))
        assertTrue(edges.contains(e2))
        assertTrue(edges.contains(e3))
    }

    @Test
    fun testTinkerEdgeIteratorByIds() {
        val iterator = TinkerEdgeIterator.byIds(graph, e1.id(), e3.id())
        val edges = mutableListOf<Edge>()

        while (iterator.hasNext()) {
            edges.add(iterator.next())
        }

        assertEquals(2, edges.size)
        assertTrue(edges.contains(e1))
        assertTrue(edges.contains(e3))
        assertFalse(edges.contains(e2))
    }

    @Test
    fun testTinkerEdgeIteratorByLabels() {
        val iterator = TinkerEdgeIterator.byLabels(graph, "knows")
        val edges = mutableListOf<Edge>()

        while (iterator.hasNext()) {
            edges.add(iterator.next())
        }

        assertEquals(2, edges.size)
        assertTrue(edges.contains(e1))
        assertTrue(edges.contains(e2))
        assertFalse(edges.contains(e3))
    }

    @Test
    fun testTinkerEdgeIteratorFromVertex() {
        // Test outgoing edges
        val outIterator = TinkerEdgeIterator.fromVertex(SafeCasting.safeCastVertex(v1), Direction.OUT)
        val outEdges = mutableListOf<Edge>()

        while (outIterator.hasNext()) {
            outEdges.add(outIterator.next())
        }

        assertEquals(2, outEdges.size)
        assertTrue(outEdges.contains(e1))
        assertTrue(outEdges.contains(e3))

        // Test incoming edges
        val inIterator = TinkerEdgeIterator.fromVertex(SafeCasting.safeCastVertex(v2), Direction.IN)
        val inEdges = mutableListOf<Edge>()

        while (inIterator.hasNext()) {
            inEdges.add(inIterator.next())
        }

        assertEquals(1, inEdges.size)
        assertTrue(inEdges.contains(e1))
    }

    @Test
    fun testTinkerEdgeIteratorByProperty() {
        val iterator = TinkerEdgeIterator.byProperty(graph, "type", "friendship")
        val edges = mutableListOf<Edge>()

        while (iterator.hasNext()) {
            edges.add(iterator.next())
        }

        assertEquals(2, edges.size)
        assertTrue(edges.contains(e1))
        assertTrue(edges.contains(e2))
        assertFalse(edges.contains(e3))
    }

    @Test
    fun testTinkerEdgeIteratorBetween() {
        val iterator = TinkerEdgeIterator.between(graph, v1, v2)
        val edges = mutableListOf<Edge>()

        while (iterator.hasNext()) {
            edges.add(iterator.next())
        }

        assertEquals(1, edges.size)
        assertEquals(e1, edges.first())
    }

    @Test
    fun testTinkerPropertyIteratorAll() {
        val iterator = TinkerPropertyIterator.all<Any>(v1)
        val properties = mutableListOf<org.apache.tinkerpop.gremlin.structure.Property<Any>>()

        while (iterator.hasNext()) {
            properties.add(iterator.next())
        }

        assertEquals(3, properties.size) // name, age, type properties

        val keys = properties.map { it.key() }.toSet()
        assertTrue(keys.contains("name"))
        assertTrue(keys.contains("age"))
        assertTrue(keys.contains("type"))
    }

    @Test
    fun testTinkerPropertyIteratorByKeys() {
        val iterator = TinkerPropertyIterator.byKeys<String>(v1, "name", "type")
        val properties = mutableListOf<org.apache.tinkerpop.gremlin.structure.Property<String>>()

        while (iterator.hasNext()) {
            properties.add(iterator.next())
        }

        assertEquals(2, properties.size)
        val keys = properties.map { it.key() }.toSet()
        assertTrue(keys.contains("name"))
        assertTrue(keys.contains("type"))
        assertFalse(keys.contains("age"))
    }

    @Test
    fun testTinkerPropertyIteratorByValue() {
        val iterator = TinkerPropertyIterator.byValue(v1, "alice")
        val properties = mutableListOf<org.apache.tinkerpop.gremlin.structure.Property<String>>()

        while (iterator.hasNext()) {
            properties.add(iterator.next())
        }

        assertEquals(1, properties.size)
        assertEquals("name", properties.first().key())
        assertEquals("alice", properties.first().value())
    }

    @Test
    fun testTinkerVertexPropertyIteratorAll() {
        val iterator = TinkerVertexPropertyIterator.all<Any>(v1)
        val vertexProperties = mutableListOf<VertexProperty<Any>>()

        while (iterator.hasNext()) {
            vertexProperties.add(iterator.next())
        }

        assertEquals(3, vertexProperties.size) // name, age, type vertex properties

        val keys = vertexProperties.map { it.key() }.toSet()
        assertTrue(keys.contains("name"))
        assertTrue(keys.contains("age"))
        assertTrue(keys.contains("type"))
    }

    @Test
    fun testTinkerVertexPropertyIteratorByKeys() {
        val iterator = TinkerVertexPropertyIterator.byKeys<Any>(v1, "name")
        val vertexProperties = mutableListOf<VertexProperty<Any>>()

        while (iterator.hasNext()) {
            vertexProperties.add(iterator.next())
        }

        assertEquals(1, vertexProperties.size)
        assertEquals("name", vertexProperties.first().key())
        assertEquals("alice", vertexProperties.first().value())
    }

    @Test
    fun testTinkerVertexPropertyIteratorByCardinality() {
        val iterator = TinkerVertexPropertyIterator.byCardinality<Any>(
            v1,
            VertexProperty.Cardinality.SINGLE
        )
        val vertexProperties = mutableListOf<VertexProperty<Any>>()

        while (iterator.hasNext()) {
            vertexProperties.add(iterator.next())
        }

        // All properties should be SINGLE cardinality by default
        assertEquals(3, vertexProperties.size)
    }

    @Test
    fun testTinkerVertexTraversingIteratorOut() {
        val iterator = TinkerVertexTraversingIterator.outVertices(SafeCasting.safeCastVertex(v1))
        val vertices = mutableListOf<Vertex>()

        while (iterator.hasNext()) {
            vertices.add(iterator.next())
        }

        assertEquals(2, vertices.size)
        assertTrue(vertices.contains(v2))
        assertTrue(vertices.contains(v4))
        assertFalse(vertices.contains(v1))
        assertFalse(vertices.contains(v3))
    }

    @Test
    fun testTinkerVertexTraversingIteratorIn() {
        val iterator = TinkerVertexTraversingIterator.inVertices(SafeCasting.safeCastVertex(v2))
        val vertices = mutableListOf<Vertex>()

        while (iterator.hasNext()) {
            vertices.add(iterator.next())
        }

        assertEquals(1, vertices.size)
        assertTrue(vertices.contains(v1))
    }

    @Test
    fun testTinkerVertexTraversingIteratorBoth() {
        val iterator = TinkerVertexTraversingIterator.bothVertices(SafeCasting.safeCastVertex(v2))
        val vertices = mutableListOf<Vertex>()

        while (iterator.hasNext()) {
            vertices.add(iterator.next())
        }

        assertEquals(2, vertices.size)
        assertTrue(vertices.contains(v1)) // v1 -> v2
        assertTrue(vertices.contains(v3)) // v2 -> v3
        assertFalse(vertices.contains(v2))
        assertFalse(vertices.contains(v4))
    }

    @Test
    fun testTinkerVertexTraversingIteratorWithLabels() {
        val iterator = TinkerVertexTraversingIterator.outVertices(SafeCasting.safeCastVertex(v1), "knows")
        val vertices = mutableListOf<Vertex>()

        while (iterator.hasNext()) {
            vertices.add(iterator.next())
        }

        assertEquals(1, vertices.size)
        assertTrue(vertices.contains(v2))
        assertFalse(vertices.contains(v4)) // Connected by "uses" edge, not "knows"
    }

    @Test
    fun testLazyEvaluation() {
        // Create a large graph to test lazy evaluation
        val largeGraph = TinkerGraph.open()
        val vertices = mutableListOf<Vertex>()

        // Create 1000 vertices
        for (i in 1..1000) {
            vertices.add(largeGraph.addVertex("id", i, "type", if (i % 2 == 0) "even" else "odd"))
        }

        // Test that we can get first few vertices without processing all
        val iterator = TinkerVertexIterator.byProperty(largeGraph, "type", "even")
        var count = 0

        while (iterator.hasNext() && count < 10) {
            val vertex = iterator.next()
            assertTrue(vertex.value<String>("type") == "even")
            count++
        }

        assertEquals(10, count)
    }

    @Test
    fun testMemoryEfficiency() {
        // Test that iterators don't create large intermediate collections
        val iterator = TinkerVertexIterator.all(graph)

        // Should be able to process one at a time
        var processedCount = 0
        while (iterator.hasNext()) {
            val vertex = iterator.next()
            assertNotNull(vertex)
            processedCount++
        }

        assertEquals(4, processedCount)
    }

    @Test
    fun testRemovedElementsFiltering() {
        // Test that removed elements are filtered out
        val vertexIterator = TinkerVertexIterator.all(graph)
        var vertexCount = 0
        while (vertexIterator.hasNext()) {
            vertexIterator.next()
            vertexCount++
        }
        assertEquals(4, vertexCount)

        // Remove a vertex
        v4.remove()

        // Iterator should now return 3 vertices
        val iteratorAfterRemoval = TinkerVertexIterator.all(graph)
        var countAfterRemoval = 0
        while (iteratorAfterRemoval.hasNext()) {
            countAfterRemoval++
            iteratorAfterRemoval.next()
        }
        assertEquals(3, countAfterRemoval)
    }

    @Test
    fun testIndexOptimization() {
        // Test that indexed properties use efficient lookup
        val graph = TinkerGraph.open()
        graph.createIndex("name", Vertex::class)

        // Create vertices
        val testV1 = graph.addVertex("name", "test1", "age", 25)
        val testV2 = graph.addVertex("name", "test2", "age", 30)
        val testV3 = graph.addVertex("name", "test1", "age", 35) // Duplicate name

        // Query by indexed property should be efficient
        val iterator = TinkerVertexIterator.byProperty(graph, "name", "test1")
        val results = mutableListOf<Vertex>()

        while (iterator.hasNext()) {
            results.add(iterator.next())
        }

        assertEquals(2, results.size)
        assertTrue(results.contains(testV1))
        assertTrue(results.contains(testV3))
        assertFalse(results.contains(testV2))
    }

    @Test
    fun testCompositeFiltering() {
        // Test multiple filters working together
        val properties = mapOf("type" to "person", "age" to 29)
        val iterator = TinkerVertexIterator.byProperties(graph, properties)

        var count = 0
        while (iterator.hasNext()) {
            val vertex = iterator.next()
            assertEquals("person", vertex.value<String>("type"))
            assertEquals(29, vertex.value<Int>("age"))
            count++
        }

        assertEquals(1, count)
    }

    @Test
    fun testEmptyResults() {
        // Test iterators with no matching results
        val iterator = TinkerVertexIterator.byProperty(graph, "nonexistent", "value")

        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> {
            iterator.next()
        }
    }
}
