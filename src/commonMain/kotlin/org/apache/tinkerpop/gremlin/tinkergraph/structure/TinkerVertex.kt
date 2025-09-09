package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators.TinkerEdgeIterator
import org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators.TinkerVertexTraversingIterator
import org.apache.tinkerpop.gremlin.tinkergraph.util.LoggingConfig

/**
 * TinkerVertex is the vertex implementation for TinkerGraph.
 * It maintains adjacency lists for incoming and outgoing edges and supports
 * vertex properties with different cardinalities.
 */
class TinkerVertex(
    id: Any,
    label: String,
    graph: TinkerGraph
) : TinkerElement(id, label, graph), Vertex {

    companion object {
        private val logger = LoggingConfig.getLogger<TinkerVertex>()
    }

    /**
     * Outgoing edges organized by label.
     * Maps edge label to a mutable set of edges.
     */
    private val outEdges: MutableMap<String, MutableSet<TinkerEdge>> = mutableMapOf()

    /**
     * Incoming edges organized by label.
     * Maps edge label to a mutable set of edges.
     */
    private val inEdges: MutableMap<String, MutableSet<TinkerEdge>> = mutableMapOf()

    /**
     * Vertex properties organized by key.
     * Maps property key to a list of vertex properties (supporting multi-properties).
     */
    private val vertexProperties: MutableMap<String, MutableList<TinkerVertexProperty<*>>> = mutableMapOf()

    /**
     * Cardinality tracking for property keys.
     * Maps property key to its cardinality.
     */
    private val propertyCardinalities: MutableMap<String, VertexProperty.Cardinality> = mutableMapOf()

    override fun addEdge(label: String, inVertex: Vertex, vararg keyValues: Any?): Edge {
        val properties = ElementHelper.asMap(keyValues)
        return addEdge(label, inVertex, properties)
    }

    override fun addEdge(label: String, inVertex: Vertex, properties: Map<String, Any?>): Edge {
        checkRemoved()

        if (inVertex !is TinkerVertex) {
            throw IllegalArgumentException("Target vertex must be a TinkerVertex")
        }

        return elementGraph.addEdge(this, inVertex, label, properties)
    }

    override fun edges(direction: Direction, vararg edgeLabels: String): Iterator<Edge> {
        checkRemoved()
        return TinkerEdgeIterator.fromVertex(this, direction, *edgeLabels)
    }

    override fun vertices(direction: Direction, vararg edgeLabels: String): Iterator<Vertex> {
        checkRemoved()
        return TinkerVertexTraversingIterator.traverse(this, direction, *edgeLabels)
    }

    override fun <V> property(key: String, value: V, vararg keyValues: Any?): VertexProperty<V> {
        checkRemoved()
        ElementHelper.validateProperty(key, value, elementGraph.allowNullPropertyValues)

        val metaProperties = ElementHelper.asMap(keyValues)
        val cardinality = elementGraph.defaultVertexPropertyCardinality

        return addVertexProperty(key, value, metaProperties, cardinality)
    }

    /**
     * Add a vertex property with explicit cardinality specification.
     */
    fun <V> property(key: String, value: V, cardinality: VertexProperty.Cardinality, vararg keyValues: Any?): VertexProperty<V> {
        checkRemoved()
        ElementHelper.validateProperty(key, value, elementGraph.allowNullPropertyValues)

        val metaProperties = ElementHelper.asMap(keyValues)
        return addVertexProperty(key, value, metaProperties, cardinality)
    }

    /**
     * Override the two-parameter property method to create VertexProperty objects.
     */
    override fun <V> property(key: String, value: V): VertexProperty<V> {
        checkRemoved()
        ElementHelper.validateProperty(key, value, elementGraph.allowNullPropertyValues)

        return addVertexProperty(key, value)
    }

    /**
     * Override Element.property(key) getter to look in vertex properties instead of element properties.
     */
    override fun <V> property(key: String): Property<V> {
        checkRemoved()

        val properties = vertexProperties[key]
        if (properties != null && properties.isNotEmpty()) {
            val activeProperties = properties.filter { !it.isVertexPropertyRemoved() }
            if (activeProperties.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                return activeProperties.first() as Property<V>
            }
        }

        return Property.empty()
    }

    /**
     * Override value method to look in vertex properties.
     */
    override fun <V> value(key: String): V? {
        checkRemoved()
        val properties = vertexProperties[key]
        return if (properties != null && properties.isNotEmpty()) {
            // Return the first non-removed property value
            val firstProperty = properties.firstOrNull { !it.isVertexPropertyRemoved() }
            try {
                @Suppress("UNCHECKED_CAST") // Safe cast - property type consistency is maintained
                firstProperty?.value() as V?
            } catch (e: ClassCastException) {
                logger.d(e) { "ClassCastException when getting value for property '$key' on vertex $id" }
                null
            }
        } else {
            null
        }
    }

    /**
     * Get all values for a property key (for multi-properties).
     */
    fun <V> values(key: String): Iterator<V> {
        checkRemoved()
        return try {
            @Suppress("UNCHECKED_CAST") // Safe cast - property type consistency is maintained
            val properties = vertexProperties[key] as? List<TinkerVertexProperty<V>> ?: emptyList()
            properties.filter { !it.isVertexPropertyRemoved() }.map { it.value() }.iterator()
        } catch (e: ClassCastException) {
            logger.d(e) { "ClassCastException when getting values for property '$key' on vertex $id" }
            emptyList<V>().iterator()
        }
    }

    /**
     * Override keys method to return keys from vertex properties.
     */
    override fun keys(): Set<String> {
        checkRemoved()
        return vertexProperties.keys.toSet()
    }



    override fun <V> properties(vararg propertyKeys: String): Iterator<VertexProperty<V>> {
        checkRemoved()

        val keys = if (propertyKeys.isEmpty()) {
            vertexProperties.keys
        } else {
            propertyKeys.toSet()
        }

        val result = mutableListOf<VertexProperty<V>>()
        keys.forEach { key ->
            val properties = vertexProperties[key]
            if (properties != null) {
                try {
                    @Suppress("UNCHECKED_CAST") // Safe cast - property type consistency is maintained
                    result.addAll(properties as List<VertexProperty<V>>)
                } catch (e: ClassCastException) {
                    logger.d(e) { "ClassCastException when getting properties for key '$key' on vertex $id, skipping" }
                    // Skip properties that don't match the expected type
                }
            }
        }

        return result.filter { property ->
            (property as? TinkerVertexProperty<*>)?.isVertexPropertyRemoved() == false
        }.iterator()
    }

    /**
     * Add a vertex property with specified cardinality.
     */
    fun <V> addVertexProperty(
        key: String,
        value: V,
        cardinality: VertexProperty.Cardinality
    ): TinkerVertexProperty<V> {
        return addVertexProperty(key, value, emptyMap(), cardinality)
    }

    /**
     * Check if this vertex has a property with the given key.
     */
    fun hasProperty(key: String): Boolean {
        checkRemoved()
        val properties = vertexProperties[key]
        return properties != null && properties.any { !it.isVertexPropertyRemoved() }
    }

    /**
     * Count properties with the given key.
     */
    fun propertyCount(key: String): Int {
        checkRemoved()
        val properties = vertexProperties[key]
        return properties?.count { !it.isVertexPropertyRemoved() } ?: 0
    }

    /**
     * Get property cardinality for a given key.
     */
    fun getPropertyCardinality(key: String): VertexProperty.Cardinality {
        checkRemoved()
        return propertyCardinalities[key] ?: VertexProperty.Cardinality.SINGLE
    }

    /**
     * Add a vertex property with specified cardinality and meta-properties.
     */
    fun <V> addVertexProperty(
        key: String,
        value: V,
        metaProperties: Map<String, Any?> = emptyMap(),
        cardinality: VertexProperty.Cardinality = VertexProperty.Cardinality.SINGLE
    ): TinkerVertexProperty<V> {
        checkRemoved()

        val propertyList = vertexProperties.getOrPut(key) { mutableListOf() }

        // Store cardinality for this key
        propertyCardinalities[key] = cardinality

        // Handle cardinality
        when (cardinality) {
            VertexProperty.Cardinality.SINGLE -> {
                // Remove existing properties with this key
                val toRemove = propertyList.filter { !it.isVertexPropertyRemoved() }
                toRemove.forEach { prop ->
                    // Update vertex index for removed property
                    elementGraph.vertexIndex.autoUpdate(key, null, prop.value(), this)
                    // Update range index for removed property
                    elementGraph.vertexRangeIndex.autoUpdate(key, null, prop.value(), this)
                    // Update composite index for removed property
                    elementGraph.vertexCompositeIndex.autoUpdate(key, this)
                    // Mark property as removed
                    prop.markPropertyRemoved()
                }
                // Clear all properties for SINGLE cardinality
                propertyList.clear()
            }
            VertexProperty.Cardinality.SET -> {
                // Check for duplicate values in SET cardinality
                val existingValues = propertyList.filter { !it.isVertexPropertyRemoved() }.map { it.value() }.toSet()
                if (value in existingValues) {
                    throw VertexProperty.Exceptions.identicalMultiPropertiesNotSupported()
                }
            }
            VertexProperty.Cardinality.LIST -> {
                // LIST allows duplicates, no special handling needed
            }
        }

        // Create the vertex property
        val vertexProperty = TinkerVertexProperty(elementGraph.getNextId(), this, key, value)
        propertyList.add(vertexProperty)

        // Add meta-properties
        ElementHelper.attachProperties(vertexProperty, metaProperties)

        // Update vertex index
        elementGraph.vertexIndex.autoUpdate(key, value, null, this)
        // Update range index
        elementGraph.vertexRangeIndex.autoUpdate(key, value, null, this)
        // Update composite index
        elementGraph.vertexCompositeIndex.autoUpdate(key, this)

        return vertexProperty
    }

    /**
     * Remove a vertex property.
     */
    internal fun removeVertexProperty(vertexProperty: TinkerVertexProperty<*>) {
        val key = vertexProperty.key()
        val propertyList = vertexProperties[key]

        if (propertyList != null) {
            val oldValue = vertexProperty.value()
            propertyList.remove(vertexProperty)

            // Clean up empty property lists
            if (propertyList.isEmpty() || propertyList.all { it.isVertexPropertyRemoved() }) {
                vertexProperties.remove(key)
                propertyCardinalities.remove(key)
            }

            // Update vertex index
            elementGraph.vertexIndex.autoUpdate(key, null, oldValue, this)

            // Mark vertex property as removed
            vertexProperty.markRemoved()
        }
    }

    /**
     * Remove all properties with the given key.
     */
    fun removeProperties(key: String): Int {
        checkRemoved()
        val propertyList = vertexProperties[key] ?: return 0

        var removedCount = 0
        val toRemove = propertyList.filter { !it.isVertexPropertyRemoved() }

        toRemove.forEach { prop ->
            val oldValue = prop.value()
            // Update vertex index
            elementGraph.vertexIndex.autoUpdate(key, null, oldValue, this)
            // Mark property as removed
            prop.markPropertyRemoved()
            removedCount++
        }

        // Remove the key entirely
        vertexProperties.remove(key)
        propertyCardinalities.remove(key)

        return removedCount
    }

    /**
     * Remove properties with specific key and value (useful for SET/LIST cardinalities).
     */
    fun <V> removeProperty(key: String, value: V): Boolean {
        checkRemoved()
        val propertyList = vertexProperties[key] ?: return false

        val propertyToRemove = propertyList.firstOrNull {
            !it.isVertexPropertyRemoved() && it.value() == value
        }

        if (propertyToRemove != null) {
            removeVertexProperty(propertyToRemove)
            return true
        }

        return false
    }

    /**
     * Get vertex property by key and value (for SET cardinality lookups).
     */
    fun <V> getVertexProperty(key: String, value: V): TinkerVertexProperty<V>? {
        checkRemoved()

        return try {
            @Suppress("UNCHECKED_CAST") // Safe cast - property type consistency is maintained
            val properties = vertexProperties[key] as? List<TinkerVertexProperty<V>>
            properties?.firstOrNull { !it.isVertexPropertyRemoved() && it.value() == value }
        } catch (e: ClassCastException) {
            logger.d(e) { "ClassCastException when finding property '$key' with value '$value' on vertex $id" }
            null
        }
    }

    /**
     * Get all vertex properties for a key.
     */
    fun <V> getVertexProperties(key: String): List<TinkerVertexProperty<V>> {
        checkRemoved()

        return try {
            @Suppress("UNCHECKED_CAST") // Safe cast - property type consistency is maintained
            val properties = vertexProperties[key] as? List<TinkerVertexProperty<V>> ?: emptyList()
            properties.filter { !it.isVertexPropertyRemoved() }
        } catch (e: ClassCastException) {
            logger.d(e) { "ClassCastException when getting properties list for key '$key' on vertex $id" }
            emptyList()
        }
    }

    /**
     * Get a specific vertex property by ID.
     */
    fun getVertexPropertyById(id: Any): TinkerVertexProperty<*>? {
        checkRemoved()

        for (propertyList in vertexProperties.values) {
            for (prop in propertyList) {
                if (!prop.isVertexPropertyRemoved() && prop.id() == id) {
                    return prop
                }
            }
        }
        return null
    }

    /**
     * Add an outgoing edge to this vertex's adjacency list.
     */
    internal fun addOutEdge(edge: TinkerEdge) {
        val label = edge.label()
        val edgeSet = outEdges.getOrPut(label) { mutableSetOf() }
        edgeSet.add(edge)
    }

    /**
     * Add an incoming edge to this vertex's adjacency list.
     */
    internal fun addInEdge(edge: TinkerEdge) {
        val label = edge.label()
        val edgeSet = inEdges.getOrPut(label) { mutableSetOf() }
        edgeSet.add(edge)
    }

    /**
     * Remove an outgoing edge from this vertex's adjacency list.
     */
    internal fun removeOutEdge(edge: TinkerEdge) {
        val label = edge.label()
        val edgeSet = outEdges[label]
        edgeSet?.remove(edge)
        if (edgeSet?.isEmpty() == true) {
            outEdges.remove(label)
        }
    }

    /**
     * Remove an incoming edge from this vertex's adjacency list.
     */
    internal fun removeInEdge(edge: TinkerEdge) {
        val label = edge.label()
        val edgeSet = inEdges[label]
        edgeSet?.remove(edge)
        if (edgeSet?.isEmpty() == true) {
            inEdges.remove(label)
        }
    }

    /**
     * Get all outgoing edges (for internal graph operations).
     */
    internal fun getOutEdges(): Set<TinkerEdge> {
        val result = mutableSetOf<TinkerEdge>()
        outEdges.values.forEach { result.addAll(it) }
        return result
    }

    /**
     * Get all incoming edges (for internal graph operations).
     */
    internal fun getInEdges(): Set<TinkerEdge> {
        val result = mutableSetOf<TinkerEdge>()
        inEdges.values.forEach { result.addAll(it) }
        return result
    }

    /**
     * Get all edges (both incoming and outgoing).
     */
    fun getAllEdges(): Set<TinkerEdge> {
        val result = mutableSetOf<TinkerEdge>()
        result.addAll(getOutEdges())
        result.addAll(getInEdges())
        return result
    }

    /**
     * Get outgoing edge labels.
     */
    fun getOutEdgeLabels(): Set<String> {
        return outEdges.keys.toSet()
    }

    /**
     * Get incoming edge labels.
     */
    fun getInEdgeLabels(): Set<String> {
        return inEdges.keys.toSet()
    }

    /**
     * Get all edge labels (both incoming and outgoing).
     */
    fun getAllEdgeLabels(): Set<String> {
        val result = mutableSetOf<String>()
        result.addAll(getOutEdgeLabels())
        result.addAll(getInEdgeLabels())
        return result
    }

    /**
     * Count edges by direction and optional labels.
     */
    fun countEdges(direction: Direction, vararg edgeLabels: String): Int {
        val labels = if (edgeLabels.isEmpty()) null else edgeLabels.toSet()

        return when (direction) {
            Direction.OUT -> countEdgesInMap(outEdges, labels)
            Direction.IN -> countEdgesInMap(inEdges, labels)
            Direction.BOTH -> countEdgesInMap(outEdges, labels) + countEdgesInMap(inEdges, labels)
        }
    }

    /**
     * Count vertices by direction and optional labels.
     */
    fun countVertices(direction: Direction, vararg edgeLabels: String): Int {
        return vertices(direction, *edgeLabels).asSequence().count()
    }

    override fun remove() {
        checkRemoved()
        elementGraph.removeVertex(this)
    }

    /**
     * Helper method to collect edges from edge map based on labels.
     */
    private fun collectEdges(
        edgeMap: Map<String, Set<TinkerEdge>>,
        labels: Set<String>?,
        result: MutableSet<TinkerEdge>
    ) {
        if (labels == null) {
            // No label filter, add all edges
            edgeMap.values.forEach { result.addAll(it) }
        } else {
            // Filter by labels
            labels.forEach { label ->
                edgeMap[label]?.let { result.addAll(it) }
            }
        }
    }

    /**
     * Helper method to count edges in an edge map.
     */
    private fun countEdgesInMap(edgeMap: Map<String, Set<TinkerEdge>>, labels: Set<String>?): Int {
        return if (labels == null) {
            edgeMap.values.sumOf { it.size }
        } else {
            labels.sumOf { label ->
                edgeMap[label]?.size ?: 0
            }
        }
    }

    /**
     * Override getProperties to return vertex properties instead of element properties.
     */
    internal override fun getProperties(): Map<String, Property<*>> {
        val result = mutableMapOf<String, Property<*>>()
        vertexProperties.forEach { (key, propertyList) ->
            val activeProperties = propertyList.filter { !it.isVertexPropertyRemoved() }
            if (activeProperties.isNotEmpty()) {
                // For multiple properties with the same key, return the first one
                // This matches the behavior of value() method
                result[key] = activeProperties.first()
            }
        }
        return result
    }

    /**
     * Get all property keys that have active properties.
     */
    fun getActivePropertyKeys(): Set<String> {
        checkRemoved()
        return vertexProperties.filterValues { propertyList ->
            propertyList.any { !it.isVertexPropertyRemoved() }
        }.keys.toSet()
    }

    /**
     * Get property statistics for debugging and monitoring.
     */
    fun getPropertyStatistics(): Map<String, PropertyStats> {
        checkRemoved()
        val stats = mutableMapOf<String, PropertyStats>()

        vertexProperties.forEach { (key, propertyList) ->
            val activeCount = propertyList.count { !it.isVertexPropertyRemoved() }
            val totalCount = propertyList.size
            val hasMetaProperties = propertyList.any { !it.isVertexPropertyRemoved() && it.hasMetaProperties() }

            stats[key] = PropertyStats(
                activeCount = activeCount,
                totalCount = totalCount,
                hasMetaProperties = hasMetaProperties,
                cardinality = getPropertyCardinality(key)
            )
        }

        return stats
    }

    /**
     * Data class for property statistics.
     */
    data class PropertyStats(
        val activeCount: Int,
        val totalCount: Int,
        val hasMetaProperties: Boolean,
        val cardinality: VertexProperty.Cardinality
    )

    override fun toString(): String {
        return "v[$elementId]"
    }

    /**
     * Reserved property keys for TinkerPop.
     */
    object T {
        const val id = "id"
        const val label = "label"
    }
}
