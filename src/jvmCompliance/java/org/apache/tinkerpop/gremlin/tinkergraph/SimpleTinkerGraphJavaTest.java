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

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simplified Java compliance tests for TinkerGraph following Apache TinkerPop Java compliance patterns.
 *
 * This test class validates TinkerGraph behavior on JVM platform using Java, ensuring compliance
 * with Apache TinkerPop specifications. These tests are simplified to work without external
 * dependencies while maintaining the essential compliance validation patterns from upstream.
 *
 * Task 4.1.2 Phase 3 - JVM/Java Platform Compliance Testing
 *
 * @author TinkerGraphs Compliance Framework
 */
public class SimpleTinkerGraphJavaTest {

    private TinkerGraph graph;
    private GraphTraversalSource g;

    @BeforeEach
    void setUp() {
        graph = TinkerGraph.open();
        g = graph.traversal();
    }

    @Test
    @DisplayName("Test basic graph creation and configuration")
    void testBasicGraphCreation() {
        // Test basic TinkerGraph creation on JVM platform
        assertNotNull(graph);
        assertTrue(graph.features().graph().supportsComputer());
        assertTrue(graph.features().vertex().supportsAddVertices());
        assertTrue(graph.features().edge().supportsAddEdges());

        // Verify graph configuration
        assertNotNull(graph.configuration());
        assertNotNull(graph.traversal());
    }

    @Test
    @DisplayName("Test vertex creation and property operations")
    void testVertexCreationAndProperties() {
        // Test vertex creation with properties following Java compliance patterns
        Vertex vertex = graph.addVertex(T.id, 1, T.label, "person", "name", "marko", "age", 29);

        assertNotNull(vertex);
        assertEquals(1, vertex.id());
        assertEquals("person", vertex.label());
        assertEquals("marko", vertex.value("name"));
        assertEquals(29, vertex.value("age"));

        // Test property updates
        vertex.property("age", 30);
        assertEquals(30, vertex.value("age"));

        // Test multiple properties
        vertex.property("city", "santa fe");
        assertEquals("santa fe", vertex.value("city"));
    }

    @Test
    @DisplayName("Test edge creation and navigation")
    void testEdgeCreationAndNavigation() {
        // Create vertices
        Vertex marko = graph.addVertex(T.id, 1, T.label, "person", "name", "marko", "age", 29);
        Vertex vadas = graph.addVertex(T.id, 2, T.label, "person", "name", "vadas", "age", 27);
        Vertex lop = graph.addVertex(T.id, 3, T.label, "software", "name", "lop", "lang", "java");

        // Create edges
        Edge knows1 = marko.addEdge("knows", vadas, "weight", 0.5);
        Edge created1 = marko.addEdge("created", lop, "weight", 0.4);

        assertNotNull(knows1);
        assertNotNull(created1);
        assertEquals("knows", knows1.label());
        assertEquals("created", created1.label());
        assertEquals(0.5, knows1.value("weight"));
        assertEquals(0.4, created1.value("weight"));

        // Test edge navigation
        assertEquals(marko, knows1.outVertex());
        assertEquals(vadas, knows1.inVertex());
        assertEquals(marko, created1.outVertex());
        assertEquals(lop, created1.inVertex());
    }

    @Test
    @DisplayName("Test basic traversal operations")
    void testBasicTraversalOperations() {
        // Create the modern graph structure
        createModernGraph();

        // Test vertex count
        long vertexCount = g.V().count().next();
        assertEquals(6L, vertexCount);

        // Test edge count
        long edgeCount = g.E().count().next();
        assertEquals(6L, edgeCount);

        // Test label filtering
        long personCount = g.V().hasLabel("person").count().next();
        assertEquals(4L, personCount);

        long softwareCount = g.V().hasLabel("software").count().next();
        assertEquals(2L, softwareCount);
    }

    @Test
    @DisplayName("Test traversal filtering and navigation")
    void testTraversalFilteringAndNavigation() {
        createModernGraph();

        // Test property filtering
        long markoCount = g.V().has("name", "marko").count().next();
        assertEquals(1L, markoCount);

        // Test age filtering with predicates
        long olderThan30 = g.V().has("age", org.apache.tinkerpop.gremlin.process.traversal.P.gt(30)).count().next();
        assertEquals(2L, olderThan30); // josh (32) and peter (35)

        // Test out() navigation
        long markoFriends = g.V().has("name", "marko").out("knows").count().next();
        assertEquals(2L, markoFriends); // knows vadas and josh

        // Test in() navigation
        long lopCreators = g.V().has("name", "lop").in("created").count().next();
        assertEquals(3L, lopCreators); // created by marko, josh, and peter
    }

    @Test
    @DisplayName("Test Java-specific data types and operations")
    void testJavaDataTypesAndOperations() {
        // Test Java-specific data types
        Vertex vertex = graph.addVertex(
            "stringProp", "test string",
            "intProp", 42,
            "longProp", 123456789L,
            "doubleProp", 3.14159,
            "floatProp", 2.71f,
            "booleanProp", true,
            "byteProp", (byte) 127,
            "shortProp", (short) 32767
        );

        assertEquals("test string", vertex.value("stringProp"));
        assertEquals(42, vertex.value("intProp"));
        assertEquals(123456789L, vertex.value("longProp"));
        assertEquals(3.14159, vertex.value("doubleProp"));
        assertEquals(2.71f, vertex.value("floatProp"));
        assertEquals(true, vertex.value("booleanProp"));
        assertEquals((byte) 127, vertex.value("byteProp"));
        assertEquals((short) 32767, vertex.value("shortProp"));
    }

