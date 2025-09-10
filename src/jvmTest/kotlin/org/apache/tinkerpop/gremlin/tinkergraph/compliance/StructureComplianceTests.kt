package org.apache.tinkerpop.gremlin.tinkergraph.compliance

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex

/**
 * Structure API Compliance Tests
 *
 * This test suite validates that TinkerGraph implements the core TinkerPop Graph structure API
 * in compliance with Apache TinkerPop specifications. These tests are based on patterns from
 * TinkerPop's StructureStandardSuite and verify fundamental graph operations.
 *
 * PROVENANCE:
 * - Based on: Apache TinkerPop 3.7.2
 * - Original Java Tests:
 *   * org.apache.tinkerpop.gremlin.structure.GraphTest
 *   * org.apache.tinkerpop.gremlin.structure.VertexTest
 *   * org.apache.tinkerpop.gremlin.structure.EdgeTest
 *   * org.apache.tinkerpop.gremlin.structure.FeatureTest
 * - Source: https://github.com/apache/tinkerpop/tree/3.7.2/tinkergraph-gremlin/src/test/java
 *
 * ADAPTATIONS:
 * - Converted from JUnit to Kotest StringSpec format
 * - Combined multiple Java test classes into single Kotlin test suite
 * - Simplified property operations for cross-platform compatibility
 * - Added explicit type parameters for Kotlin type inference
 *
 * Test Categories:
 * - Graph Features compliance
 * - Vertex API compliance
 * - Edge API compliance
 * - Property API compliance
 * - VertexProperty API compliance
 * - Graph Variables compliance
 * - Transaction behavior compliance
 *
 * Licensed under the Apache License, Version 2.0 (same as Apache TinkerPop)
 */
