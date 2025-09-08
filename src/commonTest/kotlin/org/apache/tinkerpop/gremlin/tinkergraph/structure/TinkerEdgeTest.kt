package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.structure.Vertex

// Extension functions for missing TinkerEdge methods
fun TinkerEdge.getDirection(vertex: Vertex): Direction {
    return when (vertex) {
        this.outVertex() -> Direction.OUT
        this.inVertex() -> Direction.IN
        else -> throw IllegalArgumentException("Vertex is not incident to this edge")
    }
}

fun TinkerEdge.isIncidentTo(vertex: Vertex): Boolean {
    return vertex == this.outVertex() || vertex == this.inVertex()
}

fun TinkerEdge.connects(outV: Vertex, inV: Vertex): Boolean {
    return this.outVertex() == outV && this.inVertex() == inV
}

fun TinkerEdge.isSelfLoop(): Boolean {
    return this.outVertex() == this.inVertex()
}

val TinkerEdge.weight: Double
    get() = this.value<Double>("weight") ?: 1.0

val TinkerEdge.length: Double
    get() = this.value<Double>("length") ?: 1.0

fun TinkerEdge.bothVertices(): Iterator<Vertex> {
    return listOf(this.outVertex(), this.inVertex()).iterator()
}

fun TinkerEdge.vertices(direction: Direction): Iterator<Vertex> {
    return when (direction) {
        Direction.OUT -> listOf(this.outVertex()).iterator()
        Direction.IN -> listOf(this.inVertex()).iterator()
        Direction.BOTH -> this.bothVertices()
    }
}

fun safeCastEdge(edge: Any?): TinkerEdge? {
    return edge as? TinkerEdge
}

fun TinkerEdge.vertex(direction: Direction): Vertex {
    return when (direction) {
        Direction.OUT -> this.outVertex()
        Direction.IN -> this.inVertex()
        Direction.BOTH ->
                throw IllegalArgumentException(
                        "Direction.BOTH is not supported for vertex() method"
                )
    }
}

/**
 * Tests for TinkerEdge implementation. Tests Task 2.1.2 implementation: Enhanced edge
 * functionality.
 */
