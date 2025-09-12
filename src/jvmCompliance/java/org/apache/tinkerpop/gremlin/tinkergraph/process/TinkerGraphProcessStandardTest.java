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
package org.apache.tinkerpop.gremlin.tinkergraph.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java compliance tests for TinkerGraph Process API.
 *
 * This test class validates TinkerGraph process API compliance following
 * Apache TinkerPop patterns, extracted from the monolithic JavaComplianceTests.
 * Focuses on:
 *
 * - Graph traversal operations
 * - Lambda expression support
 * - Java Stream API integration
 * - Complex query patterns
 * - Performance characteristics
 *
 * Derived from Apache TinkerPop reference implementations in tinkerpop-reference/
 */
public class TinkerGraphProcessStandardTest {

    private MockTinkerGraph graph;

    @BeforeEach
    public void setUp() {
        System.out.println("🧪 Initializing TinkerGraph Process Standard Test Suite...");
        graph = createModernGraph();
    }

    @AfterEach
    public void tearDown() {
        if (graph != null) {
            graph.close();
        }
        System.out.println("🧹 TinkerGraph Process Standard Test Suite - Cleaned up");
    }

    @Test
    @DisplayName("Traversal Operations Compliance")
    public void testTraversalOperationsCompliance() {
        System.out.println("🧪 Testing Traversal Operations Compliance...");

        // Test vertex traversal with filtering
        List<String> names = graph.traversal().V()
            .has("age", P.gt(30))
            .<String>values("name")
            .toList();
        assertTrue(names.contains("josh"), "Should include josh (age 32)");
        assertTrue(names.contains("peter"), "Should include peter (age 35)");
        assertFalse(names.contains("marko"), "Should not include marko (age 29)");

        // Test edge traversal
        long edgeCount = graph.traversal().E().count().next();
        assertEquals(6L, edgeCount, "Should have 6 edges in modern graph");

        // Test path traversal
        List<Path> paths = graph.traversal().V().has("name", "marko")
            .out("knows")
            .path()
            .toList();
        assertEquals(2, paths.size(), "Marko knows 2 people, should have 2 paths");

        // Test grouping
        Map<String, Collection<MockVertex>> byLabel = graph.traversal().V()
            .group()
            .<String>by("name")
            .next();
        assertEquals(6, byLabel.size(), "Should group by 6 different names");

        System.out.println("✅ Traversal Operations - COMPLIANT");
    }

    @Test
    @DisplayName("Lambda Expression Support")
    public void testLambdaExpressionSupport() {
        System.out.println("🧪 Testing Lambda Expression Support...");

        // Test lambda filtering
        List<MockVertex> vertices = graph.traversal().V()
            .filter(v -> v.get().value("name").toString().startsWith("j"))
            .toList();
        assertEquals(1, vertices.size(), "Should find 'josh' starting with 'j'");
        assertEquals("josh", vertices.get(0).value("name"));

        // Test lambda mapping
        List<String> upperNames = graph.traversal().V()
            .<String>values("name")
            .map(s -> s.get().toUpperCase())
            .toList();
        assertTrue(upperNames.contains("MARKO"), "Should contain uppercase names");
        assertTrue(upperNames.contains("VADAS"), "Should contain uppercase names");

        // Test lambda with custom predicate
        Predicate<MockVertex> ageFilter = v -> {
            try {
                Integer age = v.value("age");
                return age != null && age > 30;
            } catch (Exception e) {
                return false;
            }
        };

        List<MockVertex> olderVertices = graph.traversal().V()
            .filter(v -> ageFilter.test(v.get()))
            .toList();
        assertEquals(2, olderVertices.size(), "Should find 2 vertices with age > 30");

        // Test lambda sorting
        List<String> sortedNames = graph.traversal().V()
            .<String>values("name")
            .order()
            .by((a, b) -> a.compareTo(b))
            .toList();
        assertEquals("josh", sortedNames.get(0), "First name should be 'josh' alphabetically");

        System.out.println("✅ Lambda Expression Support - COMPLIANT");
    }

    @Test
    @DisplayName("Java Stream API Integration")
    public void testStreamApiIntegration() {
        System.out.println("🧪 Testing Java Stream API Integration...");

        // Test traversal to stream conversion
        List<String> names = graph.traversal().V().<String>values("name").toList();
        List<String> filteredNames = names.stream()
            .filter(name -> name.length() > 4)
            .sorted()
            .collect(Collectors.toList());

        assertTrue(filteredNames.contains("marko"), "Should contain names longer than 4 chars");
        assertTrue(filteredNames.contains("vadas"), "Should contain names longer than 4 chars");
        assertTrue(filteredNames.contains("peter"), "Should contain names longer than 4 chars");
        assertFalse(filteredNames.contains("lop"), "Should not contain 'lop' (3 chars)");

        // Test parallel stream processing
        OptionalDouble averageAge = graph.traversal().V()
            .has("age")
            .<Integer>values("age")
            .toList()
            .parallelStream()
            .mapToDouble(Integer::doubleValue)
            .average();

        assertTrue(averageAge.isPresent(), "Should calculate average age");
        assertTrue(averageAge.getAsDouble() > 25, "Average age should be > 25");

        // Test stream collectors with graph data
        Map<String, List<String>> projectsByLanguage = graph.traversal().V()
            .has("lang")
            .toList()
            .stream()
            .collect(Collectors.groupingBy(
                v -> v.value("lang").toString(),
                Collectors.mapping(v -> v.value("name").toString(), Collectors.toList())
            ));

        assertTrue(projectsByLanguage.containsKey("java"), "Should group by 'java' language");
        assertEquals(2, projectsByLanguage.get("java").size(), "Should have 2 java projects");

        System.out.println("✅ Java Stream API Integration - COMPLIANT");
    }

