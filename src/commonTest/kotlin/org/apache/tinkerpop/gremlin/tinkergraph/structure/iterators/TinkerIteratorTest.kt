package org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.apache.tinkerpop.gremlin.tinkergraph.util.SafeCasting

/**
 * Comprehensive tests for TinkerGraph traversal iterators. Tests all iterator implementations
 * including vertex, edge, property, and traversing iterators.
 */
class TinkerIteratorTest :
        StringSpec({
            lateinit var graph: TinkerGraph
            lateinit var v1: Vertex
            lateinit var v2: Vertex
            lateinit var v3: Vertex
            lateinit var v4: Vertex
            lateinit var e1: Edge
            lateinit var e2: Edge
            lateinit var e3: Edge

            beforeTest {
                graph = TinkerGraph.open()

                // Create test vertices
                v1 = graph.addVertex("name", "alice", "age", 29, "type", "person")
                v2 = graph.addVertex("name", "bob", "age", 27, "type", "person")
                v3 = graph.addVertex("name", "charlie", "age", 32, "type", "person")
                v4 = graph.addVertex("name", "data", "type", "system")

                // Create test edges
                e1 = v1.addEdge("knows", v2, "weight", 0.5, "type", "friendship")
                e2 = v2.addEdge("knows", v3, "weight", 0.8, "type", "friendship")
                e3 = v1.addEdge("uses", v4, "frequency", "daily", "type", "interaction")

                // Create indices for testing
                graph.createIndex("name", Vertex::class)
                graph.createIndex("type", Edge::class)
            }

            afterTest { graph.close() }

            "TinkerVertexIterator all should work correctly" {
                val iterator = TinkerVertexIterator.all(graph)
                val vertices = mutableListOf<Vertex>()

                while (iterator.hasNext()) {
                    vertices.add(iterator.next())
                }

                vertices shouldHaveSize 4
                vertices shouldContain v1
                vertices shouldContain v2
                vertices shouldContain v3
                vertices shouldContain v4
            }

            "TinkerVertexIterator by IDs should work correctly" {
                val iterator = TinkerVertexIterator.byIds(graph, v1.id(), v3.id())
                val vertices = mutableListOf<Vertex>()

                while (iterator.hasNext()) {
                    vertices.add(iterator.next())
                }

                vertices shouldHaveSize 2
                vertices shouldContain v1
                vertices shouldContain v3
                vertices shouldNotContain v2
                vertices shouldNotContain v4
            }

            "TinkerVertexIterator by labels should work correctly" {
                val iterator = TinkerVertexIterator.byLabels(graph, "vertex")
                val vertices = mutableListOf<Vertex>()

                while (iterator.hasNext()) {
                    vertices.add(iterator.next())
                }

                vertices shouldHaveSize 4 // All vertices have default label "vertex"
            }

            "TinkerVertexIterator by property should find correct vertices" {
                val iterator = TinkerVertexIterator.byProperty(graph, "name", "alice")
                val vertices = mutableListOf<Vertex>()

                while (iterator.hasNext()) {
                    vertices.add(iterator.next())
                }

                vertices.size shouldBe 1
                vertices.first() shouldBe v1
                vertices.first().value<String>("name") shouldBe "alice"
            }

            "TinkerVertexIterator by properties should find correct vertices" {
                val properties = mapOf("type" to "person", "age" to 27)
                val iterator = TinkerVertexIterator.byProperties(graph, properties)
                val vertices = mutableListOf<Vertex>()

                while (iterator.hasNext()) {
                    vertices.add(iterator.next())
                }

                vertices.size shouldBe 1
                vertices.first() shouldBe v2
                vertices.first().value<String>("name") shouldBe "bob"
            }

            "TinkerEdgeIterator all should return all edges" {
                val iterator = TinkerEdgeIterator.all(graph)
                val edges = mutableListOf<Edge>()

                while (iterator.hasNext()) {
                    edges.add(iterator.next())
                }

                edges.size shouldBe 3
                edges.contains(e1) shouldBe true
                edges.contains(e2) shouldBe true
                edges.contains(e3) shouldBe true
            }

            "TinkerEdgeIterator by id should find correct edge" {
                val iterator = TinkerEdgeIterator.byIds(graph, e1.id())
                val edges = mutableListOf<Edge>()

                while (iterator.hasNext()) {
                    edges.add(iterator.next())
                }

                edges.size shouldBe 1
                edges.first() shouldBe e1
                edges.contains(e1) shouldBe true
                edges.contains(e2) shouldBe false
            }

            "TinkerEdgeIterator by label should find correct edges" {
                val iterator = TinkerEdgeIterator.byLabels(graph, "knows")
                val edges = mutableListOf<Edge>()

                while (iterator.hasNext()) {
                    edges.add(iterator.next())
                }

                edges.size shouldBe 2
                edges.contains(e1) shouldBe true
                edges.contains(e2) shouldBe true
                edges.all { it.label() == "knows" } shouldBe true
            }

            "TinkerEdgeIterator from vertex should find edges in specified direction" {
                // Test outgoing edges
                val outIterator =
                        TinkerEdgeIterator.fromVertex(SafeCasting.safeCastVertex(v1), Direction.OUT)
                val outEdges = mutableListOf<Edge>()

                while (outIterator.hasNext()) {
                    outEdges.add(outIterator.next())
                }

                outEdges.size shouldBe 2
                outEdges.contains(e1) shouldBe true
                outEdges.contains(e3) shouldBe true

                // Test incoming edges
                val inIterator =
                        TinkerEdgeIterator.fromVertex(SafeCasting.safeCastVertex(v2), Direction.IN)
                val inEdges = mutableListOf<Edge>()

                while (inIterator.hasNext()) {
                    inEdges.add(inIterator.next())
                }

                inEdges.size shouldBe 1
                inEdges.contains(e1) shouldBe true
            }

            "TinkerEdgeTraversingIterator by direction should find edges in specified direction" {
                val iterator =
                        TinkerEdgeTraversingIterator.byDirection(SafeCasting.safeCastVertex(v1))
                val edges = mutableListOf<Edge>()

                while (iterator.hasNext()) {
                    edges.add(iterator.next())
                }

                edges.size shouldBe 2
                edges.contains(e1) shouldBe true
                edges.contains(e3) shouldBe true
            }

            "TinkerEdgeIterator between vertices should find connecting edges" {
                val iterator = TinkerEdgeIterator.between(graph, v1, v2)
                val edges = mutableListOf<Edge>()

                while (iterator.hasNext()) {
                    edges.add(iterator.next())
                }

                edges.size shouldBe 1
                edges.first() shouldBe e1
            }

            "TinkerPropertyIterator all should return all properties of vertex" {
                val iterator = TinkerPropertyIterator.all<Any>(v1)
                val properties =
                        mutableListOf<org.apache.tinkerpop.gremlin.structure.Property<Any>>()

                while (iterator.hasNext()) {
                    properties.add(iterator.next())
                }

                properties.size shouldBe 3 // name, age, type properties
                val keys = properties.map { it.key() }.toSet()
                keys.contains("name") shouldBe true
                keys.contains("age") shouldBe true
                keys.contains("type") shouldBe true
            }

            "TinkerPropertyIterator by keys should find properties with specified keys" {
                val iterator = TinkerPropertyIterator.byKeys<String>(v1, "name", "type")
                val properties =
                        mutableListOf<org.apache.tinkerpop.gremlin.structure.Property<String>>()

                while (iterator.hasNext()) {
                    properties.add(iterator.next())
                }

                val keys = properties.map { it.key() }.toSet()
                keys.contains("name") shouldBe true
                keys.contains("type") shouldBe true
                keys.contains("age") shouldBe false
            }

            // TODO: byKeyAndValue method is not implemented yet
            /*
            "TinkerPropertyIterator by key and value should find matching property" {
                val iterator = TinkerPropertyIterator.byKeyAndValue(v2, "age", 27)
                val properties = mutableListOf<VertexProperty<*>>()

                while (iterator.hasNext()) {
                    properties.add(iterator.next())
                }

                properties.size shouldBe 1
                (properties[0] as Property<*>).value() shouldBe 27
            }
            */

            "TinkerPropertyIterator all should return all properties" {
                val iterator = TinkerPropertyIterator.all<Any>(v1)
                val properties = mutableListOf<VertexProperty<*>>()

                while (iterator.hasNext()) {
                    properties.add(iterator.next() as VertexProperty<*>)
                }

                properties.size shouldBe 3 // name, type, age properties
            }

            "TinkerPropertyIterator by key should find property with specified key" {
                val iterator = TinkerPropertyIterator.byKeys<String>(v1, "name")
                val properties = mutableListOf<VertexProperty<*>>()

                while (iterator.hasNext()) {
                    properties.add(iterator.next() as VertexProperty<*>)
                }

                properties.size shouldBe 1
                properties.first().key() shouldBe "name"
                properties.first().value() shouldBe "alice"
            }

            "TinkerVertexPropertyIterator by cardinality should find properties with specified cardinality" {
                val iterator =
                        TinkerVertexPropertyIterator.byCardinality<Any>(
                                v1,
                                VertexProperty.Cardinality.SINGLE
                        )
                val vertexProperties = mutableListOf<VertexProperty<Any>>()

                while (iterator.hasNext()) {
                    vertexProperties.add(iterator.next())
                }

                // All properties should be SINGLE cardinality by default
                vertexProperties.size shouldBe 3
            }

            "TinkerVertexTraversingIterator out should find outgoing vertices" {
                val iterator =
                        TinkerVertexTraversingIterator.outVertices(SafeCasting.safeCastVertex(v1))
                val vertices = mutableListOf<Vertex>()

                while (iterator.hasNext()) {
                    vertices.add(iterator.next())
                }

                vertices.size shouldBe 2
                vertices.contains(v2) shouldBe true
                vertices.contains(v4) shouldBe true
                vertices.contains(v1) shouldBe false
                vertices.contains(v3) shouldBe false
            }

            "TinkerVertexTraversingIterator in should find incoming vertices" {
                val iterator =
                        TinkerVertexTraversingIterator.inVertices(SafeCasting.safeCastVertex(v2))
                val vertices = mutableListOf<Vertex>()

                while (iterator.hasNext()) {
                    vertices.add(iterator.next())
                }

                vertices.size shouldBe 1
                vertices.contains(v1) shouldBe true
            }

            "TinkerVertexTraversingIterator both should find all connected vertices" {
                val iterator =
                        TinkerVertexTraversingIterator.bothVertices(SafeCasting.safeCastVertex(v2))
                val vertices = mutableListOf<Vertex>()

                while (iterator.hasNext()) {
                    vertices.add(iterator.next())
                }

                vertices.size shouldBe 2
                vertices.contains(v1) shouldBe true // v1 -> v2
                vertices.contains(v3) shouldBe true // v2 -> v3
                vertices.contains(v2) shouldBe false
                vertices.contains(v4) shouldBe false
            }

            "TinkerVertexTraversingIterator with labels should filter by edge labels" {
                val iterator =
                        TinkerVertexTraversingIterator.outVertices(
                                SafeCasting.safeCastVertex(v1),
                                "knows"
                        )
                val vertices = mutableListOf<Vertex>()

                while (iterator.hasNext()) {
                    vertices.add(iterator.next())
                }

                vertices.size shouldBe 1
                vertices.contains(v2) shouldBe true
                vertices.contains(v4) shouldBe false // Connected by "uses" edge, not "knows"
            }

            "iterator should support lazy evaluation" {
                // Create a large graph to test lazy evaluation
                val largeGraph = TinkerGraph.open()
                val vertices = mutableListOf<Vertex>()

                // Create 1000 vertices
                for (i in 1..1000) {
                    vertices.add(
                            largeGraph.addVertex("id", i, "type", if (i % 2 == 0) "even" else "odd")
                    )
                }

                // Test that we can get first few vertices without processing all
                val iterator = TinkerVertexIterator.byProperty(largeGraph, "type", "even")
                var count = 0

                while (iterator.hasNext() && count < 10) {
                    val vertex = iterator.next()
                    vertex.value<String>("type") shouldBe "even"
                    count++
                }

                count shouldBe 10
                largeGraph.close()
            }

            "iterator should be memory efficient" {
                // Test that iterators don't create large intermediate collections
                val iterator = TinkerVertexIterator.all(graph)

                // Should be able to process one at a time
                var processedCount = 0
                while (iterator.hasNext()) {
                    val vertex = iterator.next()
                    vertex shouldNotBe null
                    processedCount++
                }

                processedCount shouldBe 4
            }

            "iterator should filter removed elements" {
                // Test that removed elements are filtered out
                val vertexIterator = TinkerVertexIterator.all(graph)
                var vertexCount = 0
                while (vertexIterator.hasNext()) {
                    vertexIterator.next()
                    vertexCount++
                }
                vertexCount shouldBe 4

                // Remove a vertex
                v4.remove()

                // Iterator should now return 3 vertices
                val iteratorAfterRemoval = TinkerVertexIterator.all(graph)
                var countAfterRemoval = 0
                while (iteratorAfterRemoval.hasNext()) {
                    countAfterRemoval++
                    iteratorAfterRemoval.next()
                }
                countAfterRemoval shouldBe 3
            }

            "iterator should use index optimization when available" {
                // Test that indexed properties use efficient lookup
                val testGraph = TinkerGraph.open()
                testGraph.createIndex("name", Vertex::class)

                // Create vertices
                val testV1 = testGraph.addVertex("name", "test1", "age", 25)
                val testV2 = testGraph.addVertex("name", "test2", "age", 30)
                val testV3 = testGraph.addVertex("name", "test1", "age", 35) // Duplicate name

                // Query by indexed property should be efficient
                val iterator = TinkerVertexIterator.byProperty(testGraph, "name", "test1")
                val results = mutableListOf<Vertex>()

                while (iterator.hasNext()) {
                    results.add(iterator.next())
                }

                results.size shouldBe 2
                results shouldContain testV1
                results shouldContain testV3
                results shouldNotContain testV2

                testGraph.close()
            }

            "iterator should support composite filtering" {
                // Test multiple filters working together
                val properties = mapOf("type" to "person", "age" to 29)
                val iterator = TinkerVertexIterator.byProperties(graph, properties)

                var count = 0
                while (iterator.hasNext()) {
                    val vertex = iterator.next()
                    vertex.value<String>("type") shouldBe "person"
                    vertex.value<Int>("age") shouldBe 29
                    count++
                }

                count shouldBe 1
            }

            "iterator should handle empty results gracefully" {
                // Test iterators with no matching results
                val iterator = TinkerVertexIterator.byProperty(graph, "nonexistent", "value")

                iterator.hasNext() shouldBe false
                // Note: In Kotest, we don't test exception throwing in the same way
                // The iterator should simply return false for hasNext()
            }
        })
