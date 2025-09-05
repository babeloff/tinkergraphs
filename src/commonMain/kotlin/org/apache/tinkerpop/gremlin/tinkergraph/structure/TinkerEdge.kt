package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*

/**
 * TinkerEdge is the edge implementation for TinkerGraph.
 * It maintains references to the outgoing (tail) and incoming (head) vertices
 * and supports properties like any other graph element.
 *
 * This class provides:
 * - Directional edge functionality with source and target vertex references
 * - Property management inherited from TinkerElement
 * - Integration with TinkerGraph's indexing and caching systems
 * - Edge lifecycle management and validation
 *
 * @param id the unique identifier for this edge
 * @param label the label assigned to this edge
 * @param outVertex the source vertex of this edge
 * @param inVertex the target vertex of this edge
 * @param graph the TinkerGraph instance that owns this edge
 * @since 1.0.0
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

    /**
     * Returns the outgoing (source) vertex of this edge.
     * @return the source vertex
     * @throws IllegalStateException if this edge has been removed
     */
    override fun outVertex(): Vertex {
        checkNotRemoved()
        return outVertex
    }

    /**
     * Returns the incoming (target) vertex of this edge.
     * @return the target vertex
     * @throws IllegalStateException if this edge has been removed
     */
    override fun inVertex(): Vertex {
        checkNotRemoved()
        return inVertex
    }

    /**
     * Returns the vertex at the specified direction.
     * @param direction the direction (OUT for source vertex, IN for target vertex)
     * @return the vertex at the specified direction
     * @throws IllegalStateException if this edge has been removed
     * @throws IllegalArgumentException if direction is BOTH (not supported for single vertex)
     */
    override fun vertex(direction: Direction): Vertex {
        checkNotRemoved()
        return when (direction) {
            Direction.OUT -> outVertex
            Direction.IN -> inVertex
            Direction.BOTH -> throw IllegalArgumentException("Direction.BOTH is not supported for single vertex retrieval")
        }
    }

    /**
     * Returns an iterator over vertices at the specified direction.
     * @param direction the direction (OUT, IN, or BOTH)
     * @return iterator over vertices (one vertex for OUT/IN, two for BOTH)
     * @throws IllegalStateException if this edge has been removed
     */
    override fun vertices(direction: Direction): Iterator<Vertex> {
        checkNotRemoved()
        val vertices = when (direction) {
            Direction.OUT -> listOf(outVertex)
            Direction.IN -> listOf(inVertex)
            Direction.BOTH -> listOf(outVertex, inVertex)
        }
        return vertices.iterator()
    }

    /**
     * Returns an iterator over both vertices of this edge (source and target).
     * @return iterator over both vertices
     * @throws IllegalStateException if this edge has been removed
     */
    override fun bothVertices(): Iterator<Vertex> {
        checkNotRemoved()
        return listOf(outVertex, inVertex).iterator()
    }

    /**
     * Returns the vertex opposite to the specified vertex on this edge.
     * @param vertex the vertex to find the opposite of
     * @return the opposite vertex
     * @throws IllegalStateException if this edge has been removed
     * @throws IllegalArgumentException if the specified vertex is not incident to this edge
     */
    override fun otherVertex(vertex: Vertex): Vertex {
        checkNotRemoved()
        return when (vertex) {
            outVertex -> inVertex
            inVertex -> outVertex
            else -> throw IllegalArgumentException("Vertex $vertex is not incident to this edge")
        }
    }

    /**
     * Returns the direction of a vertex relative to this edge.
     * @param vertex the vertex to check
     * @return the direction of the vertex on this edge (OUT or IN)
     * @throws IllegalStateException if this edge has been removed
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
     * Checks if this edge is incident to the specified vertex.
     * An edge is incident to a vertex if the vertex is either the source or target.
     *
     * @param vertex the vertex to check
     * @return true if the vertex is either the out or in vertex of this edge
     * @throws IllegalStateException if this edge has been removed
     */
    fun isIncidentTo(vertex: Vertex): Boolean {
        checkNotRemoved()
        return vertex == outVertex || vertex == inVertex
    }

    /**
     * Checks if this edge connects the two specified vertices.
     * Returns true if the edge connects the vertices in either direction.
     *
     * @param vertex1 the first vertex
     * @param vertex2 the second vertex
     * @return true if this edge connects the two vertices (in either direction)
     * @throws IllegalStateException if this edge has been removed
     */
    fun connects(vertex1: Vertex, vertex2: Vertex): Boolean {
        checkNotRemoved()
        return (vertex1 == outVertex && vertex2 == inVertex) ||
               (vertex1 == inVertex && vertex2 == outVertex)
    }

    /**
     * Checks if this edge is a self-loop (connects a vertex to itself).
     *
     * @return true if the out vertex and in vertex are the same
     * @throws IllegalStateException if this edge has been removed
     */
    fun isSelfLoop(): Boolean {
        checkNotRemoved()
        return outVertex == inVertex
    }

    /**
     * Returns the weight of this edge.
     * Looks for a property with key "weight", defaulting to 1.0 if not found.
     *
     * @return the edge weight as a double value
     * @throws IllegalStateException if this edge has been removed
     */
    fun weight(): Double {
        checkNotRemoved()
        return value<Number>("weight")?.toDouble() ?: 1.0
    }

    /**
     * Sets the weight of this edge by creating or updating the "weight" property.
     *
     * @param weight the weight value to set
     * @return the created or updated property
     * @throws IllegalStateException if this edge has been removed
     */
    fun weight(weight: Double): Property<Double> {
        checkNotRemoved()
        return property("weight", weight)
    }

    /**
     * Removes this edge from the graph.
     * This also removes the edge from its incident vertices' adjacency lists.
     *
     * @throws IllegalStateException if this edge has already been removed
     */
    override fun remove() {
        checkNotRemoved()
        elementGraph.removeEdge(this)
    }

    /**
     * Verifies that this edge has not been removed and throws an exception if it has.
     * This checks both the edge-specific removal flag and the inherited element removal flag.
     *
     * @throws IllegalStateException if this edge has been removed
     */
    private fun checkNotRemoved() {
        if (edgeRemoved) {
            throw Element.Exceptions.elementAlreadyRemoved("Edge", elementId)
        }
        checkRemoved() // Check if element is removed
    }

    /**
     * Marks this edge as removed from the graph.
     * This is called internally when the edge is removed from the graph.
     */
    internal fun markEdgeRemoved() {
        edgeRemoved = true
        markRemoved()
    }

    /**
     * Returns whether this edge has been removed from the graph.
     * This is used by iterators for filtering removed edges.
     *
     * @return true if this edge has been removed, false otherwise
     */
    internal fun isEdgeRemoved(): Boolean {
        return edgeRemoved || super.isRemoved()
    }

    /**
     * Returns both vertices of this edge as a pair.
     *
     * @return Pair of (outVertex, inVertex)
     * @throws IllegalStateException if this edge has been removed
     */
    fun vertexPair(): Pair<Vertex, Vertex> {
        checkNotRemoved()
        return Pair(outVertex, inVertex)
    }

    /**
     * Creates a copy of this edge connecting different vertices.
     * The new edge will have the same label but properties are not copied.
     *
     * @param newOutVertex the new out vertex for the copy
     * @param newInVertex the new in vertex for the copy
     * @param newId optional new ID for the copy (auto-generated if not provided)
     * @return new edge instance connecting the specified vertices
     * @throws IllegalStateException if this edge has been removed
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
     * Creates a reversed copy of this edge by swapping the out and in vertices.
     * The new edge will have the same label but properties are not copied.
     *
     * @param newId optional new ID for the reversed edge (auto-generated if not provided)
     * @return new edge instance with vertices swapped
     * @throws IllegalStateException if this edge has been removed
     */
    fun reverse(newId: Any = elementGraph.getNextId()): TinkerEdge {
        checkNotRemoved()
        return elementGraph.addEdge(inVertex, outVertex, elementLabel, mapOf("id" to newId))
    }

    /**
     * Checks if this edge has the same direction as another edge.
     * Two edges have the same direction if they connect the same vertices in the same order.
     *
     * @param other the other edge to compare with
     * @return true if both edges have the same out and in vertices
     * @throws IllegalStateException if this edge has been removed
     */
    fun hasSameDirection(other: Edge): Boolean {
        checkNotRemoved()
        return outVertex == other.outVertex() && inVertex == other.inVertex()
    }

    /**
     * Checks if this edge has the opposite direction of another edge.
     * Two edges have opposite directions if this edge's out vertex is the other's in vertex and vice versa.
     *
     * @param other the other edge to compare with
     * @return true if this edge's out vertex is the other's in vertex and vice versa
     * @throws IllegalStateException if this edge has been removed
     */
    fun hasOppositeDirection(other: Edge): Boolean {
        checkNotRemoved()
        return outVertex == other.inVertex() && inVertex == other.outVertex()
    }

    /**
     * Returns the length of this edge.
     * This is an alias for weight() for graph algorithms that use distance/length terminology.
     *
     * @return the edge length (same as weight)
     * @throws IllegalStateException if this edge has been removed
     */
    fun length(): Double = weight()

    /**
     * Sets the length of this edge.
     * This is an alias for weight(value) for graph algorithms that use distance/length terminology.
     *
     * @param length the length value to set
     * @return the created or updated property
     * @throws IllegalStateException if this edge has been removed
     */
    fun length(length: Double): Property<Double> = weight(length)

    /**
     * Returns edge statistics for debugging and monitoring purposes.
     * The statistics include basic edge information and computed properties.
     *
     * @return map containing edge statistics (id, label, vertices, property count, etc.)
     * @throws IllegalStateException if this edge has been removed
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

    /**
     * Compares this edge to another object for equality.
     * Two edges are equal if they have the same ID.
     *
     * @param other the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false

        other as TinkerEdge
        return elementId == other.elementId
    }

    /**
     * Returns the hash code for this edge.
     * The hash code is based on the element ID.
     *
     * @return the hash code
     */
    override fun hashCode(): Int {
        return elementId.hashCode()
    }

    /**
     * Returns a string representation of this edge.
     * The format is "e[id][outVertex-label->inVertex]".
     *
     * @return string representation of this edge
     */
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
         * Creates an edge with the default label.
         *
         * @param id the unique identifier for the edge
         * @param outVertex the source vertex
         * @param inVertex the target vertex
         * @param graph the TinkerGraph instance
         * @return new TinkerEdge instance with default label
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
         * Creates a weighted edge with the specified weight property.
         *
         * @param id the unique identifier for the edge
         * @param outVertex the source vertex
         * @param inVertex the target vertex
         * @param label the edge label
         * @param weight the weight value to set
         * @param graph the TinkerGraph instance
         * @return new TinkerEdge instance with weight property set
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
