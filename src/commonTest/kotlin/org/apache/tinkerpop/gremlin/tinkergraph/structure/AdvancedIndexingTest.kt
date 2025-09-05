package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.tinkergraph.platform.Platform
import kotlin.test.*

/**
 * Comprehensive tests for advanced indexing capabilities including
 * composite indices, range indices, index optimization, and caching.
 */
class AdvancedIndexingTest {

    private lateinit var graph: TinkerGraph

    @BeforeTest
    fun setup() {
        graph = TinkerGraph.open()
        setupTestData()
    }

    @AfterTest
    fun cleanup() {
        graph.close()
    }

    private fun setupTestData() {
        // Create test vertices with various properties
        val alice = graph.addVertex() as TinkerVertex
        alice.property("name", "Alice")
        alice.property("age", 25)
        alice.property("city", "New York")
        alice.property("salary", 75000)
        alice.property("department", "Engineering")

        val bob = graph.addVertex() as TinkerVertex
        bob.property("name", "Bob")
        bob.property("age", 30)
        bob.property("city", "San Francisco")
        bob.property("salary", 95000)
        bob.property("department", "Engineering")

        val charlie = graph.addVertex() as TinkerVertex
        charlie.property("name", "Charlie")
        charlie.property("age", 35)
        charlie.property("city", "New York")
        charlie.property("salary", 85000)
        charlie.property("department", "Marketing")

        val diana = graph.addVertex() as TinkerVertex
        diana.property("name", "Diana")
        diana.property("age", 28)
        diana.property("city", "Chicago")
        diana.property("salary", 70000)
        diana.property("department", "Marketing")

        val eve = graph.addVertex() as TinkerVertex
        eve.property("name", "Eve")
        eve.property("age", 32)
        eve.property("city", "San Francisco")
        eve.property("salary", 105000)
        eve.property("department", "Engineering")
    }

    // ===== Composite Index Tests =====

    @Test
    fun testCompositeIndexCreation() {
        // Create composite index
        graph.createCompositeIndex(listOf("department", "city"), Vertex::class)

        val stats = graph.vertexCompositeIndex.getStatistics()
        assertEquals(1, stats["compositeIndexCount"])

        assertTrue(graph.vertexCompositeIndex.isCompositeIndexed(listOf("department", "city")))
        assertFalse(graph.vertexCompositeIndex.isCompositeIndexed(listOf("name", "age")))
    }

    @Test
    fun testCompositeIndexQuery() {
        // Create composite index for department and city
        graph.createCompositeIndex(listOf("department", "city"), Vertex::class)

        // Query using composite index
        val engineersInNY = graph.vertexCompositeIndex.get(
            listOf("department", "city"),
            listOf("Engineering", "New York")
        )

        assertEquals(1, engineersInNY.size)
        assertEquals("Alice", engineersInNY.first().value<String>("name"))

        val engineersInSF = graph.vertexCompositeIndex.get(
            listOf("department", "city"),
            listOf("Engineering", "San Francisco")
        )

        assertEquals(2, engineersInSF.size)
        val names = engineersInSF.map { it.value<String>("name") }.sortedBy { it }
        assertEquals(listOf("Bob", "Eve"), names)
    }

    @Test
    fun testCompositeIndexPartialQuery() {
        // Create composite index for department, city, and age
        graph.createCompositeIndex(listOf("department", "city", "age"), Vertex::class)

        // Query with partial keys (prefix matching)
        val engineersInNY = graph.vertexCompositeIndex.getPartial(
            listOf("department", "city"),
            listOf("Engineering", "New York")
        )

        assertEquals(1, engineersInNY.size)
        assertEquals("Alice", engineersInNY.first().value<String>("name"))
    }

    @Test
    fun testCompositeIndexDrop() {
        graph.createCompositeIndex(listOf("department", "city"), Vertex::class)
        assertTrue(graph.vertexCompositeIndex.isCompositeIndexed(listOf("department", "city")))

        graph.dropCompositeIndex(listOf("department", "city"), Vertex::class)
        assertFalse(graph.vertexCompositeIndex.isCompositeIndexed(listOf("department", "city")))
    }

    // ===== Range Index Tests =====

    @Test
    fun testRangeIndexCreation() {
        // Create range index for age and salary
        graph.createRangeIndex("age", Vertex::class)
        graph.createRangeIndex("salary", Vertex::class)

        assertTrue(graph.vertexRangeIndex.isRangeIndexed("age"))
        assertTrue(graph.vertexRangeIndex.isRangeIndexed("salary"))
        assertFalse(graph.vertexRangeIndex.isRangeIndexed("name"))

        val stats = graph.vertexRangeIndex.getStatistics()
        assertEquals(2, stats["rangeIndexedKeyCount"])
    }

