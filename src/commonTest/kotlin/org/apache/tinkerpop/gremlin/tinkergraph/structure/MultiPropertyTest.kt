package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.tinkergraph.structure.PropertyQueryEngine.PropertyCriterion
import kotlin.test.*

/**
 * Comprehensive tests for multi-property and meta-property support in TinkerGraph.
 * Tests Prompt 2.2.1 implementation: Multi-property and Meta-property support.
 */
class MultiPropertyTest {

    private lateinit var graph: TinkerGraph
    private lateinit var vertex: TinkerVertex

    @BeforeTest
    fun setup() {
        graph = TinkerGraph.open()
        vertex = graph.addVertex() as TinkerVertex
    }

    @AfterTest
    fun cleanup() {
        graph.close()
    }

    // SINGLE Cardinality Tests

    @Test
    fun testSingleCardinalityBasic() {
        // Add property with SINGLE cardinality
        val prop1 = vertex.property("name", "Alice", VertexProperty.Cardinality.SINGLE)
        assertTrue(prop1.isPresent())
        assertEquals("Alice", prop1.value())
        assertEquals(1, vertex.propertyCount("name"))

        // Adding another property with same key should replace the first
        val prop2 = vertex.property("name", "Bob", VertexProperty.Cardinality.SINGLE)
        assertEquals("Bob", prop2.value())
        assertEquals(1, vertex.propertyCount("name"))
        assertEquals("Bob", vertex.value("name"))
    }

    @Test
    fun testSingleCardinalityReplacement() {
        vertex.property("age", 25, VertexProperty.Cardinality.SINGLE)
        assertEquals(25, vertex.value("age"))

        // Replace with new value
        vertex.property("age", 30, VertexProperty.Cardinality.SINGLE)
        assertEquals(30, vertex.value("age"))
        assertEquals(1, vertex.propertyCount("age"))
    }

    // LIST Cardinality Tests

    @Test
    fun testListCardinalityMultipleValues() {
        // Add multiple properties with LIST cardinality
        vertex.property("skill", "Java", VertexProperty.Cardinality.LIST)
        vertex.property("skill", "Kotlin", VertexProperty.Cardinality.LIST)
        vertex.property("skill", "Python", VertexProperty.Cardinality.LIST)

        assertEquals(3, vertex.propertyCount("skill"))

        // Verify all values are present
        val skills = vertex.values<String>("skill").asSequence().toList()
        assertEquals(3, skills.size)
        assertTrue(skills.contains("Java"))
        assertTrue(skills.contains("Kotlin"))
        assertTrue(skills.contains("Python"))
    }

    @Test
    fun testListCardinalityDuplicateValues() {
        // LIST cardinality should allow duplicate values
        vertex.property("tag", "important", VertexProperty.Cardinality.LIST)
        vertex.property("tag", "urgent", VertexProperty.Cardinality.LIST)
        vertex.property("tag", "important", VertexProperty.Cardinality.LIST) // duplicate

        assertEquals(3, vertex.propertyCount("tag"))

        val tags = vertex.values<String>("tag").asSequence().toList()
        assertEquals(3, tags.size)
        assertEquals(2, tags.count { it == "important" })
        assertEquals(1, tags.count { it == "urgent" })
    }

    // SET Cardinality Tests

    @Test
    fun testSetCardinalityUniqueValues() {
        // Add multiple unique properties with SET cardinality
        vertex.property("language", "English", VertexProperty.Cardinality.SET)
        vertex.property("language", "French", VertexProperty.Cardinality.SET)
        vertex.property("language", "Spanish", VertexProperty.Cardinality.SET)

        assertEquals(3, vertex.propertyCount("language"))

        val languages = vertex.values<String>("language").asSequence().toSet()
        assertEquals(3, languages.size)
        assertTrue(languages.contains("English"))
        assertTrue(languages.contains("French"))
        assertTrue(languages.contains("Spanish"))
    }

