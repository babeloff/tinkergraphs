package org.apache.tinkerpop.gremlin.tinkergraph.io.graphson

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.apache.tinkerpop.gremlin.tinkergraph.util.LoggingConfig

/**
 * Diagnostic test to understand the current ID conflict behavior in GraphSON deserialization.
 */
class GraphSONIdDiagnosticTest : StringSpec({

    lateinit var graph: TinkerGraph
    lateinit var mapper: GraphSONMapper

    beforeTest {
        graph = TinkerGraph.open()
        mapper = GraphSONMapper.create()
    }

    afterTest {
        graph.close()
    }

    "diagnose what happens with vertex ID conflicts" {
        // Create a vertex in the existing graph
        val existingVertex = graph.addVertex("id", 1, "name", "Alice")
        println("Created existing vertex with ID: ${existingVertex.id()}")

        // Simple GraphSON with conflicting vertex ID
        val graphsonString = """
        {
          "version" : "3.0",
          "vertices" : [ {
            "@type" : "g:Vertex",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 1
              },
              "label" : "person",
              "properties" : {
                "name" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 0
                    },
                    "value" : "Bob",
                    "label" : "name"
                  }
                } ]
              }
            }
          } ],
          "edges" : [ ]
        }
        """.trimIndent()

        println("GraphSON to deserialize:")
        println(graphsonString)

        try {
            val result = mapper.readGraph(graphsonString)
            println("Deserialization succeeded unexpectedly")
            println("Resulting vertices: ${result.vertices().asSequence().count()}")
            result.vertices().forEach { vertex ->
                println("Vertex ID: ${vertex.id()}, name: ${vertex.value<String>("name")}")
            }
            result.close()
        } catch (e: Exception) {
            logger.w(e) { "Exception occurred during deserialization: ${e::class.simpleName}" }
            e.printStackTrace()
        }
    }

    "test basic GraphSON deserialization without conflicts" {
        val graphsonString = """
        {
          "version" : "3.0",
          "vertices" : [ {
            "@type" : "g:Vertex",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 42
              },
              "label" : "person",
              "properties" : {
                "name" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 0
                    },
                    "value" : "Charlie",
                    "label" : "name"
                  }
                } ]
              }
            }
          } ],
          "edges" : [ ]
        }
        """.trimIndent()

        try {
            val result = mapper.readGraph(graphsonString)
            println("Basic deserialization succeeded")
            println("Vertices: ${result.vertices().asSequence().count()}")
            val vertex = result.vertices().next()
            println("Vertex ID: ${vertex.id()}, name: ${vertex.value<String>("name")}")
            vertex.id() shouldBe 42
            vertex.value<String>("name") shouldBe "Charlie"
            result.close()
        } catch (e: Exception) {
            logger.e(e) { "Basic deserialization failed" }
            throw e
        }
    }

    "check what addVertex does with existing ID" {
        val existingVertex = graph.addVertex("id", 99, "name", "Test")
        println("Created vertex with ID: ${existingVertex.id()}")

        try {
            val duplicate = graph.addVertex("id", 99, "name", "Duplicate")
            println("Duplicate vertex creation succeeded: ${duplicate.id()}")
        } catch (e: Exception) {
            logger.w(e) { "Duplicate vertex creation failed: ${e::class.simpleName}" }
        }
    }
}) {
    companion object {
        private val logger = LoggingConfig.getLogger<GraphSONIdDiagnosticTest>()
    }
}
