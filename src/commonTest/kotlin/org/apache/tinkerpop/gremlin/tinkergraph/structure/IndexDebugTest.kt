package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.util.SafeCasting
import kotlin.test.*

/**
 * Debug test to isolate the index issue causing test failures.
 */
class IndexDebugTest {

    private lateinit var graph: TinkerGraph

    @BeforeTest
    fun setup() {
        graph = TinkerGraph.open()
    }

    @AfterTest
    fun cleanup() {
        graph.close()
    }

    @Test
    fun testBasicPropertyAndIndex() {
        println("=== Testing Basic Property and Index ===")

        // Step 1: Create vertex and add property
        val alice = SafeCasting.safeCastVertex(graph.addVertex())
        alice.property("department", "Engineering")
        alice.property("name", "Alice")

        println("Step 1 - Created vertex with properties:")
        println("  Alice department: ${alice.value<String>("department")}")
        println("  Alice name: ${alice.value<String>("name")}")
        println("  Alice keys: ${alice.keys()}")

        // Step 2: Verify property is accessible directly
        assertEquals("Engineering", alice.value<String>("department"))
        assertEquals("Alice", alice.value<String>("name"))

        // Step 3: Test query WITHOUT index (should use full scan)
        println("\nStep 2 - Query without index:")
        val queryEngine = graph.propertyQueryEngine()
        val engineersBeforeIndex = queryEngine.queryVertices(
            PropertyQueryEngine.exact("department", "Engineering")
        ).asSequence().toList()
        println("  Engineers found before index: ${engineersBeforeIndex.size}")

        // This should work even without indices
        assertEquals(1, engineersBeforeIndex.size, "Should find 1 engineer even without index")

        // Step 4: Create index AFTER adding data
        println("\nStep 3 - Creating index:")
        graph.createIndex("department", Vertex::class)

        // Step 5: Check if index was populated
        val indexedKeys = graph.vertexIndex.getIndexedKeys()
        println("  Indexed keys: $indexedKeys")
        assertTrue(indexedKeys.contains("department"), "Department should be indexed")

        // Step 6: Test query WITH index
        println("\nStep 4 - Query with index:")
        val engineersAfterIndex = queryEngine.queryVertices(
            PropertyQueryEngine.exact("department", "Engineering")
        ).asSequence().toList()
        println("  Engineers found after index: ${engineersAfterIndex.size}")

        // This should also work with indices
        assertEquals(1, engineersAfterIndex.size, "Should find 1 engineer with index")

        // Step 7: Verify the vertex is the same
        val foundVertex = SafeCasting.asTinkerVertex(engineersAfterIndex.first())!!
        println("  Found vertex name: ${foundVertex.value<String>("name")}")
        assertEquals("Alice", foundVertex.value<String>("name"))
    }

    @Test
    fun testMultipleVerticesAndIndex() {
        println("\n=== Testing Multiple Vertices and Index ===")

        // Create test data (exactly like AdvancedIndexingTest)
        val alice = SafeCasting.safeCastVertex(graph.addVertex())
        alice.property("name", "Alice")
        alice.property("department", "Engineering")

        val bob = SafeCasting.safeCastVertex(graph.addVertex())
        bob.property("name", "Bob")
        bob.property("department", "Engineering")

        val charlie = SafeCasting.safeCastVertex(graph.addVertex())
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
        val engineersBeforeIndex = queryEngine.queryVertices(
            PropertyQueryEngine.exact("department", "Engineering")
        ).asSequence().toList()
        println("Engineers before index: ${engineersBeforeIndex.size}")

        // Create index
        graph.createIndex("department", Vertex::class)

        // Test after indexing
        val engineersAfterIndex = queryEngine.queryVertices(
            PropertyQueryEngine.exact("department", "Engineering")
        ).asSequence().toList()
        println("Engineers after index: ${engineersAfterIndex.size}")

        engineersAfterIndex.forEach { vertex ->
            val v = SafeCasting.asTinkerVertex(vertex)
            if (v != null) {
                println("  Found: ${v.value<String>("name")}")
            }
        }

        assertEquals(2, engineersAfterIndex.size, "Should find 2 engineers")
    }

    @Test
    fun testIndexInternals() {
        println("\n=== Testing Index Internals ===")

        // Create vertex with property
        val vertex = SafeCasting.safeCastVertex(graph.addVertex())
        vertex.property("testKey", "testValue")

        // Check vertex index before creating index
        val indexBefore = graph.vertexIndex.getIndexedKeys()
        println("Indexed keys before: $indexBefore")

        // Create index and check internals
        graph.createIndex("testKey", Vertex::class)

        val indexAfter = graph.vertexIndex.getIndexedKeys()
        println("Indexed keys after: $indexAfter")
        assertTrue(indexAfter.contains("testKey"))

        // Check if vertex can be found in index
        val verticesFromIndex = graph.vertexIndex.get("testKey", "testValue")
        println("Vertices from index: ${verticesFromIndex.size}")
        assertEquals(1, verticesFromIndex.size)

        // Verify it's the same vertex
        val foundVertex = verticesFromIndex.first()
        assertEquals(vertex.id(), foundVertex.id())
    }

    @Test
    fun testIndexRebuild() {
        println("\n=== Testing Index Rebuild Process ===")

        // Create vertices with properties
        val vertices = (1..3).map { i ->
            val vertex = SafeCasting.safeCastVertex(graph.addVertex())
            vertex.property("number", i)
            vertex.property("category", if (i % 2 == 0) "even" else "odd")
            vertex
        }

        println("Created ${vertices.size} vertices")

        // Manually test the rebuild process
        val allVertices = graph.vertices().asSequence().mapNotNull { SafeCasting.asTinkerVertex(it) }.toList()
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
        val oddVertices = graph.propertyQueryEngine().queryVertices(
            PropertyQueryEngine.exact("category", "odd")
        ).asSequence().toList()

        println("Odd vertices found: ${oddVertices.size}")
        assertEquals(2, oddVertices.size, "Should find 2 odd vertices")
    }
}
