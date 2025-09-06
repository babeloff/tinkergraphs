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
        return try {
            when {
                element == null -> null
                element is TinkerVertex -> element
                element is Vertex -> {
                    // Check if it's already a TinkerVertex by checking its class name
                    try {
                        val className = element::class.simpleName
                        if (className == "TinkerVertex") {
                            // It's already a TinkerVertex, just return it as is
                            element as? TinkerVertex
                        } else {
                            // It's a different Vertex implementation, can't safely cast
                            null
                        }
                    } catch (e: Exception) {
                        // Fallback: try direct cast if class name check fails
                        element as? TinkerVertex
                    }
                }
                else -> {
                    // Check if it has vertex-like structure using safe property access
                    try {
                        val dynamic = element.asDynamic()
                        val hasId = js("'id' in dynamic && typeof dynamic.id !== 'undefined'") as? Boolean ?: false
                        val hasLabel = js("'label' in dynamic && typeof dynamic.label !== 'undefined'") as? Boolean ?: false

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
        } catch (e: Exception) {
            console.warn("asTinkerVertex failed for element: $element, error: ${e.message}")
            null
        }
    }

    /**
     * Safely cast an Element to TinkerEdge.
     */
    actual fun asTinkerEdge(element: Any?): TinkerEdge? {
        return try {
            when {
                element == null -> null
                element is TinkerEdge -> element
                element is Edge -> {
                    try {
                        val className = element::class.simpleName
                        if (className == "TinkerEdge") {
                            element as? TinkerEdge
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        // Fallback: try direct cast if class name check fails
                        element as? TinkerEdge
                    }
                }
                else -> {
                    try {
                        val dynamic = element.asDynamic()
                        val hasId = js("'id' in dynamic && typeof dynamic.id !== 'undefined'") as? Boolean ?: false
                        val hasLabel = js("'label' in dynamic && typeof dynamic.label !== 'undefined'") as? Boolean ?: false
                        val hasInVertex = js("'inVertex' in dynamic && typeof dynamic.inVertex !== 'undefined'") as? Boolean ?: false
                        val hasOutVertex = js("'outVertex' in dynamic && typeof dynamic.outVertex !== 'undefined'") as? Boolean ?: false

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
        } catch (e: Exception) {
            console.warn("asTinkerEdge failed for element: $element, error: ${e.message}")
            null
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
        return try {
            asTinkerVertex(vertex) ?: throw IllegalStateException("Expected TinkerVertex but got ${vertex::class.simpleName}")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to cast vertex: ${e.message}", e)
        }
    }

    /**
     * Safely cast the result of addEdge() to TinkerEdge.
     */
    actual fun safeCastEdge(edge: Edge): TinkerEdge {
        return try {
            asTinkerEdge(edge) ?: throw IllegalStateException("Expected TinkerEdge but got ${edge::class.simpleName}")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to cast edge: ${e.message}", e)
        }
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
            is Number -> {
                try {
                    JSComparableWrapper(value.toDouble())
                } catch (e: Exception) {
                    // Handle case where Number conversion fails in JS
                    JSComparableWrapper(value.toString())
                }
            }
            is Boolean -> JSComparableWrapper(value)
            else -> {
                // Try to convert to string for comparison in JavaScript
                try {
                    val stringValue = value.toString()
                    // Check if it looks like a number and convert if so
                    val numValue = stringValue.toDoubleOrNull()
                    if (numValue != null) {
                        JSComparableWrapper(numValue)
                    } else {
                        JSComparableWrapper(stringValue)
                    }
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
                // Handle JavaScript's dynamic typing more carefully
                when {
                    isNumeric(a) && isNumeric(b) -> {
                        val numA = toSafeDouble(a)
                        val numB = toSafeDouble(b)
                        when {
                            numA == null && numB == null -> 0
                            numA == null -> -1
                            numB == null -> 1
                            else -> numA.compareTo(numB)
                        }
                    }
                    a is String && b is String -> a.compareTo(b)
                    a is Boolean && b is Boolean -> a.compareTo(b)
                    else -> {
                        // Fall back to string comparison with null safety
                        val strA = a.toString()
                        val strB = b.toString()
                        strA.compareTo(strB)
                    }
                }
            } catch (e: Exception) {
                // Log the exception for debugging but don't let it propagate
                console.warn("Comparison failed between $a and $b: ${e.message}")
                0 // Return equal if comparison fails
            }
        }

        private fun isNumeric(value: Any): Boolean {
            return try {
                when (value) {
                    is Number -> true
                    is String -> value.toDoubleOrNull() != null
                    else -> false
                }
            } catch (e: Exception) {
                false
            }
        }

        private fun toSafeDouble(value: Any): Double? {
            return try {
                when (value) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull()
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }

        override fun toString(): String = try {
            value.toString()
        } catch (e: Exception) {
            "JSComparableWrapper(error)"
        }

        override fun equals(other: Any?): Boolean {
            return try {
                when (other) {
                    is JSComparableWrapper -> safeEquals(value, other.value)
                    else -> safeEquals(value, other)
                }
            } catch (e: Exception) {
                false
            }
        }

        private fun safeEquals(a: Any?, b: Any?): Boolean {
            return try {
                when {
                    a === b -> true
                    a == null || b == null -> a == b
                    isNumeric(a) && isNumeric(b) -> {
                        val numA = toSafeDouble(a)
                        val numB = toSafeDouble(b)
                        numA != null && numB != null && numA == numB
                    }
                    else -> a == b
                }
            } catch (e: Exception) {
                false
            }
        }

        override fun hashCode(): Int = try {
            when {
                isNumeric(value) -> toSafeDouble(value)?.hashCode() ?: 0
                else -> value.hashCode()
            }
        } catch (e: Exception) {
            0
        }
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

    /**
     * Safe equality comparison that handles JavaScript type coercion properly.
     */
    fun safeEquals(a: Any?, b: Any?): Boolean {
        return try {
            when {
                a === b -> true
                a == null || b == null -> a == b
                // Handle numeric comparisons in JavaScript
                isNumericValue(a) && isNumericValue(b) -> {
                    val numA = toNumericValue(a)
                    val numB = toNumericValue(b)
                    numA != null && numB != null && numA == numB
                }
                // Handle string comparisons
                a is String && b is String -> a == b
                // Handle boolean comparisons
                a is Boolean && b is Boolean -> a == b
                // Fall back to standard equality
                else -> a == b
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if a value can be treated as numeric in JavaScript.
     */
    private fun isNumericValue(value: Any?): Boolean {
        return try {
            when (value) {
                is Number -> true
                is String -> value.toDoubleOrNull() != null
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Convert a value to a numeric value if possible.
     */
    private fun toNumericValue(value: Any?): Double? {
        return try {
            when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
