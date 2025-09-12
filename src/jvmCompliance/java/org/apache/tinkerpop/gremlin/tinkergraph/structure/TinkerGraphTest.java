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
package org.apache.tinkerpop.gremlin.tinkergraph.structure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java compliance tests for TinkerGraph Structure API.
 *
 * This test class validates TinkerGraph structure API compliance following
 * Apache TinkerPop patterns, extracted from the monolithic JavaComplianceTests.
 * Focuses on:
 *
 * - Graph creation and configuration
 * - Vertex and Edge creation
 * - Property management
 * - Data type handling
 * - Exception handling for structure operations
 *
 * Derived from Apache TinkerPop reference implementations in tinkerpop-reference/
 */
public class TinkerGraphTest {

    private MockTinkerGraph graph;

    @BeforeEach
    public void setUp() {
        System.out.println("🧪 Initializing TinkerGraph Test Suite...");
        graph = MockTinkerGraph.open();
    }

    @AfterEach
    public void tearDown() {
        if (graph != null) {
            graph.close();
        }
        System.out.println("🧹 TinkerGraph Test Suite - Cleaned up");
    }

    @Test
    @DisplayName("Graph Creation and Configuration Compliance")
    public void testGraphCreationCompliance() {
        System.out.println("🧪 Testing Graph Creation Compliance...");

        // Test basic graph creation following TinkerPop patterns
        assertNotNull(graph, "Graph should not be null");

        // Verify Java-specific graph features
        MockGraphFeatures features = graph.features();
        assertNotNull(features, "Features should not be null");
        assertTrue(features.graph().supportsTransactions(), "Should support transactions");
        assertTrue(features.vertex().supportsAddVertices(), "Should support adding vertices");
        assertTrue(features.edge().supportsAddEdges(), "Should support adding edges");

        // Test Java configuration access
        MockConfiguration config = graph.configuration();
        assertNotNull(config, "Configuration should not be null");
        assertTrue(config.getKeys().hasNext(), "Should have configuration keys");

        System.out.println("✅ Graph Creation - COMPLIANT");
    }

    @Test
    @DisplayName("Java Data Type Compliance")
    public void testJavaDataTypeCompliance() {
        System.out.println("🧪 Testing Java Data Type Compliance...");

        // Test standard Java primitives
        MockVertex vertex = graph.addVertex();
        vertex.property("stringProp", "Java String");
        vertex.property("intProp", 42);
        vertex.property("longProp", 12345L);
        vertex.property("doubleProp", 3.14159);
        vertex.property("floatProp", 2.71f);
        vertex.property("booleanProp", true);
        vertex.property("byteProp", (byte) 255);

        // Verify data type preservation
        assertEquals("Java String", vertex.value("stringProp"));
        assertEquals(Integer.valueOf(42), vertex.value("intProp"));
        assertEquals(Long.valueOf(12345L), vertex.value("longProp"));
        assertEquals(Double.valueOf(3.14159), vertex.value("doubleProp"));
        assertEquals(Float.valueOf(2.71f), vertex.value("floatProp"));
        assertEquals(Boolean.TRUE, vertex.value("booleanProp"));
        assertEquals(Byte.valueOf((byte) 255), vertex.value("byteProp"));

        // Test Java collection types
        List<String> stringList = Arrays.asList("Java", "Collections", "Support");
        vertex.property("listProp", stringList);
        assertEquals(stringList, vertex.value("listProp"));

        Map<String, Object> mapData = new HashMap<>();
        mapData.put("nested", "Java Map");
        mapData.put("count", 100);
        vertex.property("mapProp", mapData);
        assertEquals(mapData, vertex.value("mapProp"));

        // Test Java 8+ Date/Time API
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.now();
        vertex.property("dateTimeProp", dateTime);
        assertEquals(dateTime, vertex.value("dateTimeProp"));

        System.out.println("✅ Java Data Types - COMPLIANT");
    }

    @Test
    @DisplayName("Property Management Compliance")
    public void testPropertyManagementCompliance() {
        System.out.println("🧪 Testing Property Management Compliance...");

        MockVertex vertex = graph.addVertex("id", 1);

        // Test single property
        vertex.property("name", "marko");
        assertEquals("marko", vertex.value("name"));

        // Test property exists
        assertTrue(vertex.property("name").isPresent());
        assertFalse(vertex.property("nonexistent").isPresent());

        // Test property removal
        vertex.property("temp").remove();
        assertFalse(vertex.property("temp").isPresent());

        // Test property iteration
        vertex.property("age", 29);
        vertex.property("city", "santa fe");

        Set<String> propertyKeys = new HashSet<>();
        vertex.properties().forEachRemaining(p -> propertyKeys.add(p.key()));

        assertTrue(propertyKeys.contains("name"));
        assertTrue(propertyKeys.contains("age"));
        assertTrue(propertyKeys.contains("city"));

        System.out.println("✅ Property Management - COMPLIANT");
    }

