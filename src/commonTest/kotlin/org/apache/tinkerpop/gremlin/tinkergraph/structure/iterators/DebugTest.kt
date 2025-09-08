package org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

/**
 * Debug test suite for diagnosing vertex property iteration issues.
 *
 * This test class provides detailed debugging capabilities for understanding vertex property
 * iteration behavior and troubleshooting common issues:
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
class DebugTest :
        StringSpec({
            "vertex property debug should iterate through properties correctly" {
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
                    println(
                            "  Property ${count++}: key='${prop.key()}', value='${prop.value()}', present=${prop.isPresent()}"
                    )
                }

                println("Total properties found: $count")

                // Convert to list for easier debugging
                val propertiesList = vertex.properties<Any>().asSequence().toList()
                println("Properties list size: ${propertiesList.size}")

                // Test assertion
                propertiesList.size shouldBe 3
            }

            "vertex property types should be correctly identified" {
                val graph = TinkerGraph.open()
                val vertex = graph.addVertex("name", "test")

                println("Testing property types...")

                // Check what type of properties we get
                val properties = vertex.properties<Any>().asSequence().toList()
                properties.forEach { prop ->
                    println(
                            "Property: ${prop::class.simpleName} - key: ${prop.key()}, value: ${prop.value()}"
                    )
                }

                // Try using the property getter method we added
                val nameProperty = vertex.property<String>("name")
                println(
                        "Direct property access: ${nameProperty::class.simpleName} - present: ${nameProperty.isPresent()}, value: ${if (nameProperty.isPresent()) nameProperty.value() else "N/A"}"
                )
            }

            "TinkerVertex internals should be accessible for debugging" {
                val graph = TinkerGraph.open()
                val vertex = graph.addVertex("test", "value")

                // Cast to TinkerVertex to access internals
                if (vertex is org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex) {
                    val tinkerVertex =
                            vertex as
                                    org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex

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
        })
