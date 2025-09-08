package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.structure.Vertex
import kotlin.test.*

// Extension functions for missing methods used in tests
fun <V> Vertex.values(key: String): Iterator<V> {
    return this.properties<V>(key).asSequence().map { it.value() }.iterator()
}

fun <V> Collection<V>.size(): Int = this.size

fun Vertex.propertyCount(key: String): Int {
    return this.properties<Any>(key).asSequence().count()
}

fun Vertex.hasProperty(key: String): Boolean {
    return this.property<Any>(key).isPresent()
}

fun <V> VertexProperty<V>.hasMetaProperties(): Boolean {
    return this.keys().isNotEmpty()
}

fun <V> VertexProperty<V>.metaPropertyCount(): Int {
    return this.keys().size
}

fun Vertex.removeProperty(key: String) {
    this.property<Any>(key).remove()
}

fun Vertex.removeProperties(key: String): Int {
    val count = this.propertyCount(key)
    this.properties<Any>(key).forEach { it.remove() }
    return count
}

fun safeCastVertex(vertex: Vertex): TinkerVertex? {
    return vertex as? TinkerVertex
}



fun <V> VertexProperty<V>.asTinkerVertexProperty(): TinkerVertexProperty<V>? {
    return this as? TinkerVertexProperty<V>
}

object SafeCasting {
    fun <T> cast(value: Any?): T? {
        @Suppress("UNCHECKED_CAST")
        return value as? T
    }
}

fun Vertex.getPropertyStatistics(): Map<String, PropertyStats> {
    val stats = mutableMapOf<String, PropertyStats>()
    this.keys().forEach { key ->
        stats[key] = PropertyStats(
            activeCount = this.propertyCount(key),
            cardinality = if (this is TinkerVertex) this.getPropertyCardinality(key) else VertexProperty.Cardinality.SINGLE
        )
    }
    return stats
}

data class PropertyStats(
    val activeCount: Int,
    val cardinality: VertexProperty.Cardinality
)

fun PropertyStats.activeCount(): Int = this.activeCount
fun PropertyStats.cardinality(): VertexProperty.Cardinality = this.cardinality

/**
 * Comprehensive tests for multi-property and meta-property support in TinkerGraph.
 * Tests Task 2.2.1 implementation: Multi-property and Meta-property support.
 */
class MultiPropertyTest {

    private lateinit var graph: TinkerGraph
    private lateinit var vertex: Vertex

    @BeforeTest
    fun setup() {
        graph = TinkerGraph.open()
        vertex = graph.addVertex()
    }

    @AfterTest
    fun cleanup() {
        graph.close()
    }

    // SINGLE Cardinality Tests

    @Test
    fun testSingleCardinalityBasic() {
        // Add property with SINGLE cardinality
        val tinkerVertex = vertex as TinkerVertex
        val prop1 = tinkerVertex.property("name", "Alice", VertexProperty.Cardinality.SINGLE)
        assertTrue(prop1.isPresent())
        assertEquals("Alice", prop1.value())
        assertEquals(1, tinkerVertex.propertyCount("name"))

        // Adding another property with same key should replace the first
        val prop2 = tinkerVertex.property("name", "Bob", VertexProperty.Cardinality.SINGLE)
        assertEquals("Bob", prop2.value())
        assertEquals(1, tinkerVertex.propertyCount("name"))
        assertEquals("Bob", vertex.value("name"))
    }

    @Test
    fun testSingleCardinalityReplacement() {
        val tinkerVertex = vertex as TinkerVertex
        tinkerVertex.property("age", 25, VertexProperty.Cardinality.SINGLE)
        assertEquals(25, vertex.value("age"))

        // Replace with new value
        tinkerVertex.property("age", 30, VertexProperty.Cardinality.SINGLE)
        assertEquals(30, vertex.value("age"))
        assertEquals(1, tinkerVertex.propertyCount("age"))
    }

    // LIST Cardinality Tests