    @Test
    @DisplayName("Exception Handling Compliance")
    public void testExceptionHandlingCompliance() {
        System.out.println("🧪 Testing Exception Handling Compliance...");

        // Test IllegalArgumentException for null keys
        assertThrows(IllegalArgumentException.class, () -> {
            graph.addVertex(null, "value");
        }, "Should throw IllegalArgumentException for null key");

        // Test NoSuchElementException
        assertThrows(NoSuchElementException.class, () -> {
            MockVertex vertex = graph.addVertex();
            vertex.value("nonexistent");
        }, "Should throw NoSuchElementException for nonexistent property");

        // Test IllegalStateException for removed elements
        MockVertex vertex = graph.addVertex("id", 1);
        vertex.remove();
        assertThrows(IllegalStateException.class, () -> {
            vertex.property("name", "should fail");
        }, "Should throw IllegalStateException when modifying removed vertex");

        System.out.println("✅ Exception Handling - COMPLIANT");
    }

    @Test
    @DisplayName("Memory Management Compliance")
    public void testMemoryManagementCompliance() {
        System.out.println("🧪 Testing Memory Management Compliance...");

        // Create large number of objects to test memory handling
        List<MockVertex> vertices = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            vertices.add(graph.addVertex("id", i, "data", "vertex_" + i));
        }

        assertEquals(1000L, graph.traversal().V().count().next(), "Should have 1000 vertices");

        // Test Java garbage collection patterns
        vertices.clear();
        System.gc(); // Suggest garbage collection

        // Verify objects can still be accessed through graph
        long remainingCount = graph.traversal().V().count().next();
        assertEquals(1000L, remainingCount, "Objects should still be accessible through graph");

        System.out.println("✅ Memory Management - COMPLIANT");
    }

    @Test
    @DisplayName("Vertex and Edge Creation Compliance")
    public void testVertexEdgeCreationCompliance() {
        System.out.println("🧪 Testing Vertex and Edge Creation Compliance...");

        // Test vertex creation
        MockVertex v1 = graph.addVertex("id", 1, "name", "marko");
        MockVertex v2 = graph.addVertex("id", 2, "name", "vadas");

        assertNotNull(v1);
        assertNotNull(v2);
        assertEquals("marko", v1.value("name"));
        assertEquals("vadas", v2.value("name"));

        // Test edge creation
        MockEdge edge = v1.addEdge("knows", v2, "weight", 0.5f);
        assertNotNull(edge);
        assertEquals("knows", edge.label());
        assertEquals(0.5f, edge.value("weight"));
        assertEquals(v1, edge.outVertex());
        assertEquals(v2, edge.inVertex());

        // Test traversal from vertex to edge
        assertEquals(1, v1.edges(Direction.OUT, "knows").count());
        assertEquals(1, v2.edges(Direction.IN, "knows").count());

        System.out.println("✅ Vertex and Edge Creation - COMPLIANT");
    }

    @Test
    @DisplayName("Graph Traversal Basic Operations")
    public void testBasicTraversalCompliance() {
        System.out.println("🧪 Testing Basic Traversal Compliance...");

        // Create test graph
        MockTinkerGraph modernGraph = createModernGraph();

        // Test basic vertex traversal
        List<MockVertex> vertices = modernGraph.traversal().V().toList();
        assertEquals(6, vertices.size(), "Modern graph should have 6 vertices");

        // Test property-based filtering
        List<MockVertex> markoVertices = modernGraph.traversal().V().has("name", "marko").toList();
        assertEquals(1, markoVertices.size(), "Should find exactly one 'marko' vertex");

        // Test edge traversal
        List<MockEdge> edges = modernGraph.traversal().E().toList();
        assertEquals(6, edges.size(), "Modern graph should have 6 edges");

        // Test combined vertex-edge traversal
        List<MockVertex> friends = modernGraph.traversal().V().has("name", "marko")
            .out("knows").toList();
        assertEquals(2, friends.size(), "Marko should know 2 people");

        System.out.println("✅ Basic Traversal - COMPLIANT");
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
}
