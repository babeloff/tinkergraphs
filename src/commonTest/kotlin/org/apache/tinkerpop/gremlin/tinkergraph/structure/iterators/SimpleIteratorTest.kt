package org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

/**
 * Simple test suite to verify basic iterator functionality works correctly.
 *
 * This test class provides fundamental validation of TinkerGraph iterator implementations without
 * complex scenarios. It focuses on core functionality including:
 * - Basic vertex iteration and property access
 * - Edge iteration and traversal
 * - Property iterator functionality
 * - Iterator behavior with empty graphs
 *
 * These tests serve as a baseline to ensure iterator implementations work correctly before testing
 * more advanced scenarios in other test suites.
 *
 * @see org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators.TinkerVertexIterator
 * @see org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators.TinkerEdgeIterator
 * @see org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators.TinkerPropertyIterator
 */
class SimpleIteratorTest :
        StringSpec({
            "basic vertex iteration should work correctly" {
                /**
                 * Tests basic vertex iteration functionality.
                 *
                 * Creates a simple graph with three vertices and verifies that:
                 * - All vertices can be iterated correctly
                 * - Vertex properties can be accessed during iteration
                 * - The correct number of vertices is returned
                 * - Vertex names match expected values
                 */
                val graph = TinkerGraph.open()

                // Create some vertices
                val v1 = graph.addVertex("name", "alice")
                val v2 = graph.addVertex("name", "bob")
                val v3 = graph.addVertex("name", "charlie")

                // Test that basic vertex iteration works
                val vertices = graph.vertices().asSequence().toList()

                println("Vertices found: ${vertices.size}")
                vertices.forEach { v ->
                    println("  Vertex: ${v.id()} - ${v.value<String>("name")}")
                }

                vertices shouldHaveSize 3

                val names = vertices.map { it.value<String>("name") }.toSet()
                println("Names: $names")
                names shouldContain "alice"
                names shouldContain "bob"
                names shouldContain "charlie"

                graph.close()
            }

            "basic edge iteration should work correctly" {
                val graph = TinkerGraph.open()

                val alice = graph.addVertex("name", "alice")
                val bob = graph.addVertex("name", "bob")
                val charlie = graph.addVertex("name", "charlie")

                // Create some edges
                alice.addEdge("knows", bob, "weight", 0.5)
                bob.addEdge("knows", charlie, "weight", 0.8)

                // Test that basic edge iteration works
                val edges = graph.edges().asSequence().toList()

                edges shouldHaveSize 2

                val labels = edges.map { it.label() }.toSet()
                labels shouldContain "knows"

                val weights = edges.map { it.value<Double>("weight") }.toSet()
                weights shouldContain 0.5
                weights shouldContain 0.8

                graph.close()
            }

            "vertex traversal should work correctly" {
                val graph = TinkerGraph.open()

                val alice = graph.addVertex("name", "alice")
                val bob = graph.addVertex("name", "bob")
                val charlie = graph.addVertex("name", "charlie")

                alice.addEdge("knows", bob)
                bob.addEdge("knows", charlie)

                // Test outgoing vertex traversal from alice
                val outVertices = alice.vertices(Direction.OUT, "knows").asSequence().toList()

                println("Alice out vertices: ${outVertices.size}")
                outVertices.forEach { v ->
                    println("  Out vertex: ${v.id()} - ${v.value<String>("name")}")
                }

                outVertices shouldHaveSize 1
                outVertices.first().value<String>("name") shouldBe "bob"

                // Test incoming vertex traversal to charlie
                val inVertices = charlie.vertices(Direction.IN, "knows").asSequence().toList()

                inVertices shouldHaveSize 1
                inVertices.first().value<String>("name") shouldBe "bob"

                graph.close()
            }

            "edge traversal should work correctly" {
                val graph = TinkerGraph.open()

                val alice = graph.addVertex("name", "alice")
                val bob = graph.addVertex("name", "bob")
                val charlie = graph.addVertex("name", "charlie")

                alice.addEdge("knows", bob, "type", "friend")
                alice.addEdge("likes", charlie, "type", "romantic")

                // Test outgoing edge traversal from alice
                val outEdges = alice.edges(Direction.OUT).asSequence().toList()

                outEdges shouldHaveSize 2

                val labels = outEdges.map { it.label() }.toSet()
                labels shouldContain "knows"
                labels shouldContain "likes"

                val types = outEdges.map { it.value<String>("type") }.toSet()
                types shouldContain "friend"
                types shouldContain "romantic"

                graph.close()
            }

            "lazy evaluation should work correctly" {
                val graph = TinkerGraph.open()

                // Create 10 vertices
                repeat(10) { i ->
                    graph.addVertex("id", i, "type", if (i % 2 == 0) "even" else "odd")
                }

                // Test that we can stop iteration early (lazy evaluation)
                val iterator = graph.vertices()
                var count = 0

                while (iterator.hasNext() && count < 3) {
                    iterator.next()
                    count++
                }

                count shouldBe 3
                // If lazy evaluation works, we should still be able to continue
                iterator.hasNext().shouldBeTrue()

                graph.close()
            }

            "empty graph iteration should work correctly" {
                val graph = TinkerGraph.open()

                // Test empty graph iteration
                val vertices = graph.vertices().asSequence().toList()
                val edges = graph.edges().asSequence().toList()

                vertices.shouldBeEmpty()
                edges.shouldBeEmpty()

                graph.close()
            }

            "vertex property iteration should work correctly" {
                val graph = TinkerGraph.open()

                val vertex = graph.addVertex("name", "alice", "age", 29, "city", "paris")

                // Test vertex property iteration
                val vertexProperties = vertex.properties<Any>().asSequence().toList()

                vertexProperties shouldHaveSize 3

                val keys = vertexProperties.map { it.key() }.toSet()
                keys shouldContain "name"
                keys shouldContain "age"
                keys shouldContain "city"

                val values = vertexProperties.map { it.value() }.toSet()
                values shouldContain "alice"
                values shouldContain 29
                values shouldContain "paris"

                graph.close()
            }

            "vertex degree calculation should work correctly" {
                val graph = TinkerGraph.open()

                val alice = graph.addVertex("name", "alice")
                val bob = graph.addVertex("name", "bob")
                val charlie = graph.addVertex("name", "charlie")

                alice.addEdge("knows", bob)
                alice.addEdge("likes", charlie)
                bob.addEdge("knows", charlie)

                // Alice should have 2 outgoing edges
                val aliceOutEdges = alice.edges(Direction.OUT).asSequence().toList()
                aliceOutEdges shouldHaveSize 2

                // Charlie should have 2 incoming edges
                val charlieInEdges = charlie.edges(Direction.IN).asSequence().toList()
                charlieInEdges shouldHaveSize 2

                // Bob should have 1 outgoing and 1 incoming edge
                val bobOutEdges = bob.edges(Direction.OUT).asSequence().toList()
                val bobInEdges = bob.edges(Direction.IN).asSequence().toList()
                bobOutEdges shouldHaveSize 1
                bobInEdges shouldHaveSize 1

                graph.close()
            }
        })
