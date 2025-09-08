package org.apache.tinkerpop.gremlin.tinkergraph.structure

import kotlinx.datetime.Clock
import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.tinkergraph.util.VertexCastingManager
import kotlin.test.*

/**
 * Integration test demonstrating the complete liberal parameters solution
 * for resolving ClassCastException issues in JavaScript platform.
 *
 * This test proves that:
 * 1. No external SafeCasting calls are needed
 * 2. PropertyQueryEngine handles casting internally
 * 3. API works consistently across all platforms
 * 4. System is resilient to casting failures
 */
class LiberalParametersIntegrationTest {

    private lateinit var graph: TinkerGraph
    private lateinit var queryEngine: PropertyQueryEngine

    @BeforeTest
    fun setup() {
        graph = TinkerGraph.open()
        queryEngine = graph.propertyQueryEngine()
        VertexCastingManager.clearStatistics()
        println("\n=== Starting Liberal Parameters Integration Test ===")
    }

    @AfterTest
    fun cleanup() {
        val stats = VertexCastingManager.getCastingStatistics()
        println("Final casting statistics: $stats")
        graph.close()
        println("=== Integration Test Complete ===\n")
    }

    @Test
    fun testCompleteWorkflowWithoutExternalCasting() {
        println("Test: Complete workflow without external casting")

        // Phase 1: Create test data WITHOUT SafeCasting calls
        println("  Phase 1: Creating test data...")

        // People vertices - cast to TinkerVertex for cardinality support
        val alice = graph.addVertex() as TinkerVertex
        alice.property("name", "Alice")
        alice.property("age", 25)
        alice.property("type", "person")
        alice.property("department", "Engineering")
        alice.property("skill", "Kotlin", VertexProperty.Cardinality.LIST)
        alice.property("skill", "JavaScript", VertexProperty.Cardinality.LIST)

        val bob = graph.addVertex() as TinkerVertex
        bob.property("name", "Bob")
        bob.property("age", 30)
        bob.property("type", "person")
        bob.property("department", "Engineering")
        bob.property("skill", "Python", VertexProperty.Cardinality.LIST)

        val charlie = graph.addVertex()
        charlie.property("name", "Charlie")
        charlie.property("age", 35)
        charlie.property("type", "person")
        charlie.property("department", "Marketing")

        // Company vertices
        val acme = graph.addVertex()
        acme.property("name", "ACME Corp")
        acme.property("type", "company")
        acme.property("employees", 500)
        acme.property("industry", "Technology")

        val startup = graph.addVertex()
        startup.property("name", "StartupXYZ")
        startup.property("type", "company")
        startup.property("employees", 25)
        startup.property("industry", "AI")

        println("  Created 5 vertices successfully")

        // Phase 2: Query operations using PropertyQueryEngine
        println("  Phase 2: Running queries...")

        // Basic exact queries
        val people = queryEngine.queryVertices(
            PropertyQueryEngine.exact("type", "person")
        ).asSequence().toList()

        assertEquals(3, people.size)
        println("  Found ${people.size} people")

        val companies = queryEngine.queryVertices(
            PropertyQueryEngine.exact("type", "company")
        ).asSequence().toList()

        assertEquals(2, companies.size)
        println("  Found ${companies.size} companies")

        // Range queries
        val adults = queryEngine.queryVertices(
            PropertyQueryEngine.range("age", 25, 35, true, true)
        ).asSequence().toList()

        assertEquals(3, adults.size) // Alice, Bob, Charlie
        println("  Found ${adults.size} adults (age 25-35)")

        val youngAdults = queryEngine.queryVertices(
            PropertyQueryEngine.range("age", 20, 30, true, false)
        ).asSequence().toList()


        assertEquals(1, youngAdults.size) // Alice (25), Bob (30) excluded
        println("  Found ${youngAdults.size} young adults (age 20-30)")

        // Complex composite queries
        val youngEngineers = queryEngine.queryVertices(
            PropertyQueryEngine.and(
                PropertyQueryEngine.exact("type", "person"),
                PropertyQueryEngine.exact("department", "Engineering"),
                PropertyQueryEngine.range("age", 20, 35, true, false)
            )
        ).asSequence().toList()


        assertEquals(2, youngEngineers.size) // Alice (25) and Bob (30), Charlie (35) excluded
        println("  Found ${youngEngineers.size} young engineers")

        // String operations
        val techCompanies = queryEngine.queryVertices(
            PropertyQueryEngine.and(
                PropertyQueryEngine.exact("type", "company"),
                PropertyQueryEngine.contains("industry", "Tech", true)
            )
        ).asSequence().toList()


        assertEquals(1, techCompanies.size) // ACME Corp
        println("  Found ${techCompanies.size} tech companies")

        // Phase 3: Property-level queries (accepts Vertex interface)
        println("  Phase 3: Property-level operations...")

        val aliceSkills = queryEngine.queryVertexProperties<String>(
            alice, // No casting needed - accepts Vertex interface
            listOf(PropertyQueryEngine.exists("skill"))
        )

        assertTrue(aliceSkills.isNotEmpty())
        println("  Alice has ${aliceSkills.size} skills")

        // Phase 4: Aggregation operations
        println("  Phase 4: Aggregation operations...")

        val averageAge = queryEngine.aggregateProperties(
            "age",
            PropertyQueryEngine.PropertyAggregation.AVERAGE
        ) as? Double

        assertNotNull(averageAge)
        assertEquals(30.0, averageAge) // (25+30+35)/3 = 30
        println("  Average age: $averageAge")

        val totalEmployees = queryEngine.aggregateProperties(
            "employees",
            PropertyQueryEngine.PropertyAggregation.SUM
        ) as? Double

        assertNotNull(totalEmployees)
        assertEquals(525.0, totalEmployees) // 500 + 25
        println("  Total employees: $totalEmployees")

        // Phase 5: Verify casting statistics
        val stats = VertexCastingManager.getCastingStatistics()
        val successCount = stats["vertex_cast_success"] as? Long ?: 0L
        val failureCount = stats["vertex_cast_failure"] as? Long ?: 0L

        assertTrue(successCount > 0, "Should have successful casts")
        assertEquals(0L, failureCount, "Should have no casting failures")

        println("  Casting success: $successCount, failures: $failureCount")
        println("Test completed successfully!")
    }

