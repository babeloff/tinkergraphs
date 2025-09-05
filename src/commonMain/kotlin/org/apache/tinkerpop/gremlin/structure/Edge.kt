package org.apache.tinkerpop.gremlin.structure

/**
 * An Edge links two Vertex objects. Along with its Property objects, an Edge has both
 * a Direction and a label. The Direction determines which Vertex is the tail Vertex
 * (out Vertex) and which Vertex is the head Vertex (in Vertex).
 * The Edge label determines the type of relationship that exists between the two vertices.
 *
 * Diagrammatically:
 * outVertex ---label---> inVertex.
 */
interface Edge : Element {

    /**
     * Get the outgoing/tail vertex of this edge.
     * @return the outgoing vertex
     */
    fun outVertex(): Vertex

    /**
     * Get the incoming/head vertex of this edge.
     * @return the incoming vertex
     */
    fun inVertex(): Vertex

    /**
     * Get the vertex for the specified direction.
     * @param direction the direction of the vertex to retrieve
     * @return the vertex in the specified direction
     */
    fun vertex(direction: Direction): Vertex

    /**
     * Get both vertices of this edge as an iterator.
     * @param direction the direction to traverse
     * @return iterator containing the vertices
     */
    fun vertices(direction: Direction): Iterator<Vertex>

    /**
     * Get both vertices of this edge.
     * @return iterator containing both vertices
     */
    fun bothVertices(): Iterator<Vertex>

    /**
     * Get the other vertex from this edge given a vertex.
     * @param vertex the vertex to find the other vertex from
     * @return the other vertex
     */
    fun otherVertex(vertex: Vertex): Vertex

    /**
     * Default edge label
     */
    companion object {
        const val DEFAULT_LABEL = "edge"
    }

    /**
     * A collection of exceptions that are thrown by Edges
     */
    object Exceptions {
        fun userSuppliedIdsNotSupported(): UnsupportedOperationException =
            UnsupportedOperationException("Edge does not support user supplied identifiers")

        fun userSuppliedIdsOfThisTypeNotSupported(): UnsupportedOperationException =
            UnsupportedOperationException("Edge does not support user supplied identifiers of this type")

        fun edgeRemovalNotSupported(): UnsupportedOperationException =
            UnsupportedOperationException("Edge removal is not supported")

        fun edgeAdditionNotSupported(): UnsupportedOperationException =
            UnsupportedOperationException("Edge addition is not supported")
    }
}
