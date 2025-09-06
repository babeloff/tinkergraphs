package org.apache.tinkerpop.gremlin.tinkergraph.util

import org.apache.tinkerpop.gremlin.structure.Element
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerEdge
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertexProperty
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.apache.tinkerpop.gremlin.structure.Graph

/**
 * Native-specific implementation of SafeCasting utility.
 * Uses Kotlin/Native reflection capabilities with safe fallbacks.
 */
actual object SafeCasting {

    /**
     * Safely cast an Element to TinkerVertex.
     * Returns null if the cast is not possible.
     */
    actual fun asTinkerVertex(element: Any?): TinkerVertex? {
        return when {
            element == null -> null
            element is TinkerVertex -> element
            element is Vertex -> {
                // In Native, use direct casting with try-catch
                try {
                    element as? TinkerVertex
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    /**
     * Safely cast an Element to TinkerEdge.
     * Returns null if the cast is not possible.
     */
    actual fun asTinkerEdge(element: Any?): TinkerEdge? {
        return when {
            element == null -> null
            element is TinkerEdge -> element
            element is Edge -> {
                try {
                    element as? TinkerEdge
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    /**
     * Map a sequence of elements to TinkerVertex, filtering out any that can't be cast.
     */
    actual fun Sequence<*>.mapToTinkerVertices(): Sequence<TinkerVertex> {
        return this.mapNotNull { asTinkerVertex(it) }
    }

    /**
     * Map a sequence of elements to TinkerEdge, filtering out any that can't be cast.
     */
    actual fun Sequence<*>.mapToTinkerEdges(): Sequence<TinkerEdge> {
        return this.mapNotNull { asTinkerEdge(it) }
    }

    /**
     * Map an iterator of elements to TinkerVertex, filtering out any that can't be cast.
     */
    actual fun Iterator<*>.mapToTinkerVertices(): Iterator<TinkerVertex> {
        return this.asSequence().mapToTinkerVertices().iterator()
    }

    /**
     * Map an iterator of elements to TinkerEdge, filtering out any that can't be cast.
     */
    actual fun Iterator<*>.mapToTinkerEdges(): Iterator<TinkerEdge> {
        return this.asSequence().mapToTinkerEdges().iterator()
    }

    /**
     * Safely cast a VertexProperty to TinkerVertexProperty.
     * Returns null if the cast is not possible.
     */
    actual fun asTinkerVertexProperty(property: Any?): TinkerVertexProperty<*>? {
        return when {
            property == null -> null
            property is TinkerVertexProperty<*> -> property
            else -> {
                try {
                    property as? TinkerVertexProperty<*>
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Safely cast a VertexProperty to TinkerVertexProperty with type check.
     */
    actual fun <V> safeCastVertexProperty(property: org.apache.tinkerpop.gremlin.structure.VertexProperty<V>): TinkerVertexProperty<V> {
        @Suppress("UNCHECKED_CAST")
        return asTinkerVertexProperty(property) as? TinkerVertexProperty<V>
            ?: throw IllegalStateException("Expected TinkerVertexProperty but got different type")
    }

    /**
     * Safely cast the result of addVertex() to TinkerVertex.
     */
    actual fun safeCastVertex(vertex: Vertex): TinkerVertex {
        return asTinkerVertex(vertex) ?: throw IllegalStateException("Expected TinkerVertex but got different type")
    }

    /**
     * Safely cast the result of addEdge() to TinkerEdge.
     */
    actual fun safeCastEdge(edge: Edge): TinkerEdge {
        return asTinkerEdge(edge) ?: throw IllegalStateException("Expected TinkerEdge but got different type")
    }

    /**
     * Helper for vertex iteration with safe casting.
     */
    actual fun Iterator<Vertex>.asTinkerVertices(): Iterator<TinkerVertex> {
        return this.asSequence().mapNotNull { asTinkerVertex(it) }.iterator()
    }

    /**
     * Helper for edge iteration with safe casting.
     */
    actual fun Iterator<Edge>.asTinkerEdges(): Iterator<TinkerEdge> {
        return this.asSequence().mapNotNull { asTinkerEdge(it) }.iterator()
    }

    /**
     * Find first TinkerVertex in sequence by predicate.
     */
    actual fun findTinkerVertex(sequence: Sequence<*>, predicate: (TinkerVertex) -> Boolean): TinkerVertex? {
        return sequence.mapNotNull { asTinkerVertex(it) }.firstOrNull(predicate)
    }

    /**
     * Find first TinkerVertex by name property.
     */
    actual fun findVertexByName(sequence: Sequence<*>, name: String): TinkerVertex? {
        return findTinkerVertex(sequence) {
            try {
                it.value<String>("name") == name
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Safely cast a Graph to TinkerGraph.
     */
    actual fun asTinkerGraph(graph: Any?): TinkerGraph? {
        return when {
            graph == null -> null
            graph is TinkerGraph -> graph
            graph is Graph -> {
                try {
                    graph as? TinkerGraph
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    /**
     * Safely cast a value to Comparable for use in indices.
     * Native has stricter type checking than JVM but more flexible than JS.
     */
    actual fun asComparable(value: Any?): Comparable<Any>? {
        return when (value) {
            null -> null
            is String -> value as Comparable<Any>
            is Int -> value as Comparable<Any>
            is Long -> value as Comparable<Any>
            is Float -> value as Comparable<Any>
            is Double -> value as Comparable<Any>
            is Boolean -> value as Comparable<Any>
            is Short -> value as Comparable<Any>
            is Byte -> value as Comparable<Any>
            is Char -> value as Comparable<Any>
            // For other Comparable types, try safe casting
            is Comparable<*> -> {
                try {
                    @Suppress("UNCHECKED_CAST")
                    value as? Comparable<Any>
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
}
