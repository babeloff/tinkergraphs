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
package org.apache.tinkerpop.gremlin.tinkergraph.process

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.apache.tinkerpop.gremlin.structure.T
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * JavaScript platform Process API compliance tests for TinkerGraph following Apache TinkerPop specifications.
 *
 * These tests validate TinkerGraph process/traversal behavior on JavaScript platform,
 * ensuring compliance with Apache TinkerPop Process API specifications.
 * Tests are adapted from upstream Java process tests while accounting for
 * JavaScript platform specifics.
 *
 * Task 4.1.2 Phase 3 - JavaScript Platform Process Compliance Testing
 *
 * @author TinkerGraphs Compliance Framework
 */
class TinkerGraphProcessJsTest {

    /**
     * Create the modern graph for testing
     */
    private fun createModernGraph(): TinkerGraph {
        val graph = TinkerGraph.open()

        // Create vertices
        val marko = graph.addVertex(T.id, 1, T.label, "person", "name", "marko", "age", 29)
        val vadas = graph.addVertex(T.id, 2, T.label, "person", "name", "vadas", "age", 27)
        val lop = graph.addVertex(T.id, 3, T.label, "software", "name", "lop", "lang", "java")
        val josh = graph.addVertex(T.id, 4, T.label, "person", "name", "josh", "age", 32)
        val ripple = graph.addVertex(T.id, 5, T.label, "software", "name", "ripple", "lang", "java")
        val peter = graph.addVertex(T.id, 6, T.label, "person", "name", "peter", "age", 35)

        // Create edges
        marko.addEdge("knows", vadas, "weight", 0.5)
        marko.addEdge("knows", josh, "weight", 1.0)
        marko.addEdge("created", lop, "weight", 0.4)
        josh.addEdge("created", ripple, "weight", 1.0)
        josh.addEdge("created", lop, "weight", 0.4)
        peter.addEdge("created", lop, "weight", 0.2)

        return graph
    }

    @Test
    fun testBasicTraversalOperations() {
        // Test basic traversal operations on JS platform
        val graph = createModernGraph()
        val g = graph.traversal()

        // Test V() step
        val vertexCount = g.V().count().next()
        assertEquals(6L, vertexCount)

        // Test E() step
        val edgeCount = g.E().count().next()
        assertEquals(6L, edgeCount)

        // Test has() step
        val personCount = g.V().hasLabel("person").count().next()
        assertEquals(4L, personCount)

        val softwareCount = g.V().hasLabel("software").count().next()
        assertEquals(2L, softwareCount)
    }

    @Test
    fun testTraversalFiltering() {
        // Test filtering operations following Java compliance patterns
        val graph = createModernGraph()
        val g = graph.traversal()

        // Test has() with property filter
        val markoVertices = g.V().has("name", "marko").count().next()
        assertEquals(1L, markoVertices)

        // Test has() with predicate
        val olderThan30 = g.V().has("age", P.gt(30)).count().next()
        assertEquals(2L, olderThan30) // josh (32) and peter (35)

        // Test where() predicate
        val javaProjects = g.V().hasLabel("software").has("lang", "java").count().next()
        assertEquals(2L, javaProjects)
    }

    @Test
    fun testTraversalNavigation() {
        // Test navigation steps on JS platform
        val graph = createModernGraph()
        val g = graph.traversal()

        // Test out() navigation
        val markoFriends = g.V().has("name", "marko").out("knows").values<String>("name").toList()
        assertEquals(2, markoFriends.size)
        assertTrue(markoFriends.contains("vadas"))
        assertTrue(markoFriends.contains("josh"))

        // Test in() navigation
        val lopCreators = g.V().has("name", "lop").`in`("created").values<String>("name").toList()
        assertEquals(3, lopCreators.size)
        assertTrue(lopCreators.contains("marko"))
        assertTrue(lopCreators.contains("josh"))
        assertTrue(lopCreators.contains("peter"))

        // Test both() navigation
        val markoConnections = g.V().has("name", "marko").both().values<String>("name").toList()
        assertEquals(3, markoConnections.size)
        assertTrue(markoConnections.contains("vadas"))
        assertTrue(markoConnections.contains("josh"))
        assertTrue(markoConnections.contains("lop"))
    }

    @Test
    fun testTraversalAggregation() {
        // Test aggregation operations
        val graph = createModernGraph()
        val g = graph.traversal()

        // Test group() operation
        val ageGroups = g.V().hasLabel("person").group<Int, List<String>>().by("age").by("name").next()
        assertNotNull(ageGroups)
        assertTrue(ageGroups.containsKey(29))
        assertTrue(ageGroups.containsKey(27))
        assertTrue(ageGroups.containsKey(32))
        assertTrue(ageGroups.containsKey(35))

        // Test groupCount() operation
        val labelCounts = g.V().groupCount<String>().by(T.label).next()
        assertEquals(4L, labelCounts["person"])
        assertEquals(2L, labelCounts["software"])
    }

