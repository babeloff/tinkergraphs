package debug

import org.apache.tinkerpop.gremlin.tinkergraph.javascript.TinkerGraphJSAdapter

/**
 * Simple debug script to understand JavaScript test failures
 */
fun debugJSAdapter() {
    println("=== Debug JS Adapter ===")

    try {
        val adapter = TinkerGraphJSAdapter.open()
        println("✓ Adapter created successfully")

        // Test 1: Basic vertex creation
        println("\n--- Test 1: Basic vertex creation ---")
        val alice = adapter.addVertex("person")
        println("✓ Created vertex: ${alice}")
        println("  - ID: ${alice.id()}")
        println("  - Label: ${alice.label()}")

        // Test 2: Add property
        println("\n--- Test 2: Add property ---")
        alice.property("name", "Alice")
        val nameProperty = alice.property<String>("name")
        println("✓ Added name property")
        println("  - Property present: ${nameProperty.isPresent()}")
        if (nameProperty.isPresent()) {
            println("  - Property value: ${nameProperty.value()}")
        }

        // Test 3: Get vertices
        println("\n--- Test 3: Get vertices ---")
        val vertices = adapter.vertices()
        println("✓ Got vertices array")
        println("  - Vertices count: ${vertices.size}")
        vertices.forEachIndexed { index, vertex ->
            println("  - Vertex $index: ID=${vertex.id()}, Label=${vertex.label()}")
        }

        // Test 4: Find by property
        println("\n--- Test 4: Find by property ---")
        val found = adapter.findVerticesByProperty("name", "Alice")
        println("✓ Search completed")
        println("  - Found count: ${found.size}")
        found.forEachIndexed { index, vertex ->
            println("  - Found $index: ID=${vertex.id()}, Label=${vertex.label()}")
            val prop = vertex.property<String>("name")
            if (prop.isPresent()) {
                println("    - Name: ${prop.value()}")
            }
        }

        // Test 5: JSON export
        println("\n--- Test 5: JSON export ---")
        val json = adapter.toJSON()
        println("✓ JSON export completed")
        println("  - JSON length: ${json.length}")
        println("  - JSON contains 'Alice': ${json.contains("Alice")}")
        println("  - JSON preview: ${json.take(200)}...")

    } catch (e: Exception) {
        println("✗ Error occurred: ${e.message}")
        e.printStackTrace()
    }

    println("\n=== Debug Complete ===")
}

fun main() {
    debugJSAdapter()
}