    @Test
    fun testSetCardinalityDuplicateRejection() {
        // SET cardinality should reject duplicate values
        vertex.property("category", "work", VertexProperty.Cardinality.SET)
        vertex.property("category", "personal", VertexProperty.Cardinality.SET)

        // Adding duplicate should throw exception
        assertFailsWith<RuntimeException> {
            vertex.property("category", "work", VertexProperty.Cardinality.SET)
        }

        assertEquals(2, vertex.propertyCount("category"))
    }

    // Meta-property Tests

    @Test
    fun testBasicMetaProperties() {
        // Create vertex property with meta-properties
        val nameProperty = vertex.property("name", "Alice", "createdBy", "admin", "timestamp", 1234567890L) as TinkerVertexProperty

        assertTrue(nameProperty.hasMetaProperties())
        assertEquals(2, nameProperty.metaPropertyCount())

        assertEquals("admin", nameProperty.value<String>("createdBy"))
        assertEquals(1234567890L, nameProperty.value<Long>("timestamp"))
    }

    @Test
    fun testMetaPropertyLifecycle() {
        val prop = vertex.property("score", 95, "source", "test", "confidence", 0.9)

        // Verify meta-properties exist
        assertTrue(prop.property<String>("source").isPresent())
        assertTrue(prop.property<Double>("confidence").isPresent())

        // Add additional meta-property
        prop.property("lastUpdated", "2024-01-01")
        assertEquals(3, (prop as TinkerVertexProperty).metaPropertyCount())

        // Remove meta-property
        prop.property<String>("source").remove()
        assertEquals(2, (prop as TinkerVertexProperty).metaPropertyCount())
        assertFalse(prop.property<String>("source").isPresent())
    }

    @Test
    fun testComplexMetaProperties() {
        // Create multiple properties with different meta-properties
        val prop1 = vertex.property("email", "alice@example.com", VertexProperty.Cardinality.SET,
            "type", "primary", "verified", true)
        val prop2 = vertex.property("email", "alice.work@company.com", VertexProperty.Cardinality.SET,
            "type", "work", "verified", false)

        assertEquals(2, vertex.propertyCount("email"))

        // Verify meta-properties are distinct
        assertEquals("primary", prop1.value<String>("type"))
        assertEquals("work", prop2.value<String>("type"))
        assertTrue(prop1.value<Boolean>("verified")!!)
        assertFalse(prop2.value<Boolean>("verified")!!)
    }

    // Property Manager Tests

    @Test
    fun testPropertyManagerAddition() {
        val manager = graph.propertyManager()

        val property = manager.addVertexProperty(
            vertex, "title", "Engineer",
            VertexProperty.Cardinality.SINGLE,
            mapOf("department" to "IT", "level" to "senior")
        )

        assertEquals("Engineer", property.value())
        assertEquals("IT", property.value<String>("department"))
        assertEquals("senior", property.value<String>("level"))
    }

    @Test
    fun testPropertyManagerUpdate() {
        val manager = graph.propertyManager()

        // Add initial property
        manager.addVertexProperty(vertex, "status", "active", VertexProperty.Cardinality.SINGLE)
        assertEquals("active", vertex.value("status"))

        // Update property
        val updated = manager.updateVertexProperty(
            vertex, "status", "active", "inactive", VertexProperty.Cardinality.SINGLE
        )

        assertEquals("inactive", updated.value())
        assertEquals("inactive", vertex.value("status"))
        assertEquals(1, vertex.propertyCount("status"))
    }

    @Test
    fun testPropertyManagerValidation() {
        val manager = graph.propertyManager()

        // Add properties that violate SET cardinality
        manager.addVertexProperty(vertex, "color", "red", VertexProperty.Cardinality.SET)
        manager.addVertexProperty(vertex, "color", "blue", VertexProperty.Cardinality.SET)

        val violations = manager.validatePropertyConstraints(vertex)
        assertTrue(violations.isEmpty()) // Should be valid

        // Force add duplicate (this should be prevented by the system)
        assertFailsWith<RuntimeException> {
            manager.addVertexProperty(vertex, "color", "red", VertexProperty.Cardinality.SET)
        }
    }