class TinkerEdgeTest :
        StringSpec({
            lateinit var graph: TinkerGraph
            lateinit var outVertex: Vertex
            lateinit var inVertex: Vertex
            lateinit var edge: TinkerEdge

            beforeTest {
                graph = TinkerGraph.open()
                outVertex = graph.addVertex("name", "alice")
                inVertex = graph.addVertex("name", "bob")
                edge =
                        outVertex.addEdge("knows", inVertex, "weight", 1.0, "since", 2020) as
                                TinkerEdge
            }

            "edge creation should work correctly" {
                edge.shouldNotBeNull()
                edge.id().shouldNotBeNull()
                edge.label() shouldBe "knows"
                edge.graph() shouldBe graph
                edge.outVertex() shouldBe outVertex
                edge.inVertex() shouldBe inVertex
            }

            "edge properties should be manageable" {
                // Test property setting and retrieval
                val weight = edge.property("weight", 0.8)
                weight.isPresent() shouldBe true
                weight.key() shouldBe "weight"
                weight.value() shouldBe 0.8

                // Test property retrieval through edge
                edge.value<Double>("weight") shouldBe 0.8
                edge.value<Int>("since") shouldBe 2020

                // Test keys
                edge.keys() shouldContain "weight"
                edge.keys() shouldContain "since"
            }

            "vertex traversal should work correctly" {
                // Test outVertex and inVertex
                edge.outVertex() shouldBe outVertex
                edge.inVertex() shouldBe inVertex

                // Test vertex(Direction)
                edge.vertex(Direction.OUT) shouldBe outVertex
                edge.vertex(Direction.IN) shouldBe inVertex

                shouldThrow<IllegalArgumentException> { edge.vertex(Direction.BOTH) }
            }

            "vertices iterator should work correctly" {
                // Test vertices(Direction.OUT)
                val outVertices = edge.vertices(Direction.OUT).asSequence().toList()
                outVertices shouldHaveSize 1
                outVertices[0] shouldBe outVertex

                // Test vertices(Direction.IN)
                val inVertices = edge.vertices(Direction.IN).asSequence().toList()
                inVertices shouldHaveSize 1
                inVertices[0] shouldBe inVertex

                // Test vertices(Direction.BOTH)
                val bothVertices = edge.vertices(Direction.BOTH).asSequence().toList()
                bothVertices shouldHaveSize 2
                bothVertices shouldContain outVertex
                bothVertices shouldContain inVertex
            }

            "both vertices iterator should work correctly" {
                val vertices = edge.bothVertices().asSequence().toList()
                vertices shouldHaveSize 2
                vertices shouldContain outVertex
                vertices shouldContain inVertex
            }

            "other vertex should work correctly" {
                edge.otherVertex(outVertex) shouldBe inVertex
                edge.otherVertex(inVertex) shouldBe outVertex

                val thirdVertex = graph.addVertex("name", "charlie")
                shouldThrow<IllegalArgumentException> { edge.otherVertex(thirdVertex) }
            }

            "get direction should work correctly" {
                edge.getDirection(outVertex) shouldBe Direction.OUT
                edge.getDirection(inVertex) shouldBe Direction.IN

                val thirdVertex = graph.addVertex("name", "charlie")
                shouldThrow<IllegalArgumentException> { edge.getDirection(thirdVertex) }
            }

            "is incident to should work correctly" {
                edge.isIncidentTo(outVertex) shouldBe true
                edge.isIncidentTo(inVertex) shouldBe true

                val thirdVertex = graph.addVertex("name", "charlie")
                edge.isIncidentTo(thirdVertex) shouldBe false
            }

            "connects should work correctly" {
                edge.connects(outVertex, inVertex) shouldBe true
                edge.connects(inVertex, outVertex) shouldBe true // Order shouldn't matter

                val thirdVertex = graph.addVertex("name", "charlie")
                edge.connects(outVertex, thirdVertex) shouldBe false
                edge.connects(thirdVertex, inVertex) shouldBe false
            }

            "self loop detection should work correctly" {
                edge.isSelfLoop() shouldBe false

                // Create a self-loop
                val selfLoopEdge = outVertex.addEdge("reflects", outVertex) as TinkerEdge
                selfLoopEdge.isSelfLoop() shouldBe true
            }

            "weight property should work correctly" {
                // Default weight should be 1.0
                edge.weight() shouldBe 1.0

                // Set weight
                edge.weight(2.5)
                edge.weight() shouldBe 2.5

                // Weight through property
                edge.property("weight", 3.7)
                edge.weight() shouldBe 3.7
            }

            "length property should work correctly" {
                // Length is an alias for weight
                edge.length() shouldBe 1.0

                edge.length(4.2)
                edge.length() shouldBe 4.2
                edge.weight() shouldBe 4.2 // Should be the same
            }

            "vertex pair should work correctly" {
                val pair = edge.vertexPair()
                pair.first shouldBe outVertex
                pair.second shouldBe inVertex
            }

            "copy should work correctly" {
                val vertex3 = graph.addVertex("name", "charlie") as TinkerVertex
                val vertex4 = graph.addVertex("name", "david") as TinkerVertex

                val copiedEdge = edge.copy(vertex3, vertex4)

                copiedEdge.id() shouldNotBe edge.id()
                copiedEdge.label() shouldBe edge.label()
                copiedEdge.outVertex() shouldBe vertex3
                copiedEdge.inVertex() shouldBe vertex4
            }

            "reverse should work correctly" {
                val reversedEdge = edge.reverse()

                reversedEdge.id() shouldNotBe edge.id()
                reversedEdge.label() shouldBe edge.label()
                reversedEdge.outVertex() shouldBe inVertex
                reversedEdge.inVertex() shouldBe outVertex
            }

            "direction comparisons should work correctly" {
                val sameDirectionEdge = outVertex.addEdge("likes", inVertex) as TinkerEdge
                val oppositeDirectionEdge = inVertex.addEdge("dislikes", outVertex) as TinkerEdge

                edge.hasSameDirection(sameDirectionEdge) shouldBe true
                edge.hasSameDirection(oppositeDirectionEdge) shouldBe false

                edge.hasOppositeDirection(sameDirectionEdge) shouldBe false
                edge.hasOppositeDirection(oppositeDirectionEdge) shouldBe true
            }

            "edge removal should clean up properly" {
                // Verify edge exists in graph
                graph.edge(edge.id()).shouldNotBeNull()

                // Verify edge is in vertex adjacency lists
                outVertex.edges(Direction.OUT, "knows").asSequence().toList() shouldContain edge
                inVertex.edges(Direction.IN, "knows").asSequence().toList() shouldContain edge

                // Remove edge
                edge.remove()

                // Verify edge is removed from graph
                graph.edge(edge.id()).shouldBeNull()

                // Verify edge is removed from vertex adjacency lists
                outVertex.edges(Direction.OUT, "knows").asSequence().toList() shouldNotContain edge
                inVertex.edges(Direction.IN, "knows").asSequence().toList() shouldNotContain edge

                // Verify operations on removed edge throw exception
                shouldThrow<IllegalStateException> { edge.property("test", "value") }

                shouldThrow<IllegalStateException> { edge.outVertex() }
            }

            "property removal should work correctly" {
                val prop = edge.property("temp", "value")
                prop.isPresent() shouldBe true
                edge.value<String>("temp") shouldBe "value"

                prop.remove()

                prop.isPresent() shouldBe false
                edge.value<String>("temp").shouldBeNull()
            }

            "edge equality should be based on ID" {
                val edge2 = outVertex.addEdge("likes", inVertex) as TinkerEdge

                edge shouldNotBe edge2
                edge shouldBe edge // Self equality

                // Edges are equal if they have the same id
                val sameEdge = graph.edge(edge.id())
                edge shouldBe sameEdge
            }

            "toString should provide proper representation" {
                val edgeString = edge.toString()
                edgeString.startsWith("e[") shouldBe true
                edgeString.contains("knows") shouldBe true
                edgeString.contains("->") shouldBe true
                edgeString.contains(edge.id().toString()) shouldBe true
            }

            "edge labels should be correctly assigned" {
                val labeledEdge =
                        outVertex.addEdge("WORKS_FOR", inVertex, "department", "engineering") as
                                TinkerEdge
                labeledEdge.label() shouldBe "WORKS_FOR"
                labeledEdge.value<String>("department") shouldBe "engineering"
            }

            "multiple edges between vertices should work correctly" {
                val newEdge = outVertex.addEdge("likes", inVertex) as TinkerEdge
                val edge3 = outVertex.addEdge("follows", inVertex) as TinkerEdge

                // All edges should exist
                val outEdges = outVertex.edges(Direction.OUT).asSequence().toList()
                outEdges shouldHaveSize 3 // knows, likes, follows

                val inEdges = inVertex.edges(Direction.IN).asSequence().toList()
                inEdges shouldHaveSize 3

                // Test label filtering
                val knowsEdges = outVertex.edges(Direction.OUT, "knows").asSequence().toList()
                knowsEdges shouldHaveSize 1
                knowsEdges shouldContain edge

                val likesEdges = outVertex.edges(Direction.OUT, "likes").asSequence().toList()
                likesEdges shouldHaveSize 1
                likesEdges shouldContain newEdge
            }

            "statistics should be collected correctly" {
                edge.property("weight", 1.5)
                val stats = edge.getStatistics()

                stats["id"] shouldBe edge.id()
                stats["label"] shouldBe "knows"
                stats["outVertexId"] shouldBe outVertex.id()
                stats["inVertexId"] shouldBe inVertex.id()
                stats["propertyCount"] shouldBe 2 // since and weight
                stats["isSelfLoop"] shouldBe false
                stats["weight"] shouldBe 1.5
            }

            "companion methods should work correctly" {
                // Test default label creation
                val defaultEdge =
                        TinkerEdge.create(
                                graph.getNextId(),
                                outVertex as TinkerVertex,
                                inVertex as TinkerVertex,
                                graph
                        )
                defaultEdge.label() shouldBe TinkerEdge.DEFAULT_LABEL

                // Test weighted edge creation
                val weightedEdge =
                        TinkerEdge.createWeighted(
                                graph.getNextId(),
                                outVertex as TinkerVertex,
                                inVertex as TinkerVertex,
                                "weighted",
                                2.5,
                                graph
                        )
                weightedEdge.label() shouldBe "weighted"
                weightedEdge.weight() shouldBe 2.5
            }

            "edge property indexing should work correctly" {
                // Add edge with indexed property
                edge.property("type", "friendship")
                edge.property("strength", 0.8)

                // Create index
                graph.createIndex("type", Edge::class)

                // Test index lookup
                val indexedEdges = graph.edgeIndex.get("type", "friendship")
                indexedEdges shouldContain edge
            }

            "numeric weights should work correctly" {
                // Test different numeric types for weight
                edge.property("weight", 42) // Int
                edge.weight() shouldBe 42.0

                edge.property("weight", 3.14f) // Float
                edge.weight() shouldBe 3.14f.toDouble()

                edge.property("weight", 2.718) // Double
                edge.weight() shouldBe 2.718
            }
        })
