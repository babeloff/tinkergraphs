package org.apache.tinkerpop.gremlin.structure

/**
 * A VertexProperty is similar to a Property in that it denotes a key/value pair associated with an Element,
 * however it is different in the sense that it also represents an entity that it is an Element that can have
 * properties of its own.
 *
 * A VertexProperty has a label, a key, a value, and a set of key/value properties.
 * Thus, a VertexProperty extends both Element and Property.
 */
interface VertexProperty<V> : Element, Property<V> {

    /**
     * Get the Vertex that owns this VertexProperty.
     * @return the vertex that owns this property
     */
    override fun element(): Vertex

    /**
     * Get a property of this VertexProperty.
     * @param key the property key
     * @return the property value or null if not found
     */
    override fun <U> value(key: String): U?

    /**
     * Add or set a property on this VertexProperty.
     * @param key the property key
     * @param value the property value
     * @return the created property
     */
    override fun <U> property(key: String, value: U): Property<U>

    /**
     * Get the properties of this VertexProperty.
     * @param propertyKeys optional property keys to filter
     * @return iterator of properties
     */
    override fun <U> properties(vararg propertyKeys: String): Iterator<Property<U>>

    /**
     * Get all the values of VertexProperties given their key.
     * @param key the key of the vertex property
     * @return iterator of vertex property values
     */
    fun <U> values(key: String): Iterator<U>

    /**
     * Cardinality determines the number of values a single key can have.
     */
    enum class Cardinality {
        /**
         * Only one value per key is allowed (default behavior).
         */
        SINGLE,

        /**
         * Multiple values per key are allowed (List semantics).
         */
        LIST,

        /**
         * Multiple values per key are allowed (Set semantics).
         */
        SET
    }

    /**
     * A collection of exceptions that are thrown by VertexProperty
     */
    object Exceptions {
        fun multiPropertiesNotSupported(): UnsupportedOperationException =
            UnsupportedOperationException("Multiple properties on a vertex is not supported")

        fun metaPropertiesNotSupported(): UnsupportedOperationException =
            UnsupportedOperationException("Properties on a vertex property is not supported")

        fun userSuppliedIdsNotSupported(): UnsupportedOperationException =
            UnsupportedOperationException("VertexProperty does not support user supplied identifiers")

        fun userSuppliedIdsOfThisTypeNotSupported(): UnsupportedOperationException =
            UnsupportedOperationException("VertexProperty does not support user supplied identifiers of this type")

        fun identicalMultiPropertiesNotSupported(): UnsupportedOperationException =
            UnsupportedOperationException("Multiple properties on a vertex with identical key and value is not supported")
    }

    companion object {
        /**
         * Create an empty vertex property that represents the absence of a value.
         */
        fun <V> empty(): VertexProperty<V> = EmptyVertexProperty()

        /**
         * A VertexProperty implementation that denotes an empty vertex property.
         */
        private class EmptyVertexProperty<V> : VertexProperty<V> {
            override fun key(): String = throw NoSuchElementException("VertexProperty key is not present")
            override fun value(): V = throw NoSuchElementException("VertexProperty value is not present")
            override fun isPresent(): Boolean = false
            override fun element(): Vertex = throw NoSuchElementException("VertexProperty vertex is not present")
            override fun remove() = throw NoSuchElementException("VertexProperty is empty")

            override fun id(): Any? = throw NoSuchElementException("VertexProperty id is not present")
            override fun label(): String = throw NoSuchElementException("VertexProperty label is not present")
            override fun graph(): Graph = throw NoSuchElementException("VertexProperty graph is not present")
            override fun keys(): Set<String> = emptySet()
            override fun <U> value(key: String): U? = null
            override fun <U> properties(vararg propertyKeys: String): Iterator<Property<U>> = emptyList<Property<U>>().iterator()
            override fun <U> property(key: String): Property<U> = Property.empty()
            override fun <U> property(key: String, value: U): Property<U> = Property.empty()
            override fun <U> values(key: String): Iterator<U> = emptyList<U>().iterator()

            override fun equals(other: Any?): Boolean {
                return other is VertexProperty<*> && !other.isPresent()
            }

            override fun hashCode(): Int = 0
            override fun toString(): String = "vp[empty]"
        }
    }
}
