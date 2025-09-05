package org.apache.tinkerpop.gremlin.structure

/**
 * A Property denotes a key/value pair associated with an Element.
 * A property is much like a Java8 Optional in that a property can be not present.
 * The key of a Property is always a String and the value of a Property is an arbitrary Object.
 * Each underlying graph implementation should optimize implementations of Property for its specific use cases.
 */
interface Property<V> {

    /**
     * Get the key of this property.
     * @return the property key
     */
    fun key(): String

    /**
     * Get the value of this property.
     * @return the property value
     */
    fun value(): V

    /**
     * Whether the property is present or not.
     * @return true if the property is present
     */
    fun isPresent(): Boolean

    /**
     * Get the element that this property is associated with.
     * @return the associated element
     */
    fun element(): Element

    /**
     * Remove this property from its associated element.
     */
    fun remove()

    /**
     * Determine if this property is equal to another property.
     * Properties are equal if their keys and values are equal and they belong to the same element.
     */
    override fun equals(other: Any?): Boolean

    /**
     * Get the hash code of this property.
     */
    override fun hashCode(): Int

    /**
     * String representation of this property.
     */
    override fun toString(): String

    companion object {
        /**
         * Create an empty property that represents the absence of a value.
         */
        fun <V> empty(): Property<V> = EmptyProperty()

        /**
         * A Property implementation that denotes an empty property.
         */
        private class EmptyProperty<V> : Property<V> {
            override fun key(): String = throw NoSuchElementException("Property key is not present")
            override fun value(): V = throw NoSuchElementException("Property value is not present")
            override fun isPresent(): Boolean = false
            override fun element(): Element = throw NoSuchElementException("Property element is not present")
            override fun remove() = throw NoSuchElementException("Property is empty")

            override fun equals(other: Any?): Boolean {
                return other is Property<*> && !other.isPresent()
            }

            override fun hashCode(): Int = 0
            override fun toString(): String = "p[empty]"
        }
    }

    /**
     * A collection of exceptions that are thrown by Properties
     */
    object Exceptions {
        fun propertyRemovalNotSupported(): UnsupportedOperationException =
            UnsupportedOperationException("Property removal is not supported")

        fun propertyDoesNotExist(): IllegalStateException =
            IllegalStateException("Property does not exist")

        fun propertyKeyCanNotBeNull(): IllegalArgumentException =
            IllegalArgumentException("Property key can not be null")

        fun propertyKeyCanNotBeEmpty(): IllegalArgumentException =
            IllegalArgumentException("Property key can not be empty")

        fun propertyValueCanNotBeNull(): IllegalArgumentException =
            IllegalArgumentException("Property value can not be null")
    }
}
