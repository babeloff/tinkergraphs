package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*

/**
 * TinkerEdge is the edge implementation for TinkerGraph.
 * It maintains references to the outgoing (tail) and incoming (head) vertices
 * and supports properties like any other graph element.
 */
class TinkerEdge(
    id: Any,
    label: String,
    private val outVertex: TinkerVertex,
    private val inVertex: TinkerVertex,
    graph: TinkerGraph
) : TinkerElement(id, label, graph), Edge {

    /**
     * Flag to track if this edge has been removed from the graph.
     */
    private var edgeRemoved: Boolean = false

    override fun outVertex(): Vertex {
        checkNotRemoved()
        return outVertex
    }

    override fun inVertex(): Vertex {
        checkNotRemoved()
        return inVertex
    }

    override fun vertex(direction: Direction): Vertex {
        checkNotRemoved()
        return when (direction) {
            Direction.OUT -> outVertex
            Direction.IN -> inVertex
            Direction.BOTH -> throw IllegalArgumentException("Direction.BOTH is not supported for single vertex retrieval")
        }
    }

    override fun vertices(direction: Direction): Iterator<Vertex> {
        checkNotRemoved()
        val vertices = when (direction) {
            Direction.OUT -> listOf(outVertex)
            Direction.IN -> listOf(inVertex)
            Direction.BOTH -> listOf(outVertex, inVertex)
        }
        return vertices.iterator()
    }

    override fun bothVertices(): Iterator<Vertex> {
        checkNotRemoved()
        return listOf(outVertex, inVertex).iterator()
    }

    override fun otherVertex(vertex: Vertex): Vertex {
        checkNotRemoved()
        return when (vertex) {
            outVertex -> inVertex
            inVertex -> outVertex
            else -> throw IllegalArgumentException("The provided vertex is not incident to this edge")
        }
    }

    /**
     * Get the direction of a vertex relative to this edge.
     * @param vertex the vertex to check
     * @return the direction of the vertex on this edge
     * @throws IllegalArgumentException if the vertex is not incident to this edge
     */
    fun getDirection(vertex: Vertex): Direction {
        checkNotRemoved()
        return when (vertex) {
            outVertex -> Direction.OUT
            inVertex -> Direction.IN
            else -> throw IllegalArgumentException("The provided vertex is not incident to this edge")
        }
    }

    /**
     * Check if this edge is incident to the specified vertex.
     * @param vertex the vertex to check
     * @return true if the vertex is either the out or in vertex of this edge
     */
    fun isIncidentTo(vertex: Vertex): Boolean {
        checkNotRemoved()
        return vertex == outVertex || vertex == inVertex
    }

    /**
     * Check if this edge connects the two specified vertices.
     * @param vertex1 the first vertex
     * @param vertex2 the second vertex
     * @return true if this edge connects the two vertices (in either direction)
     */
    fun connects(vertex1: Vertex, vertex2: Vertex): Boolean {
        checkNotRemoved()
        return (vertex1 == outVertex && vertex2 == inVertex) ||
               (vertex1 == inVertex && vertex2 == outVertex)
    }

    /**
     * Check if this edge is a self-loop (connects a vertex to itself).
     * @return true if the out vertex and in vertex are the same
     */
    fun isSelfLoop(): Boolean {
        checkNotRemoved()
        return outVertex == inVertex
    }

    /**
     * Get the weight of this edge.
     * Looks for a property with key "weight", defaulting to 1.0 if not found.
     * @return the edge weight
     */
    fun weight(): Double {
        checkNotRemoved()
        return value<Number>("weight")?.toDouble() ?: 1.0
    }

    /**
     * Set the weight of this edge.
     * @param weight the weight value
     * @return the created property
     */
    fun weight(weight: Double): Property<Double> {
        checkNotRemoved()
        return property("weight", weight)
    }

    override fun remove() {
        checkNotRemoved()
        elementGraph.removeEdge(this)
    }

    /**
     * Check if this edge has been removed and throw exception if so.
     */
    private fun checkNotRemoved() {
        if (edgeRemoved) {
            throw Element.Exceptions.elementAlreadyRemoved("Edge", elementId)
        }
        checkRemoved() // Check if element is removed
    }

    /**
     * Mark this edge as removed (internal use by graph).
     */
    internal fun markEdgeRemoved() {
        edgeRemoved = true
        markRemoved()
    }

    /**
     * Check if this edge is removed (for iterator filtering).
     */
    internal fun isEdgeRemoved(): Boolean {
        return edgeRemoved || super.isRemoved()
    }

    /**
     * Get both vertices as a pair.
     * @return Pair of (outVertex, inVertex)
     */
    fun vertexPair(): Pair<Vertex, Vertex> {
        checkNotRemoved()
        return Pair(outVertex, inVertex)
    }

    /**
     * Create a copy of this edge connecting different vertices.
     * Properties are not copied.
     * @param newOutVertex the new out vertex
     * @param newInVertex the new in vertex
     * @param newId optional new ID (generated if not provided)
     * @return new edge instance
     */
    fun copy(
        newOutVertex: TinkerVertex,
        newInVertex: TinkerVertex,
        newId: Any = elementGraph.getNextId()
    ): TinkerEdge {
        checkNotRemoved()
        return elementGraph.addEdge(newOutVertex, newInVertex, elementLabel, mapOf("id" to newId))
    }

    /**
     * Create a reversed copy of this edge (swap out and in vertices).
     * Properties are not copied.
     * @param newId optional new ID (generated if not provided)
     * @return new edge instance with vertices swapped
     */
    fun reverse(newId: Any = elementGraph.getNextId()): TinkerEdge {
        checkNotRemoved()
        return elementGraph.addEdge(inVertex, outVertex, elementLabel, mapOf("id" to newId))
    }

    /**
     * Check if this edge has the same direction as another edge.
     * @param other the other edge
     * @return true if both edges have the same out and in vertices
     */
    fun hasSameDirection(other: Edge): Boolean {
        checkNotRemoved()
        return outVertex == other.outVertex() && inVertex == other.inVertex()
    }

    /**
     * Check if this edge has the opposite direction of another edge.
     * @param other the other edge
     * @return true if this edge's out vertex is the other's in vertex and vice versa
     */
    fun hasOppositeDirection(other: Edge): Boolean {
        checkNotRemoved()
        return outVertex == other.inVertex() && inVertex == other.outVertex()
    }

    /**
     * Get the length of this edge.
     * This is an alias for weight() for graph algorithms that use distance/length.
     * @return the edge length (same as weight)
     */
    fun length(): Double = weight()

    /**
     * Set the length of this edge.
     * This is an alias for weight(value) for graph algorithms that use distance/length.
     * @param length the length value
     * @return the created property
     */
    fun length(length: Double): Property<Double> = weight(length)

    /**
     * Get edge statistics for debugging/monitoring.
     * @return map of edge statistics
     */
    fun getStatistics(): Map<String, Any> {
        checkNotRemoved()
        return mapOf(
            "id" to elementId,
            "label" to elementLabel,
            "outVertexId" to outVertex.id(),
            "inVertexId" to inVertex.id(),
            "propertyCount" to elementProperties.size,
            "isSelfLoop" to isSelfLoop(),
            "weight" to weight()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false

        other as TinkerEdge
        return elementId == other.elementId
    }

    override fun hashCode(): Int {
        return elementId.hashCode()
    }

    override fun toString(): String {
        return "e[$elementId][$outVertex-$elementLabel->$inVertex]"
    }

    companion object {
        /**
         * Default edge label when none is specified.
         */
        const val DEFAULT_LABEL = "edge"

        /**
         * Common property key for edge weight.
         */
        const val WEIGHT_PROPERTY = "weight"

        /**
         * Create an edge with default label.
         */
        fun create(
            id: Any,
            outVertex: TinkerVertex,
            inVertex: TinkerVertex,
            graph: TinkerGraph
        ): TinkerEdge {
            return TinkerEdge(id, DEFAULT_LABEL, outVertex, inVertex, graph)
        }

        /**
         * Create a weighted edge.
         */
        fun createWeighted(
            id: Any,
            outVertex: TinkerVertex,
            inVertex: TinkerVertex,
            label: String,
            weight: Double,
            graph: TinkerGraph
        ): TinkerEdge {
            val edge = TinkerEdge(id, label, outVertex, inVertex, graph)
            edge.weight(weight)
            return edge
        }
    }
}
