package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*
import kotlin.test.*

/**
 * Test suite for TinkerVertex implementation.
 */
class TinkerVertexTest {

    private lateinit var graph: TinkerGraph
    private lateinit var vertex: TinkerVertex

    @BeforeTest
    fun setUp() {
        graph = TinkerGraph.open()
        vertex = graph.addVertex("name", "test") as TinkerVertex
    }

    @Test
    fun testVertexCreation() {
        assertNotNull(vertex)
        assertNotNull(vertex.id())
        assertEquals(Vertex.DEFAULT_LABEL, vertex.label())
        assertEquals(graph, vertex.graph())
    }

    @Test
    fun testVertexProperties() {
        // Test single property
        val property = vertex.property("age", 30)
        assertTrue(property.isPresent())
        assertEquals("age", property.key())
        assertEquals(30, property.value())

        // Test property retrieval
        assertEquals(30, vertex.value<Int>("age"))

        // Test keys
        assertTrue(vertex.keys().contains("name"))
        assertTrue(vertex.keys().contains("age"))
    }

    @Test
    fun testMultiProperties() {
        // Add multiple properties with same key (LIST cardinality)
        vertex.addVertexProperty("skill", "kotlin", cardinality = VertexProperty.Cardinality.LIST)
        vertex.addVertexProperty("skill", "java", cardinality = VertexProperty.Cardinality.LIST)

        val skills = vertex.properties<String>("skill").asSequence().toList()
        assertEquals(2, skills.size)

        val skillValues = skills.map { it.value() }.toSet()
        assertTrue(skillValues.contains("kotlin"))
        assertTrue(skillValues.contains("java"))
    }

    @Test
    fun testSetCardinality() {
        // SET cardinality should not allow duplicates
        vertex.addVertexProperty("tag", "important", cardinality = VertexProperty.Cardinality.SET)

        assertFailsWith<IllegalStateException> {
            vertex.addVertexProperty("tag", "important", cardinality = VertexProperty.Cardinality.SET)
        }
    }

    @Test
    fun testSingleCardinality() {
        // SINGLE cardinality should replace existing values
        vertex.addVertexProperty("status", "active", emptyMap(), VertexProperty.Cardinality.SINGLE)
        vertex.addVertexProperty("status", "inactive", emptyMap(), VertexProperty.Cardinality.SINGLE)

        val statusProps = vertex.properties<String>("status").asSequence().toList()
        assertEquals(1, statusProps.size)
        assertEquals("inactive", statusProps[0].value())
    }

    @Test
    fun testMetaProperties() {
        val vertexProperty = vertex.addVertexProperty("score", 95.5)

        // Add meta-property
        vertexProperty.property("timestamp", System.currentTimeMillis())
        vertexProperty.property("source", "test")

        assertTrue(vertexProperty.hasMetaProperties())
        assertEquals(2, vertexProperty.metaPropertyCount())
        assertTrue(vertexProperty.keys().contains("timestamp"))
        assertTrue(vertexProperty.keys().contains("source"))
    }

    @Test
    fun testEdgeAddition() {
        val vertex2 = graph.addVertex("name", "vertex2") as TinkerVertex

        val edge = vertex.addEdge("knows", vertex2, "since", 2020)

        assertNotNull(edge)
        assertEquals("knows", edge.label())
        assertEquals(vertex, edge.outVertex())
        assertEquals(vertex2, edge.inVertex())
        assertEquals(2020, edge.value<Int>("since"))
    }

    @Test
    fun testEdgeTraversal() {
        val vertex2 = graph.addVertex("name", "vertex2") as TinkerVertex
        val vertex3 = graph.addVertex("name", "vertex3") as TinkerVertex

        vertex.addEdge("knows", vertex2)
        vertex.addEdge("likes", vertex3)
        vertex2.addEdge("follows", vertex)

        // Test outgoing edges
        val outEdges = vertex.edges(Direction.OUT).asSequence().toList()
        assertEquals(2, outEdges.size)

        // Test incoming edges
        val inEdges = vertex.edges(Direction.IN).asSequence().toList()
        assertEquals(1, inEdges.size)

        // Test both directions
        val bothEdges = vertex.edges(Direction.BOTH).asSequence().toList()
        assertEquals(3, bothEdges.size)

        // Test edge label filtering
        val knowsEdges = vertex.edges(Direction.OUT, "knows").asSequence().toList()
        assertEquals(1, knowsEdges.size)
    }