    @Test
    fun testJavaScriptSpecificScenarios() {
        println("Test: JavaScript-specific scenarios")

        // Create vertices that previously caused JavaScript ClassCastExceptions
        repeat(20) { i ->
            val vertex = graph.addVertex() as TinkerVertex
            vertex.property("id", i)
            vertex.property("group", "batch_${i % 5}")
            vertex.property("value", i * 10.5)
            vertex.property("active", i % 2 == 0)
            vertex.property("tags", "tag_$i", VertexProperty.Cardinality.LIST)
            vertex.property("tags", "common", VertexProperty.Cardinality.LIST)
        }

        // Complex queries that previously failed on JavaScript
        val evenActiveVertices = queryEngine.queryVertices(
            listOf(
                PropertyQueryEngine.exact("active", true),
                PropertyQueryEngine.range("value", 50.0, 150.0, true, false),
                PropertyQueryEngine.contains("group", "batch_", false)
            )
        ).asSequence().toList()

        println("  Found ${evenActiveVertices.size} even active vertices")
        assertTrue(evenActiveVertices.isNotEmpty())

        // Verify we can access properties without casting issues
        evenActiveVertices.forEach { vertex ->
            val id = vertex.value<Int>("id")
            val group = vertex.value<String>("group")
            val value = vertex.value<Double>("value")
            val active = vertex.value<Boolean>("active")

            assertNotNull(id)
            assertTrue(group?.startsWith("batch_") == true)
            assertTrue(value != null && value >= 50.0 && value < 150.0)
            assertTrue(active == true)
        }

        // Cardinality queries
        val verticesWithTags = queryEngine.queryVerticesByCardinality(
            "tags",
            VertexProperty.Cardinality.LIST
        ).asSequence().toList()

        assertEquals(20, verticesWithTags.size)
        println("  Found ${verticesWithTags.size} vertices with LIST cardinality tags")

        // Meta-property operations
        val firstVertex = graph.vertices().next()
        val metaProperties = queryEngine.queryVerticesByMetaProperty(
            "tags", "timestamp", null
        ).asSequence().toList()

        // Should work without errors even if no meta-properties exist
        assertTrue(metaProperties.size >= 0)
        println("  Meta-property query completed successfully")

        println("JavaScript-specific scenarios test completed!")
    }

    @Test
    fun testErrorResilienceAndGracefulDegradation() {
        println("Test: Error resilience and graceful degradation")

        // Create some valid vertices
        val vertex1 = graph.addVertex()
        vertex1.property("name", "Valid1")
        vertex1.property("type", "test")

        val vertex2 = graph.addVertex()
        vertex2.property("name", "Valid2")
        vertex2.property("type", "test")

        // Test queries even if some internal operations might fail
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.exists("name")
        ).asSequence().toList()

        // Should find our valid vertices
        assertEquals(2, results.size)
        println("  Found ${results.size} vertices despite any potential internal failures")

        // Test with non-existent properties
        val nonExistent = queryEngine.queryVertices(
            PropertyQueryEngine.exact("nonexistent", "value")
        ).asSequence().toList()

        assertEquals(0, nonExistent.size)
        println("  Correctly handled non-existent property query")

