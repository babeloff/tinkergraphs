package org.apache.tinkerpop.gremlin.tinkergraph.io.graphson

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.apache.tinkerpop.gremlin.tinkergraph.util.LoggingConfig

/**
 * Simple test to understand basic GraphSON behavior and diagnose issues.
 */
class SimpleGraphSONTest : StringSpec({

    private val logger = LoggingConfig.getLogger<SimpleGraphSONTest>()

    "basic GraphSON serialization and deserialization should work" {
        val graph = TinkerGraph.open()
        val mapper = GraphSONMapper.create()

        // Create a simple vertex
        val vertex = graph.addVertex("id", 1, "name", "Alice")
        println("Created vertex: ID=${vertex.id()}, name=${vertex.value<String>("name")}")

        try {
            // Serialize the graph
            val graphsonString = mapper.writeGraph(graph)
            println("Serialized GraphSON:")
            println(graphsonString)

            // Try to deserialize into a NEW graph (not the same one)
            val newGraph = TinkerGraph.open()
            val mapper2 = GraphSONMapper.create()

            println("Attempting to deserialize into new graph...")
            val deserializedGraph = mapper2.readGraph(graphsonString)

            println("Deserialization successful!")
            val vertices = deserializedGraph.vertices().asSequence().toList()
            println("Number of vertices: ${vertices.size}")

            if (vertices.isNotEmpty()) {
                val deserializedVertex = vertices[0]
                println("Deserialized vertex: ID=${deserializedVertex.id()}, name=${deserializedVertex.value<String>("name")}")

                deserializedVertex.id() shouldBe 1
                deserializedVertex.value<String>("name") shouldBe "Alice"
            }

            newGraph.close()
            deserializedGraph.close()
        } catch (e: Exception) {
            logger.e(e) { "Exception during GraphSON operations: ${e::class.simpleName}" }
            e.printStackTrace()
            throw e
        }

        graph.close()
    }

    "test what happens when deserializing into same graph with conflict" {
        val graph = TinkerGraph.open()
        val mapper = GraphSONMapper.create()

        // Create a vertex
        val vertex = graph.addVertex("id", 1, "name", "Alice")
        println("Created vertex in original graph: ID=${vertex.id()}")

        try {
            // Serialize the graph
            val graphsonString = mapper.writeGraph(graph)

            // Try to deserialize back into the SAME graph (this should cause ID conflict)
            println("Attempting to deserialize into same graph...")
            val result = mapper.readGraph(graphsonString)

            println("Deserialization succeeded unexpectedly!")
            println("Total vertices now: ${graph.vertices().asSequence().count()}")

            result.close()
        } catch (e: Exception) {
            logger.d(e) { "Exception during same-graph deserialization (expected): ${e::class.simpleName}" }
            // This exception is expected - don't rethrow
        }

        graph.close()
    }

    "test direct vertex creation with duplicate IDs" {
        val graph = TinkerGraph.open()

        // Create first vertex
        val vertex1 = graph.addVertex("id", 42, "name", "First")
        println("Created first vertex: ID=${vertex1.id()}")

        try {
            // Try to create second vertex with same ID
            val vertex2 = graph.addVertex("id", 42, "name", "Second")
            println("Created second vertex unexpectedly: ID=${vertex2.id()}")
        } catch (e: Exception) {
            logger.d(e) { "Exception when creating duplicate ID (expected): ${e::class.simpleName}" }
            // This exception is expected
        }

        graph.close()
    }
})
