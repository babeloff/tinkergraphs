package org.apache.tinkerpop.gremlin.structure

/**
 * Direction is used to denote the direction of an Edge or location of a Vertex on an Edge.
 * For example: gremlin> g.V(1).outE() will return the outgoing edges of vertex 1.
 * gremlin> g.V(1).inE() will return the incoming edges of vertex 1.
 */
enum class Direction {
    /**
     * Refers to an outgoing direction from a vertex.
     */
    OUT,

    /**
     * Refers to an incoming direction to a vertex.
     */
    IN,

    /**
     * Refers to either direction (both in and out).
     */
    BOTH;

    /**
     * Get the opposite direction.
     * @return the opposite direction
     */
    fun opposite(): Direction {
        return when (this) {
            OUT -> IN
            IN -> OUT
            BOTH -> BOTH
        }
    }

    companion object {
        /**
         * Determine the direction of a vertex on an edge.
         * @param vertex the vertex to check
         * @param edge the edge to check
         * @return the direction of the vertex on the edge
         */
        fun from(vertex: Vertex, edge: Edge): Direction {
            return when {
                edge.outVertex() == vertex -> OUT
                edge.inVertex() == vertex -> IN
                else -> throw IllegalArgumentException("The provided vertex is not incident to the provided edge")
            }
        }
    }
}