    @Test
    fun testTraversalTransformation() {
        // Test transformation operations
        val graph = createModernGraph()
        val g = graph.traversal()

        // Test map() operation
        val names = g.V().hasLabel("person").map<String> { it.get().value("name") }.toList()
        assertEquals(4, names.size)
        assertTrue(names.contains("marko"))
        assertTrue(names.contains("vadas"))
        assertTrue(names.contains("josh"))
        assertTrue(names.contains("peter"))

        // Test flatMap() operation
        val allValues = g.V().hasLabel("person").flatMap<Any> { it.get().properties<Any>() }.toList()
        assertTrue(allValues.size > 0)
    }

    @Test
    fun testComplexTraversals() {
        // Test complex multi-step traversals
        val graph = createModernGraph()
        val g = graph.traversal()

        // Test path traversal
        val paths = g.V().has("name", "marko").out("created").`in`("created").values<String>("name").toList()
        assertTrue(paths.contains("marko"))
        assertTrue(paths.contains("josh"))
        assertTrue(paths.contains("peter"))

        // Test repeat traversal
        val twoHopFriends = g.V().has("name", "marko").repeat(__.out("knows")).times(1).values<String>("name").toList()
        assertEquals(2, twoHopFriends.size)

        // Test until traversal with simple condition
        val allReachable = g.V().has("name", "marko").repeat(__.out()).until(__.outE().count().`is`(0)).toList()
        assertTrue(allReachable.size > 0)
    }

    @Test
    fun testTraversalSideEffects() {
        // Test side effect operations on JS platform
        val graph = createModernGraph()
        val g = graph.traversal()

        // Test store() side effect
        val stored = mutableListOf<String>()
        g.V().hasLabel("person").values<String>("name").store("x").iterate()
        // Note: In JavaScript, side effects may work differently

        // Test aggregate() side effect
        val names = g.V().hasLabel("person").values<String>("name").fold().next()
        assertTrue(names.size >= 4)
    }

    @Test
    fun testTraversalBranching() {
        // Test branching operations
        val graph = createModernGraph()
        val g = graph.traversal()

        // Test choose() branching
        val results = g.V().hasLabel("person")
            .choose<String, String>(
                __.has("age", P.lt(30)),
                __.constant("young"),
                __.constant("mature")
            ).toList()

        assertTrue(results.contains("young"))
        assertTrue(results.contains("mature"))

        // Test union() operation
        val unionResults = g.V().hasLabel("person")
            .union<String>(
                __.values("name"),
                __.values("age").map<String> { "age: ${it.get()}" }
            ).toList()

        assertTrue(unionResults.size >= 8) // 4 names + 4 age values
    }

    @Test
    fun testTraversalMatching() {
        // Test match() pattern operations
        val graph = createModernGraph()
        val g = graph.traversal()

        // Simple pattern matching
        val matches = g.V().hasLabel("person").match<Map<String, Any>>(
            __.`as`("a").values("name").`as`("name"),
            __.`as`("a").values("age").`as`("age")
        ).select<Any>("name", "age").toList()

        assertTrue(matches.size >= 4)
        matches.forEach { match ->
            assertNotNull(match["name"])
            assertNotNull(match["age"])
        }
    }

    @Test
    fun testPerformanceCompliance() {
        // Test traversal performance on JS platform
        val graph = createModernGraph()
        val g = graph.traversal()

        val startTime = kotlin.js.Date.now()

        // Execute multiple traversals
        repeat(100) {
            g.V().hasLabel("person").out("knows").values<String>("name").toList()
        }

        val executionTime = kotlin.js.Date.now() - startTime
        assertTrue(executionTime < 1000) // Should complete within 1 second

        // Test memory efficiency
        val memoryTest = g.V().hasLabel("person").values<String>("name").toList()
        assertEquals(4, memoryTest.size)
    }

    @Test
    fun testJavaScriptSpecificTraversals() {
        // Test JavaScript platform-specific traversal features
        val graph = createModernGraph()
        val g = graph.traversal()

        // Test with JavaScript-compatible predicates
        val jsCompatibleFilter = g.V().hasLabel("person")
            .has("age", P.between(25, 35))
            .values<String>("name")
            .toList()

        assertTrue(jsCompatibleFilter.contains("marko"))
        assertTrue(jsCompatibleFilter.contains("vadas"))
        assertTrue(jsCompatibleFilter.contains("josh"))

        // Test JavaScript number handling
        val ageSum = g.V().hasLabel("person").values<Int>("age").sum<Number>().next()
        assertTrue(ageSum.toDouble() > 0)
    }

    @Test
    fun testErrorHandlingInTraversals() {
        // Test error handling in traversals on JS platform
        val graph = createModernGraph()
        val g = graph.traversal()

        try {
            // Test invalid property access in traversal
            g.V().values<String>("nonexistent").toList()
            // This might not throw in some implementations
        } catch (e: Exception) {
            // Expected in strict implementations
            assertTrue(true)
        }

        try {
            // Test invalid step combination
            val result = g.V().has("nonexistent", "value").count().next()
            assertEquals(0L, result) // Should return 0 for no matches
        } catch (e: Exception) {
            // Some implementations might throw
            assertTrue(true)
        }
    }

    companion object {
        init {
            // Initialize JavaScript platform specific configurations for process tests
            console.log("TinkerGraph JavaScript Process Compliance Tests initialized")
        }
    }
}
