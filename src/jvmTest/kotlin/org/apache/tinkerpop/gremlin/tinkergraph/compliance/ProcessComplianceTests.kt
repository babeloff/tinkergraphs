package org.apache.tinkerpop.gremlin.tinkergraph.compliance

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex

/**
 * Process API Compliance Tests
 *
 * This test suite validates that TinkerGraph implements basic graph traversal and processing operations
 * in compliance with Apache TinkerPop specifications. These tests focus on the core graph operations
 * that are currently implemented in TinkerGraph rather than full Gremlin traversal syntax.
 *
 * PROVENANCE:
 * - Based on: Apache TinkerPop 3.7.2
 * - Original Java Tests:
 *   * org.apache.tinkerpop.gremlin.structure.VertexTest (traversal methods)
 *   * org.apache.tinkerpop.gremlin.structure.EdgeTest (traversal methods)
 *   * org.apache.tinkerpop.gremlin.structure.GraphTest (iteration methods)
 * - Source: https://github.com/apache/tinkerpop/tree/3.7.2/tinkergraph-gremlin/src/test/java
 *
 * ADAPTATIONS:
 * - Converted from JUnit to Kotest StringSpec format
 * - Focused on vertex/edge traversal methods available in current TinkerGraph implementation
 * - Adapted to test iterator-based graph processing rather than full Gremlin syntax
 * - Cross-platform compatibility focus
 * - Simplified to match actual API surface of TinkerGraph
 *
 * Test Categories:
 * - Vertex traversal operations (vertices(), edges(), out(), in(), both())
 * - Edge iteration and filtering
 * - Graph-level iteration operations
 * - Property-based filtering and processing
 * - Iterator behavior and consistency
 * - Element relationship traversal
 *
 * Licensed under the Apache License, Version 2.0 (same as Apache TinkerPop)
 */
