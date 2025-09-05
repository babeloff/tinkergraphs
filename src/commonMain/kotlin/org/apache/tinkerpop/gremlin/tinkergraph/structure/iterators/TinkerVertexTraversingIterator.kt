package org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators

import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex

/**
 * A memory-efficient iterator for vertex-to-vertex traversal in TinkerGraph.
 * This iterator traverses from a source vertex to connected vertices by following
 * edges in the specified direction and with optional label filtering, without
 * creating intermediate collections.
 *
 * @param sourceVertex the vertex to traverse from
 * @param direction the direction to traverse (OUT, IN, or BOTH)
 * @param edgeLabels optional array of edge labels to filter by
 */
class TinkerVertexTraversingIterator(
    private val sourceVertex: TinkerVertex,
    private val direction: Direction,
    private val edgeLabels: Array<out String> = emptyArray()
) : Iterator<Vertex> {

    private val edgeIterator: Iterator<Edge> = TinkerEdgeIterator.fromVertex(
        sourceVertex,
        direction,
        *edgeLabels
    )

    private val visitedVertices = mutableSetOf<Any>()
    private var nextVertex: Vertex? = null
    private var hasComputedNext = false

    override fun hasNext(): Boolean {
        if (!hasComputedNext) {
            computeNext()
            hasComputedNext = true
        }
        return nextVertex != null
    }

    override fun next(): Vertex {
        if (!hasNext()) {
            throw NoSuchElementException("No more vertices available")
        }

        val result = nextVertex!!
        hasComputedNext = false
        nextVertex = null
        return result
    }

    /**
     * Computes the next unique vertex in the traversal.
     * Ensures no duplicate vertices are returned when traversing BOTH directions.
     */
    private fun computeNext() {
        while (edgeIterator.hasNext()) {
            val edge = edgeIterator.next()
            val targetVertex = when (direction) {
                Direction.OUT -> edge.inVertex()
                Direction.IN -> edge.outVertex()
                Direction.BOTH -> {
                    // For BOTH direction, get the vertex that is not the source
                    if (edge.outVertex() == sourceVertex) {
                        edge.inVertex()
                    } else {
                        edge.outVertex()
                    }
                }
            }

            // Skip if we've already seen this vertex (prevents duplicates in BOTH direction)
            val vertexId = targetVertex.id()!!
            if (vertexId !in visitedVertices) {
                visitedVertices.add(vertexId)
                nextVertex = targetVertex
                return
            }
        }

        // No more vertices found
        nextVertex = null
    }

    companion object {
        /**
         * Creates a vertex traversing iterator for outgoing edges.
         */
        fun outVertices(sourceVertex: TinkerVertex, vararg edgeLabels: String): TinkerVertexTraversingIterator {
            return TinkerVertexTraversingIterator(sourceVertex, Direction.OUT, edgeLabels)
        }

        /**
         * Creates a vertex traversing iterator for incoming edges.
         */
        fun inVertices(sourceVertex: TinkerVertex, vararg edgeLabels: String): TinkerVertexTraversingIterator {
            return TinkerVertexTraversingIterator(sourceVertex, Direction.IN, edgeLabels)
        }

        /**
         * Creates a vertex traversing iterator for both incoming and outgoing edges.
         */
        fun bothVertices(sourceVertex: TinkerVertex, vararg edgeLabels: String): TinkerVertexTraversingIterator {
            return TinkerVertexTraversingIterator(sourceVertex, Direction.BOTH, edgeLabels)
        }

        /**
         * Creates a vertex traversing iterator with custom direction and labels.
         */
        fun traverse(
            sourceVertex: TinkerVertex,
            direction: Direction,
            vararg edgeLabels: String
        ): TinkerVertexTraversingIterator {
            return TinkerVertexTraversingIterator(sourceVertex, direction, edgeLabels)
        }
    }
}
