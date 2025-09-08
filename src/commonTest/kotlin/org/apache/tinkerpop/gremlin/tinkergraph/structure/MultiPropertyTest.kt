package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
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
                val prop1 = tinkerVertex.property("tags", "kotlin", VertexProperty.Cardinality.LIST)
                val prop2 =
                        tinkerVertex.property("tags", "testing", VertexProperty.Cardinality.LIST)
                val prop3 =
                        tinkerVertex.property(
                                "tags",
                                "tinkergraph",
                                VertexProperty.Cardinality.LIST
                        )

                prop1.isPresent() shouldBe true
                prop2.isPresent() shouldBe true
                prop3.isPresent() shouldBe true

                // Should have 3 properties with the same key
                val tagProperties = vertex.properties<String>("tags").asSequence().toList()
                tagProperties shouldHaveSize 3

                val tagValues = tagProperties.map { it.value() }.sorted()
                tagValues shouldBe listOf("kotlin", "testing", "tinkergraph")
            }

            "SET cardinality should prevent duplicate values" {
                val tinkerVertex = vertex as TinkerVertex

                // Add values with SET cardinality
                val prop1 = tinkerVertex.property("skills", "java", VertexProperty.Cardinality.SET)
                val prop2 =
                        tinkerVertex.property("skills", "kotlin", VertexProperty.Cardinality.SET)
                val prop3 =
                        tinkerVertex.property(
                                "skills",
                                "java",
                                VertexProperty.Cardinality.SET
                        ) // Duplicate

                prop1.isPresent() shouldBe true
                prop2.isPresent() shouldBe true
                prop3.isPresent() shouldBe true

                // Should only have 2 unique values
                val skillProperties = vertex.properties<String>("skills").asSequence().toList()
                skillProperties shouldHaveSize 2

                val skillValues = skillProperties.map { it.value() }.toSet()
                skillValues shouldBe setOf("java", "kotlin")
            }

            "SINGLE cardinality should replace existing values" {
                val tinkerVertex = vertex as TinkerVertex

                // Add first value
                val prop1 =
                        tinkerVertex.property("name", "Alice", VertexProperty.Cardinality.SINGLE)
                prop1.value() shouldBe "Alice"

                val nameProperties1 = vertex.properties<String>("name").asSequence().toList()
                nameProperties1 shouldHaveSize 1

                // Replace with new value
                val prop2 = tinkerVertex.property("name", "Bob", VertexProperty.Cardinality.SINGLE)
                prop2.value() shouldBe "Bob"

                val nameProperties2 = vertex.properties<String>("name").asSequence().toList()
                nameProperties2 shouldHaveSize 1
                nameProperties2[0].value() shouldBe "Bob"
            }

            "should handle mixed cardinality on different keys" {
                val tinkerVertex = vertex as TinkerVertex

                // Single cardinality
                tinkerVertex.property("status", "active", VertexProperty.Cardinality.SINGLE)

                // List cardinality
                tinkerVertex.property("tags", "important", VertexProperty.Cardinality.LIST)
                tinkerVertex.property("tags", "urgent", VertexProperty.Cardinality.LIST)

                // Set cardinality
                tinkerVertex.property("categories", "work", VertexProperty.Cardinality.SET)
                tinkerVertex.property("categories", "personal", VertexProperty.Cardinality.SET)

                val statusProps = vertex.properties<String>("status").asSequence().toList()
                statusProps shouldHaveSize 1

                val tagProps = vertex.properties<String>("tags").asSequence().toList()
                tagProps shouldHaveSize 2

                val categoryProps = vertex.properties<String>("categories").asSequence().toList()
                categoryProps shouldHaveSize 2
            }

            "LIST cardinality should maintain insertion order" {
                val tinkerVertex = vertex as TinkerVertex

                tinkerVertex.property("sequence", "first", VertexProperty.Cardinality.LIST)
                tinkerVertex.property("sequence", "second", VertexProperty.Cardinality.LIST)
                tinkerVertex.property("sequence", "third", VertexProperty.Cardinality.LIST)

                val sequenceProps = vertex.properties<String>("sequence").asSequence().toList()
                sequenceProps shouldHaveSize 3

                val values = sequenceProps.map { it.value() }
                values shouldBe listOf("first", "second", "third")
            }

            "should handle numeric types with comparisons" {
                val tinkerVertex = vertex as TinkerVertex

                tinkerVertex.property("scores", 95, VertexProperty.Cardinality.LIST)
                tinkerVertex.property("scores", 87, VertexProperty.Cardinality.LIST)
                tinkerVertex.property("scores", 92, VertexProperty.Cardinality.LIST)

                val scoreProps = vertex.properties<Int>("scores").asSequence().toList()
                scoreProps shouldHaveSize 3

                val scores = scoreProps.map { it.value() }
                val maxScore = scores.maxOrNull()
                maxScore shouldBe 95

                val avgScore = scores.average()
                avgScore shouldBe ((95 + 87 + 92) / 3.0)
            }

            "should support property removal" {
                val tinkerVertex = vertex as TinkerVertex

                val prop1 = tinkerVertex.property("temp", "value1", VertexProperty.Cardinality.LIST)
                val prop2 = tinkerVertex.property("temp", "value2", VertexProperty.Cardinality.LIST)

                var tempProps = vertex.properties<String>("temp").asSequence().toList()
                tempProps shouldHaveSize 2

                // Remove one property
                prop1.remove()

                tempProps = vertex.properties<String>("temp").asSequence().toList()
                tempProps shouldHaveSize 1
                tempProps[0].value() shouldBe "value2"
            }

            "should handle meta-properties on vertex properties" {
                val tinkerVertex = vertex as TinkerVertex

                val skillProp =
                        tinkerVertex.property("skill", "kotlin", VertexProperty.Cardinality.LIST)

                // Add meta-properties to the vertex property
                skillProp.property("level", "expert")
                skillProp.property("years", 5)

                val levelProp = skillProp.property<String>("level")
                levelProp.isPresent() shouldBe true
                levelProp.value() shouldBe "expert"

                val yearsProp = skillProp.property<Int>("years")
                yearsProp.isPresent() shouldBe true
                yearsProp.value() shouldBe 5

                // Test meta-property keys
                val metaKeys = skillProp.keys()
                metaKeys shouldBe setOf("level", "years")
            }

            "should handle complex scenarios with all cardinalities" {
                val tinkerVertex = vertex as TinkerVertex

                // SINGLE: Only one value per key
                tinkerVertex.property("name", "John", VertexProperty.Cardinality.SINGLE)
                tinkerVertex.property(
                        "name",
                        "Jane",
                        VertexProperty.Cardinality.SINGLE
                ) // Replaces John

                // LIST: Multiple values, allows duplicates
                tinkerVertex.property("hobbies", "reading", VertexProperty.Cardinality.LIST)
                tinkerVertex.property("hobbies", "coding", VertexProperty.Cardinality.LIST)
                tinkerVertex.property(
                        "hobbies",
                        "reading",
                        VertexProperty.Cardinality.LIST
                ) // Duplicate allowed

                // SET: Multiple values, no duplicates
                tinkerVertex.property("languages", "english", VertexProperty.Cardinality.SET)
                tinkerVertex.property("languages", "spanish", VertexProperty.Cardinality.SET)
                tinkerVertex.property(
                        "languages",
                        "english",
                        VertexProperty.Cardinality.SET
                ) // Duplicate ignored

                val nameProps = vertex.properties<String>("name").asSequence().toList()
                nameProps shouldHaveSize 1
                nameProps[0].value() shouldBe "Jane"

                val hobbyProps = vertex.properties<String>("hobbies").asSequence().toList()
                hobbyProps shouldHaveSize 3 // Includes duplicate

                val langProps = vertex.properties<String>("languages").asSequence().toList()
                langProps shouldHaveSize 2 // No duplicates
            }
        })
