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
package org.apache.tinkerpop.gremlin.tinkergraph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple compliance tests for TinkerGraph basic functionality.
 *
 * This test class provides basic validation of TinkerGraph core functionality
 * following Apache TinkerPop patterns. Tests cover:
 *
 * - Basic graph creation and manipulation
 * - Simple vertex and edge operations
 * - Basic property handling
 * - Elementary traversal operations
 * - Simple query patterns
 * - Basic error handling
 *
 * Based on Apache TinkerPop SimpleTinkerGraphJavaTest reference implementation.
 */
public class SimpleTinkerGraphTest {

    private MockTinkerGraph graph;

    @BeforeEach
    public void setUp() {
        System.out.println("🧪 Initializing Simple TinkerGraph Test Suite...");
        graph = MockTinkerGraph.open();
    }

    @AfterEach
    public void tearDown() {
        if (graph != null) {
            graph.close();
        }
        System.out.println("🧹 Simple TinkerGraph Test Suite - Cleaned up");
    }

    @Test
    @DisplayName("Should create basic graph structure")
    public void shouldCreateBasicGraphStructure() {
        System.out.println("🧪 Testing basic graph creation...");

        // Test empty graph
        assertEquals(0L, graph.traversal().V().count().next(), "Empty graph should have no vertices");
        assertEquals(0L, graph.traversal().E().count().next(), "Empty graph should have no edges");

        // Create simple vertex
        MockVertex vertex = graph.addVertex("name", "test");
        assertNotNull(vertex, "Vertex should be created");
        assertEquals("test", vertex.value("name"), "Vertex should have correct property");

        // Verify graph contains vertex
        assertEquals(1L, graph.traversal().V().count().next(), "Graph should have one vertex");

        System.out.println("✅ Basic graph creation - COMPLIANT");
    }

    @Test
    @DisplayName("Should handle vertex operations")
    public void shouldHandleVertexOperations() {
        System.out.println("🧪 Testing vertex operations...");

        // Create vertices with different properties
        MockVertex v1 = graph.addVertex("id", 1, "name", "alice", "age", 25);
        MockVertex v2 = graph.addVertex("id", 2, "name", "bob", "age", 30);
        MockVertex v3 = graph.addVertex("id", 3, "name", "charlie");

        // Test vertex retrieval
        assertEquals("alice", v1.value("name"));
        assertEquals(25, v1.value("age"));
        assertEquals("bob", v2.value("name"));
        assertEquals(30, v2.value("age"));
        assertEquals("charlie", v3.value("name"));

        // Test property existence
        assertTrue(v1.property("age").isPresent(), "V1 should have age property");
        assertTrue(v2.property("age").isPresent(), "V2 should have age property");
        assertFalse(v3.property("age").isPresent(), "V3 should not have age property");

        // Test vertex count
        assertEquals(3L, graph.traversal().V().count().next(), "Should have 3 vertices");

        System.out.println("✅ Vertex operations - COMPLIANT");
    }

    @Test
    @DisplayName("Should handle edge operations")
    public void shouldHandleEdgeOperations() {
        System.out.println("🧪 Testing edge operations...");

        // Create vertices
        MockVertex alice = graph.addVertex("name", "alice");
        MockVertex bob = graph.addVertex("name", "bob");
        MockVertex charlie = graph.addVertex("name", "charlie");

        // Create edges
        MockEdge e1 = alice.addEdge("knows", bob, "since", 2020);
        MockEdge e2 = alice.addEdge("likes", charlie);
        MockEdge e3 = bob.addEdge("works_with", charlie, "project", "TinkerGraph");

        // Test edge properties
        assertEquals("knows", e1.label());
        assertEquals(2020, e1.value("since"));
        assertEquals("likes", e2.label());
        assertEquals("works_with", e3.label());
        assertEquals("TinkerGraph", e3.value("project"));

        // Test edge connectivity
        assertEquals(alice, e1.outVertex());
        assertEquals(bob, e1.inVertex());
        assertEquals(alice, e2.outVertex());
        assertEquals(charlie, e2.inVertex());

        // Test edge count
        assertEquals(3L, graph.traversal().E().count().next(), "Should have 3 edges");

        System.out.println("✅ Edge operations - COMPLIANT");
    }

    @Test
    @DisplayName("Should perform basic traversals")
    public void shouldPerformBasicTraversals() {
        System.out.println("🧪 Testing basic traversals...");

        // Create simple graph
        MockVertex alice = graph.addVertex("name", "alice", "age", 25);
        MockVertex bob = graph.addVertex("name", "bob", "age", 30);
        MockVertex charlie = graph.addVertex("name", "charlie", "age", 35);

        alice.addEdge("knows", bob);
        alice.addEdge("knows", charlie);
        bob.addEdge("likes", charlie);

        // Test vertex traversal
        List<String> names = graph.traversal().V()
            .values("name")
            .toList();
        assertEquals(3, names.size(), "Should traverse all vertex names");
        assertTrue(names.contains("alice"));
        assertTrue(names.contains("bob"));
        assertTrue(names.contains("charlie"));

        // Test filtered traversal
        List<MockVertex> olderThan28 = graph.traversal().V()
            .has("age", P.gt(28))
            .toList();
        assertEquals(2, olderThan28.size(), "Should find 2 vertices with age > 28");

        // Test edge traversal
        List<MockVertex> aliceFriends = graph.traversal().V()
            .has("name", "alice")
            .out("knows")
            .toList();
        assertEquals(2, aliceFriends.size(), "Alice should know 2 people");

        System.out.println("✅ Basic traversals - COMPLIANT");
    }