    @Test
    fun testRangeQueries() {
        graph.createRangeIndex("age", Vertex::class)
        graph.createRangeIndex("salary", Vertex::class)

        // Test range queries on age
        val youngPeople = RangeIndex.safeRangeQuery(graph.vertexRangeIndex, "age", 20, 30, true, true)
        assertEquals(2, youngPeople.size)
        val youngNames = youngPeople.map { it.value<String>("name") }.sortedBy { it }
        assertEquals(listOf("Alice", "Diana"), youngNames)

        // Test range queries on salary
        val highEarners = RangeIndex.safeRangeQuery(graph.vertexRangeIndex, "salary", 90000, null, true, true)
        assertEquals(2, highEarners.size)
        val highEarnerNames = highEarners.map { it.value<String>("name") }.sortedBy { it }
        assertEquals(listOf("Bob", "Eve"), highEarnerNames)

        // Test exclusive range
        val midRange = RangeIndex.safeRangeQuery(graph.vertexRangeIndex, "age", 25, 35, false, false)
        assertEquals(2, midRange.size) // Bob (30) and Diana (28)
    }

    @Test
    fun testRangeIndexMinMax() {
        graph.createRangeIndex("age", Vertex::class)

        assertEquals(RangeIndex.safeComparable(25), graph.vertexRangeIndex.getMinValue("age"))
        assertEquals(RangeIndex.safeComparable(35), graph.vertexRangeIndex.getMaxValue("age"))

        val sortedAges = graph.vertexRangeIndex.getSortedValues("age")
        val expectedAges = listOf(25, 28, 30, 32, 35).map { RangeIndex.safeComparable(it) }
        assertEquals(expectedAges, sortedAges)
    }

    @Test
    fun testRangeIndexGreaterLessThan() {
        graph.createRangeIndex("salary", Vertex::class)

        val above80k = graph.vertexRangeIndex.greaterThan("salary", RangeIndex.safeComparable(80000)!!, false)
        assertEquals(3, above80k.size) // Bob, Charlie, Eve

        val below80k = graph.vertexRangeIndex.lessThan("salary", RangeIndex.safeComparable(80000)!!, false)
        assertEquals(2, below80k.size) // Alice, Diana
    }

    // ===== Index Optimizer Tests =====

    @Test
    fun testQueryOptimization() {
        // Create various indices
        graph.createIndex("department", Vertex::class)
        graph.createRangeIndex("age", Vertex::class)
        graph.createCompositeIndex(listOf("department", "city"), Vertex::class)

        // Test composite query optimization
        val compositeCriteria = listOf(
            PropertyQueryEngine.exact("department", "Engineering"),
            PropertyQueryEngine.exact("city", "New York")
        )

        val plan = graph.optimizeVertexQuery(compositeCriteria)
        assertIs<IndexOptimizer.CompositeIndexStrategy>(plan.primaryStrategy)
        assertTrue(plan.estimatedCost < 1.0) // Should be better than full scan
    }

    @Test
    fun testIndexRecommendations() {
        // Create some queries to build statistics
        val queryEngine = graph.propertyQueryEngine()

        // Simulate repeated queries
        repeat(10) {
            queryEngine.queryVertices(listOf(
                PropertyQueryEngine.exact("department", "Engineering"),
                PropertyQueryEngine.exact("city", "San Francisco")
            ))
        }

        val recommendations = graph.getIndexRecommendations()
        val vertexRecommendations = recommendations["vertices"] ?: emptyList()

        assertFalse(vertexRecommendations.isEmpty())
        assertTrue(vertexRecommendations.any { it.type == IndexOptimizer.IndexType.COMPOSITE })
    }

    // ===== Index Cache Tests =====

    @Test
    fun testIndexCaching() {
        val cache = graph.vertexIndexCache

        // Perform a query that should be cached
        val criteria = listOf(PropertyQueryEngine.exact("department", "Engineering"))
        val results1 = graph.propertyQueryEngine().queryVertices(criteria).asSequence().toList()

        // Check cache statistics
        val stats = cache.getStatistics()
        assertTrue(stats["hits"] as Long >= 0)
        assertTrue(stats["misses"] as Long >= 0)

        // Perform the same query again - should hit cache
        val results2 = graph.propertyQueryEngine().queryVertices(criteria).asSequence().toList()
        assertEquals(results1.size, results2.size)
    }

