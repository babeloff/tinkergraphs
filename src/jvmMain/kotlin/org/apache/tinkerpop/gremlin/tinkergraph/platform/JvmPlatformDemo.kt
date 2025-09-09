package org.apache.tinkerpop.gremlin.tinkergraph.platform

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import kotlinx.coroutines.*
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Interactive demo showcasing TinkerGraph JVM platform capabilities.
 *
 * Run this to see JVM-specific features in action with real-time output.
 * For automated testing, see JVM tests in the test directory.
 *
 * Usage: Run `pixi run gradle runJvmDemo` or execute the JVM application directly.
 */
object JvmPlatformDemo {

    fun runDemo() {
        println("☕ TinkerGraph JVM Platform Demo")
        println("===============================")

        detectJvmEnvironment()
        demoBasicJvmFeatures()
        demoConcurrentOperations()
        demoPersistenceCapabilities()
        demoMemoryMappedStorage()
        demoJavaInterop()
        demoPerformanceShowcase()

        println("\n✅ JVM demo completed! Check JVM tests for automated testing.")
    }

    private fun detectJvmEnvironment() {
        println("\n🔍 JVM Environment Detection")
        println("-".repeat(33))

        val runtime = Runtime.getRuntime()
        val javaVersion = System.getProperty("java.version")
        val jvmName = System.getProperty("java.vm.name")
        val jvmVendor = System.getProperty("java.vm.vendor")
        val osName = System.getProperty("os.name")
        val osArch = System.getProperty("os.arch")

        println("☕ Java Version: $javaVersion")
        println("🖥️  JVM: $jvmName ($jvmVendor)")
        println("💻 OS: $osName ($osArch)")
        println("🧠 Available Processors: ${runtime.availableProcessors()}")
        println("📊 Max Memory: ${runtime.maxMemory() / 1024 / 1024}MB")
        println("💾 Free Memory: ${runtime.freeMemory() / 1024 / 1024}MB")

        // Check for enterprise features
        val hasJMX = try {
            ManagementFactory.getPlatformMBeanServer() != null
        } catch (e: Exception) { false }

        if (hasJMX) {
            println("📈 JMX monitoring available")
        }
    }

    private fun demoBasicJvmFeatures() {
        println("\n📊 Basic JVM Features Demo")
        println("-".repeat(31))

        // Create graph with JVM optimizations
        val graph = TinkerGraph.open()

        // Java Collections integration
        val javaList = java.util.ArrayList<String>()
        javaList.add("vertex1")
        javaList.add("vertex2")
        javaList.add("vertex3")

        println("✓ Created Java ArrayList with ${javaList.size} elements")

        // Create vertices using Java collections
        javaList.forEach { label ->
            val vertex = graph.addVertex("javaNode")
            vertex.property("label", label)
            vertex.property("timestamp", System.currentTimeMillis())
        }

        println("✓ Created ${graph.vertices().asSequence().count()} vertices using Java Collections")

        // Demonstrate Java 8+ streams integration
        val vertexLabels = graph.vertices().asSequence()
            .mapNotNull { 
                try { it.value<String>("label") } catch (e: Exception) { null }
            }
            .sorted()
            .toList()

        println("✓ Processed vertices using streams: $vertexLabels")
    }

