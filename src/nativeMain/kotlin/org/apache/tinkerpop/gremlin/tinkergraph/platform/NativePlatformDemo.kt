package org.apache.tinkerpop.gremlin.tinkergraph.platform

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

/**
 * Interactive demo showcasing TinkerGraph native platform capabilities.
 *
 * Run this to see native features in action with real-time output.
 * For automated testing, see NativePlatformTest in the test directory.
 *
 * Usage: Run `pixi run gradle runNativeDemo` or execute the main function.
 */
object NativePlatformDemo {

    fun runDemo() {
        println("🚀 TinkerGraph Native Platform Demo")
        println("====================================")

        demoBasicGraphOperations()
        demoPlatformFeatures()
        demoPerformanceShowcase()

        println("\n✅ Demo completed! Check NativePlatformTest for automated testing.")
    }

    private fun demoBasicGraphOperations() {
        println("\n📊 Basic Graph Operations Demo")
        println("-".repeat(35))

        val graph = TinkerGraph.open()

        // Create vertices
        val alice = graph.addVertex("person")
        alice.property("name", "Alice")
        alice.property("age", 30)

        val bob = graph.addVertex("person")
        bob.property("name", "Bob")
        bob.property("age", 25)

        // Create edge
        val knows = alice.addEdge("knows", bob)
        knows.property("since", "2020")

        println("✓ Created graph with ${graph.vertices().asSequence().count()} vertices and ${graph.edges().asSequence().count()} edges")
        println("✓ Alice (${alice.value<Int>("age")}) knows Bob (${bob.value<Int>("age")}) since ${knows.value<String>("since")}")
    }

    private fun demoPlatformFeatures() {
        println("\n⚡ Native Platform Features Demo")
        println("-".repeat(38))

        // Platform info
        val perfStats = Platform.getNativePerformanceStatistics()
        println("📍 Platform: ${perfStats.platformInfo.osFamily} on ${perfStats.platformInfo.cpuArchitecture}")
        println("🖥️  Cores: ${perfStats.platformInfo.availableCores}, Memory: ${perfStats.platformInfo.availableMemory / 1_000_000}MB")

        // Timing demonstration
        val startTime = Platform.currentTimeMillis()
        Platform.sleep(50) // Short sleep for demo
        val elapsed = Platform.timeDifference(startTime, Platform.currentTimeMillis())
        println("⏱️  Timing precision: ${elapsed}ms (expected ~50ms)")
    }

    private fun demoPerformanceShowcase() {
        println("\n🚀 Performance Showcase")
        println("-".repeat(25))

        val startTime = Platform.currentTimeMillis()

        // Create larger graph for performance demo
        val graph = TinkerGraph.open()
        repeat(100) { i ->
            val vertex = graph.addVertex("node")
            vertex.property("id", i)
            vertex.property("value", "data_$i")
        }

        val graphCreationTime = Platform.timeDifference(startTime, Platform.currentTimeMillis())

        // Query performance
        val queryStart = Platform.currentTimeMillis()
        val count = graph.vertices().asSequence().count()
        val queryTime = Platform.timeDifference(queryStart, Platform.currentTimeMillis())

        println("📈 Created $count vertices in ${graphCreationTime}ms")
        println("🔍 Queried all vertices in ${queryTime}ms")
        println("💡 Native optimizations active and performing well!")
    }
}

/**
 * Entry point for native platform demo.
 *
 * Run with: `pixi run gradle runNativeDemo`
 * Or compile and execute the native binary directly.
 */
fun main() {
    try {
        NativePlatformDemo.runDemo()
    } catch (e: Exception) {
        println("❌ Demo failed: ${e.message}")
        println("💡 This is expected if native platform features are not fully implemented")
    }
}
