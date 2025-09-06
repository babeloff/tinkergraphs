package org.apache.tinkerpop.gremlin.tinkergraph.javascript

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import kotlin.test.*

/**
 * Tests for TinkerGraphJSAdapter functionality in JavaScript environments.
 */
class TinkerGraphJSAdapterTest {

    private lateinit var adapter: TinkerGraphJSAdapter

    @BeforeTest
    fun setup() {
        adapter = TinkerGraphJSAdapter.open()
    }

    @AfterTest
    fun cleanup() {
        adapter.clear()
    }

    @Test
    fun testCreateAdapter() {
        assertNotNull(adapter)
        assertNotNull(adapter.getGraph())
    }

    @Test
    fun testAddVertex() {
        val vertex = adapter.addVertex("person")

        assertNotNull(vertex)
        assertEquals("person", vertex.label())
        assertNotNull(vertex.id())
    }

    @Test
    fun testAddVertexWithProperties() {
        val properties = js("{}")
        properties.name = "Alice"
        properties.age = 30

        val vertex = adapter.addVertex("person", properties)

        assertNotNull(vertex)
        assertEquals("person", vertex.label())
        assertEquals("Alice", vertex.value<String>("name"))
        assertEquals(30, vertex.value<Int>("age"))
    }

    @Test
    fun testAddEdge() {
        val alice = adapter.addVertex("person")
        val bob = adapter.addVertex("person")

        val edge = adapter.addEdge(alice, "knows", bob)

        assertNotNull(edge)
        assertEquals("knows", edge.label())
        assertEquals(alice, edge.outVertex())
        assertEquals(bob, edge.inVertex())
    }

    @Test
    fun testAddEdgeWithProperties() {
        val alice = adapter.addVertex("person")
        val bob = adapter.addVertex("person")

        val properties = js("{}")
        properties.since = 2020
        properties.weight = 0.8

        val edge = adapter.addEdge(alice, "knows", bob, properties)

        assertNotNull(edge)
        assertEquals("knows", edge.label())
        assertEquals(2020, edge.value<Int>("since"))
        assertEquals(0.8, edge.value<Double>("weight"))
    }

    @Test
    fun testGetVertices() {
        adapter.addVertex("person")
        adapter.addVertex("company")

        val vertices = adapter.vertices()

        assertEquals(2, vertices.size)
    }

    @Test
    fun testGetEdges() {
        val alice = adapter.addVertex("person")
        val bob = adapter.addVertex("person")
        val charlie = adapter.addVertex("person")

        adapter.addEdge(alice, "knows", bob)
        adapter.addEdge(alice, "knows", charlie)

        val edges = adapter.edges()

        assertEquals(2, edges.size)
    }

    @Test
    fun testFindVerticesByProperty() {
        val alice = adapter.addVertex("person")
        alice.property("name", "Alice")

        val bob = adapter.addVertex("person")
        bob.property("name", "Bob")

        val charlie = adapter.addVertex("person")
        charlie.property("name", "Charlie")

        val results = adapter.findVerticesByProperty("name", "Alice")

        assertEquals(1, results.size)
        assertEquals(alice, results[0])
    }

    @Test
    fun testFindEdgesByProperty() {
        val alice = adapter.addVertex("person")
        val bob = adapter.addVertex("person")
        val charlie = adapter.addVertex("person")

        val edge1 = adapter.addEdge(alice, "knows", bob)
        edge1.property("type", "friend")

        val edge2 = adapter.addEdge(alice, "knows", charlie)
        edge2.property("type", "colleague")

        val results = adapter.findEdgesByProperty("type", "friend")

        assertEquals(1, results.size)
        assertEquals(edge1, results[0])
    }

    @Test
    fun testGetVertexById() {
        val alice = adapter.addVertex("person")
        val id = alice.id()

        val found = adapter.getVertex(id)

        assertNotNull(found)
        assertEquals(alice, found)
    }

    @Test
    fun testGetEdgeById() {
        val alice = adapter.addVertex("person")
        val bob = adapter.addVertex("person")
        val edge = adapter.addEdge(alice, "knows", bob)
        val id = edge.id()

        val found = adapter.getEdge(id)

        assertNotNull(found)
        assertEquals(edge, found)
    }

    @Test
    fun testGetVertexByIdNotFound() {
        val found = adapter.getVertex("nonexistent")

        assertNull(found)
    }

    @Test
    fun testGetStatistics() {
        adapter.addVertex("person")
        adapter.addVertex("person")
        val alice = adapter.addVertex("person")
        val bob = adapter.addVertex("person")
        adapter.addEdge(alice, "knows", bob)

        val stats = adapter.getStatistics()

        assertEquals(4, stats.vertexCount)
        assertEquals(1, stats.edgeCount)
    }

    @Test
    fun testToJSON() {
        val alice = adapter.addVertex("person")
        alice.property("name", "Alice")
        alice.property("age", 30)

        val bob = adapter.addVertex("person")
        bob.property("name", "Bob")
        bob.property("age", 25)

        val edge = adapter.addEdge(alice, "knows", bob)
        edge.property("since", 2020)

        val json = adapter.toJSON()

        assertNotNull(json)
        assertTrue(json.isNotEmpty())
        assertTrue(json.contains("Alice"))
        assertTrue(json.contains("Bob"))
        assertTrue(json.contains("knows"))
    }

    @Test
    fun testClear() {
        adapter.addVertex("person")
        adapter.addVertex("person")
        val alice = adapter.addVertex("person")
        val bob = adapter.addVertex("person")
        adapter.addEdge(alice, "knows", bob)

        adapter.clear()

        val vertices = adapter.vertices()
        val edges = adapter.edges()

        assertEquals(0, vertices.size)
        assertEquals(0, edges.size)
    }

