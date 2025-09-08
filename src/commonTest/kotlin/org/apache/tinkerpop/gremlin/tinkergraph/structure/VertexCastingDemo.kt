package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.tinkergraph.util.VertexCastingManager
import kotlin.test.*

/**
 * Demo test to verify the new liberal parameter approach works correctly.
 * This test demonstrates that external SafeCasting calls are no longer needed
 * and that PropertyQueryEngine handles casting internally.
 */
class VertexCastingDemo {

    private lateinit var graph: TinkerGraph
    private lateinit var queryEngine: PropertyQueryEngine

    @BeforeTest
    fun setup() {
        graph = TinkerGraph.open()
        queryEngine = graph.propertyQueryEngine()
        VertexCastingManager.clearStatistics()
    }

    @AfterTest
    fun cleanup() {
        graph.close()
    }

    @Test
    fun testLiberalParameterApproach() {
        // Create vertices without SafeCasting - should work seamlessly
        val alice = graph.addVertex() // Returns Vertex interface
        alice.property("name", "Alice")
        alice.property("age", 25)
        alice.property("type", "person")

        val bob = graph.addVertex() // Returns Vertex interface
        bob.property("name", "Bob")
        bob.property("age", 30)
        bob.property("type", "person")

        val company = graph.addVertex() // Returns Vertex interface
        company.property("name", "ACME Corp")
        company.property("type", "company")
        company.property("employees", 500)

        // Query using PropertyQueryEngine - should handle casting internally
        val people = queryEngine.queryVertices(
            PropertyQueryEngine.exact("type", "person")
        ).asSequence().toList()

        assertEquals(2, people.size)
        println("Found ${people.size} people")

        // Query with range criteria - should work without casting issues
        val adults = queryEngine.queryVertices(
            PropertyQueryEngine.range("age", 25, 35, true, true)
        ).asSequence().toList()

        assertEquals(2, adults.size)
        println("Found ${adults.size} adults")

        // Query vertex properties - accepts Vertex interface
        val aliceProperties = queryEngine.queryVertexProperties<String>(
            alice, // Vertex interface - no casting needed
            listOf(PropertyQueryEngine.exists("name"))
        )

        assertTrue(aliceProperties.isNotEmpty())
        assertEquals("name", aliceProperties.first().key())
        assertEquals("Alice", aliceProperties.first().value())

        // Complex composite query
        val youngPeople = queryEngine.queryVertices(
            PropertyQueryEngine.and(
                PropertyQueryEngine.exact("type", "person"),
                PropertyQueryEngine.range("age", 20, 30, true, true)
            )
        ).asSequence().toList()

        assertEquals(2, youngPeople.size) // Alice (25) and Bob (30)

        // Verify casting statistics
        val stats = VertexCastingManager.getCastingStatistics()
        println("Casting statistics: $stats")

        // Should show successful vertex casts with no failures
        val successCount = stats["vertex_cast_success"] as? Long ?: 0L
        val failureCount = stats["vertex_cast_failure"] as? Long ?: 0L

        assertTrue(successCount > 0, "Expected some successful vertex casts")
        assertEquals(0L, failureCount, "Expected no casting failures")
    }

    @Test
    fun testCastingManagerDiagnostics() {
        val vertex = graph.addVertex()
        vertex.property("name", "Test")

        // Test diagnostic functionality
        val diagnosis = VertexCastingManager.diagnoseObjectType(vertex)
        assertNotNull(diagnosis)
        assertTrue(diagnosis.contains("Vertex"), "Diagnosis should mention Vertex")

        println("Vertex diagnosis:\n$diagnosis")

        // Test with null
        val nullDiagnosis = VertexCastingManager.diagnoseObjectType(null)
        assertEquals("null", nullDiagnosis)

        // Test with non-vertex object
        val stringDiagnosis = VertexCastingManager.diagnoseObjectType("not a vertex")
        assertNotNull(stringDiagnosis)
        println("String diagnosis:\n$stringDiagnosis")
    }

    @Test
    fun testGracefulFailureHandling() {
        // This test verifies that the system handles failures gracefully
        // without throwing ClassCastExceptions

        // Create some vertices
        val vertex1 = graph.addVertex()
        vertex1.property("name", "Test1")

        val vertex2 = graph.addVertex()
        vertex2.property("name", "Test2")

        // Query should work even if some internal casting operations fail
        val results = queryEngine.queryVertices(
            PropertyQueryEngine.exists("name")
        ).asSequence().toList()

        // Should find the vertices we created
        assertEquals(2, results.size)

        // Check that no ClassCastExceptions were thrown
        val stats = VertexCastingManager.getCastingStatistics()
        println("Graceful failure test stats: $stats")

        // Even if there were some failures, the system should continue working
        assertTrue(results.isNotEmpty(), "Query should return results despite any internal failures")
    }

    @Test
    fun testMultiPlatformCompatibility() {
        // This test focuses on operations that previously caused JavaScript issues

        // Create test data
        repeat(10) { i ->
            val vertex = graph.addVertex()
            vertex.property("id", i)
            vertex.property("group", if (i % 2 == 0) "even" else "odd")
            vertex.property("value", i * 10)
        }

        // Complex query that previously caused ClassCastExceptions
        val evenVertices = queryEngine.queryVertices(
            listOf(
                PropertyQueryEngine.exact("group", "even"),
                PropertyQueryEngine.range("value", 20, 80, true, false)
            )
        ).asSequence().toList()

        // Should find vertices with id: 2, 4, 6 (values 20, 40, 60)
        assertEquals(3, evenVertices.size)

        // Verify we can access properties without casting issues
        evenVertices.forEach { vertex ->
            val id = vertex.value<Int>("id")
            val group = vertex.value<String>("group")
            val value = vertex.value<Int>("value")

            assertNotNull(id)
            assertEquals("even", group)
            assertTrue(value != null && value >= 20 && value < 80)

            println("Found even vertex: id=$id, value=$value")
        }

        // Aggregation operations that previously failed
        val count = queryEngine.aggregateProperties("value", PropertyQueryEngine.PropertyAggregation.COUNT)
        assertEquals(10, count)

        val sum = queryEngine.aggregateProperties("value", PropertyQueryEngine.PropertyAggregation.SUM) as Double
        assertEquals(450.0, sum) // 0+10+20+...+90 = 450

        println("Successfully completed multi-platform compatibility test")
    }
}
