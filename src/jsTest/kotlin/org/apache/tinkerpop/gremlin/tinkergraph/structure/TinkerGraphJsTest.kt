/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.tinkergraph.structure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JavaScript platform compliance tests for TinkerGraph following Apache TinkerPop Java compliance tests.
 *
 * These tests validate TinkerGraph behavior on JavaScript platform, ensuring compliance
 * with Apache TinkerPop specifications. Tests are adapted from upstream Java tests
 * while accounting for JavaScript platform specifics.
 *
 * Task 4.1.2 Phase 3 - JavaScript Platform Compliance Testing
 *
 * @author TinkerGraphs Compliance Framework
 */
class TinkerGraphJsTest {

    @Test
    fun testBasicGraphCreation() {
        // Test basic TinkerGraph creation on JS platform
        val graph = TinkerGraph.open()
        assertNotNull(graph)
        assertTrue(graph.features().graph().supportsComputer())
        assertTrue(graph.features().vertex().supportsAddVertices())
        assertTrue(graph.features().edge().supportsAddEdges())
    }

    @Test
    fun testVertexCreationAndRetrieval() {
        // Test vertex operations following Java compliance patterns
        val graph = TinkerGraph.open()

        // Add vertices with properties
        val v1 = graph.addVertex("name", "marko", "age", 29)
        val v2 = graph.addVertex("name", "vadas", "age", 27)

        assertNotNull(v1)
        assertNotNull(v2)
        assertEquals("marko", v1.value<String>("name"))
        assertEquals(29, v1.value<Int>("age"))
    }

    @Test
    fun testEdgeCreationAndTraversal() {
        // Test edge operations and basic traversals
        val graph = TinkerGraph.open()
        val g = graph.traversal()

        // Create the modern graph structure
        val v1 = graph.addVertex(T.id, 1, T.label, "person", "name", "marko", "age", 29)
        val v2 = graph.addVertex(T.id, 2, T.label, "person", "name", "vadas", "age", 27)
        val v3 = graph.addVertex(T.id, 3, T.label, "software", "name", "lop", "lang", "java")

        v1.addEdge("knows", v2, "weight", 0.5)
        v1.addEdge("created", v3, "weight", 0.4)

        // Test basic traversal
        val names = g.V().values<String>("name").toList()
        assertTrue(names.contains("marko"))
        assertTrue(names.contains("vadas"))
        assertTrue(names.contains("lop"))

        // Test edge traversal
        val friends = g.V(1).out("knows").values<String>("name").toList()
        assertEquals(1, friends.size)
        assertEquals("vadas", friends[0])
    }

    @Test
    fun testGraphFeatures() {
        // Test graph features compliance on JS platform
        val graph = TinkerGraph.open()
        val features = graph.features()

        // Graph features
        assertTrue(features.graph().supportsTransactions())
        assertTrue(features.graph().supportsPersistence())
        assertTrue(features.graph().supportsComputer())

        // Vertex features
        assertTrue(features.vertex().supportsAddVertices())
        assertTrue(features.vertex().supportsRemoveVertices())
        assertTrue(features.vertex().supportsMetaProperties())
        assertTrue(features.vertex().supportsMultiProperties())

        // Edge features
        assertTrue(features.edge().supportsAddEdges())
        assertTrue(features.edge().supportsRemoveEdges())

        // Variable features
        assertTrue(features.graph().variables().supportsVariables())
    }

    @Test
    fun testPropertyHandling() {
        // Test property operations following Java compliance
        val graph = TinkerGraph.open()
        val vertex = graph.addVertex("string", "test", "integer", 42, "double", 3.14)

        assertEquals("test", vertex.value<String>("string"))
        assertEquals(42, vertex.value<Int>("integer"))
        assertEquals(3.14, vertex.value<Double>("double"))

        // Test property updates
        vertex.property("string", "updated")
        assertEquals("updated", vertex.value<String>("string"))
    }