    private fun demoConcurrentOperations() = runBlocking {
        println("\n⚡ Concurrent Operations Demo")
        println("-".repeat(34))

        val graph = TinkerGraph.open()

        println("🔄 Starting concurrent operations...")

        // Create thread pool for demonstration
        val executor = Executors.newFixedThreadPool(4)

        try {
            // Measure concurrent vertex creation (simplified)
            val concurrentTime = measureTimeMillis {
                val futures = (1..50).map { i ->
                    executor.submit {
                        synchronized(graph) {
                            val vertex = graph.addVertex("concurrent")
                            vertex.property("id", i)
                            vertex.property("thread", Thread.currentThread().name)
                        }
                    }
                }

                // Wait for all operations to complete
                futures.forEach { it.get() }
            }

            println("✓ Created ${graph.vertices().asSequence().count()} vertices concurrently in ${concurrentTime}ms")

            // Demonstrate basic edge creation
            val vertices = graph.vertices().asSequence().take(10).toList()
            val edgeTime = measureTimeMillis {
                vertices.forEachIndexed { i, vertex ->
                    if (i < vertices.size - 1) {
                        synchronized(graph) {
                            vertex.addEdge("connects", vertices[i + 1])
                        }
                    }
                }
            }

            println("✓ Created ${graph.edges().asSequence().count()} edges in ${edgeTime}ms")
            println("🛡️  Synchronized operations completed successfully")

        } finally {
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun demoPersistenceCapabilities() {
        println("\n💾 Persistence Capabilities Demo")
        println("-".repeat(37))

        val graph = TinkerGraph.open()
        val persistence = JvmPersistenceLayer(graph)

        // Create sample data
        val alice = graph.addVertex("person")
        alice.property("name", "Alice")
        alice.property("age", 30)

        val bob = graph.addVertex("person")
        bob.property("name", "Bob")
        bob.property("age", 25)

        val edge = alice.addEdge("knows", bob)
        edge.property("since", "2020")

        println("✓ Created sample graph with ${graph.vertices().asSequence().count()} vertices")

        try {
            // Demonstrate JSON persistence
            val tempFile = File.createTempFile("tinkergraph-demo", ".json")
            val saveTime = measureTimeMillis {
                persistence.saveToFile(tempFile.absolutePath, "json")
            }
            println("💾 Saved graph to JSON in ${saveTime}ms (${tempFile.length()} bytes)")

            // Clear graph and reload
            graph.vertices().forEach { it.remove() }
            println("🗑️  Cleared graph")

            val loadTime = measureTimeMillis {
                persistence.loadFromFile(tempFile.absolutePath, "json")
            }
            println("📖 Reloaded graph from JSON in ${loadTime}ms")
            println("✓ Graph restored: ${graph.vertices().asSequence().count()} vertices, ${graph.edges().asSequence().count()} edges")

            // Cleanup
            tempFile.delete()

        } catch (e: Exception) {
            println("⚠️  Persistence demo skipped: ${e.message}")
            println("💡 Full persistence features may require additional setup")
        }
    }

    private fun demoMemoryMappedStorage() {
        println("\n🗄️  Memory-Mapped Storage Demo")
        println("-".repeat(35))

        try {
            val tempFile = File.createTempFile("tinkergraph-mmap", ".dat")
            val storage = MemoryMappedStorage(tempFile.absolutePath)

            println("📂 Created memory-mapped file: ${tempFile.absolutePath}")

            // Write some test data
            val writeTime = measureTimeMillis {
                repeat(1000) { i ->
                    val data = "vertex_data_$i".toByteArray()
                    storage.write(i * 100L, data)
                }
            }
            println("✏️  Wrote 1000 records in ${writeTime}ms")

            // Read back data
            val readTime = measureTimeMillis {
                repeat(100) { i ->
                    val data = storage.read(i * 100L, 20)
                    // Process data (just counting for demo)
                }
            }
            println("📖 Read 100 records in ${readTime}ms")

            // Get storage statistics
            val stats = storage.getStorageStatistics()
            println("📊 Storage stats:")
            println("   Total operations: ${stats.totalOperations}")
            println("   Cache hit ratio: ${String.format("%.2f", stats.cacheHitRatio * 100)}%")

            storage.close()
            tempFile.delete()
            println("✓ Memory-mapped storage demo completed")

        } catch (e: Exception) {
            println("⚠️  Memory-mapped storage demo skipped: ${e.message}")
            println("💡 Feature may require specific JVM configuration")
        }
    }

    private fun demoJavaInterop() {
        println("\n🔗 Java Interoperability Demo")
        println("-".repeat(34))

        val graph = TinkerGraph.open()

        // Demonstrate Java Collections integration
        val javaMap = java.util.concurrent.ConcurrentHashMap<String, Any>()
        javaMap["type"] = "demo"
        javaMap["version"] = 1.0
        javaMap["features"] = java.util.Arrays.asList("graphs", "vertices", "edges")

        println("✓ Created Java ConcurrentHashMap with ${javaMap.size} entries")

        // Use Java collections with TinkerGraph
        val vertex = graph.addVertex("javaIntegration")
        javaMap.forEach { (key, value) ->
            vertex.property(key, value)
        }

        println("✓ Applied Java map properties to vertex")

        // Demonstrate Java 8+ Optional handling
        val optionalProperty = try {
            java.util.Optional.ofNullable(vertex.value<String>("type"))
        } catch (e: Exception) {
            java.util.Optional.empty<String>()
        }

        if (optionalProperty.isPresent) {
            println("✓ Java Optional handling: ${optionalProperty.get()}")
        }

        // Java Stream API integration
        val propertyCount = vertex.properties<Any>().asSequence()
            .map { it.key() }
            .distinct()
            .count()

        println("✓ Processed properties using streams: $propertyCount unique keys")

        // Demonstrate exception handling with Java types
        try {
            val javaList = vertex.value<java.util.List<String>>("features")
            println("✓ Java List retrieved: ${javaList.size} items")
        } catch (e: ClassCastException) {
            println("⚠️  Type casting handled gracefully")
        }
    }

    private fun demoPerformanceShowcase() {
        println("\n🚀 JVM Performance Showcase")
        println("-".repeat(32))

        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()

        // Large graph creation performance
        val graph = TinkerGraph.open()

        val creationTime = measureTimeMillis {
            // Create vertices in batches for better performance
            repeat(10) { batch ->
                val vertices = (1..100).map { i ->
                    val vertex = graph.addVertex("perfTest")
                    vertex.property("id", batch * 100 + i)
                    vertex.property("batch", batch)
                    vertex.property("data", "performance_test_data_${batch}_${i}")
                    vertex
                }

                // Connect within batch
                vertices.zipWithNext { v1, v2 ->
                    v1.addEdge("connects", v2)
                }
            }
        }

        val vertexCount = graph.vertices().asSequence().count()
        val edgeCount = graph.edges().asSequence().count()

        println("📈 Created $vertexCount vertices and $edgeCount edges in ${creationTime}ms")

        // Query performance
        val queryTime = measureTimeMillis {
            // Complex query simulation
            val results = graph.vertices().asSequence()
                .filter { it.value<Int>("batch") % 2 == 0 }
                .map { vertex ->
                    vertex.value<String>("data") to vertex.edges(org.apache.tinkerpop.gremlin.structure.Direction.OUT).asSequence().count()
                }
                .toList()

            println("🔍 Query processed ${results.size} vertices")
        }

        println("⚡ Complex query completed in ${queryTime}ms")

        // Memory analysis
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryUsed = (finalMemory - initialMemory) / 1024 / 1024

        println("🧠 Memory usage: ${memoryUsed}MB for graph storage")

        // Garbage collection hint
        val gcTime = measureTimeMillis {
            runtime.gc()
        }
        println("🗑️  Garbage collection completed in ${gcTime}ms")

        // JVM optimization info
        val vmName = System.getProperty("java.vm.name")
        println("⚙️  Running on $vmName with JIT optimizations")
    }
}

/**
 * Entry point for JVM platform demo.
 *
 * Run with: `pixi run gradle runJvmDemo`
 * Or compile and execute the JVM application directly.
 */
fun main() {
    try {
        JvmPlatformDemo.runDemo()
    } catch (e: Exception) {
        println("❌ JVM demo failed: ${e.message}")
        e.printStackTrace()
        println("💡 Some features may require specific JVM configuration or dependencies")
    }
}
