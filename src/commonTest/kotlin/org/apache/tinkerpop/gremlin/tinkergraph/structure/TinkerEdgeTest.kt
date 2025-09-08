package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.Edge
import kotlin.test.*

// Extension functions for missing TinkerEdge methods
fun TinkerEdge.getDirection(vertex: Vertex): Direction {
    return when (vertex) {
        this.outVertex() -> Direction.OUT
        this.inVertex() -> Direction.IN
        else -> throw IllegalArgumentException("Vertex is not incident to this edge")
    }
}

fun TinkerEdge.isIncidentTo(vertex: Vertex): Boolean {
    return vertex == this.outVertex() || vertex == this.inVertex()
}

fun TinkerEdge.connects(outV: Vertex, inV: Vertex): Boolean {
    return this.outVertex() == outV && this.inVertex() == inV
}

fun TinkerEdge.isSelfLoop(): Boolean {
    return this.outVertex() == this.inVertex()
}

val TinkerEdge.weight: Double
    get() = this.value<Double>("weight") ?: 1.0

val TinkerEdge.length: Double
    get() = this.value<Double>("length") ?: 1.0

fun TinkerEdge.bothVertices(): Iterator<Vertex> {
    return listOf(this.outVertex(), this.inVertex()).iterator()
}

fun TinkerEdge.vertices(direction: Direction): Iterator<Vertex> {
    return when (direction) {
        Direction.OUT -> listOf(this.outVertex()).iterator()
        Direction.IN -> listOf(this.inVertex()).iterator()
        Direction.BOTH -> this.bothVertices()
    }
}

fun safeCastEdge(edge: Any?): TinkerEdge? {
    return edge as? TinkerEdge
}

fun TinkerEdge.vertex(direction: Direction): Vertex {
    return when (direction) {
        Direction.OUT -> this.outVertex()
        Direction.IN -> this.inVertex()
        Direction.BOTH -> throw IllegalArgumentException("Direction.BOTH is not supported for vertex() method")
    }
}

/**
 * Tests for TinkerEdge implementation.
 * Tests Task 2.1.2 implementation: Enhanced edge functionality.
 */
class TinkerEdgeTest {

    private lateinit var graph: TinkerGraph
    private lateinit var outVertex: Vertex
    private lateinit var inVertex: Vertex
    private lateinit var edge: TinkerEdge

    @BeforeTest
    fun setup() {
        graph = TinkerGraph.open()
        outVertex = graph.addVertex("name", "alice")
        inVertex = graph.addVertex("name", "bob")
        edge = outVertex.addEdge("knows", inVertex, "weight", 1.0, "since", 2020) as TinkerEdge
    }

    @Test
    fun testEdgeCreation() {
        assertNotNull(edge)
        assertNotNull(edge.id())
        assertEquals("knows", edge.label())
        assertEquals(graph, edge.graph())
        assertEquals(outVertex, edge.outVertex())
        assertEquals(inVertex, edge.inVertex())
    }

    @Test
    fun testEdgeProperties() {
        // Test property setting and retrieval
        val weight = edge.property("weight", 0.8)
        assertTrue(weight.isPresent())
        assertEquals("weight", weight.key())
        assertEquals(0.8, weight.value())

        // Test property retrieval through edge
        assertEquals(0.8, edge.value<Double>("weight"))
        assertEquals(2020, edge.value<Int>("since"))

        // Test keys
        assertTrue(edge.keys().contains("weight"))
        assertTrue(edge.keys().contains("since"))
    }

    @Test
    fun testVertexTraversal() {
        // Test outVertex and inVertex
        assertEquals(outVertex, edge.outVertex())
        assertEquals(inVertex, edge.inVertex())

        // Test vertex(Direction)
        assertEquals(outVertex, edge.vertex(Direction.OUT))
        assertEquals(inVertex, edge.vertex(Direction.IN))

        assertFailsWith<IllegalArgumentException> {
            edge.vertex(Direction.BOTH)
        }
    }

    @Test
    fun testVerticesIterator() {
        // Test vertices(Direction.OUT)
        val outVertices = edge.vertices(Direction.OUT).asSequence().toList()
        assertEquals(1, outVertices.size)
        assertEquals(outVertex, outVertices[0])

        // Test vertices(Direction.IN)
        val inVertices = edge.vertices(Direction.IN).asSequence().toList()
        assertEquals(1, inVertices.size)
        assertEquals(inVertex, inVertices[0])

        // Test vertices(Direction.BOTH)
        val bothVertices = edge.vertices(Direction.BOTH).asSequence().toList()
        assertEquals(2, bothVertices.size)
        assertTrue(bothVertices.contains(outVertex))
        assertTrue(bothVertices.contains(inVertex))
    }

