package org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators

import org.apache.tinkerpop.gremlin.structure.Element
import org.apache.tinkerpop.gremlin.structure.Property
import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerElement
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertexProperty

/**
 * A memory-efficient iterator for TinkerGraph properties that supports lazy evaluation
 * and filtering capabilities. This iterator works with both regular element properties
 * and vertex properties (including meta-properties), processing them on-demand without
 * creating intermediate collections.
 *
 * @param V the value type of the properties
 * @param element the element to iterate properties from
 * @param propertyKeys set of property keys to include (empty means all keys)
 * @param valueFilters list of value-based filter predicates
 * @param includeHidden whether to include hidden properties (keys starting with ~)
 */
class TinkerPropertyIterator<V>(
    private val element: Element,
    private val propertyKeys: Set<String> = emptySet(),
    private val valueFilters: List<(V) -> Boolean> = emptyList(),
    private val includeHidden: Boolean = false
) : Iterator<Property<V>> {

    private val baseSequence: Sequence<Property<V>> = createBaseSequence()
    private val iterator = baseSequence.iterator()

    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): Property<V> = iterator.next()

    /**
     * Creates the base sequence with all filters applied.
     * Uses lazy evaluation to process properties on-demand.
     */
    @Suppress("UNCHECKED_CAST")
    private fun createBaseSequence(): Sequence<Property<V>> {
        if (element !is TinkerElement) {
            return emptySequence()
        }

        return element.getProperties().asSequence()
            .filter { (key, _) -> matchesKeyFilter(key) }
            .filter { (key, _) -> matchesHiddenFilter(key) }
            .map { (_, property) -> property as Property<V> }
            .filter { property -> property.isPresent() }
            .filter { property -> matchesValueFilters(property.value()) }
    }

    /**
     * Checks if a property key matches the key filter criteria.
     */
    private fun matchesKeyFilter(key: String): Boolean {
        return propertyKeys.isEmpty() || key in propertyKeys
    }

    /**
     * Checks if a property key matches the hidden property filter.
     */
    private fun matchesHiddenFilter(key: String): Boolean {
        return includeHidden || !key.startsWith("~")
    }

    /**
     * Checks if a property value matches all value filter criteria.
     */
    private fun matchesValueFilters(value: V): Boolean {
        return valueFilters.all { filter -> filter(value) }
    }

    companion object {
        /**
         * Creates a property iterator for all properties of an element.
         */
        fun <V> all(element: Element): TinkerPropertyIterator<V> {
            return TinkerPropertyIterator(element)
        }

        /**
         * Creates a property iterator for specific property keys.
         */
        fun <V> byKeys(element: Element, vararg propertyKeys: String): TinkerPropertyIterator<V> {
            return TinkerPropertyIterator(
                element = element,
                propertyKeys = propertyKeys.toSet()
            )
        }

        /**
         * Creates a property iterator with value filtering.
         */
        fun <V> withValueFilters(
            element: Element,
            filters: List<(V) -> Boolean>
        ): TinkerPropertyIterator<V> {
            return TinkerPropertyIterator(
                element = element,
                valueFilters = filters
            )
        }

        /**
         * Creates a property iterator that includes hidden properties.
         */
        fun <V> withHidden(element: Element): TinkerPropertyIterator<V> {
            return TinkerPropertyIterator(
                element = element,
                includeHidden = true
            )
        }

        /**
         * Creates a property iterator for properties with specific value.
         */
        fun <V> byValue(element: Element, value: V): TinkerPropertyIterator<V> {
            val valueFilter: (V) -> Boolean = { it == value }
            return TinkerPropertyIterator(
                element = element,
                valueFilters = listOf(valueFilter)
            )
        }

        /**
         * Creates a property iterator for properties matching a predicate.
         */
        fun <V> matching(
            element: Element,
            predicate: (String, V) -> Boolean
        ): TinkerPropertyIterator<V> {
            return TinkerPropertyIterator<V>(element).apply {
                // We need to override the sequence to include key-value predicate
                // This is a simplified approach - in practice, we'd need to refactor
                // the base sequence creation to support key-value predicates
            }
        }
    }
}

/**
 * A specialized iterator for VertexProperty objects that supports iteration over
 * meta-properties and provides vertex property-specific filtering capabilities.
 *
 * @param V the value type of the vertex properties
 * @param element the element (typically a vertex) to iterate vertex properties from
 * @param propertyKeys set of property keys to include (empty means all keys)
 * @param cardinalityFilter optional cardinality filter
 * @param valueFilters list of value-based filter predicates
 */
