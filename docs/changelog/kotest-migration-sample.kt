package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.apache.tinkerpop.gremlin.structure.*

/**
 * SAMPLE: TinkerVertex Test Migration from kotlin.test to kotest
 *
 * This demonstrates how the original TinkerVertexTest.kt would be migrated from kotlin.test to
 * kotest framework using StringSpec.
 *
 * BEFORE (kotlin.test):
 * ```
 * class TinkerVertexTest {
 *     @BeforeTest
 *     fun setUp() { ... }
 *
 *     @Test
 *     fun testVertexCreation() {
 *         assertNotNull(vertex)
 *         assertEquals(expected, actual)
 *     }
 * }
 * ```
 *
 * AFTER (kotest StringSpec):
 */
class TinkerVertexKotestSample :
        StringSpec({

            // Setup can be done with beforeTest block
            lateinit var graph: TinkerGraph
            lateinit var vertex: TinkerVertex

            beforeTest {
                graph = TinkerGraph.open()
                vertex = graph.addVertex("name", "test") as TinkerVertex
            }

            // Tests are defined as strings with natural language descriptions
            "vertex creation should work correctly" {
                // kotest matchers are more readable than assertions
                vertex.shouldNotBeNull()
                vertex.id().shouldNotBeNull()
                vertex.label() shouldBe Vertex.DEFAULT_LABEL
                vertex.graph() shouldBe graph
            }

            "vertex properties should be manageable" {
                // Test single property
                val property = vertex.property("age", 30)
                property.isPresent() shouldBe true
                property.key() shouldBe "age"
                property.value() shouldBe 30

                // Test property retrieval
                vertex.value<Int>("age") shouldBe 30

                // Test keys - kotest collections matchers
                vertex.keys() shouldContain "name"
                vertex.keys() shouldContain "age"
            }

            "multi-properties with LIST cardinality should work" {
                // Add multiple properties with same key
                vertex.addVertexProperty(
                        "skill",
                        "kotlin",
                        cardinality = VertexProperty.Cardinality.LIST
                )
                vertex.addVertexProperty(
                        "skill",
                        "java",
                        cardinality = VertexProperty.Cardinality.LIST
                )

                val skills = vertex.properties<String>("skill").asSequence().toList()
                skills shouldHaveSize 2

                val skillValues = skills.map { it.value() }.toSet()
                skillValues shouldContain "kotlin"
                skillValues shouldContain "java"
            }

            "SET cardinality should prevent duplicates" {
                // kotest exception testing is more expressive
                vertex.addVertexProperty(
                        "tag",
                        "important",
                        cardinality = VertexProperty.Cardinality.SET
                )

                shouldThrow<UnsupportedOperationException> {
                    vertex.addVertexProperty(
                            "tag",
                            "important",
                            cardinality = VertexProperty.Cardinality.SET
                    )
                }
            }

            "SINGLE cardinality should replace existing values" {
                vertex.addVertexProperty(
                        "status",
                        "active",
                        emptyMap(),
                        VertexProperty.Cardinality.SINGLE
                )
                vertex.addVertexProperty(
                        "status",
                        "inactive",
                        emptyMap(),
                        VertexProperty.Cardinality.SINGLE
                )

                val statusProps = vertex.properties<String>("status").asSequence().toList()
                statusProps shouldHaveSize 1
                statusProps[0].value() shouldBe "inactive"
            }

            "edge addition should create proper connections" {
                val v2 = graph.addVertex("name", "vertex2") as TinkerVertex
                val edge = vertex.addEdge("knows", v2, "since", 2020)

                edge.shouldNotBeNull()
                edge.label() shouldBe "knows"
                edge.outVertex() shouldBe vertex
                edge.inVertex() shouldBe v2
                edge.value<Int>("since") shouldBe 2020
            }

            "vertex removal should clean up properly" {
                val vertex2 = graph.addVertex() as TinkerVertex
                vertex.addEdge("knows", vertex2)

                // Verify vertex exists
                graph.vertex(vertex.id()).shouldNotBeNull()

                // Remove vertex
                vertex.remove()

                // Verify vertex is removed - kotest handles nullability elegantly
                graph.vertex(vertex.id()) shouldBe null

                // Verify operations on removed vertex throw exception
                shouldThrow<IllegalStateException> { vertex.property("test", "value") }
            }
        })

/**
 * ALTERNATIVE: FunSpec style for more structured organization
 *
 * This shows how the same tests could be organized with FunSpec for better grouping and nested
 * contexts.
 */
class TinkerVertexKotestFunSpecSample :
        FunSpec({
            lateinit var graph: TinkerGraph
            lateinit var vertex: TinkerVertex

            beforeTest {
                graph = TinkerGraph.open()
                vertex = graph.addVertex("name", "test") as TinkerVertex
            }

            context("Vertex Creation") {
                test("should create vertex with proper attributes") {
                    vertex.shouldNotBeNull()
                    vertex.id().shouldNotBeNull()
                    vertex.label() shouldBe Vertex.DEFAULT_LABEL
                    vertex.graph() shouldBe graph
                }
            }

            context("Property Management") {
                test("should handle single properties") {
                    val property = vertex.property("age", 30)
                    property.isPresent() shouldBe true
                    property.key() shouldBe "age"
                    property.value() shouldBe 30
                }

                test("should support multi-properties with LIST cardinality") {
                    vertex.addVertexProperty(
                            "skill",
                            "kotlin",
                            cardinality = VertexProperty.Cardinality.LIST
                    )
                    vertex.addVertexProperty(
                            "skill",
                            "java",
                            cardinality = VertexProperty.Cardinality.LIST
                    )

                    val skills = vertex.properties<String>("skill").asSequence().toList()
                    skills shouldHaveSize 2
                }

                test("should prevent duplicates with SET cardinality") {
                    vertex.addVertexProperty(
                            "tag",
                            "important",
                            cardinality = VertexProperty.Cardinality.SET
                    )

                    shouldThrow<UnsupportedOperationException> {
                        vertex.addVertexProperty(
                                "tag",
                                "important",
                                cardinality = VertexProperty.Cardinality.SET
                        )
                    }
                }
            }

            context("Edge Operations") {
                test("should create edges properly") {
                    val v2 = graph.addVertex("name", "vertex2") as TinkerVertex
                    val edge = vertex.addEdge("knows", v2, "since", 2020)

                    edge.shouldNotBeNull()
                    edge.label() shouldBe "knows"
                    edge.outVertex() shouldBe vertex
                    edge.inVertex() shouldBe v2
                }
            }
        })

/*
 * MIGRATION BENEFITS DEMONSTRATED:
 *
 * 1. READABILITY: Natural language test descriptions
 *    - "vertex creation should work correctly" vs "testVertexCreation()"
 *
 * 2. ASSERTIONS: More expressive matchers
 *    - vertex.shouldNotBeNull() vs assertNotNull(vertex)
 *    - actual shouldBe expected vs assertEquals(expected, actual)
 *
 * 3. ORGANIZATION: Better test structuring
 *    - StringSpec for simple tests
 *    - FunSpec for grouped/contextual tests
 *
 * 4. EXCEPTION TESTING: Cleaner syntax
 *    - shouldThrow<Exception> { code } vs assertFailsWith<Exception> { code }
 *
 * 5. COLLECTIONS: Rich matchers
 *    - collection shouldContain element vs assertTrue(collection.contains(element))
 *    - collection shouldHaveSize 2 vs assertEquals(2, collection.size)
 *
 * 6. LIFECYCLE: Flexible setup/teardown
 *    - beforeTest/afterTest blocks vs @BeforeTest/@AfterTest annotations
 */