    @Test
    @DisplayName("Test graph features compliance")
    void testGraphFeaturesCompliance() {
        Graph.Features features = graph.features();

        // Test graph features
        assertTrue(features.graph().supportsTransactions());
        assertTrue(features.graph().supportsPersistence());
        assertTrue(features.graph().supportsComputer());

        // Test vertex features
        assertTrue(features.vertex().supportsAddVertices());
        assertTrue(features.vertex().supportsRemoveVertices());
        assertTrue(features.vertex().supportsMetaProperties());
        assertTrue(features.vertex().supportsMultiProperties());

        // Test edge features
        assertTrue(features.edge().supportsAddEdges());
        assertTrue(features.edge().supportsRemoveEdges());

        // Test variable features
        assertTrue(features.graph().variables().supportsVariables());
        assertTrue(features.graph().variables().supportsBooleanValues());
        assertTrue(features.graph().variables().supportsIntegerValues());
        assertTrue(features.graph().variables().supportsStringValues());
    }

    @Test
    @DisplayName("Test performance baseline on JVM")
    void testPerformanceBaseline() {
        long startTime = System.currentTimeMillis();

        // Create vertices for performance testing
        for (int i = 0; i < 1000; i++) {
            graph.addVertex("id", i, "name", "vertex_" + i, "value", i * 1.5);
        }

        long creationTime = System.currentTimeMillis() - startTime;
        assertTrue(creationTime < 5000, "Vertex creation should complete within 5 seconds");

        // Test traversal performance
        long traversalStart = System.currentTimeMillis();
        long count = g.V().count().next();
        long traversalTime = System.currentTimeMillis() - traversalStart;

        assertEquals(1000L, count);
        assertTrue(traversalTime < 1000, "Traversal should complete within 1 second");
    }

    @Test
    @DisplayName("Test error handling and exceptions")
    void testErrorHandlingAndExceptions() {
        Vertex vertex = graph.addVertex("name", "test");

        // Test exception for nonexistent property
        assertThrows(Exception.class, () -> {
            vertex.value("nonexistent_property");
        });

        // Test exception for null operations
        assertThrows(Exception.class, () -> {
            graph.addVertex(null, "invalid");
        });
    }

    @Test
    @DisplayName("Test transaction support")
    void testTransactionSupport() {
        // Test basic transaction lifecycle
        assertNotNull(graph.tx());

        // Create vertex within transaction context
        graph.tx().open();
        Vertex vertex = graph.addVertex("name", "transaction_test");
        assertNotNull(vertex);
        graph.tx().commit();

        // Verify vertex persisted
        long count = g.V().has("name", "transaction_test").count().next();
        assertEquals(1L, count);
    }

    @Test
    @DisplayName("Test complex traversal patterns")
    void testComplexTraversalPatterns() {
        createModernGraph();

        // Test path traversal - people who worked on same projects as marko
        long collaborators = g.V().has("name", "marko")
            .out("created")
            .in("created")
            .dedup()
            .count()
            .next();
        assertEquals(3L, collaborators); // marko, josh, peter

        // Test repeat traversal
        long twoHopConnections = g.V().has("name", "marko")
            .repeat(org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.out())
            .times(2)
            .count()
            .next();
        assertTrue(twoHopConnections > 0);

        // Test aggregation
        java.util.Map<String, Long> labelCounts = g.V()
            .groupCount()
            .by(T.label)
            .next();
        assertEquals(4L, labelCounts.get("person").longValue());
        assertEquals(2L, labelCounts.get("software").longValue());
    }

    /**
     * Helper method to create the modern graph structure for testing
     */
    private void createModernGraph() {
        // Create vertices
        Vertex marko = graph.addVertex(T.id, 1, T.label, "person", "name", "marko", "age", 29);
        Vertex vadas = graph.addVertex(T.id, 2, T.label, "person", "name", "vadas", "age", 27);
        Vertex lop = graph.addVertex(T.id, 3, T.label, "software", "name", "lop", "lang", "java");
        Vertex josh = graph.addVertex(T.id, 4, T.label, "person", "name", "josh", "age", 32);
        Vertex ripple = graph.addVertex(T.id, 5, T.label, "software", "name", "ripple", "lang", "java");
        Vertex peter = graph.addVertex(T.id, 6, T.label, "person", "name", "peter", "age", 35);

        // Create edges
        marko.addEdge("knows", vadas, "weight", 0.5);
        marko.addEdge("knows", josh, "weight", 1.0);
        marko.addEdge("created", lop, "weight", 0.4);
        josh.addEdge("created", ripple, "weight", 1.0);
        josh.addEdge("created", lop, "weight", 0.4);
        peter.addEdge("created", lop, "weight", 0.2);
    }
}
