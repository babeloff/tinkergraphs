package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*

/**
 * Base class for TinkerGraph elements (vertices and edges).
 * Provides common functionality for property management and element identification.
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

    override fun id(): Any = elementId

    override fun label(): String = elementLabel

    override fun graph(): Graph = elementGraph

    override fun keys(): Set<String> = elementProperties.keys.toSet()

    @Suppress("UNCHECKED_CAST")
    override fun <V> value(key: String): V? {
        checkRemoved()
        val property = elementProperties[key] as? Property<V>
        return if (property?.isPresent() == true) property.value() else null
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V> properties(vararg propertyKeys: String): Iterator<Property<V>> {
        checkRemoved()

        val keys = if (propertyKeys.isEmpty()) {
            elementProperties.keys
        } else {
            propertyKeys.toSet()
        }

        return keys
            .mapNotNull { key -> elementProperties[key] as? Property<V> }
            .filter { it.isPresent() }
            .iterator()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V> property(key: String): Property<V> {
        checkRemoved()
        val property = elementProperties[key] as? Property<V>
        return if (property?.isPresent() == true) property else Property.empty()
    }

    override fun <V> property(key: String, value: V): Property<V> {
        checkRemoved()
        validateProperty(key, value)

        val property = TinkerProperty(key, value, this)
        elementProperties[key] = property

        // Update indices if this is a vertex
        if (this is TinkerVertex) {
            elementGraph.vertexIndex.autoUpdate(key, value, null, this)
        } else if (this is TinkerEdge) {
            elementGraph.edgeIndex.autoUpdate(key, value, null, this)
        }

        return property
    }

    /**
     * Remove a property from this element.
     * @param key the property key to remove
     */
    fun removeProperty(key: String) {
        checkRemoved()
        val oldProperty = elementProperties.remove(key)

        // Update indices if this is a vertex and property existed
        if (oldProperty != null && oldProperty.isPresent()) {
            if (this is TinkerVertex) {
                elementGraph.vertexIndex.autoUpdate(key, null, oldProperty.value(), this)
            } else if (this is TinkerEdge) {
                elementGraph.edgeIndex.autoUpdate(key, null, oldProperty.value(), this)
            }
        }
    }

    /**
     * Get all property values as a map.
     * @return map of property key-value pairs
     */
    fun valueMap(): Map<String, Any?> {
        checkRemoved()
        return elementProperties.mapValues { (_, property) ->
            if (property.isPresent()) property.value() else null
        }
    }

    /**
     * Check if this element has been removed and throw exception if so.
     */
    protected fun checkRemoved() {
        if (removed) {
            throw Element.Exceptions.elementAlreadyRemoved(this::class.simpleName ?: "Element", elementId)
        }
    }

    /**
     * Mark this element as removed.
     */
    internal fun markRemoved() {
        removed = true
    }

    /**
     * Get the internal properties map for iterator support.
     * This method is used by the TinkerGraph iterators for efficient property access.
     */
    internal fun getProperties(): Map<String, Property<*>> {
        return elementProperties
    }

    /**
     * Check if this element is removed (for iterator filtering).
     */
    internal fun isRemoved(): Boolean {
        return removed
    }

    /**
     * Validate property key and value.
     */
    private fun validateProperty(key: String, value: Any?) {
        if (key.isBlank()) {
            throw Element.Exceptions.propertyKeyCanNotBeEmpty()
        }
        if (value == null && !elementGraph.allowNullPropertyValues) {
            throw Element.Exceptions.propertyValueCanNotBeNull()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false

        other as TinkerElement
        return elementId == other.elementId
    }

    override fun hashCode(): Int {
        return elementId.hashCode()
    }

    override fun toString(): String {
        val className = this::class.simpleName?.lowercase() ?: "element"
        return "$className[$elementId]"
    }

    /**
     * TinkerGraph-specific property implementation.
     */
    class TinkerProperty<V>(
        private val propertyKey: String,
        private val propertyValue: V,
        private val propertyElement: Element
    ) : Property<V> {

        override fun key(): String = propertyKey

        override fun value(): V = propertyValue

        override fun isPresent(): Boolean = true

        override fun element(): Element = propertyElement

        override fun remove() {
            if (propertyElement is TinkerElement) {
                propertyElement.removeProperty(propertyKey)
            } else {
                throw Property.Exceptions.propertyRemovalNotSupported()
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Property<*>) return false

            return propertyKey == other.key() &&
                   propertyValue == other.value() &&
                   propertyElement == other.element()
        }

        override fun hashCode(): Int {
            var result = propertyKey.hashCode()
            result = 31 * result + (propertyValue?.hashCode() ?: 0)
            result = 31 * result + propertyElement.hashCode()
            return result
        }

        override fun toString(): String = "p[$propertyKey->$propertyValue]"
    }
}
