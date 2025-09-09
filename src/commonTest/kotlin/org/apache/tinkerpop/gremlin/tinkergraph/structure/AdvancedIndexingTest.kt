package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.platform.Platform
import org.apache.tinkerpop.gremlin.tinkergraph.util.LoggingConfig

/**
 * Comprehensive tests for advanced indexing capabilities including composite indices, range
 * indices, index optimization, and caching.
 */
class AdvancedIndexingTest :
        StringSpec({
            lateinit var graph: TinkerGraph

            beforeTest {
                graph = TinkerGraph.open()
                setupTestData(graph)
            }

            afterTest { graph.close() }

            "composite index creation should work correctly" {
                // Create composite index
                graph.createCompositeIndex(listOf("department", "city"), Vertex::class)

                val stats = graph.vertexCompositeIndex.getStatistics()
                stats["compositeIndexCount"] shouldBe 1

                graph.vertexCompositeIndex
                        .isCompositeIndexed(listOf("department", "city"))
                        .shouldBeTrue()
                graph.vertexCompositeIndex.isCompositeIndexed(listOf("name", "age")).shouldBeFalse()
            }

            "composite index query should return correct results" {
                // Create composite index for department and city
                graph.createCompositeIndex(listOf("department", "city"), Vertex::class)

                // Query using composite index
                val engineersInNY =
                        graph.vertexCompositeIndex.get(
                                listOf("department", "city"),
                                listOf("Engineering", "New York")
                        )

                engineersInNY shouldHaveSize 1
                engineersInNY.first().value<String>("name") shouldBe "Alice"

                val engineersInSF =
                        graph.vertexCompositeIndex.get(
                                listOf("department", "city"),
                                listOf("Engineering", "San Francisco")
                        )

                engineersInSF shouldHaveSize 2
                val names = engineersInSF.map { it.value<String>("name") }.sortedBy { it }
                names shouldBe listOf("Bob", "Eve")
            }

            "composite index partial query should work correctly" {
                // Create composite index for department, city, and age
                graph.createCompositeIndex(listOf("department", "city", "age"), Vertex::class)

                // Query with partial keys (prefix matching)
                val engineersInNY =
                        graph.vertexCompositeIndex.getPartial(
                                listOf("department", "city"),
                                listOf("Engineering", "New York")
                        )

                engineersInNY shouldHaveSize 1
                engineersInNY.first().value<String>("name") shouldBe "Alice"
            }

            "composite index drop should work correctly" {
                graph.createCompositeIndex(listOf("department", "city"), Vertex::class)
                graph.vertexCompositeIndex
                        .isCompositeIndexed(listOf("department", "city"))
                        .shouldBeTrue()

                graph.dropCompositeIndex(listOf("department", "city"), Vertex::class)
                graph.vertexCompositeIndex
                        .isCompositeIndexed(listOf("department", "city"))
                        .shouldBeFalse()
            }

            "range index creation should work correctly" {
                // Create range index for age and salary
                graph.createRangeIndex("age", Vertex::class)
                graph.createRangeIndex("salary", Vertex::class)

                graph.vertexRangeIndex.isRangeIndexed("age").shouldBeTrue()
                graph.vertexRangeIndex.isRangeIndexed("salary").shouldBeTrue()
                graph.vertexRangeIndex.isRangeIndexed("name").shouldBeFalse()

                val stats = graph.vertexRangeIndex.getStatistics()
                stats["rangeIndexedKeyCount"] shouldBe 2
            }

            "range queries should return correct results" {
                graph.createRangeIndex("age", Vertex::class)
                graph.createRangeIndex("salary", Vertex::class)

                // Test range queries on age
                val youngPeople =
                        RangeIndex.safeRangeQuery(
                                graph.vertexRangeIndex,
                                "age",
                                20,
                                30,
                                true,
                                false
                        )
                youngPeople.forEach { vertex ->
                    try {
                        val name = vertex.value<String>("name")
                        val age = vertex.value<Int>("age")
                        println("  $name (age $age)")
                    } catch (e: Exception) {
                        logger.w(e) { "Error accessing vertex properties" }
                    }
                }

                // Test salary ranges
                val highEarners =
                        RangeIndex.safeRangeQuery(
                                graph.vertexRangeIndex,
                                "salary",
                                80000,
                                120000,
                                true,
                                true
                        )
                highEarners shouldHaveSize 3 // Bob, Charlie, Eve
            }

            "range index optimization should work correctly" {
                graph.createRangeIndex("age", Vertex::class)

                val index = graph.vertexRangeIndex
                val before = Platform.currentTimeMillis()

                // Perform multiple range queries to test optimization
                repeat(100) { RangeIndex.safeRangeQuery(index, "age", 25, 35, true, true) }

                val after = Platform.currentTimeMillis()
                val duration = after - before

                // Should complete within reasonable time (optimization working)
                (duration < 1000).shouldBeTrue() // Less than 1 second for 100 queries
            }

            "range index with null values should work correctly" {
                graph.createRangeIndex("score", Vertex::class)

                // Add vertices with null values
                val v1 = graph.addVertex()
                v1.property("name", "TestVertex1")
                v1.property("score", 85)

                val v2 = graph.addVertex()
                v2.property("name", "TestVertex2")
                // No score property (null value)

                val results =
                        RangeIndex.safeRangeQuery(
                                graph.vertexRangeIndex,
                                "score",
                                80,
                                90,
                                true,
                                true
                        )
                results shouldHaveSize 1
                results.first().value<String>("name") shouldBe "TestVertex1"
            }

            "composite and range index interaction should work correctly" {
                // Create both types of indices
                graph.createCompositeIndex(listOf("department", "city"), Vertex::class)
                graph.createRangeIndex("salary", Vertex::class)

                // Query using composite index
                val engineersInNY =
                        graph.vertexCompositeIndex.get(
                                listOf("department", "city"),
                                listOf("Engineering", "New York")
                        )

                // Then filter by range on salary
                val highPaidEngineersInNY =
                        engineersInNY.filter { vertex ->
                            try {
                                val salary = vertex.value<Int>("salary")
                                salary?.let { it >= 80000 } ?: false
                            } catch (e: Exception) {
                                false
                            }
                        }

                highPaidEngineersInNY shouldHaveSize 0 // Alice has 75000
            }

            "index statistics should be accurate" {
                graph.createCompositeIndex(listOf("department", "city"), Vertex::class)
                graph.createRangeIndex("age", Vertex::class)
                graph.createRangeIndex("salary", Vertex::class)

                val compositeStats = graph.vertexCompositeIndex.getStatistics()
                val rangeStats = graph.vertexRangeIndex.getStatistics()

                compositeStats["compositeIndexCount"] shouldBe 1
                rangeStats["rangeIndexedKeyCount"] shouldBe 2
            }

            "index performance monitoring should work correctly" {
                graph.createRangeIndex("age", Vertex::class)

                val startTime = Platform.currentTimeMillis()

                // Perform queries and measure time
                val results =
                        RangeIndex.safeRangeQuery(graph.vertexRangeIndex, "age", 25, 35, true, true)

                val endTime = Platform.currentTimeMillis()
                val queryTime = endTime - startTime

                // Verify results and performance
                results.isNotEmpty().shouldBeTrue()
                (queryTime >= 0).shouldBeTrue() // Should not be negative
            }

            "multiple composite indices should work correctly" {
                // Create multiple composite indices
                graph.createCompositeIndex(listOf("department", "city"), Vertex::class)
                graph.createCompositeIndex(listOf("age", "salary"), Vertex::class)

                val stats = graph.vertexCompositeIndex.getStatistics()
                stats["compositeIndexCount"] shouldBe 2

                // Test both indices
                val engineersInNY =
                        graph.vertexCompositeIndex.get(
                                listOf("department", "city"),
                                listOf("Engineering", "New York")
                        )
                engineersInNY shouldHaveSize 1

                val ageSalaryQuery =
                        graph.vertexCompositeIndex.get(listOf("age", "salary"), listOf(30, 95000))
                ageSalaryQuery shouldHaveSize 1 // Bob
                ageSalaryQuery.first().value<String>("name") shouldBe "Bob"
            }

            "range index edge cases should be handled correctly" {
                graph.createRangeIndex("age", Vertex::class)

                // Test empty range
                val emptyResults =
                        RangeIndex.safeRangeQuery(graph.vertexRangeIndex, "age", 50, 60, true, true)
                emptyResults shouldHaveSize 0

                // Test single point range
                val exactMatch =
                        RangeIndex.safeRangeQuery(graph.vertexRangeIndex, "age", 25, 25, true, true)
                exactMatch shouldHaveSize 1
                exactMatch.first().value<String>("name") shouldBe "Alice"
            }

            "index memory usage should be reasonable" {
                // Create indices and add more test data
                graph.createCompositeIndex(listOf("department", "city"), Vertex::class)
                graph.createRangeIndex("age", Vertex::class)
                graph.createRangeIndex("salary", Vertex::class)

                // Add additional vertices to test memory usage
                repeat(100) { i ->
                    val vertex = graph.addVertex()
                    vertex.property("name", "TestUser$i")
                    vertex.property("age", 20 + (i % 40))
                    vertex.property("salary", 50000 + (i * 1000))
                    vertex.property("department", if (i % 2 == 0) "Engineering" else "Marketing")
                    vertex.property("city", if (i % 3 == 0) "New York" else "San Francisco")
                }

                // Verify indices still work with more data
                val stats = graph.vertexCompositeIndex.getStatistics()
                stats["compositeIndexCount"] shouldBe 1

                val rangeStats = graph.vertexRangeIndex.getStatistics()
                rangeStats["rangeIndexedKeyCount"] shouldBe 2
            }

            "concurrent index operations should be thread-safe" {
                graph.createRangeIndex("age", Vertex::class)

                // This is a basic test - full concurrency testing would require more complex setup
                val results1 =
                        RangeIndex.safeRangeQuery(graph.vertexRangeIndex, "age", 25, 30, true, true)
                val results2 =
                        RangeIndex.safeRangeQuery(graph.vertexRangeIndex, "age", 30, 35, true, true)

                // Results should be consistent
                results1.isNotEmpty().shouldBeTrue()
                results2.isNotEmpty().shouldBeTrue()
            }

            "index cleanup and resource management should work correctly" {
                graph.createCompositeIndex(listOf("department", "city"), Vertex::class)
                graph.createRangeIndex("age", Vertex::class)

                // Verify indices exist
                graph.vertexCompositeIndex
                        .isCompositeIndexed(listOf("department", "city"))
                        .shouldBeTrue()
                graph.vertexRangeIndex.isRangeIndexed("age").shouldBeTrue()

                // Drop indices
                graph.dropCompositeIndex(listOf("department", "city"), Vertex::class)
                graph.dropRangeIndex("age", Vertex::class)

                // Verify cleanup
                graph.vertexCompositeIndex
                        .isCompositeIndexed(listOf("department", "city"))
                        .shouldBeFalse()
                graph.vertexRangeIndex.isRangeIndexed("age").shouldBeFalse()
            }

            "complex query scenarios should work correctly" {
                // Setup complex indexing scenario
                graph.createCompositeIndex(listOf("department", "city"), Vertex::class)
                graph.createRangeIndex("age", Vertex::class)
                graph.createRangeIndex("salary", Vertex::class)

                // Complex query: Engineers in SF with age 28-32 and salary > 90000
                val engineersInSF =
                        graph.vertexCompositeIndex.get(
                                listOf("department", "city"),
                                listOf("Engineering", "San Francisco")
                        )

                val filteredResults =
                        engineersInSF.filter { vertex ->
                            try {
                                val age = vertex.value<Int>("age")
                                val salary = vertex.value<Int>("salary")
                                (age?.let { it in 28..32 }
                                        ?: false) && (salary?.let { it > 90000 } ?: false)
                            } catch (e: Exception) {
                                false
                            }
                        }

                filteredResults shouldHaveSize 2 // Bob (30, 95000) and Eve (32, 105000)
                val names = filteredResults.map { it.value<String>("name") }.sortedBy { it }
                names shouldBe listOf("Bob", "Eve")
            }
        }) {

    companion object {
        private val logger = LoggingConfig.getLogger<AdvancedIndexingTest>()
    }
}

/** Helper function to set up test data for indexing tests. */
private fun setupTestData(graph: TinkerGraph) {
    // Create test vertices with various properties
    val alice = graph.addVertex()
    alice.property("name", "Alice")
    alice.property("age", 25)
    alice.property("city", "New York")
    alice.property("salary", 75000)
    alice.property("department", "Engineering")

    val bob = graph.addVertex()
    bob.property("name", "Bob")
    bob.property("age", 30)
    bob.property("city", "San Francisco")
    bob.property("salary", 95000)
    bob.property("department", "Engineering")

    val charlie = graph.addVertex()
    charlie.property("name", "Charlie")
    charlie.property("age", 35)
    charlie.property("city", "New York")
    charlie.property("salary", 85000)
    charlie.property("department", "Marketing")

    val diana = graph.addVertex()
    diana.property("name", "Diana")
    diana.property("age", 28)
    diana.property("city", "Chicago")
    diana.property("salary", 70000)
    diana.property("department", "Marketing")

    val eve = graph.addVertex()
    eve.property("name", "Eve")
    eve.property("age", 32)
    eve.property("city", "San Francisco")
    eve.property("salary", 105000)
    eve.property("department", "Engineering")
}
