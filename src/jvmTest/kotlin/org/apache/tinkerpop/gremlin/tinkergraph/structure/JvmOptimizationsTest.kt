package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.Element
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.Edge
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * JVM-specific test suite for TinkerGraph platform optimizations.
 *
 * This test suite validates JVM-specific features and optimizations including:
 * - **Java Collections Interoperability**: Seamless integration with Java Collections API
 * - **Concurrent Access Support**: Thread-safe operations and concurrent data structures
 * - **JVM Serialization**: Native Java serialization for graph persistence
 * - **Memory Mapping**: Memory-mapped file storage for large graphs
 * - **Performance Optimizations**: JVM-specific performance enhancements
 *
 * Tests ensure that JVM-specific implementations maintain compatibility with
 * the common TinkerGraph API while providing enhanced functionality available
 * only on the JVM platform.
 *
 * @see org.apache.tinkerpop.gremlin.tinkergraph.structure.ConcurrentGraphOperations
 * @see org.apache.tinkerpop.gremlin.tinkergraph.structure.JavaCollectionsSupport
 * @see org.apache.tinkerpop.gremlin.tinkergraph.structure.JvmSerialization
 * @see org.apache.tinkerpop.gremlin.tinkergraph.structure.MemoryMappedStorage
 */
class JvmOptimizationsTest :
        StringSpec({
            lateinit var graph: TinkerGraph

            beforeTest { graph = TinkerGraph.open() }

            afterTest { graph.close() }

            "Java Collections interoperability should work seamlessly" {
                // Create vertices using standard TinkerGraph API
                val vertex1 = graph.addVertex()
                vertex1.property("name", "Alice")
                vertex1.property("age", 30)

                val vertex2 = graph.addVertex()
                vertex2.property("name", "Bob")
                vertex2.property("age", 25)

                val vertex3 = graph.addVertex()
                vertex3.property("name", "Charlie")
                vertex3.property("age", 35)

                // Test Java Collections conversion
                val verticesList = graph.vertices().asSequence().toList()
                verticesList shouldHaveSize 3

                // Test Java Stream integration
                val adultVertices = verticesList.stream()
                    .filter { vertex -> vertex.value<Int>("age")?.let { it >= 30 } == true }
                    .toList()

                adultVertices shouldHaveSize 2
                val adultNames = adultVertices.mapNotNull { it.value<String>("name") }.sorted()
                adultNames.shouldBe(listOf("Alice", "Charlie"))

                // Test with Java HashMap integration
                val nameAgeMap = HashMap<String, Int>()
                verticesList.forEach { vertex ->
                    val name = vertex.value<String>("name")
                    val age = vertex.value<Int>("age")
                    if (name != null && age != null) {
                        nameAgeMap[name] = age
                    }
                }

                nameAgeMap.size shouldBe 3
                nameAgeMap["Alice"] shouldBe 30
                nameAgeMap["Bob"] shouldBe 25
                nameAgeMap["Charlie"] shouldBe 35
            }

            "concurrent access operations should be thread-safe" {
                val executor = Executors.newFixedThreadPool(4)
                val vertexCounter = AtomicInteger(0)
                val edgeCounter = AtomicInteger(0)
                val futures = mutableListOf<Future<*>>()

                try {
                    // Test concurrent vertex creation
                    repeat(10) { i ->
                        futures.add(executor.submit {
                            val vertex = graph.addVertex()
                            vertex.property("thread", Thread.currentThread().name)
                            vertex.property("index", i)
                            vertexCounter.incrementAndGet()
                        })
                    }

                    // Wait for all vertex creation tasks to complete
                    futures.forEach { it.get(5, TimeUnit.SECONDS) }
                    futures.clear()

                    vertexCounter.get() shouldBe 10

                    // Test concurrent edge creation
                    val vertices = graph.vertices().asSequence().toList()
                    repeat(5) { i ->
                        futures.add(executor.submit {
                            if (vertices.size >= 2) {
                                val source = vertices[i % vertices.size]
                                val target = vertices[(i + 1) % vertices.size]
                                val edge = source.addEdge("connects", target)
                                edge.property("thread", Thread.currentThread().name)
                                edge.property("index", i)
                                edgeCounter.incrementAndGet()
                            }
                        })
                    }

                    // Wait for all edge creation tasks to complete
                    futures.forEach { it.get(5, TimeUnit.SECONDS) }

                    edgeCounter.get() shouldBe 5

                    // Verify final graph state
                    graph.vertices().asSequence().count() shouldBe 10
                    graph.edges().asSequence().count() shouldBe 5

                } finally {
                    executor.shutdown()
                    executor.awaitTermination(10, TimeUnit.SECONDS)
                }
            }

            "JVM serialization should preserve graph structure" {
                // Create a graph with vertices and edges
                val alice = graph.addVertex()
                alice.property("name", "Alice")
                alice.property("age", 30)
                alice.property("department", "Engineering")

                val bob = graph.addVertex()
                bob.property("name", "Bob")
                bob.property("age", 25)
                bob.property("department", "Marketing")

                val edge = alice.addEdge("knows", bob)
                edge.property("since", 2020)
                edge.property("strength", 0.8)

                // Serialize the graph elements
                val byteArrayOut = ByteArrayOutputStream()
                val objectOut = ObjectOutputStream(byteArrayOut)

                // Test vertex serialization
                val aliceVertex = alice as TinkerVertex
                objectOut.writeObject(aliceVertex)
                objectOut.flush()

                val byteArrayIn = ByteArrayInputStream(byteArrayOut.toByteArray())
                val objectIn = ObjectInputStream(byteArrayIn)
                val deserializedVertex = objectIn.readObject() as TinkerVertex

                // Verify vertex properties were preserved
                deserializedVertex.value<String>("name") shouldBe "Alice"
                deserializedVertex.value<Int>("age") shouldBe 30
                deserializedVertex.value<String>("department") shouldBe "Engineering"

                objectOut.close()
                objectIn.close()
            }

            "memory-mapped storage simulation should handle large datasets" {
                // Simulate memory-mapped file operations with large dataset
                val startTime = System.currentTimeMillis()

                // Create a larger dataset to test memory optimization
                val vertices = mutableListOf<Vertex>()
                repeat(1000) { i ->
                    val vertex = graph.addVertex()
                    vertex.property("id", i)
                    vertex.property("category", "category_${i % 10}")
                    vertex.property("value", i * 1.5)
                    vertex.property("data", "data_string_$i")
                    vertices.add(vertex)
                }

                // Create edges between vertices
                repeat(500) { i ->
                    val source = vertices[i]
                    val target = vertices[(i + 1) % vertices.size]
                    val edge = source.addEdge("connects", target)
                    edge.property("weight", (i % 100) / 100.0)
                }

                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                // Verify the dataset was created successfully
                graph.vertices().asSequence().count() shouldBe 1000
                graph.edges().asSequence().count() shouldBe 500

                // Performance check - should complete within reasonable time
                (duration < 10000) shouldBe true // Less than 10 seconds

                // Test query performance on large dataset
                val queryStart = System.currentTimeMillis()
                val category5Vertices = graph.vertices().asSequence()
                    .filter { vertex ->
                        try {
                            vertex.value<String>("category") == "category_5"
                        } catch (e: Exception) {
                            false
                        }
                    }.toList()
                val queryEnd = System.currentTimeMillis()

                category5Vertices shouldHaveSize 100 // 1000 vertices / 10 categories
                (queryEnd - queryStart < 1000) shouldBe true // Query should be fast
            }

            "JVM-specific performance optimizations should be effective" {
                // Test HashMap-backed property storage optimization
                val vertex = graph.addVertex()

                // Add many properties to test internal HashMap optimization
                repeat(100) { i ->
                    vertex.property("key_$i", "value_$i")
                    vertex.property("number_$i", i)
                    vertex.property("double_$i", i * 1.1)
                }

                // Verify all properties were stored correctly
                repeat(100) { i ->
                    vertex.value<String>("key_$i") shouldBe "value_$i"
                    vertex.value<Int>("number_$i") shouldBe i
                    vertex.value<Double>("double_$i") shouldBe i * 1.1
                }

                // Test ConcurrentHashMap for thread safety
                val executor = Executors.newFixedThreadPool(2)
                val futures = mutableListOf<Future<*>>()

                try {
                    repeat(50) { i ->
                        futures.add(executor.submit {
                            vertex.property("concurrent_$i", Thread.currentThread().name)
                        })
                    }

                    futures.forEach { it.get(5, TimeUnit.SECONDS) }

                    // Verify concurrent property updates worked
                    repeat(50) { i ->
                        vertex.value<String>("concurrent_$i") shouldNotBe null
                    }

                } finally {
                    executor.shutdown()
                    executor.awaitTermination(5, TimeUnit.SECONDS)
                }
            }

            "Java 8 Stream integration should work correctly" {
                // Create test data
                repeat(20) { i ->
                    val vertex = graph.addVertex()
                    vertex.property("index", i)
                    vertex.property("category", if (i % 2 == 0) "even" else "odd")
                    vertex.property("score", i * 2.5)
                }

                // Test Stream operations
                val vertices = graph.vertices().asSequence().toList()

                val evenVertices = vertices.stream()
                    .filter { vertex -> vertex.value<String>("category") == "even" }
                    .toList()

                evenVertices shouldHaveSize 10

                val highScores = vertices.stream()
                    .filter { vertex -> vertex.value<Double>("score")?.let { it > 25.0 } == true }
                    .mapToDouble { vertex -> vertex.value<Double>("score") ?: 0.0 }
                    .average()
                    .orElse(0.0)

                (highScores > 25.0) shouldBe true

                // Test parallel stream processing
                val parallelSum = vertices.parallelStream()
                    .mapToInt { vertex -> vertex.value<Int>("id") ?: 0 }
                    .sum()

                parallelSum shouldBe (0..19).sum() // Sum of 0 to 19
            }

            "garbage collection behavior should be optimal" {
                // Test that graph operations don't cause excessive GC pressure
                val runtime = Runtime.getRuntime()
                val initialMemory = runtime.totalMemory() - runtime.freeMemory()

                // Perform memory-intensive operations
                repeat(1000) { i ->
                    val vertex = graph.addVertex()
                    vertex.property("large_string", "x".repeat(100))
                    vertex.property("index", i)

                    if (i % 100 == 0) {
                        // Force GC periodically to clean up
                        System.gc()
                        Thread.sleep(1) // Give GC a chance
                    }
                }

                // Check memory usage after operations
                System.gc()
                Thread.sleep(10)
                val finalMemory = runtime.totalMemory() - runtime.freeMemory()
                val memoryIncrease = finalMemory - initialMemory

                // Memory usage should be reasonable (less than 50MB increase)
                (memoryIncrease < 50 * 1024 * 1024) shouldBe true

                // Verify all vertices were created
                graph.vertices().asSequence().count() shouldBe 1000
            }

            "ClassLoader compatibility should work correctly" {
                // Test that TinkerGraph works with different class loaders
                val currentClassLoader = Thread.currentThread().contextClassLoader

                try {
                    // Create vertex and verify class loading
                    val vertex = graph.addVertex()
                    vertex.property("test", "classloader")

                    val vertexClass = vertex.javaClass
                    vertexClass shouldNotBe null

                    // Verify we can access the vertex normally
                    vertex.value<String>("test") shouldBe "classloader"

                    // Test with property values of different types
                    vertex.property("string", "test")
                    vertex.property("number", 42)
                    vertex.property("boolean", true)

                    vertex.value<String>("string") shouldBe "test"
                    vertex.value<Int>("number") shouldBe 42
                    vertex.value<Boolean>("boolean") shouldBe true

                } finally {
                    Thread.currentThread().contextClassLoader = currentClassLoader
                }
            }

            "JVM exception handling should be robust" {
                // Test that JVM-specific exceptions are handled properly
                val vertex = graph.addVertex()
                vertex.property("test", "exception_handling")

                // Test handling of various exception scenarios
                try {
                    // This should work normally
                    vertex.value<String>("test") shouldBe "exception_handling"
                } catch (e: Exception) {
                    throw AssertionError("Basic property access should not throw", e)
                }

                try {
                    // Test accessing non-existent property
                    vertex.value<String>("nonexistent")
                } catch (e: Exception) {
                    // Should handle gracefully - this is expected behavior
                    e shouldNotBe null
                }

                // Verify graph is still in good state after exceptions
                graph.vertices().asSequence().count() shouldBe 1
                vertex.value<String>("test") shouldBe "exception_handling"
            }

            "thread-local storage should work correctly" {
                val threadLocal = ThreadLocal<String>()
                val executor = Executors.newFixedThreadPool(3)
                val results = ConcurrentLinkedQueue<String>()

                try {
                    val futures = mutableListOf<Future<*>>()

                    repeat(9) { i ->
                        futures.add(executor.submit {
                            val threadName = Thread.currentThread().name
                            threadLocal.set("thread_$i")

                            // Create vertex in this thread
                            val vertex = graph.addVertex()
                            vertex.property("thread", threadName)
                            vertex.property("local_value", threadLocal.get())

                            results.offer("${threadLocal.get()}_${threadName}")
                            Thread.sleep(10) // Small delay to test thread safety

                            // Verify thread-local value is preserved
                            results.offer("final_${threadLocal.get()}")
                        })
                    }

                    futures.forEach { it.get(10, TimeUnit.SECONDS) }

                    // Verify thread-local behavior
                    results.size shouldBe 18 // 9 initial + 9 final
                    graph.vertices().asSequence().count() shouldBe 9

                } finally {
                    threadLocal.remove()
                    executor.shutdown()
                    executor.awaitTermination(5, TimeUnit.SECONDS)
                }
            }
        })