    @Test
    fun testJavaScriptEnvironmentDetection() {
        val isJS = TinkerGraphJSAdapter.isJavaScriptEnvironment()
        assertTrue(isJS) // Should be true in JavaScript test environment
    }

    @Test
    fun testLocalStorageDetection() {
        // This may vary depending on test environment
        val hasLocalStorage = TinkerGraphJSAdapter.hasLocalStorage()
        // Just ensure the method doesn't throw an exception
        assertTrue(hasLocalStorage || !hasLocalStorage)
    }

    @Test
    fun testIndexedDBDetection() {
        // This may vary depending on test environment
        val hasIndexedDB = TinkerGraphJSAdapter.hasIndexedDB()
        // Just ensure the method doesn't throw an exception
        assertTrue(hasIndexedDB || !hasIndexedDB)
    }

    @Test
    fun testJSVertex() {
        val vertex = adapter.addVertex("person")
        vertex.property("name", "Alice")
        vertex.property("age", 30)

        val jsVertex = JSVertex(vertex)

        assertEquals(vertex.id(), jsVertex.getId())
        assertEquals("person", jsVertex.getLabel())
        assertEquals("Alice", jsVertex.getProperty("name"))
        assertEquals(30, jsVertex.getProperty("age"))
        assertNull(jsVertex.getProperty("nonexistent"))

        val properties = jsVertex.getProperties()
        assertEquals("Alice", properties.name)
        assertEquals(30, properties.age)
    }

    @Test
    fun testJSEdge() {
        val alice = adapter.addVertex("person")
        val bob = adapter.addVertex("person")
        val edge = adapter.addEdge(alice, "knows", bob)
        edge.property("since", 2020)

        val jsEdge = JSEdge(edge)

        assertEquals(edge.id(), jsEdge.getId())
        assertEquals("knows", jsEdge.getLabel())
        assertEquals(alice.id(), jsEdge.getOutVertex().getId())
        assertEquals(bob.id(), jsEdge.getInVertex().getId())
        assertEquals(2020, jsEdge.getProperty("since"))

        val properties = jsEdge.getProperties()
        assertEquals(2020, properties.since)
    }

    @Test
    fun testJSVertexAddEdge() {
        val alice = adapter.addVertex("person")
        val bob = adapter.addVertex("person")

        val jsAlice = JSVertex(alice)
        val jsBob = JSVertex(bob)

        val jsEdge = jsAlice.addEdge("knows", jsBob)

        assertNotNull(jsEdge)
        assertEquals("knows", jsEdge.getLabel())
        assertEquals(alice.id(), jsEdge.getOutVertex().getId())
        assertEquals(bob.id(), jsEdge.getInVertex().getId())
    }

    @Test
    fun testJSVertexGetEdges() {
        val alice = adapter.addVertex("person")
        val bob = adapter.addVertex("person")
        val charlie = adapter.addVertex("person")

        adapter.addEdge(alice, "knows", bob)
        adapter.addEdge(alice, "works_with", charlie)
        adapter.addEdge(bob, "knows", alice)

        val jsAlice = JSVertex(alice)

        val outEdges = jsAlice.getOutEdges()
        assertEquals(2, outEdges.size)

        val knowsEdges = jsAlice.getOutEdges("knows")
        assertEquals(1, knowsEdges.size)
        assertEquals("knows", knowsEdges[0].getLabel())

        val inEdges = jsAlice.getInEdges()
        assertEquals(1, inEdges.size)
    }

    @Test
    fun testErrorHandling() {
        // Test with null properties
        val vertex = adapter.addVertex("person", null)
        assertNotNull(vertex)
        assertEquals("person", vertex.label())

        // Test with undefined properties in dynamic object
        val properties = js("{}")
        properties.name = "Alice"
        properties.undefinedProp = undefined

        val vertex2 = adapter.addVertex("person", properties)
        assertNotNull(vertex2)
        assertEquals("Alice", vertex2.value<String>("name"))
        // undefined property should not be added
        assertFalse(vertex2.property<Any>("undefinedProp").isPresent())
    }

    @Test
    fun testComplexGraph() {
        // Create a more complex graph
        val alice = adapter.addVertex("person")
        alice.property("name", "Alice")
        alice.property("age", 30)

        val bob = adapter.addVertex("person")
        bob.property("name", "Bob")
        bob.property("age", 25)

        val charlie = adapter.addVertex("person")
        charlie.property("name", "Charlie")
        charlie.property("age", 35)

        val company = adapter.addVertex("company")
        company.property("name", "Tech Corp")

        val knows1 = adapter.addEdge(alice, "knows", bob)
        knows1.property("since", 2015)

        val knows2 = adapter.addEdge(alice, "knows", charlie)
        knows2.property("since", 2018)

        val worksAt1 = adapter.addEdge(alice, "works_at", company)
        worksAt1.property("position", "Engineer")

        val worksAt2 = adapter.addEdge(bob, "works_at", company)
        worksAt2.property("position", "Designer")

        // Verify the graph structure
        val vertices = adapter.vertices()
        val edges = adapter.edges()

        assertEquals(4, vertices.size)
        assertEquals(4, edges.size)

        // Test finding by properties
        val people = adapter.findVerticesByProperty("age", 30)
        assertEquals(1, people.size)
        assertEquals("Alice", people[0].value<String>("name"))

        val recentKnows = adapter.findEdgesByProperty("since", 2018)
        assertEquals(1, recentKnows.size)

        // Test JSON export
        val json = adapter.toJSON()
        assertTrue(json.contains("Tech Corp"))
        assertTrue(json.contains("Engineer"))
        assertTrue(json.contains("Designer"))
    }
}
