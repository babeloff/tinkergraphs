package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.apache.tinkerpop.gremlin.tinkergraph.util.SafeCasting
import org.apache.tinkerpop.gremlin.tinkergraph.util.LoggingConfig

/**
 * Simple diagnostic test to verify property setting and retrieval works correctly across all
 * platforms (JVM, JS, Native).
 */
class PropertyDiagnosticTest :
        StringSpec({

            companion object {
                private val logger = LoggingConfig.getLogger<PropertyDiagnosticTest>()
            }
            lateinit var graph: TinkerGraph

            beforeTest { graph = TinkerGraph.open() }

            afterTest { graph.close() }

            "basic property setting should work correctly" {
                // Test 1: Create vertex and add properties one by one
                val vertex = SafeCasting.safeCastVertex(graph.addVertex())

                // Add properties
                vertex.property("name", "Alice")
                vertex.property("age", 25)
                vertex.property("department", "Engineering")

                // Verify properties were set
                vertex.value<String>("name") shouldBe "Alice"
                vertex.value<Int>("age") shouldBe 25
                vertex.value<String>("department") shouldBe "Engineering"

                // Verify keys
                val keys = vertex.keys()
                keys.contains("name").shouldBeTrue()
                keys.contains("age").shouldBeTrue()
                keys.contains("department").shouldBeTrue()
                keys shouldHaveSize 3
            }

            "vertex with initial properties should work correctly" {
                // Test 2: Create vertex with initial properties
                val vertex = SafeCasting.safeCastVertex(graph.addVertex("name", "Bob", "age", 30))

                // Verify initial properties
                vertex.value<String>("name") shouldBe "Bob"
                vertex.value<Int>("age") shouldBe 30

                // Add more properties
                vertex.property("department", "Marketing")
                vertex.value<String>("department") shouldBe "Marketing"
            }

            "property query should work correctly" {
                // Test 3: Create multiple vertices and query by property
                val alice = graph.addVertex()
                alice.property("name", "Alice")
                alice.property("department", "Engineering")

                val bob = graph.addVertex()
                bob.property("name", "Bob")
                bob.property("department", "Engineering")

                val charlie = graph.addVertex()
                charlie.property("name", "Charlie")
                charlie.property("department", "Marketing")

                // Count total vertices
                val allVertices = graph.vertices().asSequence().toList()
                allVertices shouldHaveSize 3

                // Verify each vertex has properties
                val aliceFromGraph =
                        allVertices.asSequence().find { it.value<String>("name") == "Alice" }
                aliceFromGraph.shouldNotBeNull()
                aliceFromGraph.value<String>("department") shouldBe "Engineering"

                // Count engineering department
                val engineers =
                        allVertices.filter { vertex ->
                            try {
                                vertex.value<String>("department") == "Engineering"
                            } catch (e: Exception) {
                                false
                            }
                        }
                println("Found ${engineers.size} engineers")
                engineers shouldHaveSize 2
            }

            "vertex properties should work correctly" {
                // Test 4: Test getVertexProperties method specifically
                val vertex = SafeCasting.safeCastVertex(graph.addVertex())
                vertex.property("name", "Diana")
                vertex.property("age", 28)

                // Test getVertexProperties method
                val nameProps = vertex.getVertexProperties<String>("name")
                nameProps shouldHaveSize 1
                nameProps.first().value() shouldBe "Diana"

                val ageProps = vertex.getVertexProperties<Int>("age")
                ageProps shouldHaveSize 1
                ageProps.first().value() shouldBe 28

                // Test non-existent property
                val nonExistentProps = vertex.getVertexProperties<String>("nonexistent")
                nonExistentProps shouldHaveSize 0
            }

            "property update single cardinality should work correctly" {
                // Test 5: Test property updates with SINGLE cardinality
                val vertex = SafeCasting.safeCastVertex(graph.addVertex())

                // Set initial property
                vertex.property("status", "active")
                vertex.value<String>("status") shouldBe "active"

                // Update property (should replace)
                vertex.property("status", "inactive")
                vertex.value<String>("status") shouldBe "inactive"

                // Should only have one status property
                val statusProps = vertex.getVertexProperties<String>("status")
                statusProps shouldHaveSize 1
            }

            "property query engine exact match should work correctly" {
                // Test 6: Test PropertyQueryEngine with exact same setup as failing
                // AdvancedIndexingTest

                // Create test vertices with various properties (same as AdvancedIndexingTest)
                val alice = SafeCasting.safeCastVertex(graph.addVertex())
                alice.property("name", "Alice")
                alice.property("age", 25)
                alice.property("city", "New York")
                alice.property("salary", 75000)
                alice.property("department", "Engineering")

                val bob = SafeCasting.safeCastVertex(graph.addVertex())
                bob.property("name", "Bob")
                bob.property("age", 30)
                bob.property("city", "San Francisco")
                bob.property("salary", 95000)
                bob.property("department", "Engineering")

                val eve = SafeCasting.safeCastVertex(graph.addVertex())
                eve.property("name", "Eve")
                eve.property("age", 32)
                eve.property("city", "San Francisco")
                eve.property("salary", 105000)
                eve.property("department", "Engineering")

                val charlie = SafeCasting.safeCastVertex(graph.addVertex())
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

                // Verify properties are set correctly (direct access)
                alice.value<String>("department") shouldBe "Engineering"
                bob.value<String>("department") shouldBe "Engineering"
                eve.value<String>("department") shouldBe "Engineering"
                charlie.value<String>("department") shouldBe "Marketing"
                diana.value<String>("department") shouldBe "Marketing"

                // Create indices for optimization (after data is created, like in failing test)
                println("Creating indices...")
                graph.createIndex(
                        "department",
                        org.apache.tinkerpop.gremlin.structure.Vertex::class
                )
                graph.createRangeIndex("age", org.apache.tinkerpop.gremlin.structure.Vertex::class)
                graph.createCompositeIndex(
                        listOf("department", "city"),
                        org.apache.tinkerpop.gremlin.structure.Vertex::class
                )
                println("Indices created")

                // Verify vertices still have properties after index creation
                println("Verifying properties after index creation:")
                val allVerticesAfterIndex = graph.vertices().asSequence().toList()
                allVerticesAfterIndex.forEach { vertex ->
                    val v = SafeCasting.asTinkerVertex(vertex)
                    if (v != null) {
                        val name = v.value<String>("name")
                        val dept = v.value<String>("department")
                        println("  Vertex: $name, Department: $dept")
                    }
                }

                // Test PropertyQueryEngine exact match
                val queryEngine = graph.propertyQueryEngine()
                println("Testing PropertyQueryEngine...")

                // Test exact query for Engineering department
                val engineers =
                        queryEngine
                                .queryVertices(
                                        PropertyQueryEngine.exact("department", "Engineering")
                                )
                                .asSequence()
                                .toList()

                // Debug: print what we found
                println("Found ${engineers.size} engineers:")
                engineers.forEach { vertex ->
                    val name = vertex.value<String>("name")
                    val dept = vertex.value<String>("department")
                    println("  - $name: $dept")
                }

                // Force test to fail if we don't find engineers to see output
                if (engineers.size != 3) {
                    logger.w { "ERROR: Expected 3 engineers but found ${engineers.size}" }
                    logger.d { "All vertices in graph:" }
                    graph.vertices().asSequence().forEach { vertex ->
                        val v = SafeCasting.asTinkerVertex(vertex)
                        if (v != null) {
                            logger.d { "  ID: ${v.id()}, Keys: ${v.keys()}" }
                            v.keys().forEach { key -> logger.d { "    $key: ${v.value<Any>(key)}" } }
                        }
                    }
                }

                engineers shouldHaveSize 3 // Should find 3 engineers (Alice, Bob, Eve)

                // Test exact query for Marketing department
                val marketing =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("department", "Marketing"))
                                .asSequence()
                                .toList()

                marketing shouldHaveSize 2 // Should find 2 marketing employees (Charlie, Diana)

                // Test exact query for a specific name
                val aliceResults =
                        queryEngine
                                .queryVertices(PropertyQueryEngine.exact("name", "Alice"))
                                .asSequence()
                                .toList()

                aliceResults shouldHaveSize 1 // Should find exactly 1 Alice
                aliceResults.first().value<String>("name") shouldBe "Alice"
            }
        })
