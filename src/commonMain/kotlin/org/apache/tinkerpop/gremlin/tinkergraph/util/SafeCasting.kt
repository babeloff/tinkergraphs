package org.apache.tinkerpop.gremlin.tinkergraph.util

import org.apache.tinkerpop.gremlin.structure.Element
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerEdge
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertexProperty
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

/**
 * Utility object for safe type casting across different Kotlin platforms.
 * Provides platform-safe alternatives to direct casting that may fail in Kotlin/JS.
 */
expect object SafeCasting {

    /**
     * Safely cast an Element to TinkerVertex.
     * Returns null if the cast is not possible.
     */
    fun asTinkerVertex(element: Any?): TinkerVertex?

    /**
     * Safely cast an Element to TinkerEdge.
     * Returns null if the cast is not possible.
     */
    fun asTinkerEdge(element: Any?): TinkerEdge?

    /**
     * Map a sequence of elements to TinkerVertex, filtering out any that can't be cast.
     */
    fun Sequence<*>.mapToTinkerVertices(): Sequence<TinkerVertex>

    /**
     * Map a sequence of elements to TinkerEdge, filtering out any that can't be cast.
     */
    fun Sequence<*>.mapToTinkerEdges(): Sequence<TinkerEdge>

    /**
     * Map an iterator of elements to TinkerVertex, filtering out any that can't be cast.
     */
    fun Iterator<*>.mapToTinkerVertices(): Iterator<TinkerVertex>

    /**
     * Map an iterator of elements to TinkerEdge, filtering out any that can't be cast.
     */
    fun Iterator<*>.mapToTinkerEdges(): Iterator<TinkerEdge>

    /**
     * Safely cast a VertexProperty to TinkerVertexProperty.
     * Returns null if the cast is not possible.
     */
    fun asTinkerVertexProperty(property: Any?): TinkerVertexProperty<*>?

    /**
     * Safely cast a VertexProperty to TinkerVertexProperty with type check.
     */
    fun <V> safeCastVertexProperty(property: org.apache.tinkerpop.gremlin.structure.VertexProperty<V>): TinkerVertexProperty<V>

    /**
     * Safely cast the result of addVertex() to TinkerVertex.
     */
    fun safeCastVertex(vertex: Vertex): TinkerVertex

    /**
     * Safely cast the result of addEdge() to TinkerEdge.
     */
    fun safeCastEdge(edge: Edge): TinkerEdge

    /**
     * Helper for vertex iteration with safe casting.
     */
    fun Iterator<Vertex>.asTinkerVertices(): Iterator<TinkerVertex>

    /**
     * Helper for edge iteration with safe casting.
     */
    fun Iterator<Edge>.asTinkerEdges(): Iterator<TinkerEdge>

    /**
     * Find first TinkerVertex in sequence by predicate.
     */
    fun findTinkerVertex(sequence: Sequence<*>, predicate: (TinkerVertex) -> Boolean): TinkerVertex?

    /**
     * Find first TinkerVertex by name property.
     */
    fun findVertexByName(sequence: Sequence<*>, name: String): TinkerVertex?

    /**
     * Safely cast a Graph to TinkerGraph.
     * Returns null if the cast is not possible.
     */
    fun asTinkerGraph(graph: Any?): TinkerGraph?

    /**
     * Safely cast a value to Comparable for use in indices.
     * This provides better cross-platform compatibility than direct casting.
     */
    fun asComparable(value: Any?): Comparable<Any>?
}