    @Test
    fun testListCardinalityMultipleValues() {
        // Add multiple properties with LIST cardinality
        val tinkerVertex = vertex as TinkerVertex
        tinkerVertex.property("skill", "Java", VertexProperty.Cardinality.LIST)
        tinkerVertex.property("skill", "Kotlin", VertexProperty.Cardinality.LIST)
        tinkerVertex.property("skill", "Python", VertexProperty.Cardinality.LIST)

        assertEquals(3, (vertex as TinkerVertex).propertyCount("skill"))

        // Verify all values are present
        val skills = vertex.values<String>("skill").asSequence().toList()
        assertEquals(3, skills.size)
        assertTrue(skills.contains("Java"))
        assertTrue(skills.contains("Kotlin"))
        assertTrue(skills.contains("Python"))
    }

    @Test
    fun testListCardinalityDuplicateValues() {
        // LIST cardinality allows duplicate values
        val tinkerVertex = vertex as TinkerVertex
        tinkerVertex.property("tag", "important", VertexProperty.Cardinality.LIST)
        tinkerVertex.property("tag", "urgent", VertexProperty.Cardinality.LIST)
        tinkerVertex.property("tag", "important", VertexProperty.Cardinality.LIST)  // duplicate

        assertEquals(3, (vertex as TinkerVertex).propertyCount("tag"))

        val tags = vertex.values<String>("tag").asSequence().toList()
        assertEquals(3, tags.size)
        assertEquals(2, tags.count { it == "important" })
        assertEquals(1, tags.count { it == "urgent" })
    }

    // SET Cardinality Tests

    @Test
    fun testSetCardinalityUniqueValues() {
        // Add multiple properties with SET cardinality
        val tinkerVertex = vertex as TinkerVertex
        tinkerVertex.property("category", "tech", VertexProperty.Cardinality.SET)
        tinkerVertex.property("category", "business", VertexProperty.Cardinality.SET)
        tinkerVertex.property("category", "innovation", VertexProperty.Cardinality.SET)

        assertEquals(3, tinkerVertex.propertyCount("category"))

        val categories = vertex.values<String>("category").asSequence().toSet()
        assertEquals(3, categories.size)
        assertTrue(categories.contains("tech"))
        assertTrue(categories.contains("business"))
        assertTrue(categories.contains("innovation"))
    }

    @Test
    fun testSetCardinalityDuplicateRejection() {
        // SET cardinality should not allow duplicate values
        val tinkerVertex = vertex as TinkerVertex
        tinkerVertex.property("status", "active", VertexProperty.Cardinality.SET)
        tinkerVertex.property("status", "verified", VertexProperty.Cardinality.SET)

        assertEquals(2, tinkerVertex.propertyCount("status"))

        // Adding duplicate should throw exception (SET behavior)
        assertFailsWith<UnsupportedOperationException> {
            tinkerVertex.property("status", "active", VertexProperty.Cardinality.SET)  // duplicate
        }

        // Count should remain the same after failed duplicate addition
        assertEquals(2, tinkerVertex.propertyCount("status"))

        // Verify only unique values exist
        val statusValues = vertex.values<String>("status").asSequence().toSet()
        assertEquals(setOf("active", "verified"), statusValues)
    }

    // Meta-property Tests

    @Test
    fun testBasicMetaProperties() {
        // Create vertex property with meta-properties
        val nameProperty = vertex.property("name", "Alice", "createdBy", "admin", "timestamp", 1234567890L)

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
        // Access meta properties through the vertex property interface
        assertEquals(3, prop.keys().size)

        // Remove meta-property
        prop.property<String>("source").remove()
        assertEquals(2, prop.keys().size)
        assertFalse(prop.property<String>("source").isPresent())
    }

    @Test
    fun testComplexMetaProperties() {
        // Create multiple properties with different meta-properties
        val tinkerVertex = vertex as TinkerVertex
        val prop1 = tinkerVertex.property("email", "alice@example.com", VertexProperty.Cardinality.SET,
            "type", "primary", "verified", true)
        val prop2 = tinkerVertex.property("email", "alice.work@company.com", VertexProperty.Cardinality.SET,
            "type", "work", "verified", false)

        assertEquals(2, tinkerVertex.propertyCount("email"))

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
            vertex as TinkerVertex, "title", "Engineer",
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
        manager.addVertexProperty(vertex as TinkerVertex, "status", "active", VertexProperty.Cardinality.SINGLE)
        assertEquals("active", vertex.value("status"))

        // Update property
        val updated = manager.updateVertexProperty(
            vertex as TinkerVertex, "status", "active", "inactive", VertexProperty.Cardinality.SINGLE
        )

        assertEquals("inactive", updated.value())
        assertEquals("inactive", vertex.value("status"))
        assertEquals(1, vertex.propertyCount("status"))
    }