    @Test
    @DisplayName("Should handle property operations")
    public void shouldHandlePropertyOperations() {
        System.out.println("🧪 Testing property operations...");

        MockVertex vertex = graph.addVertex("name", "test");

        // Test property setting and getting
        vertex.property("age", 25);
        vertex.property("city", "New York");
        vertex.property("active", true);

        assertEquals("test", vertex.value("name"));
        assertEquals(25, vertex.value("age"));
        assertEquals("New York", vertex.value("city"));
        assertEquals(true, vertex.value("active"));

        // Test property modification
        vertex.property("age", 26);
        assertEquals(26, vertex.value("age"));

        // Test property removal
        vertex.property("city").remove();
        assertFalse(vertex.property("city").isPresent(), "City property should be removed");

        // Test property iteration
        Set<String> propertyKeys = new HashSet<>();
        vertex.properties().forEachRemaining(p -> propertyKeys.add(p.key()));
        assertTrue(propertyKeys.contains("name"));
        assertTrue(propertyKeys.contains("age"));
        assertTrue(propertyKeys.contains("active"));
        assertFalse(propertyKeys.contains("city"));

        System.out.println("✅ Property operations - COMPLIANT");
    }

    @Test
    @DisplayName("Should handle data types correctly")
    public void shouldHandleDataTypesCorrectly() {
        System.out.println("🧪 Testing data type handling...");

        MockVertex vertex = graph.addVertex("id", 1);

        // Test various data types
        vertex.property("stringVal", "hello world");
        vertex.property("intVal", 42);
        vertex.property("longVal", 123456789L);
        vertex.property("doubleVal", 3.14159);
        vertex.property("floatVal", 2.71f);
        vertex.property("booleanVal", true);
        vertex.property("byteVal", (byte) 255);

        // Verify data type preservation
        assertEquals("hello world", vertex.value("stringVal"));
        assertEquals(Integer.valueOf(42), vertex.value("intVal"));
        assertEquals(Long.valueOf(123456789L), vertex.value("longVal"));
        assertEquals(Double.valueOf(3.14159), vertex.value("doubleVal"));
        assertEquals(Float.valueOf(2.71f), vertex.value("floatVal"));
        assertEquals(Boolean.TRUE, vertex.value("booleanVal"));
        assertEquals(Byte.valueOf((byte) 255), vertex.value("byteVal"));

        // Test collection types
        List<String> listVal = Arrays.asList("a", "b", "c");
        vertex.property("listVal", listVal);
        assertEquals(listVal, vertex.value("listVal"));

        System.out.println("✅ Data type handling - COMPLIANT");
    }

    @Test
    @DisplayName("Should handle simple queries")
    public void shouldHandleSimpleQueries() {
        System.out.println("🧪 Testing simple queries...");

        // Create test data
        MockVertex person1 = graph.addVertex("type", "person", "name", "alice", "age", 25);
        MockVertex person2 = graph.addVertex("type", "person", "name", "bob", "age", 30);
        MockVertex software1 = graph.addVertex("type", "software", "name", "project1", "lang", "java");
        MockVertex software2 = graph.addVertex("type", "software", "name", "project2", "lang", "python");

        person1.addEdge("created", software1, "year", 2020);
        person2.addEdge("created", software2, "year", 2021);
        person1.addEdge("knows", person2, "since", 2019);

        // Query by type
        List<MockVertex> people = graph.traversal().V()
            .has("type", "person")
            .toList();
        assertEquals(2, people.size(), "Should find 2 people");

        List<MockVertex> software = graph.traversal().V()
            .has("type", "software")
            .toList();
        assertEquals(2, software.size(), "Should find 2 software projects");

        // Query by property value
        MockVertex alice = graph.traversal().V()
            .has("name", "alice")
            .next();
        assertNotNull(alice);
        assertEquals("alice", alice.value("name"));

        // Query with traversal
        List<MockVertex> createdByAlice = graph.traversal().V()
            .has("name", "alice")
            .out("created")
            .toList();
        assertEquals(1, createdByAlice.size(), "Alice should have created 1 project");

        System.out.println("✅ Simple queries - COMPLIANT");
    }