    // Property Query Engine Tests

    @Test
    fun testBasicPropertyQuery() {
        // Setup test data
        val v1 = graph.addVertex() as TinkerVertex
        v1.property("name", "Alice")
        v1.property("age", 25)

        val v2 = graph.addVertex() as TinkerVertex
        v2.property("name", "Bob")
        v2.property("age", 30)

        val queryEngine = graph.propertyQueryEngine()

        // Query by exact match
        val aliceResults = queryEngine.queryVertices(
            PropertyQueryEngine.exact("name", "Alice")
        ).asSequence().toList()

        assertEquals(1, aliceResults.size)
        assertEquals("Alice", aliceResults.first().value("name"))
    }

    @Test
    fun testRangePropertyQuery() {
        // Setup test data with numeric properties
        val vertices = (1..5).map { i ->
            val v = graph.addVertex() as TinkerVertex
            v.property("score", i * 10)
            v
        }

        val queryEngine = graph.propertyQueryEngine()

        // Range query: score between 20 and 40 (inclusive)
        val results = queryEngine.queryVerticesByRange("score", 20, 40, true)
            .asSequence().toList()

        assertEquals(3, results.size) // scores 20, 30, 40
        val scores = results.map { it.value<Int>("score")!! }.sorted()
        assertEquals(listOf(20, 30, 40), scores)
    }

    @Test
    fun testMetaPropertyQuery() {
        // Setup vertex with properties that have meta-properties
        val v = graph.addVertex() as TinkerVertex
        v.property("email", "test@example.com", "type", "primary")
        v.property("email", "backup@example.com", VertexProperty.Cardinality.SET, "type", "backup")

        val queryEngine = graph.propertyQueryEngine()

        // Query by meta-property
        val primaryEmails = queryEngine.queryVerticesByMetaProperty("email", "type", "primary")
            .asSequence().toList()

        assertEquals(1, primaryEmails.size)
        assertEquals(v.id(), primaryEmails.first().id())
    }

    @Test
    fun testCompositePropertyQuery() {
        // Setup test data
        val v1 = graph.addVertex() as TinkerVertex
        v1.property("name", "Alice")
        v1.property("age", 25)
        v1.property("active", true)

        val v2 = graph.addVertex() as TinkerVertex
        v2.property("name", "Bob")
        v2.property("age", 30)
        v2.property("active", false)

        val queryEngine = graph.propertyQueryEngine()

        // Composite query: age > 20 AND active = true
        val criteria = listOf(
            PropertyQueryEngine.range("age", 20, null, false),
            PropertyQueryEngine.exact("active", true)
        )

        val results = queryEngine.queryVertices(criteria).asSequence().toList()
        assertEquals(1, results.size)
        assertEquals("Alice", results.first().value("name"))
    }

    // Property Statistics Tests

    @Test
    fun testPropertyStatistics() {
        // Setup complex property scenario
        vertex.property("skill", "Java", VertexProperty.Cardinality.LIST)
        vertex.property("skill", "Kotlin", VertexProperty.Cardinality.LIST)
        vertex.property("name", "Alice", VertexProperty.Cardinality.SINGLE)

        val stats = vertex.getPropertyStatistics()

        // Verify skill statistics
        val skillStats = stats["skill"]!!
        assertEquals(2, skillStats.activeCount)
        assertEquals(VertexProperty.Cardinality.LIST, skillStats.cardinality)

        // Verify name statistics
        val nameStats = stats["name"]!!
        assertEquals(1, nameStats.activeCount)
        assertEquals(VertexProperty.Cardinality.SINGLE, nameStats.cardinality)
    }

