package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.instanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import org.apache.tinkerpop.gremlin.structure.VertexProperty

/**
 * Property-based test suite for TinkerVertex using Kotest Property framework.
 *
 * This test suite demonstrates the incorporation of property-based testing
 * into the existing test framework, validating vertex behavior across
 * randomly generated inputs to ensure correctness and robustness.
 */
class PropertyBasedVertexTest : StringSpec({

    "vertex should maintain string properties correctly across random inputs" {
        checkAll(
            Arb.string(1..20),  // property key
            Arb.string(1..50)   // property value
        ) { propertyKey, propertyValue ->
            val graph = TinkerGraph.open()
            val vertex = graph.addVertex() as TinkerVertex

            if (propertyKey.isBlank()) {
                // Blank property keys should throw IllegalArgumentException
                val exception = runCatching { vertex.property(propertyKey, propertyValue) }.exceptionOrNull()
                exception shouldBe instanceOf<IllegalArgumentException>()
                exception?.message shouldBe "Property key can not be empty"
            } else {
                // Valid property keys should work normally
                vertex.property(propertyKey, propertyValue)
                vertex.value<String>(propertyKey) shouldBe propertyValue
                vertex.keys() shouldContain propertyKey
            }
        }
    }

    "vertex should handle integer properties with full range" {
        checkAll(
            Arb.string(1..15),
            Arb.int()
        ) { propertyKey, propertyValue ->
            val graph = TinkerGraph.open()
            val vertex = graph.addVertex() as TinkerVertex

            if (propertyKey.isBlank()) {
                // Blank property keys should throw IllegalArgumentException
                val exception = runCatching { vertex.property(propertyKey, propertyValue) }.exceptionOrNull()
                exception shouldBe instanceOf<IllegalArgumentException>()
                exception?.message shouldBe "Property key can not be empty"
            } else {
                // Valid property keys should work normally
                vertex.property(propertyKey, propertyValue)
                vertex.value<Int>(propertyKey) shouldBe propertyValue
            }
        }
    }

    "vertex should maintain double properties with precision" {
        checkAll(
            Arb.string(1..15),
            Arb.double(-1000.0..1000.0)
        ) { propertyKey, propertyValue ->
            val graph = TinkerGraph.open()
            val vertex = graph.addVertex() as TinkerVertex

            if (propertyKey.isBlank()) {
                // Blank property keys should throw IllegalArgumentException
                val exception = runCatching { vertex.property(propertyKey, propertyValue) }.exceptionOrNull()
                exception shouldBe instanceOf<IllegalArgumentException>()
                exception?.message shouldBe "Property key can not be empty"
            } else {
                // Valid property keys should work normally
                vertex.property(propertyKey, propertyValue)
                vertex.value<Double>(propertyKey) shouldBe propertyValue
            }
        }
    }

    "vertex should correctly handle boolean properties" {
        checkAll(
            Arb.string(1..15),
            Arb.boolean()
        ) { propertyKey, propertyValue ->
            val graph = TinkerGraph.open()
            val vertex = graph.addVertex() as TinkerVertex

            if (propertyKey.isBlank()) {
                // Blank property keys should throw IllegalArgumentException
                val exception = runCatching { vertex.property(propertyKey, propertyValue) }.exceptionOrNull()
                exception shouldBe instanceOf<IllegalArgumentException>()
                exception?.message shouldBe "Property key can not be empty"
            } else {
                // Valid property keys should work normally
                vertex.property(propertyKey, propertyValue)
                vertex.value<Boolean>(propertyKey) shouldBe propertyValue
            }
        }
    }

    "SINGLE cardinality should always replace existing values" {
        checkAll(
            Arb.string(1..15),
            Arb.list(Arb.string(1..30), 2..5)
        ) { propertyKey, values ->
            val graph = TinkerGraph.open()
            val vertex = graph.addVertex() as TinkerVertex

            if (propertyKey.isBlank()) {
                // Blank property keys should throw IllegalArgumentException
                val exception = runCatching {
                    vertex.property(propertyKey, values.first(), VertexProperty.Cardinality.SINGLE)
                }.exceptionOrNull()
                exception shouldBe instanceOf<IllegalArgumentException>()
                exception?.message shouldBe "Property key can not be empty"
            } else {
                // Set multiple values with SINGLE cardinality
                values.forEach { value ->
                    vertex.property(propertyKey, value, VertexProperty.Cardinality.SINGLE)
                }

                // Should only have the last value
                val properties = vertex.properties<String>(propertyKey).asSequence().toList()
                properties.size shouldBe 1
                properties[0].value() shouldBe values.last()
            }
        }
    }

    "LIST cardinality should accumulate all values" {
        checkAll(
            Arb.string(1..15),
            Arb.list(Arb.string(1..30), 1..5)
        ) { propertyKey, values ->
            val graph = TinkerGraph.open()
            val vertex = graph.addVertex() as TinkerVertex

            if (propertyKey.isBlank()) {
                // Blank property keys should throw IllegalArgumentException
                val exception = runCatching {
                    vertex.property(propertyKey, values.first(), VertexProperty.Cardinality.LIST)
                }.exceptionOrNull()
                exception shouldBe instanceOf<IllegalArgumentException>()
                exception?.message shouldBe "Property key can not be empty"
            } else {
                // Add all values with LIST cardinality
                values.forEach { value ->
                    vertex.property(propertyKey, value, VertexProperty.Cardinality.LIST)
                }

                // Should have all values
                val properties = vertex.properties<String>(propertyKey).asSequence().toList()
                properties.size shouldBe values.size

                val actualValues = properties.map { it.value() }.toSet()
                val expectedValues = values.toSet()
                actualValues shouldBe expectedValues
            }
        }
    }

    "vertex should handle multiple properties independently" {
        checkAll(
            Arb.map(
                Arb.string(1..15),
                Arb.string(1..30),
                minSize = 1,
                maxSize = 8
            )
        ) { properties ->
            val graph = TinkerGraph.open()
            val vertex = graph.addVertex() as TinkerVertex

            val validProperties = properties.filterKeys { !it.isBlank() }
            val blankProperties = properties.filterKeys { it.isBlank() }

            // Set all valid properties
            validProperties.forEach { (key, value) ->
                vertex.property(key, value)
            }

            // Test that blank keys throw exceptions
            blankProperties.forEach { (key, value) ->
                val exception = runCatching { vertex.property(key, value) }.exceptionOrNull()
                exception shouldBe instanceOf<IllegalArgumentException>()
                exception?.message shouldBe "Property key can not be empty"
            }

            // Verify all valid properties exist and have correct values
            validProperties.forEach { (key, expectedValue) ->
                vertex.keys() shouldContain key
                vertex.value<String>(key) shouldBe expectedValue
            }

            // Verify property count matches valid properties only
            vertex.keys().size shouldBe validProperties.size
        }
    }

    "property removal should maintain graph consistency" {
        checkAll(
            Arb.string(1..15),
            Arb.string(1..30)
        ) { propertyKey, propertyValue ->
            val graph = TinkerGraph.open()
            val vertex = graph.addVertex() as TinkerVertex

            if (propertyKey.isBlank()) {
                // Blank property keys should throw IllegalArgumentException
                val exception = runCatching { vertex.property(propertyKey, propertyValue) }.exceptionOrNull()
                exception shouldBe instanceOf<IllegalArgumentException>()
                exception?.message shouldBe "Property key can not be empty"
            } else {
                // Set property
                vertex.property(propertyKey, propertyValue)
                vertex.keys() shouldContain propertyKey

                // Remove property
                val prop = vertex.property<String>(propertyKey)
                prop.remove()

                // Verify property is removed
                vertex.keys().contains(propertyKey) shouldBe false
            }
        }
    }

    "vertices should have unique identities regardless of content" {
        checkAll(
            Arb.string(1..20),
            Arb.string(1..30)
        ) { propertyKey, propertyValue ->
            val graph = TinkerGraph.open()
            val vertex1 = graph.addVertex() as TinkerVertex
            val vertex2 = graph.addVertex() as TinkerVertex

            if (propertyKey.isBlank()) {
                // Blank property keys should throw IllegalArgumentException for both vertices
                val exception1 = runCatching { vertex1.property(propertyKey, propertyValue) }.exceptionOrNull()
                val exception2 = runCatching { vertex2.property(propertyKey, propertyValue) }.exceptionOrNull()
                exception1 shouldBe instanceOf<IllegalArgumentException>()
                exception2 shouldBe instanceOf<IllegalArgumentException>()
                exception1?.message shouldBe "Property key can not be empty"
                exception2?.message shouldBe "Property key can not be empty"
            } else {
                // Set same properties on both vertices
                vertex1.property(propertyKey, propertyValue)
                vertex2.property(propertyKey, propertyValue)

                // Vertices should not be equal despite identical content
                vertex1 shouldNotBe vertex2
                vertex1.id() shouldNotBe vertex2.id()
            }

            // Same vertex should equal itself regardless
            vertex1 shouldBe vertex1
        }
    }

    "vertex labels should be preserved correctly" {
        checkAll(
            Arb.string(1..20),
            Arb.string(1..30)
        ) { vertexLabel, propertyValue ->
            val graph = TinkerGraph.open()

            if (vertexLabel.isBlank()) {
                // Blank vertex labels should be handled (they may be allowed, let's test)
                val vertex = graph.addVertex("label", vertexLabel, "testProp", propertyValue) as TinkerVertex
                vertex.label() shouldBe vertexLabel
                vertex.value<String>("testProp") shouldBe propertyValue
            } else {
                val vertex = graph.addVertex("label", vertexLabel, "testProp", propertyValue) as TinkerVertex
                vertex.label() shouldBe vertexLabel
                vertex.value<String>("testProp") shouldBe propertyValue
            }
        }
    }

    "edge creation from vertex should maintain connectivity" {
        checkAll(
            Arb.string(1..15),
            Arb.double(0.0..10.0)
        ) { edgeLabel, weightValue ->
            val graph = TinkerGraph.open()
            val vertex1 = graph.addVertex("id", 1) as TinkerVertex
            val vertex2 = graph.addVertex("id", 2) as TinkerVertex

            if (edgeLabel.isBlank()) {
                // Test if blank edge labels are allowed or should throw exception
                val edge = vertex1.addEdge(edgeLabel, vertex2, "weight", weightValue) as TinkerEdge
                edge.label() shouldBe edgeLabel
                edge.outVertex() shouldBe vertex1
                edge.inVertex() shouldBe vertex2
                edge.value<Double>("weight") shouldBe weightValue
            } else {
                val edge = vertex1.addEdge(edgeLabel, vertex2, "weight", weightValue) as TinkerEdge

                edge.label() shouldBe edgeLabel
                edge.outVertex() shouldBe vertex1
                edge.inVertex() shouldBe vertex2
                edge.value<Double>("weight") shouldBe weightValue

                // Verify edge appears in vertex adjacency lists
                val outEdges = vertex1.edges(org.apache.tinkerpop.gremlin.structure.Direction.OUT).asSequence().toList()
                outEdges shouldContain edge

                val inEdges = vertex2.edges(org.apache.tinkerpop.gremlin.structure.Direction.IN).asSequence().toList()
                inEdges shouldContain edge
            }
        }
    }
})
