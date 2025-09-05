package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*

/**
 * Base class for TinkerGraph elements (vertices and edges).
 * Provides common functionality for property management and element identification.
 *
 * This abstract class implements the core Element interface and provides:
 * - Property management with automatic index updates
 * - Element lifecycle management (creation, removal, validation)
 * - Integration with TinkerGraph's indexing system
 * - Common operations shared between vertices and edges
 *
 * @param elementId the unique identifier for this element
 * @param elementLabel the label assigned to this element
 * @param elementGraph the TinkerGraph instance that owns this element
 * @since 1.0.0
 */
abstract class TinkerElement(
    protected val elementId: Any,
    protected val elementLabel: String,
    protected val elementGraph: TinkerGraph
) : Element {

    /**
     * Properties associated with this element.
     * Key is property name, value is the property object.
     */
    protected val elementProperties: MutableMap<String, Property<*>> = mutableMapOf()

    /**
     * Flag to track if this element has been removed from the graph.
     */
    protected var removed: Boolean = false

    /**
     * Returns the unique identifier of this element.
     * @return the element's identifier
     */
    override fun id(): Any = elementId

    /**
     * Returns the label of this element.
     * @return the element's label
     */
    override fun label(): String = elementLabel

    /**
     * Returns the graph that owns this element.
     * @return the parent TinkerGraph instance
     */
    override fun graph(): Graph = elementGraph

    /**
     * Returns all property keys for this element.
     * @return set of property keys
     */
    override fun keys(): Set<String> = elementProperties.keys.toSet()

    /**
     * Returns the value of a property or null if the property doesn't exist.
     * @param V the expected type of the property value
     * @param key the property key
     * @return the property value or null if not present
     * @throws IllegalStateException if this element has been removed
     */
    override fun <V> value(key: String): V? {
        checkRemoved()
        return try {
            @Suppress("UNCHECKED_CAST") // Safe cast - Property interface guarantees type consistency
            val property = elementProperties[key] as? Property<V>
            if (property?.isPresent() == true) property.value() else null
        } catch (e: ClassCastException) {
            null
        }
    }

    /**
     * Returns an iterator over properties with the specified keys.
     * If no keys are specified, returns all properties.
     * @param V the expected type of the property values
     * @param propertyKeys the keys of properties to include (empty means all)
     * @return iterator over matching properties
     * @throws IllegalStateException if this element has been removed
     */
    override fun <V> properties(vararg propertyKeys: String): Iterator<Property<V>> {
        checkRemoved()

        val keys = if (propertyKeys.isEmpty()) {
            elementProperties.keys
        } else {
            propertyKeys.toSet()
        }

        return keys
            .mapNotNull { key ->
                try {
                    @Suppress("UNCHECKED_CAST") // Safe cast - Property interface guarantees type consistency
                    elementProperties[key] as? Property<V>
                } catch (e: ClassCastException) {
                    null
                }
            }
            .filter { it.isPresent() }
            .iterator()
    }

    /**
     * Returns the property for the specified key, or an empty property if not found.
     * @param V the expected type of the property value
     * @param key the property key
     * @return the property or an empty property if not present
     * @throws IllegalStateException if this element has been removed
     */
    override fun <V> property(key: String): Property<V> {
        checkRemoved()
        return try {
            @Suppress("UNCHECKED_CAST") // Safe cast - Property interface guarantees type consistency
            val existingProperty = elementProperties[key] as? Property<V>
            existingProperty ?: Property.empty()
        } catch (e: ClassCastException) {
            Property.empty()
        }
    }

    /**
     * Sets a property on this element and updates all relevant indices.
     * @param V the type of the property value
     * @param key the property key
     * @param value the property value
     * @return the created property
     * @throws IllegalStateException if this element has been removed
     * @throws IllegalArgumentException if the key is blank or value is null when not allowed
     */
    override fun <V> property(key: String, value: V): Property<V> {
        checkRemoved()
        validateProperty(key, value)

        val property = TinkerProperty(key, value, this)
        elementProperties[key] = property

        // Update all indices if this is a vertex
        if (this is TinkerVertex) {
            elementGraph.vertexIndex.autoUpdate(key, value, null, this)
            elementGraph.vertexCompositeIndex.autoUpdate(key, this)
            elementGraph.vertexRangeIndex.autoUpdate(key, value, null, this)
            elementGraph.vertexIndexCache.invalidateKey(key)
        } else if (this is TinkerEdge) {
            elementGraph.edgeIndex.autoUpdate(key, value, null, this)
            elementGraph.edgeCompositeIndex.autoUpdate(key, this)
            elementGraph.edgeRangeIndex.autoUpdate(key, value, null, this)
            elementGraph.edgeIndexCache.invalidateKey(key)
        }

        return property
    }

    /**
     * Removes a property from this element and updates all relevant indices.
     * If the property doesn't exist, this is a no-op.
     *
     * @param key the property key to remove
     * @throws IllegalStateException if this element has been removed
     */
    fun removeProperty(key: String) {
        checkRemoved()
        val oldProperty = elementProperties.remove(key)

        // Update all indices if this is a vertex and property existed
        if (oldProperty != null && oldProperty.isPresent()) {
            if (this is TinkerVertex) {
                elementGraph.vertexIndex.autoUpdate(key, null, oldProperty.value(), this)
                elementGraph.vertexCompositeIndex.autoUpdate(key, this)
                elementGraph.vertexRangeIndex.autoUpdate(key, null, oldProperty.value(), this)
                elementGraph.vertexIndexCache.invalidateKey(key)
            } else if (this is TinkerEdge) {
                elementGraph.edgeIndex.autoUpdate(key, null, oldProperty.value(), this)
                elementGraph.edgeCompositeIndex.autoUpdate(key, this)
                elementGraph.edgeRangeIndex.autoUpdate(key, null, oldProperty.value(), this)
                elementGraph.edgeIndexCache.invalidateKey(key)
            }
        }
    }

    /**
     * Returns all property values as a map of key-value pairs.
     * Properties that are not present will have null values in the map.
     *
     * @return map of property key-value pairs
     * @throws IllegalStateException if this element has been removed
     */
    fun valueMap(): Map<String, Any?> {
        checkRemoved()
        return elementProperties.mapValues { (_, property) ->
            if (property.isPresent()) property.value() else null
        }
    }

    /**
     * Verifies that this element has not been removed from the graph.
     * @throws IllegalStateException if this element has been removed
     */
    protected fun checkRemoved() {
        if (removed) {
            throw Element.Exceptions.elementAlreadyRemoved(this::class.simpleName ?: "Element", elementId)
        }
    }

    /**
     * Marks this element as removed from the graph.
     * This is called internally when the element is removed from the graph.
     */
    internal fun markRemoved() {
        removed = true
    }

    /**
     * Returns the internal properties map for iterator support.
     * This method is used by the TinkerGraph iterators for efficient property access
     * without creating defensive copies.
     *
     * @return the internal properties map
     */
    internal open fun getProperties(): Map<String, Property<*>> {
        return elementProperties
    }

    /**
     * Returns whether this element has been removed from the graph.
     * This is used by iterators for filtering removed elements.
     *
     * @return true if this element has been removed, false otherwise
     */
    internal fun isRemoved(): Boolean {
        return removed
    }

    /**
     * Validates a property key and value according to graph constraints.
     *
     * @param key the property key to validate
     * @param value the property value to validate
     * @throws IllegalArgumentException if the key is blank
     * @throws IllegalArgumentException if the value is null and null values are not allowed
     */
    private fun validateProperty(key: String, value: Any?) {
        if (key.isBlank()) {
            throw Element.Exceptions.propertyKeyCanNotBeEmpty()
        }
        if (value == null && !elementGraph.allowNullPropertyValues) {
            throw Element.Exceptions.propertyValueCanNotBeNull()
        }
    }

    /**
     * Compares this element to another object for equality.
     * Two elements are equal if they have the same ID and are of the same type.
     *
     * @param other the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false

        other as TinkerElement
        return elementId == other.elementId
    }

    /**
     * Returns the hash code for this element.
     * The hash code is based on the element ID.
     *
     * @return the hash code
     */
    override fun hashCode(): Int {
        return elementId.hashCode()
    }

    /**
     * Returns a string representation of this element.
     * The format is "elementtype[id]" (e.g., "vertex[1]" or "edge[2]").
     *
     * @return string representation of this element
     */
    override fun toString(): String {
        val className = this::class.simpleName?.lowercase() ?: "element"
        return "$className[$elementId]"
    }

    /**
     * TinkerGraph-specific property implementation.
     *
     * This class represents a property attached to a graph element (vertex or edge).
     * It provides the standard Property interface functionality and integrates
     * with TinkerGraph's property management system.
     *
     * @param V the type of the property value
     * @param propertyKey the key identifying this property
     * @param propertyValue the value stored in this property
     * @param propertyElement the element that owns this property
     */
    class TinkerProperty<V>(
        private val propertyKey: String,
        private val propertyValue: V,
        private val propertyElement: Element
    ) : Property<V> {

        private var removed: Boolean = false

        /**
         * Returns the key of this property.
         * @return the property key
         */
        override fun key(): String = propertyKey

        /**
         * Returns the value of this property.
         * @return the property value
         */
        override fun value(): V = propertyValue

        /**
         * Returns whether this property is present (not removed).
         * @return true if the property is present, false if removed
         */
        override fun isPresent(): Boolean = !removed

        /**
         * Returns the element that owns this property.
         * @return the owning element
         */
        override fun element(): Element = propertyElement

        /**
         * Removes this property from its owning element.
         * @throws UnsupportedOperationException if property removal is not supported
         */
        override fun remove() {
            if (propertyElement is TinkerElement) {
                propertyElement.removeProperty(propertyKey)
                removed = true
            } else {
                throw Property.Exceptions.propertyRemovalNotSupported()
            }
        }

        /**
         * Compares this property to another object for equality.
         * Two properties are equal if they have the same key, value, and element.
         *
         * @param other the object to compare with
         * @return true if the objects are equal, false otherwise
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Property<*>) return false

            return propertyKey == other.key() &&
                   propertyValue == other.value() &&
                   propertyElement == other.element()
        }

        /**
         * Returns the hash code for this property.
         * The hash code is based on the key, value, and element.
         *
         * @return the hash code
         */
        override fun hashCode(): Int {
            var result = propertyKey.hashCode()
            result = 31 * result + (propertyValue?.hashCode() ?: 0)
            result = 31 * result + propertyElement.hashCode()
            return result
        }

        /**
         * Returns a string representation of this property.
         * The format is "p[key->value]".
         *
         * @return string representation of this property
         */
        override fun toString(): String = "p[$propertyKey->$propertyValue]"
    }
}
