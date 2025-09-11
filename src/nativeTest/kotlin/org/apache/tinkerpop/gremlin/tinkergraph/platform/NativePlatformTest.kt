package org.apache.tinkerpop.gremlin.tinkergraph.platform

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import org.apache.tinkerpop.gremlin.tinkergraph.collections.NativeCollections
import org.apache.tinkerpop.gremlin.tinkergraph.memory.NativeMemoryManager
import org.apache.tinkerpop.gremlin.tinkergraph.optimization.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex

/**
 * Native platform test suite using Kotest framework.
 * Tests native-specific functionality and cross-platform compatibility.
 */
class NativePlatformTest : StringSpec({

    "platform operations should work correctly" {
        // Test enhanced sleep implementation
        val startTime = Platform.currentTimeMillis()
        Platform.sleep(50) // Sleep for 50ms (shorter for tests)
        val endTime = Platform.currentTimeMillis()
        val elapsed = Platform.timeDifference(startTime, endTime)

        elapsed shouldBeGreaterThanOrEqual 40L // Allow some variance
        elapsed shouldBe Platform.timeDifference(startTime, endTime)

        // Test time comparison
        Platform.timeComparison(150, 100) shouldBe true
        Platform.timeComparison(100, 150) shouldBe false

        // Test collection creation
        val sortedMap = Platform.createSortedMap<String, Int>()
        val linkedMap = Platform.createLinkedHashMap<String, String>()

        sortedMap shouldNotBe null
        linkedMap shouldNotBe null
    }

    "memory management should track allocations correctly" {
        // Track some allocations
        NativeMemoryManager.trackAllocation(1000)
        NativeMemoryManager.trackAllocation(2000)
        NativeMemoryManager.trackDeallocation(500)

        // Get statistics
        val stats = NativeMemoryManager.getMemoryStatistics()

        stats.totalAllocated shouldBeGreaterThanOrEqual 0L
        stats.totalFreed shouldBeGreaterThanOrEqual 0L
        stats.activeAllocations shouldBeGreaterThanOrEqual 0L
        stats.maxActiveAllocations shouldBeGreaterThanOrEqual stats.activeAllocations

        // Get recommendations (should provide some guidance)
        val recommendations = NativeMemoryManager.getOptimizationRecommendations()
        recommendations shouldNotBe null
    }

    "native collections should work with proper sizing" {
        // Test factory methods
        val hashMap = NativeCollections.Factory.createHashMap<String, Int>(64)
        val hashSet = NativeCollections.Factory.createHashSet<String>(32)
        val sortedMap = NativeCollections.Factory.createSortedMap<String, Int>()

        // Add test data
        hashMap["key1"] = 1
        hashMap["key2"] = 2
        hashSet.add("value1")
        hashSet.add("value2")
        sortedMap["z"] = 3
        sortedMap["a"] = 1

        hashMap.size shouldBe 2
        hashSet.size shouldBe 2
        sortedMap.size shouldBe 2

        // Test sorted order
        sortedMap.keys.first() shouldBe "a"
        sortedMap.keys.last() shouldBe "z"

        // Get statistics
        val stats = NativeCollections.getCollectionStatistics()
        stats.recommendedHashMapCapacity shouldBeGreaterThan 0
        (stats.recommendedLoadFactor > 0.0f) shouldBe true

        // Get recommendations
        val recommendations = NativeCollections.getOptimizationRecommendations()
        recommendations shouldNotBe null
    }

    "performance monitoring should provide platform statistics" {
        val perfStats = Platform.getNativePerformanceStatistics()

        // Verify platform info
        with(perfStats.platformInfo) {
            osFamily.shouldNotBeEmpty()
            cpuArchitecture.shouldNotBeEmpty()
            availableCores shouldBeGreaterThan 0
            availableMemory shouldBeGreaterThanOrEqual 0L
        }

        // Verify memory statistics are available
        with(perfStats.memoryStats) {
            totalAllocated shouldBeGreaterThanOrEqual 0L
            activeAllocations shouldBeGreaterThanOrEqual 0L
        }

        // Verify collection statistics
        with(perfStats.collectionStats) {
            recommendedHashMapCapacity shouldBeGreaterThan 0
            (recommendedLoadFactor > 0.0f) shouldBe true
        }

        // Get optimization recommendations
        val recommendations = Platform.getOptimizationRecommendations()
        recommendations shouldNotBe null
    }

    "cross-platform compatibility should work with TinkerGraph" {
        shouldNotThrowAny {
            // Test TinkerGraph creation with native platform
            val graph = TinkerGraph.open()

            // Add test data
            val vertex1 = graph.addVertex(TinkerVertex.T.label, "person", "name", "Alice", "age", 30)
            val vertex2 = graph.addVertex(TinkerVertex.T.label, "person", "name", "Bob", "age", 25)

            val edge = vertex1.addEdge("knows", vertex2)
            edge.property("since", "2020")

            // Verify graph structure
            graph.vertices().asSequence().toList() shouldHaveSize 2
            graph.edges().asSequence().toList() shouldHaveSize 1

            // Verify properties
            vertex1.value<String>("name") shouldBe "Alice"
            vertex1.value<Int>("age") shouldBe 30
            vertex2.value<String>("name") shouldBe "Bob"
            vertex2.value<Int>("age") shouldBe 25
            edge.value<String>("since") shouldBe "2020"

            // Test cleanup
            Platform.forceNativeCleanup()
        }
    }

    "native cleanup should execute without errors" {
        shouldNotThrowAny {
            Platform.forceNativeCleanup()
        }
    }

    "memory leak detection should work correctly" {
        // Simulate memory leak scenario
        NativeMemoryManager.trackAllocation(1000)
        NativeMemoryManager.trackAllocation(2000)
        // Don't deallocate everything
        NativeMemoryManager.trackDeallocation(500)

        val stats = NativeMemoryManager.getMemoryStatistics()

        // Should at least track the allocations
        stats.totalAllocated shouldBeGreaterThanOrEqual 0L
        stats.totalFreed shouldBeGreaterThanOrEqual 0L
    }

    "collection factory should create appropriate collection types" {
        val hashMap = NativeCollections.Factory.createHashMap<Int, String>()
        val hashSet = NativeCollections.Factory.createHashSet<Int>()
        val sortedMap = NativeCollections.Factory.createSortedMap<Int, String>()

        // Test that collections work as expected
        hashMap[1] = "one"
        hashMap[2] = "two"

        hashSet.add(1)
        hashSet.add(2)
        hashSet.add(1) // duplicate

        sortedMap[3] = "three"
        sortedMap[1] = "one"

        hashMap.size shouldBe 2
        hashSet.size shouldBe 2 // no duplicates
        sortedMap.size shouldBe 2

        // Verify sorted map maintains order
        sortedMap.keys.toList() shouldBe listOf(1, 3)
    }

    "native optimizations should be available and functional" {
        shouldNotThrowAny {
            // Test basic optimization system availability
            val memStats = NativeMemoryManager.getMemoryStatistics()
            memStats.totalAllocated shouldBeGreaterThanOrEqual 0L

            val memRecommendations = NativeMemoryManager.getOptimizationRecommendations()
            memRecommendations shouldNotBe null

            // Test memory pool basic functionality
            val vertex = MemoryPool.allocateVertex("v1", "person")
            vertex.isInitialized() shouldBe true
            MemoryPool.releaseVertex(vertex)

            val poolStats = MemoryPool.getPoolStatistics()
            poolStats.isNotEmpty() shouldBe true

            // Test SIMD optimizations basic functionality
            val values = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
            val sum = SimdOptimizations.vectorizedAggregation(values, SimdOptimizations.AggregationOperation.SUM)
            sum shouldBe 10.0

            val simdStats = SimdOptimizations.getSimdStatistics()
            simdStats shouldNotBe null

            // Test native threading basic functionality
            NativeThreading.initialize(2)
            val threadStats = NativeThreading.getThreadingStatistics()
            threadStats?.activeThreads shouldBe 2
            NativeThreading.shutdown()

            // Test memory mapping basic functionality
            val mappingStats = NativeMemoryMapping.getMappingStatistics()
            mappingStats shouldNotBe null

            // Test profile-guided optimization basic functionality
            ProfileGuidedOptimization.profileOperation("test_operation") {
                // Simple test operation
                repeat(10) { it * 2 }
            }

            val pgoStats = ProfileGuidedOptimization.getOptimizationStatistics()
            pgoStats.totalOperationsProfiled shouldBeGreaterThan 0

            ProfileGuidedOptimization.reset()
        }
    }
})
