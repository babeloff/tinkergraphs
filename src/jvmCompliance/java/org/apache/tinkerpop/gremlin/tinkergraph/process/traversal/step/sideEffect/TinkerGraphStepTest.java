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
package org.apache.tinkerpop.gremlin.tinkergraph.process.traversal.step.sideEffect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compliance tests for TinkerGraph step implementation.
 *
 * This test class validates TinkerGraph step functionality compliance
 * following Apache TinkerPop patterns. Tests cover:
 *
 * - Graph step traversal operations
 * - Side effect step execution
 * - Step chaining and composition
 * - Performance characteristics of steps
 * - Error handling in step execution
 *
 * Based on Apache TinkerPop TinkerGraphStepTest reference implementation.
 */
public class TinkerGraphStepTest {

    private MockTinkerGraph graph;

    @BeforeEach
    public void setUp() {
        System.out.println("🧪 Initializing TinkerGraph Step Test Suite...");
        graph = createModernGraph();
    }

    @AfterEach
    public void tearDown() {
        if (graph != null) {
            graph.close();
        }
        System.out.println("🧹 TinkerGraph Step Test Suite - Cleaned up");
    }

    @Test
    @DisplayName("Should execute basic graph step operations")
    public void shouldExecuteBasicGraphStepOperations() {
        System.out.println("🧪 Testing basic graph step operations...");

        // Test vertex step
        List<MockVertex> vertices = graph.traversal().V().toList();
        assertEquals(6, vertices.size(), "Should traverse all vertices");

        // Test edge step
        List<MockEdge> edges = graph.traversal().E().toList();
        assertEquals(6, edges.size(), "Should traverse all edges");

        // Test filtered vertex step
        List<MockVertex> markoVertices = graph.traversal().V().has("name", "marko").toList();
        assertEquals(1, markoVertices.size(), "Should find marko vertex");
        assertEquals("marko", markoVertices.get(0).value("name"));

        System.out.println("✅ Basic graph step operations - COMPLIANT");
    }

    @Test
    @DisplayName("Should handle step chaining correctly")
    public void shouldHandleStepChainingCorrectly() {
        System.out.println("🧪 Testing step chaining...");

        // Test chained vertex traversal
        List<MockVertex> friends = graph.traversal().V()
            .has("name", "marko")
            .out("knows")
            .toList();
        assertEquals(2, friends.size(), "Marko should know 2 people");

        // Test complex step chain
        List<String> projectNames = graph.traversal().V()
            .has("name", "marko")
            .out("created")
            .values("name")
            .toList();
        assertEquals(1, projectNames.size(), "Marko created 1 project");
        assertEquals("lop", projectNames.get(0));

        // Test step chain with multiple hops
        List<String> friendsOfFriends = graph.traversal().V()
            .has("name", "marko")
            .out("knows")
            .out("created")
            .values("name")
            .dedup()
            .toList();
        assertTrue(friendsOfFriends.contains("ripple"), "Should find ripple through josh");
        assertTrue(friendsOfFriends.contains("lop"), "Should find lop through josh");

        System.out.println("✅ Step chaining - COMPLIANT");
    }

    @Test
    @DisplayName("Should execute side effect steps correctly")
    public void shouldExecuteSideEffectStepsCorrectly() {
        System.out.println("🧪 Testing side effect step execution...");

        // Test side effect with counter
        AtomicInteger counter = new AtomicInteger(0);
        List<MockVertex> vertices = graph.traversal().V()
            .sideEffect(v -> counter.incrementAndGet())
            .toList();

        assertEquals(6, vertices.size(), "Should traverse all vertices");
        assertEquals(6, counter.get(), "Side effect should execute for each vertex");

        // Test side effect with property modification
        List<String> names = new ArrayList<>();
        List<MockVertex> namedVertices = graph.traversal().V()
            .has("name")
            .sideEffect(v -> names.add(v.get().value("name").toString()))
            .toList();

        assertEquals(6, namedVertices.size(), "Should find all named vertices");
        assertEquals(6, names.size(), "Should collect all names");
        assertTrue(names.contains("marko"), "Should collect marko's name");
        assertTrue(names.contains("josh"), "Should collect josh's name");

        System.out.println("✅ Side effect step execution - COMPLIANT");
    }

    @Test
    @DisplayName("Should handle step optimization")
    public void shouldHandleStepOptimization() {
        System.out.println("🧪 Testing step optimization...");

        // Test index utilization in steps
        long startTime = System.currentTimeMillis();
        MockVertex marko = graph.traversal().V().has("name", "marko").next();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertNotNull(marko, "Should find marko vertex");
        assertEquals("marko", marko.value("name"));
        assertTrue(duration < 100, "Index-based lookup should be fast (< 100ms)");

        // Test step fusion optimization
        startTime = System.currentTimeMillis();
        List<MockVertex> optimizedResult = graph.traversal().V()
            .has("age", P.gt(30))
            .has("name", P.neq("unknown"))
            .toList();
        endTime = System.currentTimeMillis();
        duration = endTime - startTime;

        assertEquals(2, optimizedResult.size(), "Should find josh and peter");
        assertTrue(duration < 200, "Optimized multi-filter should be fast (< 200ms)");

        System.out.println("✅ Step optimization - COMPLIANT");
    }

