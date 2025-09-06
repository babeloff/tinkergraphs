package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.util.SafeCasting

/**
 * TinkerVertexProperty is the vertex property implementation for TinkerGraph.
 * It extends both Element and Property interfaces, allowing properties to have
 * their own properties (meta-properties).
 *
 * This class provides:
 * - Vertex property functionality with cardinality support
 * - Meta-property management (properties on properties)
 * - Integration with TinkerGraph's indexing and caching systems
 * - Lifecycle management and validation
 *
 * @param V the type of the vertex property value
 * @param id the unique identifier for this vertex property
 * @param vertex the vertex that owns this property
 * @param propertyKey the key identifying this property
 * @param propertyValue the value stored in this property
 * @since 1.0.0
 */
class TinkerVertexProperty<V>(
    id: Any,
    private val vertex: TinkerVertex,
    private val propertyKey: String,
    private val propertyValue: V
) : VertexProperty<V>, TinkerElement(id, VertexProperty::class.simpleName ?: "vertexproperty", SafeCasting.asTinkerGraph(vertex.graph()) ?: throw IllegalStateException("Expected TinkerGraph")) {

    /**
     * Flag to track if this vertex property has been removed.
     */
    private var propertyRemoved: Boolean = false

    /**
     * Returns the key of this vertex property.
     * @return the property key
     */
    override fun key(): String = propertyKey

    /**
     * Returns the value of this vertex property.
     * @return the property value
     */
    override fun value(): V = propertyValue

    /**
     * Returns whether this vertex property is present (not removed).
     * @return true if the property is present, false if removed
     */
    override fun isPresent(): Boolean = !propertyRemoved

    /**
     * Returns the vertex that owns this property.
     * @return the owning vertex
     */
    override fun element(): Vertex = vertex

    /**
     * Removes this vertex property from its owning vertex.
     * This also removes all meta-properties associated with this vertex property.
     * @throws IllegalStateException if this vertex property has already been removed
     */
    override fun remove() {
        checkNotRemoved()
        vertex.removeVertexProperty(this)
        propertyRemoved = true
        markRemoved()
    }

    /**
     * Returns the value of a meta-property or null if the meta-property doesn't exist.
     * @param U the expected type of the meta-property value
     * @param key the meta-property key
     * @return the meta-property value or null if not present
     * @throws IllegalStateException if this vertex property has been removed
     */
    override fun <U> value(key: String): U? {
        checkNotRemoved()
        return super.value(key)
    }

    /**
     * Sets a meta-property on this vertex property.
     * @param U the type of the meta-property value
     * @param key the meta-property key
     * @param value the meta-property value
     * @return the created meta-property
     * @throws IllegalStateException if this vertex property has been removed
     * @throws UnsupportedOperationException if meta-properties are not supported
     * @throws IllegalArgumentException if the key is blank or value is null when not allowed
     */
    override fun <U> property(key: String, value: U): Property<U> {
        checkNotRemoved()

        if (!elementGraph.features().vertex().supportsMetaProperties()) {
            throw VertexProperty.Exceptions.metaPropertiesNotSupported()
        }

        return super.property(key, value)
    }

    /**
     * Returns an iterator over meta-properties with the specified keys.
     * If no keys are specified, returns all meta-properties.
     * @param U the expected type of the meta-property values
     * @param propertyKeys the keys of meta-properties to include (empty means all)
     * @return iterator over matching meta-properties
     * @throws IllegalStateException if this vertex property has been removed
     */
    override fun <U> properties(vararg propertyKeys: String): Iterator<Property<U>> {
        checkNotRemoved()
        return super.properties(*propertyKeys)
    }

    /**
     * Returns all meta-property keys for this vertex property.
     * @return set of meta-property keys
     * @throws IllegalStateException if this vertex property has been removed
     */
    override fun keys(): Set<String> {
        checkNotRemoved()
        return super.keys()
    }

    /**
     * Returns an iterator over values for a specific meta-property key.
     * In TinkerGraph, each meta-property key has at most one value.
     * @param U the expected type of the meta-property values
     * @param key the meta-property key
     * @return iterator over values (empty or single value)
     * @throws IllegalStateException if this vertex property has been removed
     */
    override fun <U> values(key: String): Iterator<U> {
        checkNotRemoved()

        return try {
            @Suppress("UNCHECKED_CAST") // Safe cast - Property interface guarantees type consistency
            val property = elementProperties[key] as? Property<U>
            if (property?.isPresent() == true) {
                listOf(property.value()).iterator()
            } else {
                emptyList<U>().iterator()
            }
        } catch (e: ClassCastException) {
            emptyList<U>().iterator()
        }
    }

    /**
     * Returns all meta-property values for a given key as a list.
     * In TinkerGraph, each meta-property key has at most one value.
     *
     * @param U the expected type of the meta-property values
     * @param key the meta-property key
     * @return list of values (empty or single value)
     * @throws IllegalStateException if this vertex property has been removed
     */
    fun <U> getMetaPropertyValues(key: String): List<U> {
        checkNotRemoved()
        return try {
            @Suppress("UNCHECKED_CAST") // Safe cast - Property interface guarantees type consistency
            val property = elementProperties[key] as? Property<U>
            if (property?.isPresent() == true) {
                listOf(property.value())
            } else {
                emptyList()
            }
        } catch (e: ClassCastException) {
            emptyList()
        }
    }

    /**
     * Returns the vertex that owns this property.
     * This is the same as element() but with the correct return type.
     *
     * @return the owning vertex
     */
    fun vertex(): TinkerVertex = vertex

    /**
     * Returns the cardinality of this vertex property.
     * For TinkerGraph, cardinality is determined by the graph's default setting
     * or the specific cardinality set when the property was created.
     *
     * @return the cardinality of this vertex property
     */
    fun cardinality(): VertexProperty.Cardinality {
        return elementGraph.defaultVertexPropertyCardinality
    }

    /**
     * Check if this vertex property has been removed and throw exception if so.
     */
    private fun checkNotRemoved() {
        if (propertyRemoved) {
            throw IllegalStateException("VertexProperty has been removed")
        }
        checkRemoved() // Check if element is removed
    }

    /**
     * Check if this vertex property is removed (for iterator filtering).
     */
    internal fun isVertexPropertyRemoved(): Boolean {
        return propertyRemoved || super.isRemoved()
    }

    /**
     * Mark this vertex property as removed (internal use).
     */
    internal fun markPropertyRemoved() {
        propertyRemoved = true
    }

    /**
     * Check if this vertex property supports meta-properties.
     */
    fun supportsMetaProperties(): Boolean {
        return elementGraph.features().vertex().supportsMetaProperties()
    }

    /**
     * Get all meta-properties as a map.
     */
    fun metaProperties(): Map<String, Any?> {
        checkNotRemoved()
        return valueMap()
    }

    /**
     * Returns whether this vertex property has any meta-properties.
     *
     * @return true if this vertex property has meta-properties, false otherwise
     * @throws IllegalStateException if this vertex property has been removed
     */
    fun hasMetaProperties(): Boolean {
        checkNotRemoved()
        return elementProperties.isNotEmpty()
    }

    /**
     * Returns the number of meta-properties attached to this vertex property.
     *
     * @return the count of meta-properties
     * @throws IllegalStateException if this vertex property has been removed
     */
    fun metaPropertyCount(): Int {
        checkNotRemoved()
        return elementProperties.size
    }

    /**
     * Creates a copy of this vertex property with the same value and meta-properties.
     * The copy will have a new ID and can optionally belong to a different vertex.
     *
     * @param newId the ID for the copied vertex property (default: auto-generated)
     * @param newVertex the vertex to attach the copy to (default: same vertex)
     * @return a new TinkerVertexProperty with copied data
     * @throws IllegalStateException if this vertex property has been removed
     */
    fun copy(newId: Any = elementGraph.getNextId(), newVertex: TinkerVertex = vertex): TinkerVertexProperty<V> {
        checkNotRemoved()

        val copy = TinkerVertexProperty(newId, newVertex, propertyKey, propertyValue)

        // Copy meta-properties
        elementProperties.forEach { (key, property) ->
            if (property.isPresent()) {
                copy.property(key, property.value())
            }
        }

        return copy
    }

    /**
     * Compares this vertex property with another for value equality.
     * Two vertex properties are value-equal if they have the same key and value,
     * regardless of their IDs or meta-properties.
     *
     * @param other the vertex property to compare with
     * @return true if the key and value are equal, false otherwise
     */
    fun valueEquals(other: VertexProperty<*>): Boolean {
        return propertyKey == other.key() && propertyValue == other.value()
    }

    /**
     * Returns a hash code based on key and value only (ignoring meta-properties).
     * This can be used for value-based hashing when ID-based hashing is not desired.
     *
     * @return hash code based on key and value
     */
    fun valueHashCode(): Int {
        var result = propertyKey.hashCode()
        result = 31 * result + (propertyValue?.hashCode() ?: 0)
        return result
    }

    /**
     * Compares this vertex property to another object for equality.
     * Two vertex properties are equal if they have the same ID.
     *
     * @param other the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VertexProperty<*>) return false

        // VertexProperty equality is based on id
        return this.id() == other.id()
    }

    /**
     * Returns the hash code for this vertex property.
     * The hash code is based on the element ID.
     *
     * @return the hash code
     */
    override fun hashCode(): Int {
        return elementId.hashCode()
    }

    /**
     * Returns a string representation of this vertex property.
     * The format is "vp[key->value[metaprops]]" where metaprops are shown if present.
     *
     * @return string representation of this vertex property
     */
    override fun toString(): String {
        val metaPropsStr = if (hasMetaProperties()) {
            val metaProps = elementProperties.entries.joinToString(",") { "${it.key}=${it.value.value()}" }
            "[$metaProps]"
        } else {
            ""
        }
        return "vp[$propertyKey->$propertyValue$metaPropsStr]"
    }

    companion object {
        /**
         * Creates a simple vertex property without meta-properties.
         * The ID will be auto-generated by the graph.
         *
         * @param V the type of the vertex property value
         * @param vertex the vertex to attach the property to
         * @param key the property key
         * @param value the property value
         * @return a new TinkerVertexProperty instance
         */
        fun <V> of(vertex: TinkerVertex, key: String, value: V): TinkerVertexProperty<V> {
            val graph = SafeCasting.asTinkerGraph(vertex.graph()) ?: throw IllegalStateException("Expected TinkerGraph")
            return TinkerVertexProperty(graph.getNextId(), vertex, key, value)
        }

        /**
         * Creates a vertex property with a specified ID.
         *
         * @param V the type of the vertex property value
         * @param id the unique identifier for the vertex property
         * @param vertex the vertex to attach the property to
         * @param key the property key
         * @param value the property value
         * @return a new TinkerVertexProperty instance
         */
        fun <V> of(id: Any, vertex: TinkerVertex, key: String, value: V): TinkerVertexProperty<V> {
            return TinkerVertexProperty(id, vertex, key, value)
        }
    }
}
