package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.tinkergraph.util.SafeCasting
import kotlin.test.*

/**
 * Tests for PropertyQueryEngine advanced querying capabilities.
 */
class PropertyQueryEngineTest {

    private lateinit var graph: TinkerGraph
    private lateinit var queryEngine: PropertyQueryEngine

    @BeforeTest
    fun setup() {
        graph = TinkerGraph.open()
        queryEngine = graph.propertyQueryEngine()
        setupTestData()
    }

    @AfterTest
    fun cleanup() {
        graph.close()
    }

    private fun setupTestData() {
        // Create vertices with various property patterns

        // Person vertices
        val alice = SafeCasting.safeCastVertex(graph.addVertex())
        alice.property("name", "Alice")
        alice.property("age", 25)
        alice.property("type", "person")
        alice.property("skill", "Java", VertexProperty.Cardinality.LIST)
        alice.property("skill", "Kotlin", VertexProperty.Cardinality.LIST)
        alice.property("email", "alice@example.com", "verified", true, "type", "primary")

        val bob = SafeCasting.safeCastVertex(graph.addVertex())
        bob.property("name", "Bob")
        bob.property("age", 30)
        bob.property("type", "person")
        bob.property("skill", "Python", VertexProperty.Cardinality.LIST)
        bob.property("skill", "JavaScript", VertexProperty.Cardinality.LIST)
        bob.property("email", "bob@example.com", "verified", false, "type", "primary")

        val charlie = SafeCasting.safeCastVertex(graph.addVertex())
        charlie.property("name", "Charlie")
        charlie.property("age", 35)
        charlie.property("type", "person")
        charlie.property("skill", "C++", VertexProperty.Cardinality.LIST)
        charlie.property("skill", "Rust", VertexProperty.Cardinality.LIST)

        // Company vertices
        val acmeCorp = SafeCasting.safeCastVertex(graph.addVertex())
        acmeCorp.property("name", "ACME Corp")
        acmeCorp.property("type", "company")
        acmeCorp.property("employees", 500)
        acmeCorp.property("industry", "Technology")

        val globodyne = SafeCasting.safeCastVertex(graph.addVertex())
        globodyne.property("name", "Globodyne")
        globodyne.property("type", "company")
        globodyne.property("employees", 150)
        globodyne.property("industry", "Consulting")
    }

    // Basic Query Tests

    @Test
    fun testExactCriterion() {
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.exact("type", "person")
        ).asSequence().toList()

