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
 * JavaScript-specific implementation of SafeCasting utility.
 * Provides safer type casting for the JavaScript runtime where reflection is limited.
 */
actual object SafeCasting {

    /**
     * Safely cast an Element to TinkerVertex.
     * In JavaScript, we rely on structural typing and duck typing.
     */
    actual fun asTinkerVertex(element: Any?): TinkerVertex? {
        return when {
            element == null -> null
            element is TinkerVertex -> element
            element is Vertex -> {
                // In JavaScript, use unsafeCast for better compatibility
                try {
                    element.unsafeCast<TinkerVertex>()
                } catch (e: Exception) {
                    null
                }
            }
            else -> {
                // Try to check if it has vertex-like properties
                try {
                    val dynamic = element.asDynamic()
                    if (dynamic.id != null && dynamic.label != null) {
                        element.unsafeCast<TinkerVertex>()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Safely cast an Element to TinkerEdge.
     */
    actual fun asTinkerEdge(element: Any?): TinkerEdge? {
        return when {
            element == null -> null
            element is TinkerEdge -> element
            element is Edge -> {
                try {
                    element.unsafeCast<TinkerEdge>()
                } catch (e: Exception) {
                    null
                }
            }
            else -> {
                try {
                    val dynamic = element.asDynamic()
                    if (dynamic.id != null && dynamic.label != null && dynamic.inVertex != null && dynamic.outVertex != null) {
                        element.unsafeCast<TinkerEdge>()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
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
     */
    actual fun asTinkerVertexProperty(property: Any?): TinkerVertexProperty<*>? {
        return when {
            property == null -> null
            property is TinkerVertexProperty<*> -> property
            else -> {
                try {
                    property.unsafeCast<TinkerVertexProperty<*>>()
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
        return asTinkerVertexProperty(property)?.unsafeCast<TinkerVertexProperty<V>>()
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
                    graph.unsafeCast<TinkerGraph>()
                } catch (e: Exception) {
                    null
                }
            }
            else -> {
                try {
                    val dynamic = graph.asDynamic()
                    if (dynamic.vertices != null && dynamic.edges != null) {
                        graph.unsafeCast<TinkerGraph>()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Safely cast a value to Comparable for use in indices.
     * JavaScript has more flexible type coercion.
     */
    actual fun asComparable(value: Any?): Comparable<Any>? {
        return when (value) {
            null -> null
            is String -> value.unsafeCast<Comparable<Any>>()
            is Number -> {
                // In JavaScript, all numbers are doubles
                val doubleValue = value.toDouble()
                doubleValue.unsafeCast<Comparable<Any>>()
            }
            is Boolean -> value.unsafeCast<Comparable<Any>>()
            else -> {
                // Try to convert to string for comparison in JavaScript
                try {
                    val stringValue = value.toString()
                    stringValue.unsafeCast<Comparable<Any>>()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * JavaScript-specific helper to safely access dynamic properties.
     */
    fun safeDynamicAccess(obj: Any?, property: String): Any? {
        return try {
            obj?.asDynamic()?.get(property)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * JavaScript-specific helper to check if an object has a property.
     */
    fun hasDynamicProperty(obj: Any?, property: String): Boolean {
        return try {
            val dynamic = obj?.asDynamic()
            js("property in dynamic").unsafeCast<Boolean>()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * JavaScript-specific helper for numeric comparisons.
     * Handles the fact that JavaScript treats all numbers as doubles.
     */
    fun compareNumbers(a: Any?, b: Any?): Int {
        return try {
            val numA = when (a) {
                is Number -> a.toDouble()
                is String -> a.toDouble()
                else -> return 0
            }
            val numB = when (b) {
                is Number -> b.toDouble()
                is String -> b.toDouble()
                else -> return 0
            }
            numA.compareTo(numB)
        } catch (e: Exception) {
            0
        }
    }

    /**
     * JavaScript-specific helper for safe string operations.
     */
    fun safeStringOperation(value: Any?, operation: (String) -> Boolean): Boolean {
        return try {
            val stringValue = value?.toString() ?: return false
            operation(stringValue)
        } catch (e: Exception) {
            false
        }
    }
}