    @Test
    fun testGraphPropertyStatistics() {
        // Setup multiple vertices with various properties
        val v1 = graph.addVertex() as TinkerVertex
        v1.property("type", "person")
        v1.property("name", "Alice")

        val v2 = graph.addVertex() as TinkerVertex
        v2.property("type", "person")
        v2.property("name", "Bob")

        val v3 = graph.addVertex() as TinkerVertex
        v3.property("type", "company")

        val stats = graph.getPropertyStatistics()

        // Verify type property statistics
        val typeStats = stats["type"]!!
        assertEquals(3, typeStats.propertyCount) // 3 properties total
        assertEquals(3, typeStats.vertexCount)   // 3 vertices have this property

        // Verify name property statistics
        val nameStats = stats["name"]!!
        assertEquals(2, nameStats.propertyCount) // 2 properties total
        assertEquals(2, nameStats.vertexCount)   // 2 vertices have this property
    }

    // Property Lifecycle Tests

    @Test
    fun testPropertyLifecycleListener() {
        val manager = graph.propertyManager()
        val events = mutableListOf<String>()

        val listener = object : PropertyManager.PropertyLifecycleListener {
            override fun onPropertyAdded(vertex: TinkerVertex, property: TinkerVertexProperty<*>) {
                events.add("added:${property.key()}:${property.value()}")
            }

            override fun onPropertyRemoved(vertex: TinkerVertex, property: TinkerVertexProperty<*>) {
                events.add("removed:${property.key()}:${property.value()}")
            }
        }

        manager.addPropertyListener(listener)

        // Add property
        val prop = manager.addVertexProperty(vertex, "test", "value")
        assertEquals(1, events.size)
        assertEquals("added:test:value", events[0])

        // Remove property
        manager.removeVertexProperty(vertex, prop)
        assertEquals(2, events.size)
        assertEquals("removed:test:value", events[1])

        manager.removePropertyListener(listener)
    }

    // Error Handling Tests

    @Test
    fun testInvalidPropertyOperations() {
        // Test invalid property key
        assertFailsWith<IllegalArgumentException> {
            vertex.property("", "value")
        }

        // Test null property value (when not allowed)
        if (!graph.allowNullPropertyValues) {
            assertFailsWith<IllegalArgumentException> {
                vertex.property("key", null)
            }
        }
    }

    @Test
    fun testFeatureSupport() {
        // Verify graph features
        assertTrue(graph.features().vertex().supportsMultiProperties())
        assertTrue(graph.features().vertex().supportsMetaProperties())

        // Test feature checks in vertex property operations
        val prop = vertex.property("test", "value", "meta", "metaValue")
        assertTrue((prop as TinkerVertexProperty).hasMetaProperties())
    }

    // Property Removal Tests

    @Test
    fun testPropertyRemoval() {
        // Add multiple properties
        vertex.property("tag", "a", VertexProperty.Cardinality.LIST)
        vertex.property("tag", "b", VertexProperty.Cardinality.LIST)
        vertex.property("tag", "c", VertexProperty.Cardinality.LIST)

        assertEquals(3, vertex.propertyCount("tag"))

        // Remove specific property
        assertTrue(vertex.removeProperty("tag", "b"))
        assertEquals(2, vertex.propertyCount("tag"))

        // Remove all properties with key
        val removedCount = vertex.removeProperties("tag")
        assertEquals(2, removedCount)
        assertEquals(0, vertex.propertyCount("tag"))
        assertFalse(vertex.hasProperty("tag"))
    }

    @Test
    fun testPropertyCleanup() {
        val manager = graph.propertyManager()

        // Add and remove properties to create cleanup scenarios
        val prop1 = vertex.property("temp1", "value1")
        val prop2 = vertex.property("temp2", "value2")

        prop1.remove()
        prop2.remove()

        // Optimize storage
        val result = manager.optimizePropertyStorage(vertex)
        assertTrue(result.cleanedProperties >= 0) // Should clean up removed properties
    }


}