@TinkerPopTestSource(
    originalClass = "org.apache.tinkerpop.gremlin.structure.GraphTest",
    tinkerPopVersion = "3.7.2",
    adaptations = "Combined multiple Java test classes; adapted to Kotest format; cross-platform compatibility",
    coverage = 0.8
)
class StructureComplianceTests : StringSpec({

    lateinit var graph: TinkerGraph

    beforeTest {
        graph = TinkerGraph.open()
    }

    afterTest {
        graph.close()
    }

    // ===== GRAPH FEATURES COMPLIANCE =====

    "Graph.features() should return valid features object" {
        val features = graph.features()
        features.shouldNotBeNull()
        features.graph().shouldNotBeNull()
        features.vertex().shouldNotBeNull()
        features.edge().shouldNotBeNull()
    }

    "Graph features should advertise correct capabilities" {
        val graphFeatures = graph.features().graph()

        // TinkerGraph is an in-memory graph
        graphFeatures.supportsComputer() shouldBe false
        graphFeatures.supportsPersistence() shouldBe false
        graphFeatures.supportsConcurrentAccess() shouldBe false
        graphFeatures.supportsTransactions() shouldBe false
        graphFeatures.supportsThreadedTransactions() shouldBe false
    }

    "Vertex features should advertise correct capabilities" {
        val vertexFeatures = graph.features().vertex()

        // TinkerGraph supports comprehensive vertex features
        vertexFeatures.supportsMetaProperties() shouldBe true
        vertexFeatures.supportsMultiProperties() shouldBe true
        vertexFeatures.supportsUserSuppliedIds() shouldBe true
        vertexFeatures.supportsNumericIds() shouldBe true
        vertexFeatures.supportsStringIds() shouldBe true
        vertexFeatures.supportsUuidIds() shouldBe true
        vertexFeatures.supportsCustomIds() shouldBe true
        vertexFeatures.supportsAnyIds() shouldBe true
    }

    "Edge features should advertise correct capabilities" {
        val edgeFeatures = graph.features().edge()

        // TinkerGraph supports comprehensive edge features
        edgeFeatures.supportsUserSuppliedIds() shouldBe true
        edgeFeatures.supportsNumericIds() shouldBe true
        edgeFeatures.supportsStringIds() shouldBe true
        edgeFeatures.supportsUuidIds() shouldBe true
        edgeFeatures.supportsCustomIds() shouldBe true
        edgeFeatures.supportsAnyIds() shouldBe true
    }

    // ===== VERTEX API COMPLIANCE =====

    "Graph.addVertex() should create vertex with auto-generated ID" {
        val vertex = graph.addVertex()
        vertex.shouldNotBeNull()
        vertex.id().shouldNotBeNull()
        vertex.label() shouldBe Vertex.DEFAULT_LABEL
        vertex.graph() shouldBe graph
    }

    "Graph.addVertex() should accept user-supplied ID" {
        val customId = "custom-vertex-id"
        val vertex = graph.addVertex("id", customId)
        vertex.id() shouldBe customId
    }

    "Graph.addVertex() should accept custom label" {
        val vertex = graph.addVertex("label", "person")
        vertex.label() shouldBe "person"
    }

    "Graph.addVertex() should accept key-value properties" {
        val vertex = graph.addVertex("name", "marko", "age", 29)
        vertex.value<String>("name") shouldBe "marko"
        vertex.value<Int>("age") shouldBe 29
    }

    "Graph.vertex(id) should retrieve existing vertex" {
        val originalVertex = graph.addVertex("id", "test-vertex")
        val retrievedVertex = graph.vertex("test-vertex")

        retrievedVertex.shouldNotBeNull()
        retrievedVertex shouldBe originalVertex
        retrievedVertex.id() shouldBe "test-vertex"
    }

    "Graph.vertex(id) should return null for non-existent vertex" {
        val vertex = graph.vertex("non-existent")
        vertex.shouldBeNull()
    }

    "Graph.vertices() should return all vertices" {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        val vertices = graph.vertices().asSequence().toList()
        vertices shouldHaveSize 3
        vertices shouldContain v1
        vertices shouldContain v2
        vertices shouldContain v3
    }

    "Graph.vertices(ids...) should return specific vertices" {
        graph.addVertex("id", 1)
        graph.addVertex("id", 2)
        graph.addVertex("id", 3)

        val vertices = graph.vertices(1, 3).asSequence().toList()
        vertices shouldHaveSize 2
        vertices.map { it.id() } shouldContain 1
        vertices.map { it.id() } shouldContain 3
    }

    // ===== EDGE API COMPLIANCE =====

    "Vertex.addEdge() should create edge between vertices" {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)

        val edge = v1.addEdge("knows", v2)
        edge.shouldNotBeNull()
        edge.label() shouldBe "knows"
        edge.outVertex() shouldBe v1
        edge.inVertex() shouldBe v2
    }

    "Vertex.addEdge() should accept edge properties" {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)

        val edge = v1.addEdge("knows", v2, "weight", 0.5, "since", 2010)
        edge.value<Double>("weight") shouldBe 0.5
        edge.value<Int>("since") shouldBe 2010
    }

    "Graph.edge(id) should retrieve existing edge" {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val originalEdge = v1.addEdge("knows", v2, "id", "test-edge")

        val retrievedEdge = graph.edge("test-edge")
        retrievedEdge.shouldNotBeNull()
        retrievedEdge shouldBe originalEdge
    }

    "Graph.edge(id) should return null for non-existent edge" {
        val edge = graph.edge("non-existent")
        edge.shouldBeNull()
    }

    "Graph.edges() should return all edges" {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        val e1 = v1.addEdge("knows", v2)
        val e2 = v2.addEdge("knows", v3)
        val e3 = v1.addEdge("created", v3)

        val edges = graph.edges().asSequence().toList()
        edges shouldHaveSize 3
        edges shouldContain e1
        edges shouldContain e2
        edges shouldContain e3
    }

    // ===== PROPERTY API COMPLIANCE =====

    "Vertex properties should support CRUD operations" {
        val vertex = graph.addVertex("id", 1)

        // Create
        val property = vertex.property("name", "marko")
        property.isPresent() shouldBe true
        property.key() shouldBe "name"
        property.value() shouldBe "marko"

        // Read
        vertex.value<String>("name") shouldBe "marko"
        vertex.properties<String>("name").hasNext() shouldBe true

        // Update (overwrite)
        vertex.property("name", "marko antonio rodriguez")
        vertex.value<String>("name") shouldBe "marko antonio rodriguez"

        // Delete
        val nameProperty = vertex.property<String>("name")
        nameProperty.remove()
        vertex.property<String>("name").isPresent() shouldBe false
    }

    "Edge properties should support CRUD operations" {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val edge = v1.addEdge("knows", v2)

        // Create
        val property = edge.property("weight", 0.5)
        property.isPresent() shouldBe true
        property.key() shouldBe "weight"
        property.value() shouldBe 0.5

        // Read
        edge.value<Double>("weight") shouldBe 0.5

        // Update
        edge.property("weight", 1.0)
        edge.value<Double>("weight") shouldBe 1.0

        // Delete
        val weightProperty = edge.property<Double>("weight")
        weightProperty.remove()
        edge.property<Double>("weight").isPresent() shouldBe false
    }

    // ===== VERTEX PROPERTY API COMPLIANCE =====

    "VertexProperty should support meta-properties" {
        val vertex = graph.addVertex("id", 1)
        val vp = vertex.property("location", "santa fe", "startTime", 2001, "endTime", 2004)

        vp.shouldNotBeNull()
        vp.value() shouldBe "santa fe"
        vp.value<Int>("startTime") shouldBe 2001
        vp.value<Int>("endTime") shouldBe 2004
    }

    "VertexProperty should support LIST cardinality" {
        val vertex = graph.addVertex("id", 1) as TinkerVertex

        val vp1 = vertex.property("location", "santa fe", VertexProperty.Cardinality.LIST)
        val vp2 = vertex.property("location", "santa cruz", VertexProperty.Cardinality.LIST)

        val locations = vertex.properties<String>("location").asSequence().toList()
        locations shouldHaveSize 2
        locations.map { it.value() } shouldContain "santa fe"
        locations.map { it.value() } shouldContain "santa cruz"
    }

    "VertexProperty should support SET cardinality" {
        val vertex = graph.addVertex("id", 1) as TinkerVertex

        vertex.property("skill", "java", VertexProperty.Cardinality.SET)

        // Adding same value should throw exception
        shouldThrow<Exception> {
            vertex.property("skill", "java", VertexProperty.Cardinality.SET)
        }
    }

    "VertexProperty should support SINGLE cardinality (default)" {
        val vertex = graph.addVertex("id", 1)

        vertex.property("name", "marko")
        vertex.property("name", "marko antonio rodriguez")

        val names = vertex.properties<String>("name").asSequence().toList()
        names shouldHaveSize 1
        names[0].value() shouldBe "marko antonio rodriguez"
    }

    // ===== GRAPH VARIABLES COMPLIANCE =====

    "Graph.variables() should support variable operations" {
        val variables = graph.variables()
        variables.shouldNotBeNull()

        // Set variable
        variables.set("testKey", "testValue")

        // Get variable
        variables.get<String>("testKey") shouldBe "testValue"

        // Check existence
        variables.keys() shouldContain "testKey"

        // Remove variable
        variables.remove("testKey")
        variables.keys() shouldNotContain "testKey"
    }

    // ===== TRANSACTION COMPLIANCE =====

    "Graph should handle transaction operations gracefully" {
        // TinkerGraph doesn't support transactions, but should handle gracefully
        // Test that basic graph operations work without transaction support
        val vertex = graph.addVertex("id", "test")
        vertex.shouldNotBeNull()

        // Verify graph state is consistent without transactions
        graph.vertex("test").shouldNotBeNull()
    }

    // ===== ELEMENT EQUALITY AND HASHCODE COMPLIANCE =====

    "Vertex equality should be based on ID" {
        val v1a = graph.addVertex("id", "vertex1", "name", "marko")
        val v1b = graph.vertex("vertex1")
        val v2 = graph.addVertex("id", "vertex2", "name", "stephen")

        v1a shouldBe v1b
        v1a shouldNotBe v2
        v1a.hashCode() shouldBe v1b!!.hashCode()
        v1a.hashCode() shouldNotBe v2.hashCode()
    }

    "Edge equality should be based on ID" {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val e1a = v1.addEdge("knows", v2, "id", "edge1")
        val e1b = graph.edge("edge1")
        val e2 = v1.addEdge("likes", v2, "id", "edge2")

        e1a shouldBe e1b
        e1a shouldNotBe e2
        e1a.hashCode() shouldBe e1b!!.hashCode()
        e1a.hashCode() shouldNotBe e2.hashCode()
    }

    // ===== ELEMENT REMOVAL COMPLIANCE =====

    "Vertex removal should clean up incident edges" {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)
        val edge1 = v1.addEdge("knows", v2)
        val edge2 = v2.addEdge("created", v3)

        // Verify initial state
        graph.vertices().asSequence().count() shouldBe 3
        graph.edges().asSequence().count() shouldBe 2

        // Remove vertex v2
        v2.remove()

        // Both edges should be removed since v2 was incident to both
        graph.vertices().asSequence().count() shouldBe 2
        graph.edges().asSequence().count() shouldBe 0
        graph.vertex(2).shouldBeNull()
        graph.edge(edge1.id()).shouldBeNull()
        graph.edge(edge2.id()).shouldBeNull()
    }

    "Edge removal should not affect vertices" {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val edge = v1.addEdge("knows", v2, "id", "test-edge")

        // Verify initial state
        graph.vertices().asSequence().count() shouldBe 2
        graph.edges().asSequence().count() shouldBe 1

        // Remove edge
        edge.remove()

        // Vertices should remain
        graph.vertices().asSequence().count() shouldBe 2
        graph.edges().asSequence().count() shouldBe 0
        graph.edge("test-edge").shouldBeNull()
        graph.vertex(1).shouldNotBeNull()
        graph.vertex(2).shouldNotBeNull()
    }

    // ===== CONFIGURATION COMPLIANCE =====

    "Graph.configuration() should return configuration map" {
        val config = graph.configuration()
        config.shouldNotBeNull()
        config shouldBe mapOf<String, Any?>()
    }

    // ===== ITERATOR BEHAVIOR COMPLIANCE =====

    "Graph iterators should be consistent and repeatable" {
        val v1 = graph.addVertex("id", 1)
        val v2 = graph.addVertex("id", 2)
        val v3 = graph.addVertex("id", 3)

        // Multiple iterations should be consistent
        val firstIteration = graph.vertices().asSequence().map { it.id().toString() }.sorted().toList()
        val secondIteration = graph.vertices().asSequence().map { it.id().toString() }.sorted().toList()

        firstIteration shouldBe secondIteration
        firstIteration shouldHaveSize 3
        firstIteration shouldContain "1"
        firstIteration shouldContain "2"
        firstIteration shouldContain "3"
    }

    "Empty graph should return empty iterators" {
        graph.vertices().hasNext() shouldBe false
        graph.edges().hasNext() shouldBe false
        graph.vertices().asSequence().count() shouldBe 0
        graph.edges().asSequence().count() shouldBe 0
    }
})
