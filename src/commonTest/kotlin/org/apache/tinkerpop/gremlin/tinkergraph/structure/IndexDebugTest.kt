package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.util.LoggingConfig

/** Debug test to isolate the index issue causing test failures. */
class IndexDebugTest :
        StringSpec({

            lateinit var graph: TinkerGraph

            beforeTest { graph = TinkerGraph.open() }

            afterTest { graph.close() }

            "basic property and index should work correctly" {
                println("=== Testing Basic Property and Index ===")

                // Step 1: Create vertex and add property
                val alice = graph.addVertex()
                alice.property("department", "Engineering")
                alice.property("name", "Alice")

                println("Step 1 - Created vertex with properties:")
                println("  Alice department: ${alice.value<String>("department")}")
                println("  Alice name: ${alice.value<String>("name")}")
                println("  Alice keys: ${alice.keys()}")

                // Step 2: Verify property is accessible directly
                alice.value<String>("department") shouldBe "Engineering"
                alice.value<String>("name") shouldBe "Alice"

                // Step 3: Test query WITHOUT index (should use full scan)
                println("\nStep 2 - Query without index:")
                val queryEngine = graph.propertyQueryEngine()
                val engineersBeforeIndex =
                        queryEngine
                                .queryVertices(
                                        PropertyQueryEngine.exact("department", "Engineering")
                                )
                                .asSequence()
                                .toList()
                println("  Engineers found before index: ${engineersBeforeIndex.size}")

                // This should work even without indices
                engineersBeforeIndex shouldHaveSize 1

                // Step 4: Create index AFTER adding data
                println("\nStep 3 - Creating index:")
                graph.createIndex("department", Vertex::class)

                // Step 5: Check if index was populated
                val indexedKeys = graph.vertexIndex.getIndexedKeys()
                println("  Indexed keys: $indexedKeys")
                indexedKeys.contains("department").shouldBeTrue()

                // Step 6: Test query WITH index
                println("\nStep 4 - Query with index:")
                val engineersAfterIndex =
                        queryEngine
                                .queryVertices(
                                        PropertyQueryEngine.exact("department", "Engineering")
                                )
                                .asSequence()
                                .toList()
                println("  Engineers found after index: ${engineersAfterIndex.size}")

                // This should also work with indices
                engineersAfterIndex shouldHaveSize 1

                // Step 7: Verify the vertex is the same
                val foundVertex = engineersAfterIndex.first()
                println("  Found vertex name: ${foundVertex.value<String>("name")}")
                foundVertex.value<String>("name") shouldBe "Alice"
            }

            "multiple vertices and index should work correctly" {
                println("\n=== Testing Multiple Vertices and Index ===")

                // Create test data (exactly like AdvancedIndexingTest)
                val alice = graph.addVertex()
                alice.property("name", "Alice")
                alice.property("department", "Engineering")

                val bob = graph.addVertex()
                bob.property("name", "Bob")
                bob.property("department", "Engineering")

                val charlie = graph.addVertex()
                charlie.property("name", "Charlie")
                charlie.property("department", "Marketing")

                println("Created vertices:")
                listOf(alice, bob, charlie).forEach { vertex ->
                    val name = vertex.value<String>("name")
                    val dept = vertex.value<String>("department")
                    println("  $name: $dept")
                }

                // Test before indexing
                val queryEngine = graph.propertyQueryEngine()
                val engineersBeforeIndex =
                        queryEngine
                                .queryVertices(
                                        PropertyQueryEngine.exact("department", "Engineering")
                                )
                                .asSequence()
                                .toList()
                println("Engineers before index: ${engineersBeforeIndex.size}")

                // Create index
                graph.createIndex("department", Vertex::class)

                // Test after indexing
                val engineersAfterIndex =
                        queryEngine
                                .queryVertices(
                                        PropertyQueryEngine.exact("department", "Engineering")
                                )
                                .asSequence()
                                .toList()
                println("Engineers after index: ${engineersAfterIndex.size}")

                engineersAfterIndex.forEach { vertex ->
                    try {
                        println("  Found: ${vertex.value<String>("name")}")
                    } catch (e: Exception) {
                        LoggingConfig.getLogger<IndexDebugTest>().w(e) { "Error accessing vertex name" }
                    }
                }

                engineersAfterIndex shouldHaveSize 2
            }

            "index internals should work correctly" {
                println("\n=== Testing Index Internals ===")

                // Create vertex with property
                val vertex = graph.addVertex()
                vertex.property("testKey", "testValue")

                // Check vertex index before creating index
                val indexBefore = graph.vertexIndex.getIndexedKeys()
                println("Indexed keys before: $indexBefore")

                // Create index and check internals
                graph.createIndex("testKey", Vertex::class)

                val indexAfter = graph.vertexIndex.getIndexedKeys()
                println("Indexed keys after: $indexAfter")
                indexAfter.contains("testKey").shouldBeTrue()

                // Check if vertex can be found in index
                val verticesFromIndex = graph.vertexIndex.get("testKey", "testValue")
                println("Vertices from index: ${verticesFromIndex.size}")
                verticesFromIndex shouldHaveSize 1

                // Verify it's the same vertex
                val foundVertex = verticesFromIndex.first()
                foundVertex.id() shouldBe vertex.id()
            }

            "index rebuild process should work correctly" {
                println("\n=== Testing Index Rebuild Process ===")

                // Create vertices with properties
                val vertices =
                        (1..3).map { i ->
                            val vertex = graph.addVertex()
                            vertex.property("number", i)
                            vertex.property("category", if (i % 2 == 0) "even" else "odd")
                            vertex
                        }

                println("Created ${vertices.size} vertices")

                // Manually test the rebuild process
                val allVertices = graph.vertices().asSequence().toList()
                println("Total vertices in graph: ${allVertices.size}")

                // Check each vertex's properties
                allVertices.forEach { vertex ->
                    val number = vertex.value<Int>("number")
                    val category = vertex.value<String>("category")
                    println("  Vertex ${vertex.id()}: number=$number, category=$category")
                }

                // Create index and see what happens
                graph.createIndex("category", Vertex::class)

                // Test query
                val oddVertices =
                        graph.propertyQueryEngine()
                                .queryVertices(PropertyQueryEngine.exact("category", "odd"))
                                .asSequence()
                                .toList()

                println("Odd vertices found: ${oddVertices.size}")
                oddVertices shouldHaveSize 2
            }
        }) {

    companion object {
        private val logger = LoggingConfig.getLogger<IndexDebugTest>()
    }
}
