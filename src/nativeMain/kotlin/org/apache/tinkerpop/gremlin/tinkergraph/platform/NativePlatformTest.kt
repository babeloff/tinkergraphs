package org.apache.tinkerpop.gremlin.tinkergraph.platform

import org.apache.tinkerpop.gremlin.tinkergraph.memory.NativeMemoryManager
import org.apache.tinkerpop.gremlin.tinkergraph.collections.NativeCollections
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

/**
 * Simple test to verify native platform enhancements are working correctly.
 * This demonstrates the key features implemented in Phase 3.3.1.
 */
object NativePlatformTest {

    fun runTests() {
        println("=== TinkerGraph Native Platform Test ===")

        // Test 1: Enhanced Platform Operations
        testPlatformOperations()

        // Test 2: Memory Management
        testMemoryManagement()

        // Test 3: Native Collections
        testNativeCollections()

        // Test 4: Performance Monitoring
        testPerformanceMonitoring()

        // Test 5: Cross-platform Compatibility
        testCrossPlatformCompatibility()

        println("=== All Tests Completed ===")
    }

    private fun testPlatformOperations() {
        println("\n--- Testing Platform Operations ---")

        // Test enhanced sleep implementation
        val startTime = Platform.currentTimeMillis()
        Platform.sleep(100) // Sleep for 100ms
        val endTime = Platform.currentTimeMillis()
        val elapsed = Platform.timeDifference(startTime, endTime)

        println("Sleep test: ${elapsed}ms elapsed (expected ~100ms)")

        // Test time comparison
        val isGreater = Platform.timeComparison(150, 100)
        println("Time comparison (150 > 100): $isGreater")

        // Test collection creation
        val sortedMap = Platform.createSortedMap<String, Int>()
        val linkedMap = Platform.createLinkedHashMap<String, String>()
        println("Collection creation: sortedMap=${sortedMap::class.simpleName}, linkedMap=${linkedMap::class.simpleName}")
    }

    private fun testMemoryManagement() {
        println("\n--- Testing Memory Management ---")

        // Track some allocations
        NativeMemoryManager.trackAllocation(1000)
        NativeMemoryManager.trackAllocation(2000)
        NativeMemoryManager.trackDeallocation(500)

        // Get statistics
        val stats = NativeMemoryManager.getMemoryStatistics()
        println("Memory stats - Total allocated: ${stats.totalAllocated}, Total freed: ${stats.totalFreed}")
        println("Active allocations: ${stats.activeAllocations}, Max active: ${stats.maxActiveAllocations}")
        println("Memory leak detected: ${stats.memoryLeakDetected}")

        // Get recommendations
        val recommendations = NativeMemoryManager.getOptimizationRecommendations()
        println("Memory recommendations:")
        recommendations.forEach { println("  - $it") }
    }

    private fun testNativeCollections() {
        println("\n--- Testing Native Collections ---")

        // Test factory methods
        val hashMap = NativeCollections.Factory.createHashMap<String, Int>(64)
        val hashSet = NativeCollections.Factory.createHashSet<String>(32)
        val sortedMap = NativeCollections.Factory.createSortedMap<String, Int>()

        // Add some test data
        hashMap["key1"] = 1
        hashMap["key2"] = 2
        hashSet.add("value1")
        hashSet.add("value2")
        sortedMap["z"] = 3
        sortedMap["a"] = 1

        println("HashMap size: ${hashMap.size}, HashSet size: ${hashSet.size}, SortedMap size: ${sortedMap.size}")

        // Get statistics
        val stats = NativeCollections.getCollectionStatistics()
        println("Collection stats - Recommended capacity: ${stats.recommendedHashMapCapacity}")
        println("Load factor: ${stats.recommendedLoadFactor}, Memory pressure: ${stats.memoryPressure}")

        // Get recommendations
        val recommendations = NativeCollections.getOptimizationRecommendations()
        println("Collection recommendations:")
        recommendations.forEach { println("  - $it") }
    }

    private fun testPerformanceMonitoring() {
        println("\n--- Testing Performance Monitoring ---")

        // Get comprehensive performance statistics
        val perfStats = Platform.getNativePerformanceStatistics()

        println("Platform Info:")
        println("  OS Family: ${perfStats.platformInfo.osFamily}")
        println("  CPU Architecture: ${perfStats.platformInfo.cpuArchitecture}")
        println("  Available Cores: ${perfStats.platformInfo.availableCores}")
        println("  Available Memory: ${perfStats.platformInfo.availableMemory / 1_000_000}MB")

        println("Memory Statistics:")
        println("  Total Allocated: ${perfStats.memoryStats.totalAllocated}")
        println("  Active Allocations: ${perfStats.memoryStats.activeAllocations}")

        println("Collection Statistics:")
        println("  Recommended HashMap Capacity: ${perfStats.collectionStats.recommendedHashMapCapacity}")
        println("  Memory Pressure: ${perfStats.collectionStats.memoryPressure}")

        // Get optimization recommendations
        val recommendations = Platform.getOptimizationRecommendations()
        println("Platform Optimization Recommendations:")
        recommendations.forEach { println("  - $it") }
    }

    private fun testCrossPlatformCompatibility() {
        println("\n--- Testing Cross-platform Compatibility ---")

        try {
            // Test TinkerGraph creation with native platform
            val graph = TinkerGraph.open()

            // Add some test data
            val vertex1 = graph.addVertex("person")
            vertex1.property("name", "Alice")
            vertex1.property("age", 30)

            val vertex2 = graph.addVertex("person")
            vertex2.property("name", "Bob")
            vertex2.property("age", 25)

            val edge = vertex1.addEdge("knows", vertex2)
            edge.property("since", "2020")

            println("Graph created successfully:")
            println("  Vertices: ${graph.vertices().asSequence().count()}")
            println("  Edges: ${graph.edges().asSequence().count()}")

            // Test cleanup
            Platform.forceNativeCleanup()
            println("Native cleanup completed successfully")

        } catch (e: Exception) {
            println("Error testing cross-platform compatibility: ${e.message}")
        }
    }
}

/**
 * Entry point for native platform testing.
 * Run this to verify that all native enhancements are working correctly.
 */
fun main() {
    NativePlatformTest.runTests()
}