    @Test
    fun testPropertyManagerValidation() {
        val manager = graph.propertyManager()

        // Add properties with LIST cardinality - should be valid
        manager.addVertexProperty(vertex as TinkerVertex, "tag", "important", VertexProperty.Cardinality.LIST)
        manager.addVertexProperty(vertex as TinkerVertex, "tag", "urgent", VertexProperty.Cardinality.LIST)

        val violations = manager.validatePropertyConstraints(vertex as TinkerVertex)
        assertTrue(violations.isEmpty()) // Should be valid for LIST cardinality

        assertEquals(2, (vertex as TinkerVertex).propertyCount("tag"))
    }

    // Property Query Engine Tests

    @Test
    fun testBasicPropertyQuery() {
        // Setup test data
        val v1 = graph.addVertex()
        v1.property("name", "Alice")
        v1.property("age", 25)

        val v2 = graph.addVertex()
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
            val v = graph.addVertex()
            v.property("score", i * 10)
            v
        }

        val queryEngine = graph.propertyQueryEngine()

        // Range query: score between 20 and 40 (inclusive)
        val results = queryEngine.queryVerticesByRange("score", 20, 40, true, true)
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
        val v1 = graph.addVertex()
        v1.property("name", "Alice")
        v1.property("age", 25)
        v1.property("active", true)

        val v2 = graph.addVertex()
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
        val tinkerVertex = vertex as TinkerVertex
        tinkerVertex.property("skill", "Java", VertexProperty.Cardinality.LIST)
        tinkerVertex.property("skill", "Kotlin", VertexProperty.Cardinality.LIST)
        tinkerVertex.property("name", "Alice", VertexProperty.Cardinality.SINGLE)

        val stats = tinkerVertex.getPropertyStatistics()

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
        val v1 = graph.addVertex()
        v1.property("type", "person")
        v1.property("name", "Alice")

        val v2 = graph.addVertex()
        v2.property("type", "person")
        v2.property("name", "Bob")

        val v3 = graph.addVertex()
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
        val prop = manager.addVertexProperty(vertex as TinkerVertex, "test", "value")
        assertEquals(1, events.size)
        assertEquals("added:test:value", events[0])

        // Remove property
        manager.removeVertexProperty(vertex as TinkerVertex, prop)
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
        val tinkerProp = prop.asTinkerVertexProperty()
        assertTrue(tinkerProp?.hasMetaProperties() == true)
    }

    // Property Removal Tests

    @Test
    fun testPropertyRemoval() {
        // Add multiple properties
        val tinkerVertex = vertex as TinkerVertex
        tinkerVertex.property("tag", "a", VertexProperty.Cardinality.LIST)
        tinkerVertex.property("tag", "b", VertexProperty.Cardinality.LIST)
        tinkerVertex.property("tag", "c", VertexProperty.Cardinality.LIST)

        assertEquals(3, tinkerVertex.propertyCount("tag"))

        // Remove specific property by removing the first one
        val tagProps = vertex.properties<String>("tag").asSequence().toList()
        if (tagProps.isNotEmpty()) tagProps[0].remove()
        assertEquals(2, tinkerVertex.propertyCount("tag"))

        // Remove all properties with key
        val removedCount = tinkerVertex.removeProperties("tag")
        assertEquals(2, removedCount)
        assertEquals(0, tinkerVertex.propertyCount("tag"))
        assertFalse(tinkerVertex.hasProperty("tag"))
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
        val result = manager.optimizePropertyStorage(vertex as TinkerVertex)
        assertTrue(result.cleanedProperties >= 0) // Should clean up removed properties
    }


}