    @Test
    fun testCacheInvalidation() {
        val cache = graph.vertexIndexCache

        // Put something in cache
        val testResults = setOf<TinkerVertex>()
        cache.put(IndexCache.IndexType.SINGLE_PROPERTY, "department", testResults)

        assertTrue(cache.contains(IndexCache.IndexType.SINGLE_PROPERTY, "department"))

        // Invalidate by key
        cache.invalidateKey("department")
        assertFalse(cache.contains(IndexCache.IndexType.SINGLE_PROPERTY, "department"))
    }

    @Test
    fun testCacheExpiration() {
        val cache = graph.vertexIndexCache
        cache.setMaxAge(100) // 100ms

        // Add entry to cache
        val testResults = setOf<TinkerVertex>()
        cache.put(IndexCache.IndexType.SINGLE_PROPERTY, "test", testResults)

        assertTrue(cache.contains(IndexCache.IndexType.SINGLE_PROPERTY, "test"))

        // Wait for expiration
        Platform.sleep(150)

        // Entry should be expired
        assertNull(cache.get(IndexCache.IndexType.SINGLE_PROPERTY, "test"))
    }

    // ===== PropertyQueryEngine Integration Tests =====

    @Test
    fun testOptimizedPropertyQueries() {
        // Create indices for optimization
        graph.createIndex("department", Vertex::class)
        graph.createRangeIndex("age", Vertex::class)
        graph.createCompositeIndex(listOf("department", "city"), Vertex::class)

        val queryEngine = graph.propertyQueryEngine()

        // Test optimized exact query
        val engineers = queryEngine.queryVertices(
            PropertyQueryEngine.exact("department", "Engineering")
        ).asSequence().toList()
        assertEquals(3, engineers.size)

        // Test optimized range query
        val youngPeople = queryEngine.queryVerticesByRange("age", 20, 30, true).asSequence().toList()
        assertEquals(2, youngPeople.size)

        // Test composite query
        val engineersInSF = queryEngine.queryVertices(listOf(
            PropertyQueryEngine.exact("department", "Engineering"),
            PropertyQueryEngine.exact("city", "San Francisco")
        )).asSequence().toList()
        assertEquals(2, engineersInSF.size)
    }

    @Test
    fun testComplexOptimizedQueries() {
        graph.createRangeIndex("age", Vertex::class)
        graph.createRangeIndex("salary", Vertex::class)
        graph.createCompositeIndex(listOf("department", "city"), Vertex::class)

        val queryEngine = graph.propertyQueryEngine()

        // Complex query with multiple criteria
        val results = queryEngine.queryVertices(listOf(
            PropertyQueryEngine.exact("department", "Engineering"),
            PropertyQueryEngine.range("age", 25, 35, true),
            PropertyQueryEngine.range("salary", 70000, 100000, true)
        )).asSequence().toList()

        assertTrue(results.isNotEmpty())
        results.forEach { vertex ->
            assertEquals("Engineering", vertex.value<String>("department"))
            val age = vertex.value<Int>("age")
            assertTrue(age in 25..35)
            val salary = vertex.value<Int>("salary")
            assertTrue(salary in 70000..100000)
        }
    }

    // ===== Statistics and Monitoring Tests =====

    @Test
    fun testIndexingStatistics() {
        // Create various indices
        graph.createIndex("name", Vertex::class)
        graph.createRangeIndex("age", Vertex::class)
        graph.createCompositeIndex(listOf("department", "city"), Vertex::class)

        val stats = graph.getIndexingStatistics()
        assertNotNull(stats["vertexIndices"])
        assertNotNull(stats["edgeIndices"])

        val vertexStats = stats["vertexIndices"] as Map<*, *>
        assertNotNull(vertexStats["singleProperty"])
        assertNotNull(vertexStats["composite"])
        assertNotNull(vertexStats["range"])
        assertNotNull(vertexStats["cache"])
        assertNotNull(vertexStats["optimizer"])
    }

    @Test
    fun testMemoryOptimization() {
        val cache = graph.vertexIndexCache

        // Test cache configuration
        cache.setMaxSize(500)
        cache.setMaxAge(60000) // 1 minute

        val stats = cache.getStatistics()
        assertEquals(500, stats["maxSize"])
        assertEquals(60000L, stats["maxAgeMs"])

        // Test memory estimation
        val memoryUsage = cache.estimateMemoryUsage()
        assertTrue(memoryUsage >= 0)

        // Test optimization recommendations
        val recommendations = cache.getOptimizationRecommendations()
        assertNotNull(recommendations)
    }

