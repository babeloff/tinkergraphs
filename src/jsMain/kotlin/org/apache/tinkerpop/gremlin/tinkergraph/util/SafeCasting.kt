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
 *
 * This implementation avoids using unsafeCast which can cause ClassCastExceptions
 * in the JavaScript runtime, and instead relies on structural checks and safer
 * type coercion patterns.
 */
actual object SafeCasting {

    /**
     * Safely cast an Element to TinkerVertex.
     * Uses structural typing checks instead of unsafe casting.
     */
    actual fun asTinkerVertex(element: Any?): TinkerVertex? {
        return when {
            element == null -> null
            element is TinkerVertex -> element
            element is Vertex -> {
                // Check if it's already a TinkerVertex by checking its class name
                val className = element::class.simpleName
                if (className == "TinkerVertex") {
                    // It's already a TinkerVertex, just return it as is
                    element as? TinkerVertex
                } else {
                    // It's a different Vertex implementation, can't safely cast
                    null
                }
            }
            else -> {
                // Check if it has vertex-like structure using safe property access
                try {
                    val dynamic = element.asDynamic()
                    val hasId = js("'id' in dynamic && typeof dynamic.id !== 'undefined'") as Boolean
                    val hasLabel = js("'label' in dynamic && typeof dynamic.label !== 'undefined'") as Boolean

                    if (hasId && hasLabel) {
                        // Try to treat it as a TinkerVertex
                        element as? TinkerVertex
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
                val className = element::class.simpleName
                if (className == "TinkerEdge") {
                    element as? TinkerEdge
                } else {
                    null
                }
            }
            else -> {
                try {
                    val dynamic = element.asDynamic()
                    val hasId = js("'id' in dynamic && typeof dynamic.id !== 'undefined'") as Boolean
                    val hasLabel = js("'label' in dynamic && typeof dynamic.label !== 'undefined'") as Boolean
                    val hasInVertex = js("'inVertex' in dynamic && typeof dynamic.inVertex !== 'undefined'") as Boolean
                    val hasOutVertex = js("'outVertex' in dynamic && typeof dynamic.outVertex !== 'undefined'") as Boolean

                    if (hasId && hasLabel && hasInVertex && hasOutVertex) {
                        element as? TinkerEdge
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
                val className = property::class.simpleName
                if (className == "TinkerVertexProperty") {
                    property as? TinkerVertexProperty<*>
                } else {
                    null
                }
            }
        }
    }

    /**
     * Safely cast a VertexProperty to TinkerVertexProperty with type check.
     */
    actual fun <V> safeCastVertexProperty(property: org.apache.tinkerpop.gremlin.structure.VertexProperty<V>): TinkerVertexProperty<V> {
        val tinkerProperty = asTinkerVertexProperty(property)
        if (tinkerProperty != null) {
            @Suppress("UNCHECKED_CAST")
            return tinkerProperty as TinkerVertexProperty<V>
        } else {
            throw IllegalStateException("Expected TinkerVertexProperty but got ${property::class.simpleName}")
        }
    }

    /**
     * Safely cast the result of addVertex() to TinkerVertex.
     */
    actual fun safeCastVertex(vertex: Vertex): TinkerVertex {
        return asTinkerVertex(vertex) ?: throw IllegalStateException("Expected TinkerVertex but got ${vertex::class.simpleName}")
    }

    /**
     * Safely cast the result of addEdge() to TinkerEdge.
     */
    actual fun safeCastEdge(edge: Edge): TinkerEdge {
        return asTinkerEdge(edge) ?: throw IllegalStateException("Expected TinkerEdge but got ${edge::class.simpleName}")
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
        return sequence.mapNotNull { asTinkerVertex(it) }.firstOrNull { vertex ->
            try {
                predicate(vertex)
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Find first TinkerVertex by name property.
     */
    actual fun findVertexByName(sequence: Sequence<*>, name: String): TinkerVertex? {
        return findTinkerVertex(sequence) {
            try {
                val nameValue = it.value<String>("name")
                nameValue == name
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
                val className = graph::class.simpleName
                if (className == "TinkerGraph") {
                    graph as? TinkerGraph
                } else {
                    null
                }
            }
            else -> {
                try {
                    val dynamic = graph.asDynamic()
                    val hasVertices = js("'vertices' in dynamic && typeof dynamic.vertices === 'function'") as Boolean
                    val hasEdges = js("'edges' in dynamic && typeof dynamic.edges === 'function'") as Boolean

                    if (hasVertices && hasEdges) {
                        graph as? TinkerGraph
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
            is String -> JSComparableWrapper(value)
            is Number -> JSComparableWrapper(value.toDouble())
            is Boolean -> JSComparableWrapper(value)
            else -> {
                // Try to convert to string for comparison in JavaScript
                try {
                    JSComparableWrapper(value.toString())
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * JavaScript-specific wrapper for making values comparable.
     * This avoids casting issues by implementing comparison logic directly.
     */
    private class JSComparableWrapper(private val value: Any) : Comparable<Any> {
        override fun compareTo(other: Any): Int {
            return when {
                other is JSComparableWrapper -> compareValues(value, other.value)
                else -> compareValues(value, other)
            }
        }

        private fun compareValues(a: Any, b: Any): Int {
            return try {
                when {
                    a is Number && b is Number -> {
                        a.toDouble().compareTo(b.toDouble())
                    }
                    a is String && b is String -> {
                        a.compareTo(b)
                    }
                    a is Boolean && b is Boolean -> {
                        a.compareTo(b)
                    }
                    else -> {
                        // Fall back to string comparison
                        a.toString().compareTo(b.toString())
                    }
                }
            } catch (e: Exception) {
                0 // Return equal if comparison fails
            }
        }

        override fun toString(): String = value.toString()
        override fun equals(other: Any?): Boolean {
            return when (other) {
                is JSComparableWrapper -> value == other.value
                else -> value == other
            }
        }
        override fun hashCode(): Int = value.hashCode()
    }

    /**
     * JavaScript-specific helper to safely access dynamic properties.
     */
    fun safeDynamicAccess(obj: Any?, property: String): Any? {
        return try {
            if (obj == null) return null
            val dynamic = obj.asDynamic()
            val hasProperty = js("property in dynamic") as Boolean
            if (hasProperty) {
                js("dynamic[property]")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * JavaScript-specific helper to check if an object has a property.
     */
    fun hasDynamicProperty(obj: Any?, property: String): Boolean {
        return try {
            if (obj == null) return false
            val dynamic = obj.asDynamic()
            js("property in dynamic && typeof dynamic[property] !== 'undefined'") as Boolean
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
                is String -> {
                    val parsed = a.toDoubleOrNull()
                    parsed ?: return a.compareTo(b.toString())
                }
                else -> return 0
            }
            val numB = when (b) {
                is Number -> b.toDouble()
                is String -> {
                    val parsed = b.toDoubleOrNull()
                    parsed ?: return a.toString().compareTo(b)
                }
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
            when (value) {
                null -> false
                is String -> operation(value)
                else -> {
                    val stringValue = value.toString()
                    operation(stringValue)
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Safe type checking for JavaScript runtime.
     */
    fun isInstanceOf(obj: Any?, expectedType: String): Boolean {
        return try {
            when (expectedType) {
                "TinkerVertex" -> obj is TinkerVertex
                "TinkerEdge" -> obj is TinkerEdge
                "TinkerGraph" -> obj is TinkerGraph
                "TinkerVertexProperty" -> obj is TinkerVertexProperty<*>
                else -> obj?.let { it::class.simpleName == expectedType } ?: false
            }
        } catch (e: Exception) {
            false
        }
    }
}