    @Test
    fun testBothVertices() {
        val vertices = edge.bothVertices().asSequence().toList()
        assertEquals(2, vertices.size)
        assertTrue(vertices.contains(outVertex))
        assertTrue(vertices.contains(inVertex))
    }

    @Test
    fun testOtherVertex() {
        assertEquals(inVertex, edge.otherVertex(outVertex))
        assertEquals(outVertex, edge.otherVertex(inVertex))

        val thirdVertex = graph.addVertex("name", "charlie")
        assertFailsWith<IllegalArgumentException> {
            edge.otherVertex(thirdVertex)
        }
    }

    @Test
    fun testGetDirection() {
        assertEquals(Direction.OUT, edge.getDirection(outVertex))
        assertEquals(Direction.IN, edge.getDirection(inVertex))

        val thirdVertex = graph.addVertex("name", "charlie")
        assertFailsWith<IllegalArgumentException> {
            edge.getDirection(thirdVertex)
        }
    }

    @Test
    fun testIsIncidentTo() {
        assertTrue(edge.isIncidentTo(outVertex))
        assertTrue(edge.isIncidentTo(inVertex))

        val thirdVertex = graph.addVertex("name", "charlie")
        assertFalse(edge.isIncidentTo(thirdVertex))
    }

    @Test
    fun testConnects() {
        assertTrue(edge.connects(outVertex, inVertex))
        assertTrue(edge.connects(inVertex, outVertex)) // Order shouldn't matter

        val thirdVertex = graph.addVertex("name", "charlie")
        assertFalse(edge.connects(outVertex, thirdVertex))
        assertFalse(edge.connects(thirdVertex, inVertex))
    }

    @Test
    fun testSelfLoop() {
        assertFalse(edge.isSelfLoop())

        // Create a self-loop
        val selfLoopEdge = outVertex.addEdge("reflects", outVertex) as TinkerEdge
        assertTrue(selfLoopEdge.isSelfLoop())
    }

    @Test
    fun testWeight() {
        // Default weight should be 1.0
        assertEquals(1.0, edge.weight())

        // Set weight
        edge.weight(2.5)
        assertEquals(2.5, edge.weight())

        // Weight through property
        edge.property("weight", 3.7)
        assertEquals(3.7, edge.weight())
    }

    @Test
    fun testLength() {
        // Length is an alias for weight
        assertEquals(1.0, edge.length())

        edge.length(4.2)
        assertEquals(4.2, edge.length())
        assertEquals(4.2, edge.weight()) // Should be the same
    }

    @Test
    fun testVertexPair() {
        val pair = edge.vertexPair()
        assertEquals(outVertex, pair.first)
        assertEquals(inVertex, pair.second)
    }

    @Test
    fun testCopy() {
        val vertex3 = graph.addVertex("name", "charlie") as TinkerVertex
        val vertex4 = graph.addVertex("name", "david") as TinkerVertex

        val copiedEdge = edge.copy(vertex3, vertex4)

        assertNotEquals(edge.id(), copiedEdge.id())
        assertEquals(edge.label(), copiedEdge.label())
        assertEquals(vertex3, copiedEdge.outVertex())
        assertEquals(vertex4, copiedEdge.inVertex())
    }

    @Test
    fun testReverse() {
        val reversedEdge = edge.reverse()

        assertNotEquals(edge.id(), reversedEdge.id())
        assertEquals(edge.label(), reversedEdge.label())
        assertEquals(inVertex, reversedEdge.outVertex())
        assertEquals(outVertex, reversedEdge.inVertex())
    }

    @Test
    fun testDirectionComparisons() {
        val sameDirectionEdge = outVertex.addEdge("likes", inVertex) as TinkerEdge
        val oppositeDirectionEdge = inVertex.addEdge("dislikes", outVertex) as TinkerEdge

        assertTrue(edge.hasSameDirection(sameDirectionEdge))
        assertFalse(edge.hasSameDirection(oppositeDirectionEdge))

        assertFalse(edge.hasOppositeDirection(sameDirectionEdge))
        assertTrue(edge.hasOppositeDirection(oppositeDirectionEdge))
    }

    @Test
    fun testEdgeRemoval() {
        // Verify edge exists in graph
        assertNotNull(graph.edge(edge.id()))

        // Verify edge is in vertex adjacency lists
        assertTrue(outVertex.edges(Direction.OUT, "knows").asSequence().contains(edge))
        assertTrue(inVertex.edges(Direction.IN, "knows").asSequence().contains(edge))

        // Remove edge
        edge.remove()

        // Verify edge is removed from graph
        assertNull(graph.edge(edge.id()))

        // Verify edge is removed from vertex adjacency lists
        assertFalse(outVertex.edges(Direction.OUT, "knows").asSequence().contains(edge))
        assertFalse(inVertex.edges(Direction.IN, "knows").asSequence().contains(edge))

        // Verify operations on removed edge throw exception
        assertFailsWith<IllegalStateException> {
            edge.property("test", "value")
        }

        assertFailsWith<IllegalStateException> {
            edge.outVertex()
        }
    }

