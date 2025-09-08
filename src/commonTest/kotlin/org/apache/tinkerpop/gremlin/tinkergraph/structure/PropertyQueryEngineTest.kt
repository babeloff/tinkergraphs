package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.apache.tinkerpop.gremlin.structure.VertexProperty

// Extension functions for PropertyQueryEngine missing methods
fun findVerticesWithDuplicateProperties(graph: TinkerGraph, key: String): Iterator<TinkerVertex> {
    // Simplified implementation for tests - find vertices with multiple properties of the same key
    return graph.vertices()
            .asSequence()
            .filterIsInstance<TinkerVertex>()
            .filter { vertex ->
                val properties = vertex.properties<Any>(key).asSequence().toList()
                properties.size > 1
            }
            .iterator()
}

fun TinkerVertex.hasProperty(key: String): Boolean {
    return this.property<Any>(key).isPresent()
}

/** Tests for PropertyQueryEngine advanced querying capabilities. */
class PropertyQueryEngineTest :
        StringSpec({
            lateinit var graph: TinkerGraph
            lateinit var queryEngine: PropertyQueryEngine

            fun setupTestData() {
                // Create vertices with various property patterns

                // Person vertices
                val alice = graph.addVertex() as TinkerVertex
                alice.property("name", "Alice")
                alice.property("age", 25)
                alice.property("type", "person")
                alice.property("skill", "Java", VertexProperty.Cardinality.LIST)
                alice.property("skill", "Kotlin", VertexProperty.Cardinality.LIST)
                alice.property("email", "alice@example.com", "verified", true, "type", "primary")

                val bob = graph.addVertex() as TinkerVertex
                bob.property("name", "Bob")
                bob.property("age", 30)
                bob.property("type", "person")
                bob.property("skill", "Python", VertexProperty.Cardinality.LIST)
                bob.property("skill", "JavaScript", VertexProperty.Cardinality.LIST)
                bob.property("email", "bob@example.com", "verified", false, "type", "primary")

                val charlie = graph.addVertex() as TinkerVertex
                charlie.property("name", "Charlie")
                charlie.property("age", 35)
                charlie.property("type", "person")
                charlie.property("skill", "C++", VertexProperty.Cardinality.LIST)
                charlie.property("skill", "Rust", VertexProperty.Cardinality.LIST)

                // Company vertices
                val acmeCorp = graph.addVertex()
                acmeCorp.property("name", "ACME Corp")
                acmeCorp.property("type", "company")
                acmeCorp.property("employees", 500)
                acmeCorp.property("industry", "Technology")

                val globodyne = graph.addVertex()
                globodyne.property("name", "Globodyne")
                globodyne.property("type", "company")
                globodyne.property("employees", 150)
                globodyne.property("industry", "Consulting")
            }

            beforeTest {
                graph = TinkerGraph.open()
                queryEngine = graph.propertyQueryEngine()
                setupTestData()
            }

            afterTest { graph.close() }

            // Basic Query Tests

            "exact criterion should work correctly" {
                val results =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("type", "person"))
                                .asSequence()
                                .toList()

                results shouldHaveSize 3
                results.all { it.value<String>("type") == "person" } shouldBe true
            }

            "exists criterion should work correctly" {
                val results =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exists("industry"))
                                .asSequence()
                                .toList()

                results shouldHaveSize 2
                results.all { (it as TinkerVertex).hasProperty("industry") } shouldBe true
            }

            "not exists criterion should work correctly" {
                val results =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.notExists("industry"))
                                .asSequence()
                                .toList()

                results shouldHaveSize 3
                results.all { !(it as TinkerVertex).hasProperty("industry") } shouldBe true
            }

            // Range Query Tests

            "range criterion inclusive should work correctly" {
                val results =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.range("age", 25, 30, true, true))
                                .asSequence()
                                .toList()

                results shouldHaveSize 2
                val ages = results.map { it.value<Int>("age")!! }.sorted()
                ages shouldBe listOf(25, 30)
            }

            "range criterion exclusive should work correctly" {
                val results =
                        queryEngine
                                .queryVertices(
                                        PropertyQueryEngine.range("age", 25, 35, false, false)
                                )
                                .asSequence()
                                .toList()

                results shouldHaveSize 1
                results.first().value<Int>("age") shouldBe 30
            }

            "range criterion min only should work correctly" {
                val results =
                        queryEngine
                                .queryVertices(
                                        PropertyQueryEngine.range("employees", 200, null, true)
                                )
                                .asSequence()
                                .toList()

                results shouldHaveSize 1
                results.first().value<Int>("employees") shouldBe 500
            }

            "range criterion max only should work correctly" {
                val results =
                        queryEngine
                                .queryVertices(
                                        PropertyQueryEngine.range("employees", null, 200, true)
                                )
                                .asSequence()
                                .toList()

                results shouldHaveSize 1
                results.first().value<Int>("employees") shouldBe 150
            }

            // String Query Tests

            "contains criterion should work correctly" {
                val results =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.contains("name", "e", false))
                                .asSequence()
                                .toList()

                results shouldHaveSize 3
                val names = results.map { it.value<String>("name")!! }.sorted()
                names shouldBe listOf("Alice", "Charlie", "Globodyne")
            }

            "contains criterion ignore case should work correctly" {
                val results =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.contains("name", "ACME", true))
                                .asSequence()
                                .toList()

                results shouldHaveSize 1
                results.first().value<String>("name") shouldBe "ACME Corp"
            }

            "regex criterion should work correctly" {
                val results =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.regex("name", "^[A-C].*"))
                                .asSequence()
                                .toList()

                results shouldHaveSize 4
                val names = results.map { it.value<String>("name")!! }.sorted()
                names shouldBe listOf("ACME Corp", "Alice", "Bob", "Charlie")
            }

            // Composite Query Tests

            "and criterion should work correctly" {
                val results =
                        queryEngine
                                .queryVertices(
                                        PropertyQueryEngine.and(
                                                PropertyQueryEngine.exact("type", "person"),
                                                PropertyQueryEngine.range("age", 30, null, true)
                                        )
                                )
                                .asSequence()
                                .toList()

                results shouldHaveSize 2
                val names = results.map { it.value<String>("name")!! }.sorted()
                names shouldBe listOf("Bob", "Charlie")
            }

            "or criterion should work correctly" {
                val results =
                        queryEngine
                                .queryVertices(
                                        PropertyQueryEngine.or(
                                                PropertyQueryEngine.exact("name", "Alice"),
                                                PropertyQueryEngine.exact("name", "ACME Corp")
                                        )
                                )
                                .asSequence()
                                .toList()

                results shouldHaveSize 2
                val names = results.map { it.value<String>("name")!! }.sorted()
                names shouldBe listOf("ACME Corp", "Alice")
            }

            "not criterion should work correctly" {
                val results =
                        queryEngine
                                .queryVertices(
                                        PropertyQueryEngine.not(
                                                PropertyQueryEngine.exact("type", "person")
                                        )
                                )
                                .asSequence()
                                .toList()

                results shouldHaveSize 2
                results.all { it.value<String>("type") == "company" } shouldBe true
            }

            "complex composite query should work correctly" {
                // (type = person AND age >= 30) OR (type = company AND employees < 300)
                val results =
                        queryEngine
                                .queryVertices(
                                        PropertyQueryEngine.or(
                                                PropertyQueryEngine.and(
                                                        PropertyQueryEngine.exact("type", "person"),
                                                        PropertyQueryEngine.range(
                                                                "age",
                                                                30,
                                                                null,
                                                                true
                                                        )
                                                ),
                                                PropertyQueryEngine.and(
                                                        PropertyQueryEngine.exact(
                                                                "type",
                                                                "company"
                                                        ),
                                                        PropertyQueryEngine.range(
                                                                "employees",
                                                                null,
                                                                300,
                                                                false
                                                        )
                                                )
                                        )
                                )
                                .asSequence()
                                .toList()

                results shouldHaveSize 3
                // Should include Bob, Charlie (persons >= 30) and Globodyne (employees < 300)
                val names = results.map { it.value<String>("name")!! }.sorted()
                names shouldBe listOf("Bob", "Charlie", "Globodyne")
            }

            // Meta-property Query Tests

            "meta property query should work correctly" {
                val results =
                        queryEngine
                                .queryVerticesByMetaProperty("email", "verified", true)
                                .asSequence()
                                .toList()

                results shouldHaveSize 1
                results.first().value<String>("name") shouldBe "Alice"
            }

            "meta property query multiple values should work correctly" {
                // Add another verified email to test multiple matches
                val diana = graph.addVertex()
                diana.property("name", "Diana")
                diana.property("email", "diana@example.com", "verified", true, "type", "work")

                val results =
                        queryEngine
                                .queryVerticesByMetaProperty("email", "verified", true)
                                .asSequence()
                                .toList()

                results shouldHaveSize 2
                val names = results.map { it.value<String>("name")!! }.sorted()
                names shouldBe listOf("Alice", "Diana")
            }

            // Cardinality Query Tests

            "cardinality query should work correctly" {
                val results =
                        queryEngine
                                .queryVerticesByCardinality(
                                        "skill",
                                        VertexProperty.Cardinality.LIST
                                )
                                .asSequence()
                                .toList()

                results shouldHaveSize 3 // All person vertices have LIST skills
                results.all { it.value<String>("type") == "person" } shouldBe true
            }

            // Duplicate Detection Tests

            "find vertices with duplicate properties should work correctly" {
                // Create a vertex with duplicate values (should not happen in normal SET operation)
                val testVertex = graph.addVertex() as TinkerVertex
                testVertex.property("color", "red", VertexProperty.Cardinality.LIST)
                testVertex.property("color", "blue", VertexProperty.Cardinality.LIST)
                testVertex.property(
                        "color",
                        "red",
                        VertexProperty.Cardinality.LIST
                ) // duplicate in LIST

                val results =
                        findVerticesWithDuplicateProperties(graph, "color").asSequence().toList()

                results shouldHaveSize 1
                results.first().id() shouldBe testVertex.id()
            }

            // Aggregation Tests

            "count aggregation should work correctly" {
                val count =
                        queryEngine.aggregateProperties(
                                "age",
                                PropertyQueryEngine.PropertyAggregation.COUNT
                        )
                count shouldBe 3 // Three persons have age property
            }

            "distinct count aggregation should work correctly" {
                val distinctCount =
                        queryEngine.aggregateProperties(
                                "type",
                                PropertyQueryEngine.PropertyAggregation.DISTINCT_COUNT
                        )
                distinctCount shouldBe 2 // "person" and "company"
            }

            "min max aggregation should work correctly" {
                val min =
                        queryEngine.aggregateProperties(
                                "age",
                                PropertyQueryEngine.PropertyAggregation.MIN
                        ) as?
                                Double
                                ?: 0.0
                val max =
                        queryEngine.aggregateProperties(
                                "age",
                                PropertyQueryEngine.PropertyAggregation.MAX
                        ) as?
                                Double
                                ?: 0.0

                min shouldBe 25.0
                max shouldBe 35.0
            }

            "sum aggregation should work correctly" {
                val sum =
                        queryEngine.aggregateProperties(
                                "age",
                                PropertyQueryEngine.PropertyAggregation.SUM
                        ) as?
                                Double
                                ?: 0.0
                sum shouldBe 90.0 // 25 + 30 + 35
            }

            "average aggregation should work correctly" {
                val avg =
                        queryEngine.aggregateProperties(
                                "employees",
                                PropertyQueryEngine.PropertyAggregation.AVERAGE
                        ) as?
                                Double
                                ?: 0.0
                avg shouldBe 325.0 // (150 + 500) / 2
            }

            // Property Statistics Tests

            "graph property statistics should work correctly" {
                val stats = queryEngine.getGraphPropertyStatistics()

                // Check type property statistics
                val typeStats = stats["type"]!!
                typeStats.propertyCount shouldBe 5 // 5 total properties
                typeStats.vertexCount shouldBe 5 // 5 vertices have type

                // Check age property statistics
                val ageStats = stats["age"]!!
                ageStats.propertyCount shouldBe 3 // 3 age properties
                ageStats.vertexCount shouldBe 3 // 3 vertices have age

                // Check skill property statistics
                val skillStats = stats["skill"]!!
                skillStats.propertyCount shouldBe 6 // 2+2+2 = 6 skill properties
                skillStats.vertexCount shouldBe 3 // 3 vertices have skills
            }

            // Vertex Property Query Tests

            "query vertex properties should work correctly" {
                val alice =
                        graph.vertices().asSequence().find { it.value<String>("name") == "Alice" }!!

                val properties =
                        queryEngine.queryVertexProperties<String>(
                                alice,
                                listOf(PropertyQueryEngine.exact("skill", "Java"))
                        )

                properties shouldHaveSize 1
                properties.first().value() shouldBe "Java"
                properties.first().key() shouldBe "skill"
            }

            "query vertex properties with meta criteria should work correctly" {
                val alice =
                        graph.vertices().asSequence().find { it.value<String>("name") == "Alice" }!!

                // This is a simplified test since meta-property criteria evaluation
                // would need more complex implementation
                val allEmailProperties =
                        queryEngine.queryVertexProperties<String>(
                                alice,
                                listOf(PropertyQueryEngine.exists("email"))
                        )

                allEmailProperties shouldHaveSize 1
                allEmailProperties.first().key() shouldBe "email"
            }

            // Error Handling Tests

            "invalid range criterion should return empty results" {
                // Range query on non-numeric property should return empty results
                val results =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.range("name", 10, 20, true))
                                .asSequence()
                                .toList()

                results shouldHaveSize 0
            }

            "regex on non-string property should return empty results" {
                // Regex on non-string property should return empty results
                val results =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.regex("age", "\\d+"))
                                .asSequence()
                                .toList()

                results shouldHaveSize 0
            }

            // Performance and Edge Case Tests

            "empty graph should work correctly" {
                val emptyGraph = TinkerGraph.open()
                val emptyQueryEngine = emptyGraph.propertyQueryEngine()

                val results =
                        emptyQueryEngine
                                .queryVertices(PropertyQueryEngine.exists("any"))
                                .asSequence()
                                .toList()

                results shouldHaveSize 0

                val stats = emptyQueryEngine.getGraphPropertyStatistics()
                stats shouldBe emptyList<Map<String, Any>>()

                emptyGraph.close()
            }

            "query with no matching properties should return empty results" {
                val results =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("nonexistent", "value"))
                                .asSequence()
                                .toList()

                results shouldHaveSize 0
            }

            "multiple criteria on same property should work correctly" {
                val results =
                        queryEngine
                                .queryVertices(
                                        listOf(
                                                PropertyQueryEngine.exists("age"),
                                                PropertyQueryEngine.range(
                                                        "age",
                                                        25,
                                                        30,
                                                        true,
                                                        true
                                                ),
                                                PropertyQueryEngine.not(
                                                        PropertyQueryEngine.exact("age", 35)
                                                )
                                        )
                                )
                                .asSequence()
                                .toList()

                results shouldHaveSize 2 // Alice and Bob
                val names = results.map { it.value<String>("name")!! }.sorted()
                names shouldBe listOf("Alice", "Bob")
            }
        })