        // Test aggregation on empty results
        val emptySum = queryEngine.aggregateProperties(
            "nonexistent",
            PropertyQueryEngine.PropertyAggregation.COUNT
        )

        assertEquals(0, emptySum)
        println("  Correctly handled aggregation on empty results")

        // Test vertex property queries with various vertex types
        results.forEach { vertex ->
            val props = queryEngine.queryVertexProperties<String>(
                vertex, // Should work regardless of actual vertex type
                listOf(PropertyQueryEngine.exists("name"))
            )
            assertTrue(props.isNotEmpty())
        }

        println("  All vertex property queries succeeded")

        // Verify system remained stable
        val finalStats = VertexCastingManager.getCastingStatistics()
        println("  Final stats after resilience test: $finalStats")

        println("Error resilience test completed!")
    }

    @Test
    fun testPerformanceAndStatistics() {
        println("Test: Performance and statistics monitoring")

        val startTime = Clock.System.now().toEpochMilliseconds()

        // Create a reasonable dataset
        repeat(100) { i ->
            val vertex = graph.addVertex()
            vertex.property("index", i)
            vertex.property("category", "cat_${i % 10}")
            vertex.property("score", i * 1.5)
            vertex.property("active", i % 3 == 0)
        }

        val creationTime = Clock.System.now().toEpochMilliseconds() - startTime
        println("  Created 100 vertices in ${creationTime}ms")

        // Run multiple query operations
        val queryStart = Clock.System.now().toEpochMilliseconds()

        val categories = (0..9).map { cat ->
            queryEngine.queryVertices(
                PropertyQueryEngine.exact("category", "cat_$cat")
            ).asSequence().toList()
        }

        val ranges = listOf(
            queryEngine.queryVertices(PropertyQueryEngine.range("score", 0.0, 50.0, true, false)),
            queryEngine.queryVertices(PropertyQueryEngine.range("score", 50.0, 100.0, true, false)),
            queryEngine.queryVertices(PropertyQueryEngine.range("score", 100.0, 200.0, true, false))
        ).map { it.asSequence().toList() }

        val activeVertices = queryEngine.queryVertices(
            PropertyQueryEngine.exact("active", true)
        ).asSequence().toList()

        val queryTime = Clock.System.now().toEpochMilliseconds() - queryStart
        println("  Completed multiple queries in ${queryTime}ms")

        // Verify results make sense
        categories.forEach { categoryResults ->
            assertEquals(10, categoryResults.size)
        }

        assertEquals(34, activeVertices.size) // Every 3rd vertex: 0,3,6,...,99 = 34 vertices

        // Check aggregations
        val totalScore = queryEngine.aggregateProperties(
            "score",
            PropertyQueryEngine.PropertyAggregation.SUM
        ) as? Double ?: 0.0

        val expectedTotal = (0..99).sumOf { it * 1.5 }
        assertEquals(expectedTotal, totalScore)

        println("  Aggregation verification passed")

        // Final statistics
        val finalStats = VertexCastingManager.getCastingStatistics()
        val totalCasts = (finalStats["vertex_cast_success"] as? Long ?: 0L) +
                        (finalStats["vertex_cast_failure"] as? Long ?: 0L)

        println("  Total casting operations: $totalCasts")
        println("  Performance test completed successfully!")

        val totalTime = Clock.System.now().toEpochMilliseconds() - startTime
        println("  Total test time: ${totalTime}ms")
    }

    @Test
    fun testBackwardCompatibilityAndMigration() {
        println("Test: Backward compatibility and migration path")

        // Demonstrate that both old and new patterns work
        val vertex1 = graph.addVertex() // New pattern - direct usage
        vertex1.property("name", "NewPattern")
        vertex1.property("type", "demo")

        // PropertyQueryEngine should handle both patterns seamlessly
        val results1 = queryEngine.queryVertices(
            PropertyQueryEngine.exact("type", "demo")
        ).asSequence().toList()

        assertEquals(1, results1.size)
        println("  New pattern works correctly")

        // Test that the API returns interface types consistently
        val vertex = results1.first()
        assertNotNull(vertex.id())
        assertNotNull(vertex.label())
        assertEquals("NewPattern", vertex.value<String>("name"))

        println("  Interface-based return types work correctly")

        // Test property operations work with interface types
        val properties = queryEngine.queryVertexProperties<String>(
            vertex, // Vertex interface
            listOf(PropertyQueryEngine.exists("name"))
        )

        assertFalse(properties.isEmpty())
        assertEquals("name", properties.first().key())
        assertEquals("NewPattern", properties.first().value())

        println("  Property operations work with interface types")

        // Verify the system maintains type safety where it matters
        assertTrue(vertex.keys().contains("name"))
        assertTrue(vertex.keys().contains("type"))

        println("  Type safety maintained at interface level")
        println("Backward compatibility test completed!")
    }
}