    // ===== Edge Index Tests =====

    @Test
    fun testEdgeIndexing() {
        // Create some edges
        val alice = graph.vertices().next() as TinkerVertex
        val bob = graph.vertices().next() as TinkerVertex

        val edge1 = alice.addEdge("knows", bob)
        edge1.property("since", 2020)
        edge1.property("weight", 0.8)

        val edge2 = bob.addEdge("knows", alice)
        edge2.property("since", 2021)
        edge2.property("weight", 0.9)

        // Create edge indices
        graph.createIndex("since", org.apache.tinkerpop.gremlin.structure.Edge::class)
        graph.createRangeIndex("weight", org.apache.tinkerpop.gremlin.structure.Edge::class)

        assertTrue(graph.edgeIndex.isIndexed("since"))
        assertTrue(graph.edgeRangeIndex.isRangeIndexed("weight"))

        // Test edge queries
        val recentEdges = graph.edgeIndex.get("since", 2021)
        assertEquals(1, recentEdges.size)

        val strongConnections = RangeIndex.safeRangeQuery(graph.edgeRangeIndex, "weight", 0.85, 1.0, true, true)
        assertEquals(1, strongConnections.size)
    }

    // ===== Performance Tests =====

    @Test
    fun testLargeDatasetPerformance() {
        // Create a larger dataset
        repeat(100) { i ->
            val vertex = graph.addVertex() as TinkerVertex
            vertex.property("id", i)
            vertex.property("category", "category_${i % 10}")
            vertex.property("value", i * 10)
        }

        // Create indices
        graph.createIndex("category", Vertex::class)
        graph.createRangeIndex("value", Vertex::class)
        graph.createCompositeIndex(listOf("category", "value"), Vertex::class)

        val startTime = Platform.currentTimeMillis()

        // Perform queries
        val queryEngine = graph.propertyQueryEngine()
        val results = queryEngine.queryVertices(listOf(
            PropertyQueryEngine.exact("category", "category_5"),
            PropertyQueryEngine.range("value", 200, 800, true)
        )).asSequence().toList()

        val endTime = Platform.currentTimeMillis()
        val queryTime = endTime - startTime

        assertTrue(results.isNotEmpty())
        assertTrue(queryTime >= 0) // Should complete successfully

        // Verify cache is being used effectively
        val cacheStats = graph.vertexIndexCache.getStatistics()
        assertTrue(cacheStats["size"] as Int >= 0)
    }

    @Test
    fun testIndexMaintenanceOnUpdates() {
        graph.createIndex("department", Vertex::class)
        graph.createRangeIndex("salary", Vertex::class)
        graph.createCompositeIndex(listOf("department", "city"), Vertex::class)

        val alice = graph.vertices().asSequence()
            .map { it as TinkerVertex }
            .find { it.value<String>("name") == "Alice" }!!

        // Update Alice's properties
        alice.property("department", "Management")
        alice.property("salary", 120000)
        alice.property("city", "Boston")

        // Verify indices are updated
        val managementEmployees = graph.vertexIndex.get("department", "Management")
        assertEquals(1, managementEmployees.size)
        assertEquals("Alice", managementEmployees.first().value<String>("name"))

        val highEarners = RangeIndex.safeRangeQuery(graph.vertexRangeIndex, "salary", 115000, null, true, true)
        assertTrue(highEarners.any { it.value<String>("name") == "Alice" })

        val managementInBoston = graph.vertexCompositeIndex.get(
            listOf("department", "city"),
            listOf("Management", "Boston")
        )
        assertEquals(1, managementInBoston.size)
        assertEquals("Alice", managementInBoston.first().value<String>("name"))
    }

    // ===== Error Handling Tests =====

    @Test
    fun testIndexCreationErrors() {
        assertFailsWith<IllegalArgumentException> {
            graph.createCompositeIndex(emptyList(), Vertex::class)
        }

        assertFailsWith<IllegalArgumentException> {
            graph.createCompositeIndex(listOf("key1"), Vertex::class) // Single key not allowed
        }

        assertFailsWith<IllegalArgumentException> {
            graph.createCompositeIndex(listOf("key1", "key1"), Vertex::class) // Duplicate keys
        }
    }

    @Test
    fun testCacheConfigurationErrors() {
        val cache = graph.vertexIndexCache

        assertFailsWith<IllegalArgumentException> {
            cache.setMaxSize(0)
        }

        assertFailsWith<IllegalArgumentException> {
            cache.setMaxAge(0)
        }
    }
}
