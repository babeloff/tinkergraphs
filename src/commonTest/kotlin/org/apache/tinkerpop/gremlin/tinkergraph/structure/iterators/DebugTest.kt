package org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators

import kotlin.test.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

/**
 * Debug test suite for diagnosing vertex property iteration issues.
 *
 * This test class provides detailed debugging capabilities for understanding
 * vertex property iteration behavior and troubleshooting common issues:
 * - Property creation and retrieval verification
 * - Iterator behavior analysis with detailed logging
 * - Type checking for different property implementations
 * - Internal TinkerVertex state inspection
 *
 * These tests are particularly useful for:
 * - Investigating property iteration failures
 * - Verifying property storage and retrieval mechanisms
 * - Understanding the relationship between different property types
 * - Debugging cross-platform property handling differences
 *
 * @see org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex
 * @see org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators.TinkerPropertyIterator
 */
class DebugTest {

    /**
     * Debug test for vertex property iteration with detailed logging.
     *
     * Creates a vertex with multiple properties and performs comprehensive
     * debugging analysis including:
     * - Property creation verification
     * - Key enumeration testing
     * - Individual property value retrieval
     * - Iterator-based property enumeration with detailed output
     * - Property count validation
     *
     * This test helps identify where property iteration might be failing
     * by providing extensive debug output at each step.
     */
    @Test
    fun testVertexPropertyDebug() {
        val graph = TinkerGraph.open()

        println("Creating vertex with properties...")
        val vertex = graph.addVertex("name", "alice", "age", 29, "city", "paris")

        println("Vertex created with ID: ${vertex.id()}")
        println("Vertex label: ${vertex.label()}")

        // Check keys
        val keys = vertex.keys()
        println("Vertex keys: $keys")
        println("Number of keys: ${keys.size}")

        // Try getting individual property values
        val name = vertex.value<String>("name")
        val age = vertex.value<Int>("age")
        val city = vertex.value<String>("city")

        println("Name property: $name")
        println("Age property: $age")
        println("City property: $city")

        // Try iterating through properties
        println("Iterating through vertex properties:")
        val propertiesIterator = vertex.properties<Any>()
        var count = 0
        while (propertiesIterator.hasNext()) {
            val prop = propertiesIterator.next()
            println("  Property ${count++}: key='${prop.key()}', value='${prop.value()}', present=${prop.isPresent()}")
        }

        println("Total properties found: $count")

        // Convert to list for easier debugging
        val propertiesList = vertex.properties<Any>().asSequence().toList()
        println("Properties list size: ${propertiesList.size}")

        // Test assertion
        assertEquals(3, propertiesList.size, "Expected 3 properties but found ${propertiesList.size}")
    }

    /**
     * Debug test for analyzing vertex property types and implementations.
     *
     * This test examines the specific types of property objects returned
     * by the TinkerGraph implementation to help diagnose type-related
     * iteration issues. It provides detailed type information and tests
     * both iterator-based and direct property access methods.
     */
    @Test
    fun testVertexPropertyTypes() {
        val graph = TinkerGraph.open()
        val vertex = graph.addVertex("name", "test")

        println("Testing property types...")

        // Check what type of properties we get
        val properties = vertex.properties<Any>().asSequence().toList()
        properties.forEach { prop ->
            println("Property: ${prop::class.simpleName} - key: ${prop.key()}, value: ${prop.value()}")
        }

        // Try using the property getter method we added
        val nameProperty = vertex.property<String>("name")
        println("Direct property access: ${nameProperty::class.simpleName} - present: ${nameProperty.isPresent()}, value: ${if (nameProperty.isPresent()) nameProperty.value() else "N/A"}")
    }

    /**
     * Debug test for inspecting TinkerVertex internal state and implementation.
     *
     * This test casts vertices to TinkerVertex to access implementation-specific
     * details and internal state. It helps understand how properties are stored
     * and managed internally, which is crucial for debugging iteration issues
     * that might be related to the underlying implementation.
     */
    @Test
    fun testTinkerVertexInternals() {
        val graph = TinkerGraph.open()
        val vertex = graph.addVertex("test", "value")

        // Cast to TinkerVertex to access internals
        if (vertex is org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex) {
            val tinkerVertex = vertex as org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex

            println("TinkerVertex internal state:")
            println("  Keys from vertex.keys(): ${tinkerVertex.keys()}")

            // Try accessing properties directly
            val directProps = tinkerVertex.properties<Any>().asSequence().toList()
            println("  Direct properties count: ${directProps.size}")
            directProps.forEach { prop ->
                println("    ${prop.key()} -> ${prop.value()} (${prop::class.simpleName})")
            }
        }
    }
}
