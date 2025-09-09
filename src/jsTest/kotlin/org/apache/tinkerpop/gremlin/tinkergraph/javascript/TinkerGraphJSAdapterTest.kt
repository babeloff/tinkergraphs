package org.apache.tinkerpop.gremlin.tinkergraph.javascript

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import kotlin.time.measureTime
import kotlin.time.Duration.Companion.seconds

/** Tests for TinkerGraphJSAdapter functionality in JavaScript environments. */
class TinkerGraphJSAdapterTest :
        StringSpec({
            lateinit var adapter: TinkerGraphJSAdapter

            beforeTest { adapter = TinkerGraphJSAdapter.open() }

            afterTest { adapter.clear() }

            "create adapter should initialize correctly" {
                adapter shouldNotBe null
                adapter.getGraph() shouldNotBe null
            }

            "add vertex should create vertex with label" {
                val vertex = adapter.addVertex("person")

                vertex shouldNotBe null
                vertex.label() shouldBe "person"
                vertex.id() shouldNotBe null
            }

            "add vertex with properties should set properties correctly" {
                val properties = js("{}")
                properties.name = "Alice"
                properties.age = 30

                val vertex = adapter.addVertex("person", properties)

                vertex shouldNotBe null
                vertex.label() shouldBe "person"
                vertex.value<String>("name") shouldBe "Alice"
                vertex.value<Int>("age") shouldBe 30
            }

            "add edge should connect vertices" {
                val alice = adapter.addVertex("person")
                adapter.setVertexProperty(alice, "name", "Alice")

                val bob = adapter.addVertex("person")
                adapter.setVertexProperty(bob, "name", "Bob")

                val edge = adapter.addEdge(alice, "knows", bob)

                edge shouldNotBe null
                edge.label() shouldBe "knows"
                edge.outVertex() shouldBe alice
                edge.inVertex() shouldBe bob
            }

            "add edge with properties should set edge properties" {
                val alice = adapter.addVertex("person")
                val bob = adapter.addVertex("person")

                val edgeProps = js("{}")
                edgeProps.since = 2020
                edgeProps.strength = 0.8

                val edge = adapter.addEdge(alice, "knows", bob, edgeProps)

                edge shouldNotBe null
                edge.value<Int>("since") shouldBe 2020
                edge.value<Double>("strength") shouldBe 0.8
            }

            "find vertices should return matching vertices" {
                val alice = adapter.addVertex("person")
                adapter.setVertexProperty(alice, "name", "Alice")
                adapter.setVertexProperty(alice, "age", 30)

                val bob = adapter.addVertex("person")
                adapter.setVertexProperty(bob, "name", "Bob")
                adapter.setVertexProperty(bob, "age", 25)

                val people = adapter.findVertices("person")
                people.size shouldBe 2

                val youngPeople = adapter.findVerticesByProperty("age", 25)
                youngPeople.size shouldBe 1
                adapter.getVertexProperty(youngPeople[0], "name") shouldBe "Bob"
            }

            "find edges should return matching edges" {
                val alice = adapter.addVertex("person")
                val bob = adapter.addVertex("person")
                val charlie = adapter.addVertex("person")

                adapter.addEdge(alice, "knows", bob)
                adapter.addEdge(alice, "knows", charlie)
                adapter.addEdge(bob, "likes", charlie)

                val knowsEdges = adapter.findEdges("knows")
                knowsEdges.size shouldBe 2

                val allEdges = adapter.getAllEdges()
                allEdges.size shouldBe 3
            }

            "vertex property operations should work correctly" {
                val vertex = adapter.addVertex("person")

                // Set properties
                adapter.setVertexProperty(vertex, "name", "Alice")
                adapter.setVertexProperty(vertex, "age", 30)
                adapter.setVertexProperty(vertex, "active", true)

                // Get properties
                adapter.getVertexProperty(vertex, "name") shouldBe "Alice"
                adapter.getVertexProperty(vertex, "age") shouldBe 30
                adapter.getVertexProperty(vertex, "active") shouldBe true

                // Check if property exists
                adapter.hasVertexProperty(vertex, "name") shouldBe true
                adapter.hasVertexProperty(vertex, "email") shouldBe false

                // Remove property
                adapter.removeVertexProperty(vertex, "age")
                adapter.hasVertexProperty(vertex, "age") shouldBe false
                adapter.hasVertexProperty(vertex, "name") shouldBe true
            }

            "edge property operations should work correctly" {
                val alice = adapter.addVertex("person")
                val bob = adapter.addVertex("person")
                val edge = adapter.addEdge(alice, "knows", bob)

                // Set properties
                adapter.setEdgeProperty(edge, "since", 2020)
                adapter.setEdgeProperty(edge, "strength", 0.8)
                adapter.setEdgeProperty(edge, "confirmed", true)

                // Get properties
                adapter.getEdgeProperty(edge, "since") shouldBe 2020
                adapter.getEdgeProperty(edge, "strength") shouldBe 0.8
                adapter.getEdgeProperty(edge, "confirmed") shouldBe true

                // Check if property exists
                adapter.hasEdgeProperty(edge, "since") shouldBe true
                adapter.hasEdgeProperty(edge, "weight") shouldBe false

                // Remove property
                adapter.removeEdgeProperty(edge, "strength")
                adapter.hasEdgeProperty(edge, "strength") shouldBe false
                adapter.hasEdgeProperty(edge, "since") shouldBe true
            }

            "remove operations should delete elements" {
                val alice = adapter.addVertex("person")
                val bob = adapter.addVertex("person")
                val edge = adapter.addEdge(alice, "knows", bob)

                // Verify elements exist
                adapter.getAllVertices().size shouldBe 2
                adapter.getAllEdges().size shouldBe 1

                // Remove edge
                adapter.removeEdge(edge)
                adapter.getAllEdges().size shouldBe 0
                adapter.getAllVertices().size shouldBe 2

                // Remove vertex
                adapter.removeVertex(alice)
                adapter.getAllVertices().size shouldBe 1

                adapter.removeVertex(bob)
                adapter.getAllVertices().size shouldBe 0
            }

            "graph statistics should be accurate" {
                // Initially empty
                adapter.getVertexCount() shouldBe 0
                adapter.getEdgeCount() shouldBe 0

                // Add vertices
                val alice = adapter.addVertex("person")
                val bob = adapter.addVertex("person")
                val company = adapter.addVertex("company")

                adapter.getVertexCount() shouldBe 3

                // Add edges
                adapter.addEdge(alice, "knows", bob)
                adapter.addEdge(alice, "works_for", company)

                adapter.getEdgeCount() shouldBe 2

                val stats = adapter.getStatistics()
                stats shouldNotBe null
                stats.vertexCount shouldBe 3
                stats.edgeCount shouldBe 2
            }

            "clear should remove all elements" {
                val alice = adapter.addVertex("person")
                val bob = adapter.addVertex("person")
                adapter.addEdge(alice, "knows", bob)

                adapter.getVertexCount() shouldBe 2
                adapter.getEdgeCount() shouldBe 1

                adapter.clear()

                adapter.getVertexCount() shouldBe 0
                adapter.getEdgeCount() shouldBe 0
                adapter.getAllVertices().size shouldBe 0
                adapter.getAllEdges().size shouldBe 0
            }

            "JSON export and import should preserve graph structure" {
                // Create test graph
                val alice = adapter.addVertex("person")
                adapter.setVertexProperty(alice, "name", "Alice")
                adapter.setVertexProperty(alice, "age", 30)

                val bob = adapter.addVertex("person")
                adapter.setVertexProperty(bob, "name", "Bob")
                adapter.setVertexProperty(bob, "age", 25)

                val edge = adapter.addEdge(alice, "knows", bob)
                adapter.setEdgeProperty(edge, "since", 2020)

                // Export to JSON
                val json = adapter.toJSON()
                json shouldNotBe null
                (json.length > 0) shouldBe true

                // Clear and import
                adapter.clear()
                adapter.getVertexCount() shouldBe 0

                adapter.fromJSON(json)

                // Verify structure restored
                adapter.getVertexCount() shouldBe 2
                adapter.getEdgeCount() shouldBe 1

                val people = adapter.findVertices("person")
                people.size shouldBe 2

                val knowsEdges = adapter.findEdges("knows")
                knowsEdges.size shouldBe 1
                adapter.getEdgeProperty(knowsEdges[0], "since") shouldBe 2020
            }

            "JavaScript interop should work with native objects" {
                val vertex = adapter.addVertex("test")

                // Test with JavaScript object
                val jsObject = js("{}")
                jsObject.name = "Test"
                jsObject.count = 42
                jsObject.active = true

                adapter.setVertexProperty(vertex, "data", jsObject)
                val retrieved = adapter.getVertexProperty(vertex, "data")

                retrieved shouldNotBe null
                // Basic verification - exact object comparison may vary in JS
                retrieved.toString().contains("Test") shouldBe true
            }

            "error handling should be robust" {
                val vertex = adapter.addVertex("test")
                // Test error handling
                val otherGraph = TinkerGraph.open()
                val otherVertex = otherGraph.addVertex()
                try {
                    // This should fail - vertex from different graph
                    adapter.removeVertex(otherVertex as TinkerVertex)
                } catch (e: Exception) {
                    // Expected - should handle gracefully
                    e shouldNotBe null
                }

                // Adapter should still be functional
                adapter.getVertexCount() shouldBe 1
                adapter.setVertexProperty(vertex, "recovery", "test")
                adapter.getVertexProperty(vertex, "recovery") shouldBe "test"

                otherGraph.close()
            }

            "adapter performance should be reasonable" {
                val duration = measureTime {
                    // Create moderate-sized graph
                    val vertices = mutableListOf<Any>()
                    repeat(100) { i ->
                        val vertex = adapter.addVertex("node")
                        adapter.setVertexProperty(vertex, "index", i)
                        adapter.setVertexProperty(vertex, "data", "node_$i")
                        vertices.add(vertex)
                    }

                    // Add edges
                    repeat(50) { i ->
                        adapter.addEdge(vertices[i] as TinkerVertex, "connects", vertices[(i + 1) % vertices.size] as TinkerVertex)
                    }
                }

                // Verify structure
                adapter.getVertexCount() shouldBe 100
                adapter.getEdgeCount() shouldBe 50

                // Performance check - should complete reasonably quickly
                (duration < 5.seconds) shouldBe true
            }
        })
