package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*

/**
 * TinkerVertexProperty is the vertex property implementation for TinkerGraph.
 * It extends both Element and Property interfaces, allowing properties to have
 * their own properties (meta-properties).
 */
class TinkerVertexProperty<V>(
    id: Any,
    private val vertex: TinkerVertex,
    private val propertyKey: String,
    private val propertyValue: V
) : VertexProperty<V>, TinkerElement(id, VertexProperty::class.simpleName ?: "vertexproperty", vertex.graph() as TinkerGraph) {

    /**
     * Flag to track if this vertex property has been removed.
     */
    private var propertyRemoved: Boolean = false

    override fun key(): String = propertyKey

    override fun value(): V = propertyValue

    override fun isPresent(): Boolean = !propertyRemoved

    override fun element(): Vertex = vertex

    override fun remove() {
        checkNotRemoved()
        vertex.removeVertexProperty(this)
        propertyRemoved = true
        markRemoved()
    }

    override fun <U> value(key: String): U? {
        checkNotRemoved()
        return super.value(key)
    }

    override fun <U> property(key: String, value: U): Property<U> {
        checkNotRemoved()

        if (!elementGraph.features().vertex().supportsMetaProperties()) {
            throw VertexProperty.Exceptions.metaPropertiesNotSupported()
        }

        return super.property(key, value)
    }

    override fun <U> properties(vararg propertyKeys: String): Iterator<Property<U>> {
        checkNotRemoved()
        return super.properties(*propertyKeys)
    }

    override fun keys(): Set<String> {
        checkNotRemoved()
        return super.keys()
    }

    override fun <U> values(key: String): Iterator<U> {
        checkNotRemoved()

        @Suppress("UNCHECKED_CAST")
        val property = elementProperties[key] as? Property<U>
        return if (property?.isPresent() == true) {
            listOf(property.value()).iterator()
        } else {
            emptyList<U>().iterator()
        }
    }

    /**
     * Get all meta-property values for a given key.
     */
    fun <U> getMetaPropertyValues(key: String): List<U> {
        checkNotRemoved()
        @Suppress("UNCHECKED_CAST")
        val property = elementProperties[key] as? Property<U>
        return if (property?.isPresent() == true) {
            listOf(property.value())
        } else {
            emptyList()
        }
    }

    /**
     * Get the vertex that owns this property.
     */
    fun vertex(): TinkerVertex = vertex

    /**
     * Get the cardinality of this vertex property.
     * For TinkerGraph, cardinality is determined by the graph's default setting.
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
     * Check if this vertex property has any meta-properties.
     */
    fun hasMetaProperties(): Boolean {
        checkNotRemoved()
        return elementProperties.isNotEmpty()
    }

    /**
     * Get the number of meta-properties.
     */
    fun metaPropertyCount(): Int {
        checkNotRemoved()
        return elementProperties.size
    }

    /**
     * Create a copy of this vertex property with the same value and meta-properties.
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
     * Compare this vertex property with another for value equality.
     */
    fun valueEquals(other: VertexProperty<*>): Boolean {
        return propertyKey == other.key() && propertyValue == other.value()
    }

    /**
     * Get a hash code based on key and value only (ignoring meta-properties).
     */
    fun valueHashCode(): Int {
        var result = propertyKey.hashCode()
        result = 31 * result + (propertyValue?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VertexProperty<*>) return false

        // VertexProperty equality is based on id
        return this.id() == other.id()
    }

    override fun hashCode(): Int {
        return elementId.hashCode()
    }

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
         * Create a simple vertex property without meta-properties.
         */
        fun <V> of(vertex: TinkerVertex, key: String, value: V): TinkerVertexProperty<V> {
            val graph = vertex.graph() as TinkerGraph
            return TinkerVertexProperty(graph.getNextId(), vertex, key, value)
        }

        /**
         * Create a vertex property with specified ID.
         */
        fun <V> of(id: Any, vertex: TinkerVertex, key: String, value: V): TinkerVertexProperty<V> {
            return TinkerVertexProperty(id, vertex, key, value)
        }
    }
}
