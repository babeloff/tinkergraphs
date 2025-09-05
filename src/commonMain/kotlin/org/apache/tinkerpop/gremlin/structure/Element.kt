package org.apache.tinkerpop.gremlin.structure

/**
 * An Element has an identifier, label, and collection of key/value properties.
 * An Element can be a Vertex or an Edge.
 */
interface Element {

    /**
     * Get the unique identifier of this element.
     * @return the identifier of this element
     */
    fun id(): Any?

    /**
     * Get the label of this element.
     * @return the label of this element
     */
    fun label(): String

    /**
     * Get the graph that this element belongs to.
     * @return the graph of this element
     */
    fun graph(): Graph

    /**
     * Get the keys of the properties associated with this element.
     * @return the property keys
     */
    fun keys(): Set<String>

    /**
     * Get a property value by key.
     * @param key the property key
     * @return the property value or null if not found
     */
    fun <V> value(key: String): V?

    /**
     * Get all properties of this element.
     * @param propertyKeys optional property keys to filter
     * @return iterator of properties
     */
    fun <V> properties(vararg propertyKeys: String): Iterator<Property<V>>

    /**
     * Add or set a property on this element.
     * @param key the property key
     * @param value the property value
     * @return the created property
     */
    fun <V> property(key: String, value: V): Property<V>

    /**
     * Remove this element from the graph.
     */
    fun remove()

    /**
     * Determine if this element is equal to another element.
     * Elements are equal if their ids are equal.
     */
    override fun equals(other: Any?): Boolean

    /**
     * Get the hash code of this element based on its id.
     */
    override fun hashCode(): Int

    /**
     * Common element labels
     */
    object Labels {
        const val VERTEX = "vertex"
        const val EDGE = "edge"
    }

    /**
     * A collection of exceptions that are thrown by Elements
     */
    object Exceptions {
        fun propertyDoesNotExist(key: String): IllegalArgumentException =
            IllegalArgumentException("Property does not exist: $key")

        fun propertyKeyCanNotBeNull(): IllegalArgumentException =
            IllegalArgumentException("Property key can not be null")

        fun propertyKeyCanNotBeEmpty(): IllegalArgumentException =
            IllegalArgumentException("Property key can not be empty")

        fun propertyValueCanNotBeNull(): IllegalArgumentException =
            IllegalArgumentException("Property value can not be null")

        fun elementAlreadyRemoved(elementClass: String, id: Any?): IllegalStateException =
            IllegalStateException("$elementClass with id has already been removed: $id")
    }
}
