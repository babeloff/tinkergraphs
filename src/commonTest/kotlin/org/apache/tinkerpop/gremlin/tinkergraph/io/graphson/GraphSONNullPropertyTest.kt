package org.apache.tinkerpop.gremlin.tinkergraph.io.graphson

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.assertions.throwables.shouldThrow
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

/**
 * Test suite demonstrating null property handling issues in GraphSON serialization.
 *
 * This test highlights the inconsistency between GraphSON v3.0 specification support
 * for null values (g:Null type) and TinkerGraph's default configuration that rejects
 * null property values.
 */
class GraphSONNullPropertyTest : StringSpec({

    "TinkerGraph with default configuration should reject null properties" {
        val graph = TinkerGraph.open() // Default: allowNullPropertyValues = false
        val vertex = graph.addVertex("id", 1, "name", "Alice")

        // This should throw an exception with default configuration
        shouldThrow<IllegalArgumentException> {
            vertex.property("nullProp", null)
        }

        graph.close()
    }

    "TinkerGraph with allowNullPropertyValues=true should accept null properties" {
        val config = mapOf(
            TinkerGraph.GREMLIN_TINKERGRAPH_ALLOW_NULL_PROPERTY_VALUES to true
        )
        val graph = TinkerGraph.open(config)
        val vertex = graph.addVertex("id", 1, "name", "Alice")

        // This should work with proper configuration
        vertex.property("nullProp", null)

        // Verify the property was set
        val property = vertex.property<Any?>("nullProp")
        property.isPresent() shouldBe true
        property.value() shouldBe null

        graph.close()
    }

    "GraphSON should serialize and deserialize null properties correctly" {
        val config = mapOf(
            TinkerGraph.GREMLIN_TINKERGRAPH_ALLOW_NULL_PROPERTY_VALUES to true
        )
        val graph = TinkerGraph.open(config)
        val mapper = GraphSONMapper.create()

        // Create vertex with null property
        val vertex = graph.addVertex("id", 1, "name", "Alice")
        vertex.property("nullProp", null)
        vertex.property("normalProp", "value")

        // Serialize the graph
        val graphsonString = mapper.writeGraph(graph)

        // Verify GraphSON contains g:Null type
        graphsonString shouldContain("\"@type\": \"g:Null\"")

        // Deserialize into new graph (with same configuration)
        val newGraph = TinkerGraph.open(config)
        val deserializedGraph = mapper.readGraph(graphsonString)

        val deserializedVertex = deserializedGraph.vertices().next()

        // Verify null property is preserved
        val nullProp = deserializedVertex.property<Any?>("nullProp")
        nullProp.isPresent() shouldBe true
        nullProp.value() shouldBe null

        // Verify normal property is also preserved
        deserializedVertex.value<String>("normalProp") shouldBe "value"

        graph.close()
        newGraph.close()
        deserializedGraph.close()
    }

    "demonstrate why the original GraphSON test fails" {
        val graph = TinkerGraph.open() // Default configuration - rejects nulls
        val mapper = GraphSONMapper.create()

        val vertex = graph.addVertex("id", 1, "label", "person")

        // These properties work fine
        vertex.property("stringProp", "test")
        vertex.property("intProp", 42)
        vertex.property("doubleProp", 3.14)
        vertex.property("booleanProp", true)

        // This property fails with default TinkerGraph configuration
        shouldThrow<IllegalArgumentException> {
            vertex.property("nullProp", null)
        }

        graph.close()
    }

    "fixed version of the failing test with proper configuration" {
        val config = mapOf(
            TinkerGraph.GREMLIN_TINKERGRAPH_ALLOW_NULL_PROPERTY_VALUES to true
        )
        val graph = TinkerGraph.open(config)
        val mapper = GraphSONMapper.create()

        val vertex = graph.addVertex("id", 1, "label", "person")

        // Test different property types including null
        vertex.property("stringProp", "test")
        vertex.property("intProp", 42)
        vertex.property("doubleProp", 3.14)
        vertex.property("booleanProp", true)
        vertex.property("longProp", 1234567890L)
        vertex.property("floatProp", 2.71f)
        vertex.property("nullProp", null) // This now works

        val graphsonString = mapper.writeGraph(graph)

        val deserializedGraph = mapper.readGraph(graphsonString)
        val deserializedVertex = deserializedGraph.vertices().next()

        deserializedVertex.value<String>("stringProp") shouldBe "test"
        deserializedVertex.value<Int>("intProp") shouldBe 42
        deserializedVertex.value<Double>("doubleProp") shouldBe 3.14
        deserializedVertex.value<Boolean>("booleanProp") shouldBe true
        deserializedVertex.value<Long>("longProp") shouldBe 1234567890L
        deserializedVertex.value<Float>("floatProp") shouldBe 2.71f

        // Verify null property handling
        val nullProp = deserializedVertex.property<Any?>("nullProp")
        nullProp.isPresent() shouldBe true
        nullProp.value() shouldBe null

        graph.close()
        deserializedGraph.close()
    }

    "GraphSON TYPE_NULL serialization format should be correct" {
        val config = mapOf(
            TinkerGraph.GREMLIN_TINKERGRAPH_ALLOW_NULL_PROPERTY_VALUES to true
        )
        val graph = TinkerGraph.open(config)
        val mapper = GraphSONMapper.create()

        val vertex = graph.addVertex("id", 1, "name", "Test")
        vertex.property("nullValue", null)

        val serialized = mapper.writeGraph(graph)

        // Verify the GraphSON format for null values
        println("GraphSON with null property:")
        println(serialized)

        // Should contain the proper GraphSON v3.0 null type
        serialized shouldContain("\"@type\": \"g:Null\"")
        serialized shouldContain("\"@value\": null")

        graph.close()
    }
})