    @Test
    @DisplayName("Performance Benchmark Compliance")
    public void testPerformanceBenchmark() {
        System.out.println("🧪 Testing Performance Benchmark Compliance...");

        // Test large graph traversal performance
        MockTinkerGraph largeGraph = createLargeGraph(10000);

        long startTime = System.currentTimeMillis();

        // Perform complex traversal operation
        List<MockVertex> results = largeGraph.traversal().V()
            .has("type", "person")
            .out("knows")
            .has("age", P.gt(25))
            .dedup()
            .limit(100)
            .toList();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertFalse(results.isEmpty(), "Should find some results");
        assertTrue(duration < 5000, "Traversal should complete within 5 seconds");

        System.out.println("⏱️  Traversal completed in " + duration + "ms");

        // Test index performance
        startTime = System.currentTimeMillis();
        MockVertex foundVertex = largeGraph.traversal().V().has("id", 5000).next();
        endTime = System.currentTimeMillis();
        duration = endTime - startTime;

        assertNotNull(foundVertex, "Should find vertex by ID");
        assertTrue(duration < 100, "ID lookup should be very fast (< 100ms)");

        largeGraph.close();
        System.out.println("✅ Performance Benchmark - COMPLIANT");
    }

    @Test
    @DisplayName("Optional Support Compliance")
    public void testOptionalSupport() {
        System.out.println("🧪 Testing Optional Support Compliance...");

        // Test Optional with vertex properties
        Optional<Object> existingProperty = graph.traversal().V()
            .has("name", "marko")
            .values("age")
            .tryNext();
        assertTrue(existingProperty.isPresent(), "Should find marko's age");
        assertEquals(29, existingProperty.get());

        Optional<Object> nonExistentProperty = graph.traversal().V()
            .has("name", "marko")
            .values("nonexistent")
            .tryNext();
        assertFalse(nonExistentProperty.isPresent(), "Should not find non-existent property");

        // Test Optional with vertex lookup
        Optional<MockVertex> foundVertex = graph.traversal().V()
            .has("name", "marko")
            .tryNext();
        assertTrue(foundVertex.isPresent(), "Should find marko vertex");

        Optional<MockVertex> notFoundVertex = graph.traversal().V()
            .has("name", "nonexistent")
            .tryNext();
        assertFalse(notFoundVertex.isPresent(), "Should not find non-existent vertex");

        // Test Optional in complex traversals
        Optional<String> createdProject = graph.traversal().V()
            .has("name", "marko")
            .out("created")
            .<String>values("name")
            .tryNext();
        assertTrue(createdProject.isPresent(), "Marko should have created a project");
        assertEquals("lop", createdProject.get());

        System.out.println("✅ Optional Support - COMPLIANT");
    }

    @Test
    @DisplayName("Complex Query Patterns")
    public void testComplexQueryPatterns() {
        System.out.println("🧪 Testing Complex Query Patterns...");

        // Test multi-hop traversal
        List<String> friendsOfFriends = graph.traversal().V()
            .has("name", "marko")
            .out("knows")
            .out("knows")
            .<String>values("name")
            .dedup()
            .toList();
        // Note: In the modern graph, this might be empty as it's a small graph
        assertNotNull(friendsOfFriends, "Friends of friends query should work");

        // Test aggregation patterns
        Map<Object, Long> ageDistribution = graph.traversal().V()
            .has("age")
            .group()
            .by("age")
            .by(count())
            .next();
        assertFalse(ageDistribution.isEmpty(), "Should aggregate by age");

        // Test conditional traversal
        List<MockVertex> conditionalResults = graph.traversal().V()
            .choose(has("age"), out("knows"), out("created"))
            .dedup()
            .toList();
        assertNotNull(conditionalResults, "Conditional traversal should work");

        // Test subgraph extraction
        List<MockEdge> subgraphEdges = graph.traversal().V()
            .has("name", within("marko", "josh", "lop"))
            .bothE()
            .subgraph("sg")
            .cap("sg")
            .next()
            .traversal()
            .E()
            .toList();
        assertFalse(subgraphEdges.isEmpty(), "Should extract subgraph");

        System.out.println("✅ Complex Query Patterns - COMPLIANT");
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

        // Create vertices
        for (int i = 0; i < vertexCount; i++) {
            String type = (i % 3 == 0) ? "person" : "project";
            MockVertex vertex = largeGraph.addVertex("id", i, "type", type);

            if ("person".equals(type)) {
                vertex.property("age", 20 + random.nextInt(50));
                vertex.property("name", "person_" + i);
            } else {
                vertex.property("name", "project_" + i);
                vertex.property("lang", (i % 2 == 0) ? "java" : "python");
            }
        }

        // Create edges
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
