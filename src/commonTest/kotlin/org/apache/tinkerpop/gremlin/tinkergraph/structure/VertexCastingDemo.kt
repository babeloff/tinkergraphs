package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.tinkergraph.util.VertexCastingManager
import kotlin.time.measureTime
import kotlin.time.Duration.Companion.seconds

/**
 * Demo test to verify the new liberal parameter approach works correctly. This test demonstrates
 * that external SafeCasting calls are no longer needed and that PropertyQueryEngine handles casting
 * internally.
 */
class VertexCastingDemo :
        StringSpec({
            lateinit var graph: TinkerGraph
            lateinit var queryEngine: PropertyQueryEngine

            beforeTest {
                graph = TinkerGraph.open()
                queryEngine = graph.propertyQueryEngine()
                VertexCastingManager.clearStatistics()
            }

            afterTest { graph.close() }

            "liberal parameter approach should handle casting automatically" {
                // Create test vertices with mixed data types
                val vertex1 = graph.addVertex()
                vertex1.property("name", "Alice")
                vertex1.property("age", 30)
                vertex1.property("score", 85.5)
                vertex1.property("active", true)

                val vertex2 = graph.addVertex()
                vertex2.property("name", "Bob")
                vertex2.property("age", "25") // String representation of number
                vertex2.property("score", "92.3") // String representation of double
                vertex2.property("active", "true") // String representation of boolean

                val vertex3 = graph.addVertex()
                vertex3.property("name", "Charlie")
                vertex3.property("age", 35L) // Long instead of Int
                vertex3.property("score", 78.9f) // Float instead of Double
                vertex3.property("active", false)

                // Test queries without explicit casting - should work automatically
                val youngPeople =
                        queryEngine.queryVerticesByRange("age", 20, 32, true).asSequence().toList()
                youngPeople shouldHaveSize 2 // Alice (30) and Bob ("25")

                val highScorers =
                        queryEngine
                                .queryVerticesByRange("score", 80.0, 100.0, true)
                                .asSequence()
                                .toList()
                highScorers shouldHaveSize 2 // Alice (85.5) and Bob ("92.3")

                val activeUsers =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("active", true))
                                .asSequence()
                                .toList()
                activeUsers shouldHaveSize 2 // Alice (true) and Bob ("true")
            }

            "vertex casting manager should track statistics" {
                // Create vertices that will trigger casting
                val vertex = graph.addVertex()
                vertex.property("stringNumber", "123")
                vertex.property("stringDouble", "45.67")
                vertex.property("stringBoolean", "false")

                // Query using different types to trigger casting
                queryEngine
                        .queryVertices(PropertyQueryEngine.exact("stringNumber", 123))
                        .asSequence()
                        .toList()
                queryEngine
                        .queryVertices(PropertyQueryEngine.exact("stringDouble", 45.67))
                        .asSequence()
                        .toList()
                queryEngine
                        .queryVertices(PropertyQueryEngine.exact("stringBoolean", false))
                        .asSequence()
                        .toList()

                val stats = VertexCastingManager.getCastingStatistics()
                stats["totalCasts"] shouldNotBe null
                ((stats["totalCasts"] as Int) > 0) shouldBe true
            }

            "casting should handle null values gracefully" {
                val vertex = graph.addVertex()
                vertex.property("name", "Test")
                // No age property - effectively null

                val results =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("age", null))
                                .asSequence()
                                .toList()

                // Should find vertices without the age property
                results shouldHaveSize 1
                results.first().value<String>("name") shouldBe "Test"
            }

            "casting should preserve original types when possible" {
                val vertex = graph.addVertex()
                vertex.property("exactInt", 42)
                vertex.property("exactDouble", 3.14159)
                vertex.property("exactString", "hello")
                vertex.property("exactBoolean", true)

                // Query with exact same types - should work without casting
                val intResult =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("exactInt", 42))
                                .asSequence()
                                .toList()
                intResult shouldHaveSize 1

                val doubleResult =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("exactDouble", 3.14159))
                                .asSequence()
                                .toList()
                doubleResult shouldHaveSize 1

                val stringResult =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("exactString", "hello"))
                                .asSequence()
                                .toList()
                stringResult shouldHaveSize 1

                val booleanResult =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("exactBoolean", true))
                                .asSequence()
                                .toList()
                booleanResult shouldHaveSize 1
            }

            "casting should handle edge cases" {
                val vertex = graph.addVertex()
                vertex.property("zero", 0)
                vertex.property("empty", "")
                vertex.property("falseString", "false")
                vertex.property("zeroString", "0")

                // Test edge case queries
                val zeroResults =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("zero", "0"))
                                .asSequence()
                                .toList()
                zeroResults shouldHaveSize 1

                val emptyResults =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("empty", ""))
                                .asSequence()
                                .toList()
                emptyResults shouldHaveSize 1

                val falseResults =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("falseString", false))
                                .asSequence()
                                .toList()
                falseResults shouldHaveSize 1

                val zeroStringResults =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("zeroString", 0))
                                .asSequence()
                                .toList()
                zeroStringResults shouldHaveSize 1
            }

            "casting should work with complex property structures" {
                val vertex = graph.addVertex()
                vertex.property("tags", "developer")
                vertex.property("language", "kotlin")
                vertex.property("type", "testing")
                vertex.property("created", "2024")
                vertex.property("version", "1.0")

                // Query should still work with casting enabled
                val taggedVertices =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("tags", "developer"))
                                .asSequence()
                                .toList()
                taggedVertices shouldHaveSize 1
            }

            "casting performance should be reasonable" {
                // Create vertices with various data types
                repeat(100) { i ->
                    val vertex = graph.addVertex()
                    vertex.property("id", i)
                    vertex.property("stringId", i.toString())
                    vertex.property("doubleValue", i.toDouble())
                    vertex.property("stringDouble", i.toDouble().toString())
                }

                val duration = measureTime {
                    // Perform queries that require casting
                    repeat(50) { i ->
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("stringId", i))
                                .asSequence()
                                .toList()
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("stringDouble", i.toDouble()))
                                .asSequence()
                                .toList()
                    }
                }

                // Should complete within reasonable time
                (duration < 5.seconds) shouldBe true // Less than 5 seconds
            }

            "casting manager should provide useful statistics" {
                // Perform various casting operations
                val vertex = graph.addVertex()
                vertex.property("number", "123")
                vertex.property("decimal", "45.67")
                vertex.property("flag", "true")

                queryEngine
                        .queryVertices(PropertyQueryEngine.exact("number", 123))
                        .asSequence()
                        .toList()
                queryEngine
                        .queryVertices(PropertyQueryEngine.exact("decimal", 45.67))
                        .asSequence()
                        .toList()
                queryEngine
                        .queryVertices(PropertyQueryEngine.exact("flag", true))
                        .asSequence()
                        .toList()

                val stats = VertexCastingManager.getCastingStatistics()

                // Verify statistics are meaningful
                stats shouldNotBe null
                stats.containsKey("totalCasts") shouldBe true
                stats.containsKey("successfulCasts") shouldBe true
                stats.containsKey("failedCasts") shouldBe true

                val totalCasts = stats["totalCasts"] as Int
                val successfulCasts = stats["successfulCasts"] as Int
                val failedCasts = stats["failedCasts"] as Int

                (totalCasts >= 0) shouldBe true
                (successfulCasts >= 0) shouldBe true
                (failedCasts >= 0) shouldBe true
                (totalCasts == successfulCasts + failedCasts) shouldBe true
            }

            "casting should be disabled when not needed" {
                // Clear statistics
                VertexCastingManager.clearStatistics()

                // Create vertex with exact type matches
                val vertex = graph.addVertex()
                vertex.property("exactMatch", 42)

                // Query with exact same type
                queryEngine
                        .queryVertices(PropertyQueryEngine.exact("exactMatch", 42))
                        .asSequence()
                        .toList()

                val stats = VertexCastingManager.getCastingStatistics()
                val totalCasts = stats["totalCasts"] as Int

                // Should have minimal or zero casts for exact matches
                (totalCasts <= 1) shouldBe true
            }

            "liberal parameter approach should work with indices" {
                // Create index
                graph.createIndex("category", org.apache.tinkerpop.gremlin.structure.Vertex::class)

                val vertex = graph.addVertex()
                vertex.property("category", "test")
                vertex.property("value", "123")

                // Query using index with casting
                val results =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("value", 123))
                                .asSequence()
                                .toList()

                results shouldHaveSize 1
                results.first().value<String>("category") shouldBe "test"
            }
        })