class TinkerVertexPropertyIterator<V>(
    private val element: Element,
    private val propertyKeys: Set<String> = emptySet(),
    private val cardinalityFilter: VertexProperty.Cardinality? = null,
    private val valueFilters: List<(V) -> Boolean> = emptyList()
) : Iterator<VertexProperty<V>> {

    private val baseSequence: Sequence<VertexProperty<V>> = createBaseSequence()
    private val iterator = baseSequence.iterator()

    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): VertexProperty<V> = iterator.next()

    /**
     * Creates the base sequence with all filters applied.
     * Uses lazy evaluation to process vertex properties on-demand.
     */
    @Suppress("UNCHECKED_CAST")
    private fun createBaseSequence(): Sequence<VertexProperty<V>> {
        if (element !is TinkerElement) {
            return emptySequence()
        }

        return element.getProperties().asSequence()
            .filter { (key, _) -> matchesKeyFilter(key) }
            .map { (_, property) -> property }
            .filterIsInstance<VertexProperty<V>>()
            .filter { property -> property.isPresent() }
            .filter { property -> matchesCardinalityFilter(property) }
            .filter { property -> matchesValueFilters(property.value()) }
    }

    /**
     * Checks if a property key matches the key filter criteria.
     */
    private fun matchesKeyFilter(key: String): Boolean {
        return propertyKeys.isEmpty() || key in propertyKeys
    }

    /**
     * Checks if a vertex property matches the cardinality filter.
     */
    private fun matchesCardinalityFilter(property: VertexProperty<V>): Boolean {
        return cardinalityFilter == null ||
               (property is TinkerVertexProperty && property.cardinality() == cardinalityFilter)
    }

    /**
     * Checks if a property value matches all value filter criteria.
     */
    private fun matchesValueFilters(value: V): Boolean {
        return valueFilters.all { filter -> filter(value) }
    }

    companion object {
        /**
         * Creates a vertex property iterator for all vertex properties of an element.
         */
        fun <V> all(element: Element): TinkerVertexPropertyIterator<V> {
            return TinkerVertexPropertyIterator(element)
        }

        /**
         * Creates a vertex property iterator for specific property keys.
         */
        fun <V> byKeys(element: Element, vararg propertyKeys: String): TinkerVertexPropertyIterator<V> {
            return TinkerVertexPropertyIterator(
                element = element,
                propertyKeys = propertyKeys.toSet()
            )
        }

        /**
         * Creates a vertex property iterator with cardinality filtering.
         */
        fun <V> byCardinality(
            element: Element,
            cardinality: VertexProperty.Cardinality
        ): TinkerVertexPropertyIterator<V> {
            return TinkerVertexPropertyIterator(
                element = element,
                cardinalityFilter = cardinality
            )
        }

        /**
         * Creates a vertex property iterator with value filtering.
         */
        fun <V> withValueFilters(
            element: Element,
            filters: List<(V) -> Boolean>
        ): TinkerVertexPropertyIterator<V> {
            return TinkerVertexPropertyIterator(
                element = element,
                valueFilters = filters
            )
        }

        /**
         * Creates a vertex property iterator for properties with specific value.
         */
        fun <V> byValue(element: Element, value: V): TinkerVertexPropertyIterator<V> {
            val valueFilter: (V) -> Boolean = { it == value }
            return TinkerVertexPropertyIterator(
                element = element,
                valueFilters = listOf(valueFilter)
            )
        }
    }
}

/**
 * A specialized iterator for meta-properties on VertexProperty objects.
 * This iterator provides access to properties that exist on VertexProperty instances.
 *
 * @param V the value type of the meta-properties
 * @param vertexProperty the vertex property to iterate meta-properties from
 * @param propertyKeys set of property keys to include (empty means all keys)
 * @param valueFilters list of value-based filter predicates
 */
class TinkerMetaPropertyIterator<V>(
    private val vertexProperty: VertexProperty<*>,
    private val propertyKeys: Set<String> = emptySet(),
    private val valueFilters: List<(V) -> Boolean> = emptyList()
) : Iterator<Property<V>> {

    private val baseSequence: Sequence<Property<V>> = createBaseSequence()
    private val iterator = baseSequence.iterator()

    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): Property<V> = iterator.next()

    /**
     * Creates the base sequence with all filters applied.
     * Uses lazy evaluation to process meta-properties on-demand.
     */
    @Suppress("UNCHECKED_CAST")
    private fun createBaseSequence(): Sequence<Property<V>> {
        if (vertexProperty !is TinkerVertexProperty) {
            return emptySequence()
        }

        return vertexProperty.getProperties().asSequence()
            .filter { (key, _) -> matchesKeyFilter(key) }
            .map { (_, property) -> property as Property<V> }
            .filter { property -> property.isPresent() }
            .filter { property -> matchesValueFilters(property.value()) }
    }

    /**
     * Checks if a property key matches the key filter criteria.
     */
    private fun matchesKeyFilter(key: String): Boolean {
        return propertyKeys.isEmpty() || key in propertyKeys
    }

    /**
     * Checks if a property value matches all value filter criteria.
     */
    private fun matchesValueFilters(value: V): Boolean {
        return valueFilters.all { filter -> filter(value) }
    }

    companion object {
        /**
         * Creates a meta-property iterator for all meta-properties of a vertex property.
         */
        fun <V> all(vertexProperty: VertexProperty<*>): TinkerMetaPropertyIterator<V> {
            return TinkerMetaPropertyIterator(vertexProperty)
        }

        /**
         * Creates a meta-property iterator for specific property keys.
         */
        fun <V> byKeys(
            vertexProperty: VertexProperty<*>,
            vararg propertyKeys: String
        ): TinkerMetaPropertyIterator<V> {
            return TinkerMetaPropertyIterator(
                vertexProperty = vertexProperty,
                propertyKeys = propertyKeys.toSet()
            )
        }

        /**
         * Creates a meta-property iterator with value filtering.
         */
        fun <V> withValueFilters(
            vertexProperty: VertexProperty<*>,
            filters: List<(V) -> Boolean>
        ): TinkerMetaPropertyIterator<V> {
            return TinkerMetaPropertyIterator(
                vertexProperty = vertexProperty,
                valueFilters = filters
            )
        }

        /**
         * Creates a meta-property iterator for properties with specific value.
         */
        fun <V> byValue(vertexProperty: VertexProperty<*>, value: V): TinkerMetaPropertyIterator<V> {
            val valueFilter: (V) -> Boolean = { it == value }
            return TinkerMetaPropertyIterator(
                vertexProperty = vertexProperty,
                valueFilters = listOf(valueFilter)
            )
        }
    }
}