        assertEquals(3, results.size)
        assertTrue(results.all { it.value<String>("type") == "person" })
    }

    @Test
    fun testExistsCriterion() {
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.exists("industry")
        ).asSequence().toList()

        assertEquals(2, results.size)
        assertTrue(results.all { it.hasProperty("industry") })
    }

    @Test
    fun testNotExistsCriterion() {
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.notExists("industry")
        ).asSequence().toList()

        assertEquals(3, results.size)
        assertTrue(results.all { !it.hasProperty("industry") })
    }

    // Range Query Tests

    @Test
    fun testRangeCriterionInclusive() {
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.range("age", 25, 30, true, true)
        ).asSequence().toList()

        assertEquals(2, results.size)
        val ages = results.map { it.value<Int>("age")!! }.sorted()
        assertEquals(listOf(25, 30), ages)
    }

    @Test
    fun testRangeCriterionExclusive() {
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.range("age", 25, 35, false, false)
        ).asSequence().toList()

        assertEquals(1, results.size)
        assertEquals(30, results.first().value<Int>("age"))
    }

    @Test
    fun testRangeCriterionMinOnly() {
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.range("employees", 200, null, true)
        ).asSequence().toList()

        assertEquals(1, results.size)
        assertEquals(500, results.first().value<Int>("employees"))
    }

    @Test
    fun testRangeCriterionMaxOnly() {
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.range("employees", null, 200, true)
        ).asSequence().toList()

        assertEquals(1, results.size)
        assertEquals(150, results.first().value<Int>("employees"))
    }

    // String Query Tests

    @Test
    fun testContainsCriterion() {
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.contains("name", "e", false)
        ).asSequence().toList()

        assertEquals(3, results.size)
        val names = results.map { it.value<String>("name")!! }.sorted()
        assertEquals(listOf("Alice", "Charlie", "Globodyne"), names)
    }

    @Test
    fun testContainsCriterionIgnoreCase() {
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.contains("name", "ACME", true)
        ).asSequence().toList()

        assertEquals(1, results.size)
        assertEquals("ACME Corp", results.first().value<String>("name"))
    }

    @Test
    fun testRegexCriterion() {
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.regex("name", "^[A-C].*")
        ).asSequence().toList()

        assertEquals(4, results.size)
        val names = results.map { it.value<String>("name")!! }.sorted()
        assertEquals(listOf("ACME Corp", "Alice", "Bob", "Charlie"), names)
    }

    // Composite Query Tests

    @Test
    fun testAndCriterion() {
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.and(
                PropertyQueryEngine.exact("type", "person"),
                PropertyQueryEngine.range("age", 30, null, true)
            )
        ).asSequence().toList()

        assertEquals(2, results.size)
        val names = results.map { it.value<String>("name")!! }.sorted()
        assertEquals(listOf("Bob", "Charlie"), names)
    }

    @Test
    fun testOrCriterion() {
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.or(
                PropertyQueryEngine.exact("name", "Alice"),
                PropertyQueryEngine.exact("name", "ACME Corp")
            )
        ).asSequence().toList()

        assertEquals(2, results.size)
        val names = results.map { it.value<String>("name")!! }.sorted()
        assertEquals(listOf("ACME Corp", "Alice"), names)
    }

    @Test
    fun testNotCriterion() {
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.not(
                PropertyQueryEngine.exact("type", "person")
            )
        ).asSequence().toList()

        assertEquals(2, results.size)
        assertTrue(results.all { it.value<String>("type") == "company" })
    }

    @Test
    fun testComplexCompositeQuery() {
        // (type = person AND age >= 30) OR (type = company AND employees < 300)
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.or(
                PropertyQueryEngine.and(
                    PropertyQueryEngine.exact("type", "person"),
                    PropertyQueryEngine.range("age", 30, null, true)
                ),
                PropertyQueryEngine.and(
                    PropertyQueryEngine.exact("type", "company"),
                    PropertyQueryEngine.range("employees", null, 300, false)
                )
            )
        ).asSequence().toList()

        assertEquals(3, results.size)
        // Should include Bob, Charlie (persons >= 30) and Globodyne (employees < 300)
        val names = results.map { it.value<String>("name")!! }.sorted()
        assertEquals(listOf("Bob", "Charlie", "Globodyne"), names)
    }

    // Meta-property Query Tests

    @Test
    fun testMetaPropertyQuery() {
        val results = queryEngine.queryVerticesByMetaProperty(
            "email", "verified", true
        ).asSequence().toList()

        assertEquals(1, results.size)
        assertEquals("Alice", results.first().value<String>("name"))
    }

    @Test
    fun testMetaPropertyQueryMultipleValues() {
        // Add another verified email to test multiple matches
        val diana = SafeCasting.safeCastVertex(graph.addVertex())
        diana.property("name", "Diana")
        diana.property("email", "diana@example.com", "verified", true, "type", "work")

        val results = queryEngine.queryVerticesByMetaProperty(
            "email", "verified", true
        ).asSequence().toList()

        assertEquals(2, results.size)
        val names = results.map { it.value<String>("name")!! }.sorted()
        assertEquals(listOf("Alice", "Diana"), names)
    }

    // Cardinality Query Tests

    @Test
    fun testCardinalityQuery() {
        val results = queryEngine.queryVerticesByCardinality(
            "skill", VertexProperty.Cardinality.LIST
        ).asSequence().toList()

        assertEquals(3, results.size) // All person vertices have LIST skills
        assertTrue(results.all { it.value<String>("type") == "person" })
    }

    // Duplicate Detection Tests

    @Test
    fun testFindVerticesWithDuplicateProperties() {
        // Create a vertex with duplicate values (should not happen in normal SET operation)
        val testVertex = SafeCasting.safeCastVertex(graph.addVertex())
        testVertex.property("color", "red", VertexProperty.Cardinality.LIST)
        testVertex.property("color", "blue", VertexProperty.Cardinality.LIST)
        testVertex.property("color", "red", VertexProperty.Cardinality.LIST) // duplicate in LIST

        val results = queryEngine.findVerticesWithDuplicateProperties("color")
            .asSequence().toList()

        assertEquals(1, results.size)
        assertEquals(testVertex.id(), results.first().id())
    }

    // Aggregation Tests

    @Test
    fun testCountAggregation() {
        val count = queryEngine.aggregateProperties("age", PropertyQueryEngine.PropertyAggregation.COUNT)
        assertEquals(3, count) // Three persons have age property
    }

    @Test
    fun testDistinctCountAggregation() {
        val distinctCount = queryEngine.aggregateProperties("type", PropertyQueryEngine.PropertyAggregation.DISTINCT_COUNT)
        assertEquals(2, distinctCount) // "person" and "company"
    }

    @Test
    fun testMinMaxAggregation() {
        val min = queryEngine.aggregateProperties("age", PropertyQueryEngine.PropertyAggregation.MIN) as? Double ?: 0.0
        val max = queryEngine.aggregateProperties("age", PropertyQueryEngine.PropertyAggregation.MAX) as? Double ?: 0.0

        assertEquals(25.0, min)
        assertEquals(35.0, max)
    }

    @Test
    fun testSumAggregation() {
        val sum = queryEngine.aggregateProperties("age", PropertyQueryEngine.PropertyAggregation.SUM) as? Double ?: 0.0
        assertEquals(90.0, sum) // 25 + 30 + 35
    }

    @Test
    fun testAverageAggregation() {
        val avg = queryEngine.aggregateProperties("employees", PropertyQueryEngine.PropertyAggregation.AVERAGE) as? Double ?: 0.0
        assertEquals(325.0, avg) // (150 + 500) / 2
    }

    // Property Statistics Tests

    @Test
    fun testGraphPropertyStatistics() {
        val stats = queryEngine.getGraphPropertyStatistics()

        // Check type property statistics
        val typeStats = stats["type"]!!
        assertEquals(5, typeStats.propertyCount) // 5 total properties
        assertEquals(5, typeStats.vertexCount)   // 5 vertices have type

        // Check age property statistics
        val ageStats = stats["age"]!!
        assertEquals(3, ageStats.propertyCount) // 3 age properties
        assertEquals(3, ageStats.vertexCount)   // 3 vertices have age

        // Check skill property statistics
        val skillStats = stats["skill"]!!
        assertEquals(6, skillStats.propertyCount) // 2+2+2 = 6 skill properties
        assertEquals(3, skillStats.vertexCount)   // 3 vertices have skills
    }

    // Vertex Property Query Tests

    @Test
    fun testQueryVertexProperties() {
        val alice = SafeCasting.findVertexByName(graph.vertices().asSequence(), "Alice")!!

        val properties = queryEngine.queryVertexProperties<String>(
            alice,
            listOf(PropertyQueryEngine.exact("skill", "Java"))
        )

        assertEquals(1, properties.size)
        assertEquals("Java", properties.first().value())
        assertEquals("skill", properties.first().key())
    }

    @Test
    fun testQueryVertexPropertiesWithMetaCriteria() {
        val alice = SafeCasting.findVertexByName(graph.vertices().asSequence(), "Alice")!!

        // This is a simplified test since meta-property criteria evaluation
        // would need more complex implementation
        val allEmailProperties = queryEngine.queryVertexProperties<String>(
            alice,
            listOf(PropertyQueryEngine.exists("email"))
        )

        assertEquals(1, allEmailProperties.size)
        assertEquals("email", allEmailProperties.first().key())
    }

    // Error Handling Tests

    @Test
    fun testInvalidRangeCriterion() {
        // Range query on non-numeric property should return empty results
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.range("name", 10, 20, true)
        ).asSequence().toList()

        assertEquals(0, results.size)
    }

    @Test
    fun testRegexOnNonStringProperty() {
        // Regex on non-string property should return empty results
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.regex("age", "\\d+")
        ).asSequence().toList()

        assertEquals(0, results.size)
    }

    // Performance and Edge Case Tests

    @Test
    fun testEmptyGraph() {
        val emptyGraph = TinkerGraph.open()
        val emptyQueryEngine = emptyGraph.propertyQueryEngine()

        val results = emptyQueryEngine.queryVertices(
            PropertyQueryEngine.exists("any")
        ).asSequence().toList()

        assertEquals(0, results.size)

        val stats = emptyQueryEngine.getGraphPropertyStatistics()
        assertTrue(stats.isEmpty())

        emptyGraph.close()
    }

    @Test
    fun testQueryWithNoMatchingProperties() {
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.exact("nonexistent", "value")
        ).asSequence().toList()

        assertEquals(0, results.size)
    }

    @Test
    fun testMultipleCriteriaOnSameProperty() {
        val results = queryEngine.queryVertices(
            listOf(
                PropertyQueryEngine.exists("age"),
                PropertyQueryEngine.range("age", 25, 30, true, true),
                PropertyQueryEngine.not(PropertyQueryEngine.exact("age", 35))
            )
        ).asSequence().toList()

        assertEquals(2, results.size) // Alice and Bob
        val names = results.map { it.value<String>("name")!! }.sorted()
        assertEquals(listOf("Alice", "Bob"), names)
    }
}