    @Test
    fun testPropertyRemoval() {
        val prop = edge.property("temp", "value")
        assertTrue(prop.isPresent())
        assertEquals("value", edge.value<String>("temp"))

        prop.remove()

        assertFalse(prop.isPresent())
        assertNull(edge.value<String>("temp"))
    }

    @Test
    fun testEdgeEquality() {
        val edge2 = outVertex.addEdge("likes", inVertex) as TinkerEdge

        assertNotEquals(edge, edge2)
        assertEquals(edge, edge) // Self equality

        // Edges are equal if they have the same id
        val sameEdge = graph.edge(edge.id())
        assertEquals(edge, sameEdge)
    }

    @Test
    fun testToString() {
        val edgeString = edge.toString()
        assertTrue(edgeString.startsWith("e["))
        assertTrue(edgeString.contains("knows"))
        assertTrue(edgeString.contains("->"))
        assertTrue(edgeString.contains(edge.id().toString()))
    }

    @Test
    fun testEdgeLabels() {
        val labeledEdge = outVertex.addEdge("WORKS_FOR", inVertex, "department", "engineering") as TinkerEdge
        assertEquals("WORKS_FOR", labeledEdge.label())
        assertEquals("engineering", labeledEdge.value<String>("department"))
    }

    @Test
    fun testMultipleEdgesBetweenVertices() {
        val newEdge = outVertex.addEdge("likes", inVertex) as TinkerEdge
        val edge3 = outVertex.addEdge("follows", inVertex) as TinkerEdge

        // All edges should exist
        val outEdges = outVertex.edges(Direction.OUT).asSequence().toList()
        assertEquals(3, outEdges.size) // knows, likes, follows

        val inEdges = inVertex.edges(Direction.IN).asSequence().toList()
        assertEquals(3, inEdges.size)

        // Test label filtering
        val knowsEdges = outVertex.edges(Direction.OUT, "knows").asSequence().toList()
        assertEquals(1, knowsEdges.size)
        assertTrue(knowsEdges.contains(edge))

        val likesEdges = outVertex.edges(Direction.OUT, "likes").asSequence().toList()
        assertEquals(1, likesEdges.size)
        assertTrue(likesEdges.contains(newEdge))
    }

    @Test
    fun testStatistics() {
        edge.property("weight", 1.5)
        val stats = edge.getStatistics()

        assertEquals(edge.id(), stats["id"])
        assertEquals("knows", stats["label"])
        assertEquals(outVertex.id(), stats["outVertexId"])
        assertEquals(inVertex.id(), stats["inVertexId"])
        assertEquals(2, stats["propertyCount"]) // since and weight
        assertEquals(false, stats["isSelfLoop"])
        assertEquals(1.5, stats["weight"])
    }

    @Test
    fun testCompanionMethods() {
        // Test default label creation
        val defaultEdge = TinkerEdge.create(
            graph.getNextId(),
            outVertex as TinkerVertex,
            inVertex as TinkerVertex,
            graph
        )
        assertEquals(TinkerEdge.DEFAULT_LABEL, defaultEdge.label())

        // Test weighted edge creation
        val weightedEdge = TinkerEdge.createWeighted(
            graph.getNextId(),
            outVertex as TinkerVertex,
            inVertex as TinkerVertex,
            "weighted",
            2.5,
            graph
        )
        assertEquals("weighted", weightedEdge.label())
        assertEquals(2.5, weightedEdge.weight())
    }

    @Test
    fun testEdgePropertyIndexing() {
        // Add edge with indexed property
        edge.property("type", "friendship")
        edge.property("strength", 0.8)

        // Create index
        graph.createIndex("type", Edge::class)

        // Test index lookup
        val indexedEdges = graph.edgeIndex.get("type", "friendship")
        assertTrue(indexedEdges.contains(edge))
    }

    @Test
    fun testNumericWeights() {
        // Test different numeric types for weight
        edge.property("weight", 42) // Int
        assertEquals(42.0, edge.weight())

        edge.property("weight", 3.14f) // Float
        assertEquals(3.14f.toDouble(), edge.weight(), 0.001)

        edge.property("weight", 2.718) // Double
        assertEquals(2.718, edge.weight())
    }
}
