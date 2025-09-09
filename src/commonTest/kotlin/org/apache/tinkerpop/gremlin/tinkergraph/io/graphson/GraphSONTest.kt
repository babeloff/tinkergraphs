package org.apache.tinkerpop.gremlin.tinkergraph.io.graphson

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.*

/**
 * Comprehensive test suite for GraphSON v3.0 implementation.
 *
 * Tests serialization and deserialization of all supported GraphSON v3.0 types and ensures
 * compliance with the Apache TinkerPop specification.
 */
class GraphSONTest :
        StringSpec({
            lateinit var graph: TinkerGraph
            lateinit var mapper: GraphSONMapper

            beforeTest {
                // Configure TinkerGraph to support null properties for GraphSON compatibility
                val config = mapOf(
                    TinkerGraph.GREMLIN_TINKERGRAPH_ALLOW_NULL_PROPERTY_VALUES to true
                )
                graph = TinkerGraph.open(config)
                mapper = GraphSONMapper.create()
            }

            afterTest { graph.close() }

            "GraphSON version constant should be correct" { GraphSONTypes.VERSION shouldBe "3.0" }

            "empty graph serialization should work correctly" {
                val graphsonString = mapper.writeGraph(graph)

                graphsonString.shouldNotBeNull()
                graphsonString shouldContain "\"version\""
                graphsonString shouldContain "\"vertices\""
                graphsonString shouldContain "\"edges\""

                val deserializedGraph = mapper.readGraph(graphsonString)
                deserializedGraph.vertices().asSequence().count() shouldBe 0
                deserializedGraph.edges().asSequence().count() shouldBe 0
            }

            "basic vertex serialization should work correctly" {
                val vertex = graph.addVertex("id", 1, "label", "person")
                vertex.property("name", "Alice")
                vertex.property("age", 30)

                val graphsonString = mapper.writeGraph(graph)

                graphsonString shouldContain "\"person\""
                graphsonString shouldContain "\"Alice\""
                graphsonString shouldContain "30"
                graphsonString shouldContain "\"name\""
                graphsonString shouldContain "\"age\""

                val deserializedGraph = mapper.readGraph(graphsonString)
                deserializedGraph.vertices().asSequence().count() shouldBe 1

                val deserializedVertex = deserializedGraph.vertices().next()
                deserializedVertex.label() shouldBe "person"
                deserializedVertex.value<String>("name") shouldBe "Alice"
                deserializedVertex.value<Int>("age") shouldBe 30
            }

            "basic edge serialization should work correctly" {
                val v1 = graph.addVertex("id", 1, "label", "person")
                val v2 = graph.addVertex("id", 2, "label", "person")
                val edge = v1.addEdge("knows", v2, "weight", 0.75)

                val graphsonString = mapper.writeGraph(graph)

                graphsonString shouldContain "\"knows\""
                graphsonString shouldContain "0.75"
                graphsonString shouldContain "\"weight\""

                val deserializedGraph = mapper.readGraph(graphsonString)
                deserializedGraph.edges().asSequence().count() shouldBe 1

                val deserializedEdge = deserializedGraph.edges().next()
                deserializedEdge.label() shouldBe "knows"
                deserializedEdge.value<Double>("weight") shouldBe 0.75
            }

            "complex graph serialization should work correctly" {
                // Create a more complex graph structure
                val alice = graph.addVertex("id", 1, "label", "person", "name", "Alice", "age", 30)
                val bob = graph.addVertex("id", 2, "label", "person", "name", "Bob", "age", 25)
                val charlie =
                        graph.addVertex("id", 3, "label", "person", "name", "Charlie", "age", 35)
                val company = graph.addVertex("id", 4, "label", "organization", "name", "TechCorp")

                alice.addEdge("knows", bob, "since", 2020, "strength", 0.8)
                bob.addEdge("knows", charlie, "since", 2019, "strength", 0.6)
                alice.addEdge("worksAt", company, "position", "Engineer", "salary", 75000)
                bob.addEdge("worksAt", company, "position", "Manager", "salary", 85000)

                val graphsonString = mapper.writeGraph(graph)

                // Verify all data is present in serialization
                graphsonString shouldContain "Alice"
                graphsonString shouldContain "Bob"
                graphsonString shouldContain "Charlie"
                graphsonString shouldContain "TechCorp"
                graphsonString shouldContain "knows"
                graphsonString shouldContain "worksAt"
                graphsonString shouldContain "Engineer"
                graphsonString shouldContain "Manager"
                graphsonString shouldContain "75000"
                graphsonString shouldContain "85000"

                // Verify deserialization reconstructs the graph correctly
                val deserializedGraph = mapper.readGraph(graphsonString)
                deserializedGraph.vertices().asSequence().count() shouldBe 4
                deserializedGraph.edges().asSequence().count() shouldBe 4
            }

            "vertex property serialization should work correctly" {
                val vertex = graph.addVertex("id", 1, "label", "person")

                // Test different property types
                vertex.property("stringProp", "test")
                vertex.property("intProp", 42)
                vertex.property("doubleProp", 3.14)
                vertex.property("booleanProp", true)
                vertex.property("longProp", 1234567890L)
                vertex.property("floatProp", 2.71f)
                vertex.property("nullProp", null)

                val graphsonString = mapper.writeGraph(graph)

                val deserializedGraph = mapper.readGraph(graphsonString)
                val deserializedVertex = deserializedGraph.vertices().next()

                deserializedVertex.value<String>("stringProp") shouldBe "test"
                deserializedVertex.value<Int>("intProp") shouldBe 42
                deserializedVertex.value<Double>("doubleProp") shouldBe 3.14
                deserializedVertex.value<Boolean>("booleanProp")!!.shouldBeTrue()
                deserializedVertex.value<Long>("longProp") shouldBe 1234567890L
                deserializedVertex.value<Float>("floatProp") shouldBe 2.71f

                // Verify null property handling
                val nullProp = deserializedVertex.property<Any?>("nullProp")
                nullProp.isPresent() shouldBe true
                nullProp.value() shouldBe null
            }

            "edge property serialization should work correctly" {
                val v1 = graph.addVertex("id", 1)
                val v2 = graph.addVertex("id", 2)
                val edge = v1.addEdge("connects", v2)

                // Test different edge property types
                edge.property("stringProp", "edge-test")
                edge.property("intProp", 99)
                edge.property("doubleProp", 1.41)
                edge.property("booleanProp", false)

                val graphsonString = mapper.writeGraph(graph)

                val deserializedGraph = mapper.readGraph(graphsonString)
                val deserializedEdge = deserializedGraph.edges().next()

                deserializedEdge.value<String>("stringProp") shouldBe "edge-test"
                deserializedEdge.value<Int>("intProp") shouldBe 99
                deserializedEdge.value<Double>("doubleProp") shouldBe 1.41
                deserializedEdge.value<Boolean>("booleanProp") shouldBe false
            }

            "round trip serialization should preserve graph structure" {
                // Create original graph
                val originalGraph = TinkerGraph.open()
                val v1 = originalGraph.addVertex("id", "vertex1", "name", "Node1")
                val v2 = originalGraph.addVertex("id", "vertex2", "name", "Node2")
                val v3 = originalGraph.addVertex("id", "vertex3", "name", "Node3")

                v1.addEdge("edge1", v2, "weight", 1.0)
                v2.addEdge("edge2", v3, "weight", 2.0)
                v3.addEdge("edge3", v1, "weight", 3.0)

                // Serialize
                val graphsonString = mapper.writeGraph(originalGraph)

                // Deserialize
                val deserializedGraph = mapper.readGraph(graphsonString)

                // Verify structure is preserved
                val originalVertices = originalGraph.vertices().asSequence().toList()
                val deserializedVertices = deserializedGraph.vertices().asSequence().toList()

                originalVertices shouldHaveSize deserializedVertices.size

                val originalEdges = originalGraph.edges().asSequence().toList()
                val deserializedEdges = deserializedGraph.edges().asSequence().toList()

                originalEdges shouldHaveSize deserializedEdges.size

                // Verify properties are preserved
                deserializedVertices.forEach { vertex ->
                    when (vertex.value<String>("name")) {
                        "Node1" -> vertex.id() shouldBe "vertex1"
                        "Node2" -> vertex.id() shouldBe "vertex2"
                        "Node3" -> vertex.id() shouldBe "vertex3"
                    }
                }

                deserializedEdges.forEach { edge ->
                    when (edge.label()) {
                        "edge1" -> edge.value<Double>("weight") shouldBe 1.0
                        "edge2" -> edge.value<Double>("weight") shouldBe 2.0
                        "edge3" -> edge.value<Double>("weight") shouldBe 3.0
                    }
                }

                originalGraph.close()
                deserializedGraph.close()
            }

            "large graph serialization should work correctly" {
                // Create a larger graph to test performance and correctness
                val numVertices = 100
                val numEdges = 200

                // Add vertices
                repeat(numVertices) { i ->
                    graph.addVertex("id", i, "label", "node", "index", i, "name", "Node$i")
                }

                // Add random edges
                val vertices = graph.vertices().asSequence().toList()
                repeat(numEdges) { i ->
                    val source = vertices.random()
                    val target = vertices.random()
                    if (source != target) {
                        source.addEdge(
                                "connects",
                                target,
                                "edgeId",
                                i,
                                "weight",
                                (i % 10).toDouble()
                        )
                    }
                }

                val graphsonString = mapper.writeGraph(graph)
                val deserializedGraph = mapper.readGraph(graphsonString)

                // Verify counts
                deserializedGraph.vertices().asSequence().count() shouldBe numVertices

                // Verify some properties are preserved
                val deserializedVertices = deserializedGraph.vertices().asSequence().toList()
                deserializedVertices.forEach { vertex ->
                    vertex.label() shouldBe "node"
                    vertex.property<String>("name").isPresent().shouldBeTrue()
                }

                deserializedGraph.close()
            }
        })