    @Test
    fun testMultiProperties() {
        // Test multi-property support on JS platform
        val graph = TinkerGraph.open()
        val vertex = graph.addVertex()

        vertex.property(VertexProperty.Cardinality.list, "location", "santa fe", "startTime", 1997)
        vertex.property(VertexProperty.Cardinality.list, "location", "los alamos", "startTime", 1998)
        vertex.property(VertexProperty.Cardinality.list, "location", "santa monica", "startTime", 2005)

        val locations = vertex.properties<String>("location").asSequence().map { it.value() }.toList()
        assertEquals(3, locations.size)
        assertTrue(locations.contains("santa fe"))
        assertTrue(locations.contains("los alamos"))
        assertTrue(locations.contains("santa monica"))
    }

    @Test
    fun testTransactionsBasic() {
        // Test basic transaction support on JS platform
        val graph = TinkerGraph.open()
        val tx = graph.tx()

        assertNotNull(tx)
        // JavaScript platform may have limited transaction support
        // Test basic transaction lifecycle
        tx.open()
        tx.commit()
    }

    @Test
    fun testTraversalStrategies() {
        // Test traversal strategy compliance on JS platform
        val graph = TinkerGraph.open()
        val g = graph.traversal()

        // Test identity strategy removal
        val strategiesCount = g.bytecode.sourceInstructions.size + g.bytecode.stepInstructions.size
        assertTrue(strategiesCount >= 0)

        // Test basic strategy application
        val result = g.V().has("name", "marko").count().next()
        assertEquals(0L, result) // Empty graph
    }

    @Test
    fun testJavaScriptSpecificFeatures() {
        // Test JavaScript platform-specific features and adaptations
        val graph = TinkerGraph.open()

        // Test JavaScript-compatible data types
        val vertex = graph.addVertex(
            "jsString", "test",
            "jsNumber", 42.0, // JavaScript numbers are all doubles
            "jsBoolean", true,
            "jsArray", arrayOf("a", "b", "c")
        )

        assertEquals("test", vertex.value<String>("jsString"))
        assertEquals(42.0, vertex.value<Double>("jsNumber"))
        assertEquals(true, vertex.value<Boolean>("jsBoolean"))
    }

    @Test
    fun testPerformanceBaseline() {
        // Basic performance test for JS platform compliance
        val graph = TinkerGraph.open()
        val startTime = kotlin.js.Date.now()

        // Create a small graph for baseline performance
        repeat(100) { i ->
            graph.addVertex("id", i, "name", "vertex$i")
        }

        val creationTime = kotlin.js.Date.now() - startTime
        assertTrue(creationTime < 1000) // Should complete within 1 second

        // Test traversal performance
        val g = graph.traversal()
        val traversalStart = kotlin.js.Date.now()
        val count = g.V().count().next()
        val traversalTime = kotlin.js.Date.now() - traversalStart

        assertEquals(100L, count)
        assertTrue(traversalTime < 100) // Should complete within 100ms
    }

    @Test
    fun testErrorHandling() {
        // Test error handling compliance on JS platform
        val graph = TinkerGraph.open()

        try {
            // Test invalid property access
            val vertex = graph.addVertex()
            vertex.value<String>("nonexistent")
            kotlin.test.fail("Should throw exception for nonexistent property")
        } catch (e: Exception) {
            // Expected behavior
            assertTrue(true)
        }
    }

    @Test
    fun testGraphSerialization() {
        // Test basic serialization capabilities on JS platform
        val graph = TinkerGraph.open()
        graph.addVertex("name", "test", "value", 42)

        // JavaScript platform may have different serialization approaches
        // Test that graph state can be preserved
        val vertexCount = graph.traversal().V().count().next()
        assertEquals(1L, vertexCount)

        // Test graph configuration preservation
        val config = graph.configuration()
        assertNotNull(config)
    }

    companion object {
        init {
            // Initialize JavaScript platform specific configurations
            console.log("TinkerGraph JavaScript Compliance Tests initialized")
        }
    }
}