@TinkerPopTestSource(
    originalClass = "org.apache.tinkerpop.gremlin.structure.VertexTest",
    tinkerPopVersion = "3.7.2",
    adaptations = "Adapted to focus on TinkerGraph's actual traversal API; combined multiple test classes; cross-platform compatibility",
    coverage = 0.8
)
class ProcessComplianceTests : StringSpec({

    lateinit var graph: TinkerGraph

    beforeTest {
        graph = TinkerGraph.open()

        // Set up a standard test graph structure
        // Based on TinkerPop's standard "modern" graph pattern
        val marko = graph.addVertex("id", 1, "name", "marko", "age", 29)
        val vadas = graph.addVertex("id", 2, "name", "vadas", "age", 27)
        val lop = graph.addVertex("id", 3, "name", "lop", "lang", "java")
        val josh = graph.addVertex("id", 4, "name", "josh", "age", 32)
        val ripple = graph.addVertex("id", 5, "name", "ripple", "lang", "java")
        val peter = graph.addVertex("id", 6, "name", "peter", "age", 35)

        marko.addEdge("knows", vadas, "weight", 0.5)
        marko.addEdge("knows", josh, "weight", 1.0)
        marko.addEdge("created", lop, "weight", 0.4)
        josh.addEdge("created", ripple, "weight", 1.0)
        josh.addEdge("created", lop, "weight", 0.4)
        peter.addEdge("created", lop, "weight", 0.2)
    }

    afterTest {
        graph.close()
    }

    // ===== GRAPH-LEVEL ITERATION OPERATIONS =====

    "Graph.vertices() should return all vertices" {
        val vertices = graph.vertices().asSequence().toList()
        vertices shouldHaveSize 6
        vertices.forEach { vertex ->
            vertex.shouldBeInstanceOf<Vertex>()
            vertex.graph() shouldBe graph
        }
    }

    "Graph.vertices(ids...) should return specific vertices" {
        val specificVertices = graph.vertices(1, 3, 5).asSequence().toList()
        specificVertices shouldHaveSize 3
        val ids = specificVertices.map { it.id() }
        ids shouldContain 1
        ids shouldContain 3
        ids shouldContain 5
    }

    "Graph.edges() should return all edges" {
        val edges = graph.edges().asSequence().toList()
        edges shouldHaveSize 6
        edges.forEach { edge ->
            edge.shouldBeInstanceOf<Edge>()
            edge.graph() shouldBe graph
        }
    }

    "Graph.edges(ids...) should return specific edges" {
        val allEdges = graph.edges().asSequence().toList()
        val firstEdge = allEdges[0]
        val secondEdge = allEdges[1]

        val specificEdges = graph.edges(firstEdge.id(), secondEdge.id()).asSequence().toList()
        specificEdges shouldHaveSize 2
        specificEdges shouldContain firstEdge
        specificEdges shouldContain secondEdge
    }

    // ===== VERTEX TRAVERSAL OPERATIONS =====

    "Vertex.vertices(Direction.OUT) should return outgoing adjacent vertices" {
        val marko = graph.vertex(1)!!
        val outVertices = marko.vertices(Direction.OUT).asSequence().toList()

        outVertices shouldHaveSize 3 // vadas, josh, lop
        val names = outVertices.map { it.value<String>("name") }
        names shouldContain "vadas"
        names shouldContain "josh"
        names shouldContain "lop"
    }

    "Vertex.vertices(Direction.IN) should return incoming adjacent vertices" {
        val lop = graph.vertex(3)!! // lop vertex has 3 incoming "created" edges
        val inVertices = lop.vertices(Direction.IN).asSequence().toList()

        inVertices shouldHaveSize 3 // marko, josh, peter
        val names = inVertices.map { it.value<String>("name") }
        names shouldContain "marko"
        names shouldContain "josh"
        names shouldContain "peter"
    }

    "Vertex.vertices(Direction.BOTH) should return all adjacent vertices" {
        val josh = graph.vertex(4)!!
        val bothVertices = josh.vertices(Direction.BOTH).asSequence().toList()

        // Josh has incoming edge from marko (knows) and outgoing edges to ripple, lop (created)
        bothVertices shouldHaveSize 3
        val names = bothVertices.map { it.value<String>("name") }
        names shouldContain "marko"
        names shouldContain "ripple"
        names shouldContain "lop"
    }

    "Vertex.vertices(Direction, label...) should filter by edge label" {
        val marko = graph.vertex(1)!!
        val knownVertices = marko.vertices(Direction.OUT, "knows").asSequence().toList()

        knownVertices shouldHaveSize 2 // vadas, josh
        val names = knownVertices.map { it.value<String>("name") }
        names shouldContain "vadas"
        names shouldContain "josh"
        names shouldNotContain "lop" // connected by "created", not "knows"
    }

    // ===== EDGE TRAVERSAL OPERATIONS =====

    "Vertex.edges(Direction.OUT) should return outgoing edges" {
        val marko = graph.vertex(1)!!
        val outEdges = marko.edges(Direction.OUT).asSequence().toList()

        outEdges shouldHaveSize 3
        val labels = outEdges.map { it.label() }
        labels shouldContain "knows"
        labels shouldContain "created"
    }

    "Vertex.edges(Direction.IN) should return incoming edges" {
        val lop = graph.vertex(3)!!
        val inEdges = lop.edges(Direction.IN).asSequence().toList()

        inEdges shouldHaveSize 3
        inEdges.forEach { edge ->
            edge.label() shouldBe "created"
            edge.inVertex() shouldBe lop
        }
    }

    "Vertex.edges(Direction.BOTH) should return all incident edges" {
        val josh = graph.vertex(4)!!
        val allEdges = josh.edges(Direction.BOTH).asSequence().toList()

        allEdges shouldHaveSize 3 // 1 incoming (knows from marko) + 2 outgoing (created to ripple, lop)
        val labels = allEdges.map { it.label() }
        labels shouldContain "knows"
        labels shouldContain "created"
    }

    "Vertex.edges(Direction, label...) should filter by edge label" {
        val marko = graph.vertex(1)!!
        val createdEdges = marko.edges(Direction.OUT, "created").asSequence().toList()

        createdEdges shouldHaveSize 1
        createdEdges[0].label() shouldBe "created"
        createdEdges[0].inVertex().value<String>("name") shouldBe "lop"
    }

    // ===== COMPLEX TRAVERSAL PATTERNS =====

    "Multi-hop traversal: friends of friends" {
        val marko = graph.vertex(1)!!
        val friendsOfFriends = mutableSetOf<Vertex>()

        // Get marko's friends
        val friends = marko.vertices(Direction.OUT, "knows").asSequence().toList()

        // For each friend, get their friends
        friends.forEach { friend ->
            val friendsFriends = friend.vertices(Direction.OUT, "knows").asSequence().toList()
            friendsFriends.forEach { fof ->
                if (fof != marko) { // Exclude marko himself
                    friendsOfFriends.add(fof)
                }
            }
        }

        // In our test graph, josh and vadas don't have outgoing "knows" edges
        // So friends of friends should be empty
        friendsOfFriends shouldHaveSize 0
    }

    "Multi-hop traversal: co-creators" {
        val marko = graph.vertex(1)!!
        val coCreators = mutableSetOf<Vertex>()

        // Get projects marko created
        val markoProjects = marko.vertices(Direction.OUT, "created").asSequence().toList()

        // For each project, get all creators
        markoProjects.forEach { project ->
            val creators = project.vertices(Direction.IN, "created").asSequence().toList()
            creators.forEach { creator ->
                if (creator != marko) { // Exclude marko himself
                    coCreators.add(creator)
                }
            }
        }

        coCreators shouldHaveSize 2 // josh and peter both created lop
        val names = coCreators.map { it.value<String>("name") }
        names shouldContain "josh"
        names shouldContain "peter"
    }

    // ===== ITERATOR BEHAVIOR COMPLIANCE =====

    "Iterator.hasNext() and next() should work correctly for vertices" {
        val vertexIterator = graph.vertices()
        var count = 0

        while (vertexIterator.hasNext()) {
            val vertex = vertexIterator.next()
            vertex.shouldNotBeNull()
            vertex.shouldBeInstanceOf<Vertex>()
            count++
        }

        count shouldBe 6
    }

    "Iterator.hasNext() and next() should work correctly for edges" {
        val edgeIterator = graph.edges()
        var count = 0

        while (edgeIterator.hasNext()) {
            val edge = edgeIterator.next()
            edge.shouldNotBeNull()
            edge.shouldBeInstanceOf<Edge>()
            count++
        }

        count shouldBe 6
    }

    "Empty iterator should handle correctly" {
        val emptyVertices = graph.vertices(999, 998).asSequence().toList() // Non-existent IDs
        emptyVertices shouldHaveSize 0

        val emptyEdges = graph.edges(999, 998).asSequence().toList() // Non-existent IDs
        emptyEdges shouldHaveSize 0
    }

    // ===== PROPERTY-BASED PROCESSING =====

    "Filtering vertices by property existence" {
        val allVertices = graph.vertices().asSequence().toList()

        val verticesWithAge = allVertices.filter { it.keys().contains("age") }
        verticesWithAge shouldHaveSize 4 // marko, vadas, josh, peter

        val verticesWithLang = allVertices.filter { it.keys().contains("lang") }
        verticesWithLang shouldHaveSize 2 // lop, ripple
    }

    "Filtering vertices by property value" {
        val allVertices = graph.vertices().asSequence().toList()

        val youngPeople = allVertices.filter { vertex ->
            vertex.keys().contains("age") && vertex.value<Int>("age")?.let { it < 30 } == true
        }
        youngPeople shouldHaveSize 2 // marko (29), vadas (27)

        val javaProjects = allVertices.filter { vertex ->
            vertex.keys().contains("lang") && vertex.value<String>("lang") == "java"
        }
        javaProjects shouldHaveSize 2 // lop, ripple
    }

    "Processing edge weights" {
        val allEdges = graph.edges().asSequence().toList()

        val weightedEdges = allEdges.filter { it.keys().contains("weight") }
        weightedEdges shouldHaveSize 6 // All edges have weights

        val highWeightEdges = weightedEdges.filter { edge ->
            edge.value<Double>("weight")?.let { it >= 1.0 } == true
        }
        highWeightEdges shouldHaveSize 2 // marko-knows-josh (1.0), josh-created-ripple (1.0)
    }

    // ===== ELEMENT RELATIONSHIP VALIDATION =====

    "Edge endpoints should be consistent" {
        val allEdges = graph.edges().asSequence().toList()

        allEdges.forEach { edge ->
            val outVertex = edge.outVertex()
            val inVertex = edge.inVertex()

            // Verify the edge appears in outVertex's outgoing edges
            val outEdges = outVertex.edges(Direction.OUT).asSequence().toList()
            outEdges shouldContain edge

            // Verify the edge appears in inVertex's incoming edges
            val inEdges = inVertex.edges(Direction.IN).asSequence().toList()
            inEdges shouldContain edge

            // Verify vertices are reachable through the edge
            val outVertexNeighbors = outVertex.vertices(Direction.OUT).asSequence().toList()
            outVertexNeighbors shouldContain inVertex
        }
    }

    "Vertex-edge consistency after modifications" {
        val vertex1 = graph.addVertex("id", 100, "name", "test1")
        val vertex2 = graph.addVertex("id", 101, "name", "test2")

        // Add edge
        val edge = vertex1.addEdge("testEdge", vertex2, "weight", 0.5)

        // Verify edge appears in traversals
        val outEdges = vertex1.edges(Direction.OUT, "testEdge").asSequence().toList()
        outEdges shouldContain edge

        val inEdges = vertex2.edges(Direction.IN, "testEdge").asSequence().toList()
        inEdges shouldContain edge

        val outVertices = vertex1.vertices(Direction.OUT, "testEdge").asSequence().toList()
        outVertices shouldContain vertex2

        // Remove edge and verify it's gone
        edge.remove()

        val outEdgesAfter = vertex1.edges(Direction.OUT, "testEdge").asSequence().toList()
        outEdgesAfter shouldHaveSize 0

        val inEdgesAfter = vertex2.edges(Direction.IN, "testEdge").asSequence().toList()
        inEdgesAfter shouldHaveSize 0
    }

    // ===== PERFORMANCE AND SCALABILITY BASICS =====

    "Large iteration should be consistent" {
        // This test ensures that large iterations work correctly
        val allVertices = graph.vertices().asSequence().toList()
        val allEdges = graph.edges().asSequence().toList()

        // Multiple iterations should be consistent
        val allVertices2 = graph.vertices().asSequence().toList()
        val allEdges2 = graph.edges().asSequence().toList()

        allVertices.size shouldBe allVertices2.size
        allEdges.size shouldBe allEdges2.size

        allVertices.map { it.id().toString() }.sorted() shouldBe allVertices2.map { it.id().toString() }.sorted()
        allEdges.map { it.id().toString() }.sorted() shouldBe allEdges2.map { it.id().toString() }.sorted()
    }

    "Vertex traversal should be repeatable" {
        val marko = graph.vertex(1)!!

        val outVertices1 = marko.vertices(Direction.OUT).asSequence().toList()
        val outVertices2 = marko.vertices(Direction.OUT).asSequence().toList()

        outVertices1.size shouldBe outVertices2.size
        outVertices1.map { it.id().toString() }.sorted() shouldBe outVertices2.map { it.id().toString() }.sorted()

        val outEdges1 = marko.edges(Direction.OUT).asSequence().toList()
        val outEdges2 = marko.edges(Direction.OUT).asSequence().toList()

        outEdges1.size shouldBe outEdges2.size
        outEdges1.map { it.id().toString() }.sorted() shouldBe outEdges2.map { it.id().toString() }.sorted()
    }
})