    @Test
    fun testVertexTraversal() {
        val vertex2 = graph.addVertex("name", "vertex2") as TinkerVertex
        val vertex3 = graph.addVertex("name", "vertex3") as TinkerVertex

        vertex.addEdge("knows", vertex2)
        vertex.addEdge("likes", vertex3)

        val outVertices = vertex.vertices(Direction.OUT).asSequence().toList()
        assertEquals(2, outVertices.size)
        assertTrue(outVertices.contains(vertex2))
        assertTrue(outVertices.contains(vertex3))

        // Test with label filtering
        val knownVertices = vertex.vertices(Direction.OUT, "knows").asSequence().toList()
        assertEquals(1, knownVertices.size)
        assertTrue(knownVertices.contains(vertex2))
    }

    @Test
    fun testEdgeCounting() {
        val vertex2 = graph.addVertex() as TinkerVertex
        val vertex3 = graph.addVertex() as TinkerVertex

        vertex.addEdge("knows", vertex2)
        vertex.addEdge("likes", vertex3)
        vertex2.addEdge("follows", vertex)

        assertEquals(2, vertex.countEdges(Direction.OUT))
        assertEquals(1, vertex.countEdges(Direction.IN))
        assertEquals(3, vertex.countEdges(Direction.BOTH))
        assertEquals(1, vertex.countEdges(Direction.OUT, "knows"))
    }

    @Test
    fun testVertexRemoval() {
        val vertex2 = graph.addVertex() as TinkerVertex
        vertex.addEdge("knows", vertex2)

        // Verify vertex exists
        assertNotNull(graph.vertex(vertex.id()))

        // Remove vertex
        vertex.remove()

        // Verify vertex is removed from graph
        assertNull(graph.vertex(vertex.id()))

        // Verify operations on removed vertex throw exception
        assertFailsWith<IllegalStateException> {
            vertex.property("test", "value")
        }
    }

    @Test
    fun testVertexPropertyRemoval() {
        val prop = vertex.property("temp", "value")
        assertTrue(prop.isPresent())
        assertEquals("value", vertex.value<String>("temp"))

        prop.remove()

        assertFalse(prop.isPresent())
        assertNull(vertex.value<String>("temp"))
    }

    @Test
    fun testVertexEquality() {
        val vertex2 = graph.addVertex() as TinkerVertex

        assertNotEquals(vertex, vertex2)
        assertEquals(vertex, vertex) // Self equality

        // Vertices are equal if they have the same id
        val sameVertex = graph.vertex(vertex.id())
        assertEquals(vertex, sameVertex)
    }

    @Test
    fun testToString() {
        val vertexString = vertex.toString()
        assertTrue(vertexString.startsWith("v["))
        assertTrue(vertexString.endsWith("]"))
        assertTrue(vertexString.contains(vertex.id().toString()))
    }

    @Test
    fun testVertexLabels() {
        val labeledVertex = graph.addVertex(T.label, "person", "name", "Alice") as TinkerVertex
        assertEquals("person", labeledVertex.label())
    }

    @Test
    fun testEdgeLabels() {
        val vertex2 = graph.addVertex() as TinkerVertex
        val vertex3 = graph.addVertex() as TinkerVertex

        vertex.addEdge("knows", vertex2)
        vertex.addEdge("likes", vertex3)
        vertex2.addEdge("follows", vertex)

        val outLabels = vertex.getOutEdgeLabels()
        assertEquals(2, outLabels.size)
        assertTrue(outLabels.contains("knows"))
        assertTrue(outLabels.contains("likes"))

        val inLabels = vertex.getInEdgeLabels()
        assertEquals(1, inLabels.size)
        assertTrue(inLabels.contains("follows"))

        val allLabels = vertex.getAllEdgeLabels()
        assertEquals(3, allLabels.size)
    }

    /**
     * Helper object for reserved keys.
     */
    object T {
        const val id = "id"
        const val label = "label"
    }
}
