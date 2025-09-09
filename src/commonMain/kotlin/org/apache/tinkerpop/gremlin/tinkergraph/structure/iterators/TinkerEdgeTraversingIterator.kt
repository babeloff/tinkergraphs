package org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators

import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex

/**
 * A memory-efficient iterator for edge traversal in TinkerGraph.
 * This iterator traverses edges from a source vertex in the specified direction
 * with optional label filtering, without creating intermediate collections.
 *
 * @param sourceVertex the vertex to traverse from
 * @param direction the direction to traverse (OUT, IN, or BOTH)
 * @param edgeLabels optional array of edge labels to filter by
 */
class TinkerEdgeTraversingIterator(
    private val sourceVertex: TinkerVertex,
    private val direction: Direction,
    private val edgeLabels: Array<out String> = emptyArray()
) : Iterator<Edge> {

    private val edgeIterator: Iterator<Edge> = TinkerEdgeIterator.fromVertex(
        sourceVertex,
        direction,
        *edgeLabels
    )

    override fun hasNext(): Boolean = edgeIterator.hasNext()

    override fun next(): Edge = edgeIterator.next()

    companion object {
        /**
         * Creates a TinkerEdgeTraversingIterator for traversing edges in a specific direction.
         *
         * @param vertex the source vertex
         * @param direction the direction to traverse (OUT, IN, or BOTH)
         * @param edgeLabels optional edge labels to filter by
         * @return iterator over edges
         */
        fun byDirection(
            vertex: Vertex,
            direction: Direction = Direction.BOTH,
            vararg edgeLabels: String
        ): TinkerEdgeTraversingIterator {
            require(vertex is TinkerVertex) { "Vertex must be a TinkerVertex" }
            return TinkerEdgeTraversingIterator(vertex, direction, edgeLabels)
        }

        /**
         * Creates a TinkerEdgeTraversingIterator for traversing all edges from a vertex.
         *
         * @param vertex the source vertex
         * @param edgeLabels optional edge labels to filter by
         * @return iterator over all edges
         */
        fun fromVertex(
            vertex: Vertex,
            vararg edgeLabels: String
        ): TinkerEdgeTraversingIterator {
            return byDirection(vertex, Direction.BOTH, *edgeLabels)
        }

        /**
         * Creates a TinkerEdgeTraversingIterator for traversing outgoing edges from a vertex.
         *
         * @param vertex the source vertex
         * @param edgeLabels optional edge labels to filter by
         * @return iterator over outgoing edges
         */
        fun outgoing(
            vertex: Vertex,
            vararg edgeLabels: String
        ): TinkerEdgeTraversingIterator {
            return byDirection(vertex, Direction.OUT, *edgeLabels)
        }

        /**
         * Creates a TinkerEdgeTraversingIterator for traversing incoming edges to a vertex.
         *
         * @param vertex the source vertex
         * @param edgeLabels optional edge labels to filter by
         * @return iterator over incoming edges
         */
        fun incoming(
            vertex: Vertex,
            vararg edgeLabels: String
        ): TinkerEdgeTraversingIterator {
            return byDirection(vertex, Direction.IN, *edgeLabels)
        }
    }
}
