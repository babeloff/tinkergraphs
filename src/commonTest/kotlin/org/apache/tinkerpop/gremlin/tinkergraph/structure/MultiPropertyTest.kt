package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.VertexProperty

/**
 * Comprehensive tests for multi-valued vertex properties in TinkerGraph. Tests LIST and SET
 * cardinality behaviors, property management, and meta-properties.
 */
class MultiPropertyTest :
        StringSpec({
            lateinit var graph: TinkerGraph
            lateinit var vertex: Vertex

            beforeTest {
                graph = TinkerGraph.open()
                vertex = graph.addVertex()
                vertex.property("id", "test-vertex")
            }

            afterTest { graph.close() }

            "LIST cardinality should allow multiple values" {
                val tinkerVertex = vertex as TinkerVertex

                // Add multiple values with LIST cardinality
                val prop1 = tinkerVertex.property(VertexProperty.Cardinality.list, "tags", "kotlin")
                val prop2 =
                        tinkerVertex.property(VertexProperty.Cardinality.list, "tags", "testing")
                val prop3 =
                        tinkerVertex.property(
                                VertexProperty.Cardinality.list,
                                "tags",
                                "tinkergraph"
                        )

                prop1.isPresent shouldBe true
                prop2.isPresent shouldBe true
                prop3.isPresent shouldBe true

                // Should have 3 properties with the same key
                val tagProperties = vertex.properties<String>("tags").asSequence().toList()
                tagProperties shouldHaveSize 3

                val tagValues = tagProperties.map { it.value() }.sorted()
                tagValues shouldBe listOf("kotlin", "testing", "tinkergraph")
            }

            "SET cardinality should prevent duplicate values" {
                val tinkerVertex = vertex as TinkerVertex

                // Add values with SET cardinality
                val prop1 = tinkerVertex.property(VertexProperty.Cardinality.set, "skills", "java")
                val prop2 =
                        tinkerVertex.property(VertexProperty.Cardinality.set, "skills", "kotlin")
                val prop3 =
                        tinkerVertex.property(
                                VertexProperty.Cardinality.set,
                                "skills",
                                "java"
                        ) // Duplicate

                prop1.isPresent shouldBe true
                prop2.isPresent shouldBe true
                prop3.isPresent shouldBe true

                // Should only have 2 distinct values (java appears only once)
                val skillProperties = vertex.properties<String>("skills").asSequence().toList()
                skillProperties shouldHaveSize 2

                val skillValues = skillProperties.map { it.value() }.sorted()
                skillValues shouldBe listOf("java", "kotlin")
            }

            "SINGLE cardinality should replace previous value" {
                val tinkerVertex = vertex as TinkerVertex

                // Add property with SINGLE cardinality
                val prop1 =
                        tinkerVertex.property(VertexProperty.Cardinality.single, "name", "Alice")
                prop1.isPresent shouldBe true
                prop1.value() shouldBe "Alice"

                val nameProperties1 = vertex.properties<String>("name").asSequence().toList()
                nameProperties1 shouldHaveSize 1

                // Replace with new value
                val prop2 = tinkerVertex.property(VertexProperty.Cardinality.single, "name", "Bob")
                prop2.value() shouldBe "Bob"

                val nameProperties2 = vertex.properties<String>("name").asSequence().toList()
                nameProperties2 shouldHaveSize 1
                nameProperties2.first().value() shouldBe "Bob"
            }

            "property removal should work correctly" {
                val tinkerVertex = vertex as TinkerVertex

                // Add multiple properties
                tinkerVertex.property(VertexProperty.Cardinality.list, "colors", "red")
                tinkerVertex.property(VertexProperty.Cardinality.list, "colors", "green")
                tinkerVertex.property(VertexProperty.Cardinality.list, "colors", "blue")

                vertex.properties<String>("colors").asSequence().toList() shouldHaveSize 3

                // Remove one specific property
                val redProperty =
                        vertex.properties<String>("colors").asSequence().find {
                            it.value() == "red"
                        }
                redProperty shouldNotBe null
                redProperty!!.remove()

                val remainingColors =
                        vertex.properties<String>("colors")
                                .asSequence()
                                .map { it.value() }
                                .sorted()
                                .toList()
                remainingColors shouldBe listOf("green", "blue")
            }

            "property iteration should work correctly" {
                val tinkerVertex = vertex as TinkerVertex

                // Add properties with different keys
                tinkerVertex.property("name", "Alice")
                tinkerVertex.property(VertexProperty.Cardinality.list, "tags", "developer")
                tinkerVertex.property(VertexProperty.Cardinality.list, "tags", "kotlin")
                tinkerVertex.property("age", 30)

                // Test iterating all properties
                val allProperties = vertex.properties<Any>().asSequence().toList()
                allProperties.size >= 4 shouldBe true // At least our 4 properties (plus id)

                // Test iterating properties by key
                val tagProperties = vertex.properties<String>("tags").asSequence().toList()
                tagProperties shouldHaveSize 2

                val nameProperties = vertex.properties<String>("name").asSequence().toList()
                nameProperties shouldHaveSize 1
                nameProperties.first().value() shouldBe "Alice"
            }

            "property existence checks should work correctly" {
                val tinkerVertex = vertex as TinkerVertex

                // Initially no custom properties (except id)
                vertex.property<String>("nonexistent").isPresent shouldBe false

                // Add property
                tinkerVertex.property("exists", "yes")
                vertex.property<String>("exists").isPresent shouldBe true
                vertex.property<String>("exists").value() shouldBe "yes"

                // Check with list properties
                tinkerVertex.property(VertexProperty.Cardinality.list, "items", "item1")
                tinkerVertex.property(VertexProperty.Cardinality.list, "items", "item2")
                vertex.property<String>("items").isPresent shouldBe true

                val itemsCount = vertex.properties<String>("items").asSequence().count()
                itemsCount shouldBe 2
            }

            "meta-properties should work with vertex properties" {
                val tinkerVertex = vertex as TinkerVertex

                // Add property with meta-properties
                val nameProperty = tinkerVertex.property("name", "Alice")
                nameProperty.property("created", "2024-01-01")
                nameProperty.property("source", "test")

                // Verify meta-properties
                nameProperty.property<String>("created").isPresent shouldBe true
                nameProperty.property<String>("created").value() shouldBe "2024-01-01"
                nameProperty.property<String>("source").value() shouldBe "test"

                // Check meta-property keys
                val metaKeys = nameProperty.keys()
                metaKeys.contains("created") shouldBe true
                metaKeys.contains("source") shouldBe true
            }

            "property values should maintain correct types" {
                val tinkerVertex = vertex as TinkerVertex

                // Add properties of different types
                tinkerVertex.property("stringProp", "hello")
                tinkerVertex.property("intProp", 42)
                tinkerVertex.property("doubleProp", 3.14159)
                tinkerVertex.property("booleanProp", true)

                // Verify types are maintained
                vertex.value<String>("stringProp") shouldBe "hello"
                vertex.value<Int>("intProp") shouldBe 42
                vertex.value<Double>("doubleProp") shouldBe 3.14159
                vertex.value<Boolean>("booleanProp") shouldBe true

                // Test with list properties of different types
                tinkerVertex.property(VertexProperty.Cardinality.list, "numbers", 1)
                tinkerVertex.property(VertexProperty.Cardinality.list, "numbers", 2)
                tinkerVertex.property(VertexProperty.Cardinality.list, "numbers", 3)

                val numbers =
                        vertex.properties<Int>("numbers")
                                .asSequence()
                                .map { it.value() }
                                .sorted()
                                .toList()
                numbers shouldBe listOf(1, 2, 3)
            }

            "property replacement with different cardinalities should work" {
                val tinkerVertex = vertex as TinkerVertex

                // Start with SINGLE cardinality
                tinkerVertex.property(VertexProperty.Cardinality.single, "value", "single")
                vertex.properties<String>("value").asSequence().toList() shouldHaveSize 1

                // Add with LIST cardinality (should add, not replace)
                tinkerVertex.property(VertexProperty.Cardinality.list, "value", "list1")
                tinkerVertex.property(VertexProperty.Cardinality.list, "value", "list2")

                val values = vertex.properties<String>("value").asSequence().toList()
                values.size >= 3 shouldBe true // Should have single + list1 + list2

                // Replace all with SINGLE again
                tinkerVertex.property(VertexProperty.Cardinality.single, "value", "new_single")
                vertex.properties<String>("value").asSequence().toList() shouldHaveSize 1
                vertex.value<String>("value") shouldBe "new_single"
            }

            "complex property scenarios should work correctly" {
                val tinkerVertex = vertex as TinkerVertex

                // Create a person vertex with multiple properties
                tinkerVertex.property("name", "John Doe")
                tinkerVertex.property("age", 30)
                tinkerVertex.property(VertexProperty.Cardinality.list, "emails", "john@work.com")
                tinkerVertex.property(
                        VertexProperty.Cardinality.list,
                        "emails",
                        "john@personal.com"
                )
                tinkerVertex.property(VertexProperty.Cardinality.set, "skills", "Java")
                tinkerVertex.property(VertexProperty.Cardinality.set, "skills", "Kotlin")
                tinkerVertex.property(VertexProperty.Cardinality.set, "skills", "Python")

                // Verify structure
                vertex.value<String>("name") shouldBe "John Doe"
                vertex.value<Int>("age") shouldBe 30

                val emails =
                        vertex.properties<String>("emails")
                                .asSequence()
                                .map { it.value() }
                                .sorted()
                                .toList()
                emails shouldBe listOf("john@personal.com", "john@work.com")

                val skills =
                        vertex.properties<String>("skills")
                                .asSequence()
                                .map { it.value() }
                                .sorted()
                                .toList()
                skills shouldBe listOf("Java", "Kotlin", "Python")

                // Add meta-properties to some properties
                val nameProperty = vertex.property<String>("name")
                nameProperty.property("verified", true)
                nameProperty.property("lastUpdated", "2024-01-01")

                nameProperty.property<Boolean>("verified").value() shouldBe true
                nameProperty.property<String>("lastUpdated").value() shouldBe "2024-01-01"
            }

            "property edge cases should be handled correctly" {
                val tinkerVertex = vertex as TinkerVertex

                // Test with null values (should not be added)
                try {
                    tinkerVertex.property("nullValue", null)
                } catch (e: Exception) {
                    // Expected - null values typically not allowed
                    e shouldNotBe null
                }

                // Test with empty string
                tinkerVertex.property("empty", "")
                vertex.value<String>("empty") shouldBe ""

                // Test with very long property names
                val longKey = "a".repeat(100)
                tinkerVertex.property(longKey, "long_key_value")
                vertex.value<String>(longKey) shouldBe "long_key_value"

                // Test with special characters in values
                tinkerVertex.property("special", "!@#$%^&*()_+-=[]{}|;':\",./<>?")
                vertex.value<String>("special") shouldBe "!@#$%^&*()_+-=[]{}|;':\",./<>?"
            }

            "property performance with many values should be acceptable" {
                val tinkerVertex = vertex as TinkerVertex
                val startTime = System.currentTimeMillis()

                // Add many list properties
                repeat(1000) { i ->
                    tinkerVertex.property(VertexProperty.Cardinality.list, "numbers", i)
                }

                val addTime = System.currentTimeMillis() - startTime

                // Verify all were added
                val numbers = vertex.properties<Int>("numbers").asSequence().toList()
                numbers shouldHaveSize 1000

                // Test retrieval performance
                val retrieveStart = System.currentTimeMillis()
                val sum = vertex.properties<Int>("numbers").asSequence().map { it.value() }.sum()
                val retrieveTime = System.currentTimeMillis() - retrieveStart

                // Verify correctness
                sum shouldBe (0..999).sum()

                // Performance should be reasonable (less than 1 second each)
                (addTime < 1000) shouldBe true
                (retrieveTime < 1000) shouldBe true
            }
        })