    @Test
    @DisplayName("Should handle error conditions gracefully")
    public void shouldHandleErrorConditionsGracefully() {
        System.out.println("🧪 Testing error condition handling...");

        MockVertex vertex = graph.addVertex("name", "test");

        // Test nonexistent property access
        assertThrows(NoSuchElementException.class, () -> {
            vertex.value("nonexistent");
        }, "Should throw exception for nonexistent property");

        // Test empty traversal results
        List<MockVertex> noResults = graph.traversal().V()
            .has("name", "nonexistent")
            .toList();
        assertTrue(noResults.isEmpty(), "Should return empty list for no matches");

        // Test invalid property operations
        assertThrows(IllegalArgumentException.class, () -> {
            graph.addVertex(null, "value");
        }, "Should throw exception for null property key");

        // Test removed element access
        vertex.remove();
        assertThrows(IllegalStateException.class, () -> {
            vertex.property("new", "value");
        }, "Should throw exception when accessing removed vertex");

        System.out.println("✅ Error condition handling - COMPLIANT");
    }

    @Test
    @DisplayName("Should demonstrate graph connectivity")
    public void shouldDemonstrateGraphConnectivity() {
        System.out.println("🧪 Testing graph connectivity...");

        // Create a small social network
        MockVertex alice = graph.addVertex("name", "alice", "type", "person");
        MockVertex bob = graph.addVertex("name", "bob", "type", "person");
        MockVertex charlie = graph.addVertex("name", "charlie", "type", "person");
        MockVertex david = graph.addVertex("name", "david", "type", "person");

        // Create connections
        alice.addEdge("knows", bob);
        bob.addEdge("knows", charlie);
        charlie.addEdge("knows", david);
        alice.addEdge("likes", charlie);

        // Test direct connections
        List<MockVertex> aliceKnows = graph.traversal().V()
            .has("name", "alice")
            .out("knows")
            .toList();
        assertEquals(1, aliceKnows.size(), "Alice directly knows 1 person");
        assertEquals("bob", aliceKnows.get(0).value("name"));

        // Test two-hop connections
        List<MockVertex> twoHops = graph.traversal().V()
            .has("name", "alice")
            .out("knows")
            .out("knows")
            .toList();
        assertEquals(1, twoHops.size(), "Alice knows charlie through bob");
        assertEquals("charlie", twoHops.get(0).value("name"));

        // Test all connections from alice
        List<MockVertex> allConnections = graph.traversal().V()
            .has("name", "alice")
            .out()
            .dedup()
            .toList();
        assertEquals(2, allConnections.size(), "Alice has 2 total connections");

        System.out.println("✅ Graph connectivity - COMPLIANT");
    }

    @Test
    @DisplayName("Should support basic aggregation operations")
    public void shouldSupportBasicAggregationOperations() {
        System.out.println("🧪 Testing basic aggregation operations...");

        // Create test data with ages
        graph.addVertex("name", "alice", "age", 25, "dept", "engineering");
        graph.addVertex("name", "bob", "age", 30, "dept", "engineering");
        graph.addVertex("name", "charlie", "age", 35, "dept", "marketing");
        graph.addVertex("name", "diana", "age", 28, "dept", "marketing");

        // Test count aggregation
        long totalCount = graph.traversal().V().count().next();
        assertEquals(4L, totalCount, "Should count all vertices");

        long engineeringCount = graph.traversal().V()
            .has("dept", "engineering")
            .count()
            .next();
        assertEquals(2L, engineeringCount, "Should count engineering dept vertices");

        // Test value aggregation
        List<Integer> ages = graph.traversal().V()
            .has("age")
            .values("age")
            .toList();
        assertEquals(4, ages.size(), "Should collect all ages");
        assertTrue(ages.contains(25));
        assertTrue(ages.contains(30));
        assertTrue(ages.contains(35));
        assertTrue(ages.contains(28));

        // Test grouping
        Map<String, Collection<MockVertex>> byDept = graph.traversal().V()
            .group()
            .by("dept")
            .next();
        assertEquals(2, byDept.size(), "Should group by 2 departments");
        assertTrue(byDept.containsKey("engineering"));
        assertTrue(byDept.containsKey("marketing"));

        System.out.println("✅ Basic aggregation operations - COMPLIANT");
    }

    @Test
    @DisplayName("Should handle graph modification operations")
    public void shouldHandleGraphModificationOperations() {
        System.out.println("🧪 Testing graph modification operations...");

        // Create initial graph
        MockVertex alice = graph.addVertex("name", "alice");
        MockVertex bob = graph.addVertex("name", "bob");
        MockEdge edge = alice.addEdge("knows", bob);

        assertEquals(2L, graph.traversal().V().count().next(), "Should have 2 vertices initially");
        assertEquals(1L, graph.traversal().E().count().next(), "Should have 1 edge initially");

        // Add more elements
        MockVertex charlie = graph.addVertex("name", "charlie");
        alice.addEdge("likes", charlie);

        assertEquals(3L, graph.traversal().V().count().next(), "Should have 3 vertices after addition");
        assertEquals(2L, graph.traversal().E().count().next(), "Should have 2 edges after addition");

        // Remove elements
        edge.remove();
        assertEquals(3L, graph.traversal().V().count().next(), "Should still have 3 vertices after edge removal");
        assertEquals(1L, graph.traversal().E().count().next(), "Should have 1 edge after removal");

        bob.remove();
        assertEquals(2L, graph.traversal().V().count().next(), "Should have 2 vertices after vertex removal");

        System.out.println("✅ Graph modification operations - COMPLIANT");
    }
}
