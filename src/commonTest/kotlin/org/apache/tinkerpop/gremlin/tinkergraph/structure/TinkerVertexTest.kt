package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.tinkerpop.gremlin.structure.*

// Extension functions for missing TinkerVertex methods
fun TinkerVertex.addVertexProperty(
        key: String,
        value: Any?,
        metaProperties: Map<String, Any> = emptyMap(),
        cardinality: VertexProperty.Cardinality = VertexProperty.Cardinality.SINGLE
): VertexProperty<*> {
    return this.property(key, value, cardinality)
}

fun TinkerVertex.countEdges(direction: Direction, vararg labels: String): Int {
    return this.edges(direction, *labels).asSequence().count()
}

fun TinkerVertex.getAllEdgeLabels(): Set<String> {
    return this.getOutEdgeLabels() + this.getInEdgeLabels()
}

fun safeCastVertex(vertex: Any?): TinkerVertex? {
    return vertex as? TinkerVertex
}

/** Test suite for TinkerVertex implementation. */
class TinkerVertexTest :
        StringSpec({
            lateinit var graph: TinkerGraph
            lateinit var vertex: TinkerVertex

            beforeTest {
                graph = TinkerGraph.open()
                vertex = graph.addVertex("name", "test") as TinkerVertex
            }

            "vertex creation should work correctly" {
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

                // Test keys
                vertex.keys() shouldContain "name"
                vertex.keys() shouldContain "age"
            }

            "multi properties with LIST cardinality should work" {
                // Add multiple properties with same key (LIST cardinality)
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
                // SET cardinality should not allow duplicates
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
                // SINGLE cardinality should replace existing values
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

            "meta properties should be supported" {
                val vertexProperty = vertex.addVertexProperty("score", 95.5)

                // Add meta-property
                vertexProperty.property("timestamp", 1234567890L)
                vertexProperty.property("source", "test")

                vertexProperty.hasMetaProperties() shouldBe true
                vertexProperty.metaPropertyCount() shouldBe 2
                vertexProperty.keys() shouldContain "timestamp"
                vertexProperty.keys() shouldContain "source"
            }

            "edge addition should create proper connections" {
                val v2 = safeCastVertex(graph.addVertex("name", "vertex2"))!!

                val edge = vertex.addEdge("knows", v2, "since", 2020)

                edge.shouldNotBeNull()
                edge.label() shouldBe "knows"
                edge.outVertex() shouldBe vertex
                edge.inVertex() shouldBe v2
                edge.value<Int>("since") shouldBe 2020
            }

            "edge traversal should work correctly" {
                val v2 = safeCastVertex(graph.addVertex("name", "vertex2"))!!
                val v3 = safeCastVertex(graph.addVertex("name", "vertex3"))!!

                vertex.addEdge("knows", v2)
                vertex.addEdge("likes", v3)
                v2.addEdge("follows", vertex)

                // Test outgoing edges
                val outEdges = vertex.edges(Direction.OUT).asSequence().toList()
                outEdges shouldHaveSize 2

                // Test incoming edges
                val inEdges = vertex.edges(Direction.IN).asSequence().toList()
                inEdges shouldHaveSize 1

                // Test both directions
                val bothEdges = vertex.edges(Direction.BOTH).asSequence().toList()
                bothEdges shouldHaveSize 3

                // Test edge label filtering
                val knowsEdges = vertex.edges(Direction.OUT, "knows").asSequence().toList()
                knowsEdges shouldHaveSize 1
            }

            "vertex traversal should work correctly" {
                val v2 = safeCastVertex(graph.addVertex("name", "vertex2"))!!
                val v3 = safeCastVertex(graph.addVertex("name", "vertex3"))!!

                vertex.addEdge("knows", v2)
                vertex.addEdge("likes", v3)

                val outVertices = vertex.vertices(Direction.OUT).asSequence().toList()
                outVertices shouldHaveSize 2
                outVertices shouldContain v2
                outVertices shouldContain v3

                // Test with label filtering
                val knownVertices = vertex.vertices(Direction.OUT, "knows").asSequence().toList()
                knownVertices shouldHaveSize 1
                knownVertices shouldContain v2
            }

            "edge counting should work correctly" {
                val v2 = safeCastVertex(graph.addVertex())!!
                val v3 = safeCastVertex(graph.addVertex())!!

                vertex.addEdge("knows", v2)
                vertex.addEdge("likes", v3)
                v2.addEdge("follows", vertex)

                vertex.countEdges(Direction.OUT) shouldBe 2
                vertex.countEdges(Direction.IN) shouldBe 1
                vertex.countEdges(Direction.BOTH) shouldBe 3
                vertex.countEdges(Direction.OUT, "knows") shouldBe 1
            }

            "vertex removal should clean up properly" {
                val vertex2 = safeCastVertex(graph.addVertex())!!
                vertex.addEdge("knows", vertex2)

                // Verify vertex exists
                graph.vertex(vertex.id()).shouldNotBeNull()

                // Remove vertex
                vertex.remove()

                // Verify vertex is removed from graph
                graph.vertex(vertex.id()).shouldBeNull()

                // Verify operations on removed vertex throw exception
                shouldThrow<IllegalStateException> { vertex.property("test", "value") }
            }

            "vertex property removal should work correctly" {
                val prop = vertex.property("temp", "value")
                prop.isPresent() shouldBe true
                vertex.value<String>("temp") shouldBe "value"

                prop.remove()

                prop.isPresent() shouldBe false
                vertex.value<String>("temp").shouldBeNull()
            }

            "vertex equality should be based on ID" {
                val labelVertex = safeCastVertex(graph.addVertex())!!

                vertex shouldNotBe labelVertex
                vertex shouldBe vertex // Self equality

                // Vertices are equal if they have the same id
                val sameVertex = graph.vertex(vertex.id())
                vertex shouldBe sameVertex
            }

            "toString should provide proper representation" {
                val vertexString = vertex.toString()
                vertexString.startsWith("v[") shouldBe true
                vertexString.endsWith("]") shouldBe true
                vertexString.contains(vertex.id().toString()) shouldBe true
            }

            "vertex labels should be properly assigned" {
                val labeledVertex =
                        safeCastVertex(graph.addVertex("label", "person", "name", "Alice"))!!
                labeledVertex.label() shouldBe "person"
            }

            "edge labels should be correctly tracked" {
                val edgeLabelsVertex = safeCastVertex(graph.addVertex())!!
                val v3label = safeCastVertex(graph.addVertex())!!

                vertex.addEdge("knows", edgeLabelsVertex)
                vertex.addEdge("likes", v3label)
                edgeLabelsVertex.addEdge("follows", vertex)

                val outLabels = vertex.getOutEdgeLabels()
                outLabels shouldHaveSize 2
                outLabels shouldContain "knows"
                outLabels shouldContain "likes"

                val inLabels = vertex.getInEdgeLabels()
                inLabels shouldHaveSize 1
                inLabels shouldContain "follows"

                val allLabels = vertex.getAllEdgeLabels()
                allLabels shouldHaveSize 3
            }
        })

/** Helper object for reserved keys. */
private object T {
    const val id = "id"
    const val label = "label"
}
