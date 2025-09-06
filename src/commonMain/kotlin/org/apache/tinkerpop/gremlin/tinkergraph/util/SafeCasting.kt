package org.apache.tinkerpop.gremlin.tinkergraph.util

import org.apache.tinkerpop.gremlin.structure.Element
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerEdge

/**
 * Utility object for safe type casting across different Kotlin platforms.
 * Provides platform-safe alternatives to direct casting that may fail in Kotlin/JS.
 */
object SafeCasting {

    /**
     * Safely cast an Element to TinkerVertex.
     * Returns null if the cast is not possible.
     */
    fun asTinkerVertex(element: Any?): TinkerVertex? {
        return when {
            element == null -> null
            element is TinkerVertex -> element
            element is Vertex -> {
                // Try to check if it's actually a TinkerVertex instance
                try {
                    if (element::class.simpleName == "TinkerVertex") {
                        element as? TinkerVertex
                    } else {
                        null
                    }
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
    fun asTinkerEdge(element: Any?): TinkerEdge? {
        return when {
            element == null -> null
            element is TinkerEdge -> element
            element is Edge -> {
                try {
                    if (element::class.simpleName == "TinkerEdge") {
                        element as? TinkerEdge
                    } else {
                        null
                    }
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
    fun Sequence<*>.mapToTinkerVertices(): Sequence<TinkerVertex> {
        return this.mapNotNull { asTinkerVertex(it) }
    }

    /**
     * Map a sequence of elements to TinkerEdge, filtering out any that can't be cast.
     */
    fun Sequence<*>.mapToTinkerEdges(): Sequence<TinkerEdge> {
        return this.mapNotNull { asTinkerEdge(it) }
    }

    /**
     * Map an iterator of elements to TinkerVertex, filtering out any that can't be cast.
     */
    fun Iterator<*>.mapToTinkerVertices(): Iterator<TinkerVertex> {
        return this.asSequence().mapToTinkerVertices().iterator()
    }

    /**
     * Map an iterator of elements to TinkerEdge, filtering out any that can't be cast.
     */
    fun Iterator<*>.mapToTinkerEdges(): Iterator<TinkerEdge> {
        return this.asSequence().mapToTinkerEdges().iterator()
    }

    /**
     * Safely cast a value to Comparable for use in indices.
     * This provides better cross-platform compatibility than direct casting.
     */
    fun asComparable(value: Any?): Comparable<Any>? {
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