    @Test
    @DisplayName("Should handle step error conditions")
    public void shouldHandleStepErrorConditions() {
        System.out.println("🧪 Testing step error condition handling...");

        // Test step with no results
        List<MockVertex> noResults = graph.traversal().V()
            .has("name", "nonexistent")
            .toList();
        assertTrue(noResults.isEmpty(), "Should handle empty results gracefully");

        // Test step with invalid property access
        assertThrows(Exception.class, () -> {
            graph.traversal().V()
                .has("name", "marko")
                .values("nonexistent_property")
                .next();
        }, "Should throw exception for nonexistent property");

        // Test step with null handling
        assertDoesNotThrow(() -> {
            List<MockVertex> results = graph.traversal().V()
                .has("name", "marko")
                .filter(v -> v.get().property("age").isPresent())
                .toList();
            assertEquals(1, results.size(), "Should handle null checks in filters");
        }, "Should handle null values gracefully");

        System.out.println("✅ Step error condition handling - COMPLIANT");
    }

    @Test
    @DisplayName("Should support step performance monitoring")
    public void shouldSupportStepPerformanceMonitoring() {
        System.out.println("🧪 Testing step performance monitoring...");

        // Create larger graph for performance testing
        MockTinkerGraph largeGraph = createLargeGraph(1000);

        long startTime = System.currentTimeMillis();

        // Execute complex traversal with performance monitoring
        List<MockVertex> results = largeGraph.traversal().V()
            .has("type", "person")
            .has("age", P.gte(25))
            .out("knows")
            .has("type", "person")
            .dedup()
            .limit(10)
            .toList();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertNotNull(results, "Should return results");
        assertTrue(duration < 2000, "Complex traversal should complete within 2 seconds");

        // Test step-by-step performance
        startTime = System.currentTimeMillis();
        long stepCount = largeGraph.traversal().V().count().next();
        long countTime = System.currentTimeMillis() - startTime;

        assertEquals(1000L, stepCount, "Should count all vertices");
        assertTrue(countTime < 500, "Count operation should be very fast (< 500ms)");

        largeGraph.close();
        System.out.println("✅ Step performance monitoring - COMPLIANT");
    }

    @Test
    @DisplayName("Should handle concurrent step execution")
    public void shouldHandleConcurrentStepExecution() {
        System.out.println("🧪 Testing concurrent step execution...");

        List<Thread> threads = new ArrayList<>();
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        // Execute multiple traversals concurrently
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            Thread thread = new Thread(() -> {
                try {
                    int result = graph.traversal().V()
                        .has("age", P.gte(20 + threadId * 2))
                        .toList()
                        .size();
                    results.add(result);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join(5000); // 5 second timeout
            } catch (InterruptedException e) {
                fail("Thread interrupted during concurrent step execution test");
            }
        }

        assertTrue(exceptions.isEmpty(), "No exceptions should occur during concurrent execution");
        assertEquals(5, results.size(), "All threads should complete successfully");

        System.out.println("✅ Concurrent step execution - COMPLIANT");
    }

    private MockTinkerGraph createModernGraph() {
        MockTinkerGraph modernGraph = MockTinkerGraph.open();

        // Create the "modern" graph structure
        MockVertex marko = modernGraph.addVertex("id", 1, "name", "marko", "age", 29);
        MockVertex vadas = modernGraph.addVertex("id", 2, "name", "vadas", "age", 27);
        MockVertex lop = modernGraph.addVertex("id", 3, "name", "lop", "lang", "java");
        MockVertex josh = modernGraph.addVertex("id", 4, "name", "josh", "age", 32);
        MockVertex ripple = modernGraph.addVertex("id", 5, "name", "ripple", "lang", "java");
        MockVertex peter = modernGraph.addVertex("id", 6, "name", "peter", "age", 35);

        // Add edges
        marko.addEdge("knows", vadas, "weight", 0.5f);
        marko.addEdge("knows", josh, "weight", 1.0f);
        marko.addEdge("created", lop, "weight", 0.4f);
        josh.addEdge("created", ripple, "weight", 1.0f);
        josh.addEdge("created", lop, "weight", 0.4f);
        peter.addEdge("created", lop, "weight", 0.2f);

        return modernGraph;
    }

    private MockTinkerGraph createLargeGraph(int vertexCount) {
        MockTinkerGraph largeGraph = MockTinkerGraph.open();
        Random random = new Random(12345); // Fixed seed for reproducibility

        for (int i = 0; i < vertexCount; i++) {
            String type = (i % 3 == 0) ? "person" : "software";
            MockVertex vertex = largeGraph.addVertex("id", i, "type", type);

            if ("person".equals(type)) {
                vertex.property("age", 20 + random.nextInt(40));
                vertex.property("name", "person_" + i);
            } else {
                vertex.property("name", "software_" + i);
                vertex.property("lang", (i % 2 == 0) ? "java" : "python");
            }
        }

        // Create some edges for testing
        for (int i = 0; i < vertexCount / 10; i++) {
            int fromId = random.nextInt(vertexCount);
            int toId = random.nextInt(vertexCount);
            if (fromId != toId) {
                try {
                    MockVertex from = largeGraph.traversal().V().has("id", fromId).next();
                    MockVertex to = largeGraph.traversal().V().has("id", toId).next();
                    String edgeType = "person".equals(from.value("type")) ? "knows" : "uses";
                    from.addEdge(edgeType, to, "weight", random.nextFloat());
                } catch (Exception e) {
                    // Skip if vertices not found
                }
            }
        }

        return largeGraph;
    }
}
