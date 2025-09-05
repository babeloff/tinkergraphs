package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators.TinkerEdgeIterator
import org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators.TinkerVertexTraversingIterator

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
        ElementHelper.validateProperty(key, value)

        val metaProperties = ElementHelper.asMap(keyValues)
        val cardinality = elementGraph.defaultVertexPropertyCardinality

        return addVertexProperty(key, value, metaProperties, cardinality)
    }

    /**
     * Override the two-parameter property method to create VertexProperty objects.
     */
    override fun <V> property(key: String, value: V): VertexProperty<V> {
        checkRemoved()
        ElementHelper.validateProperty(key, value)
        return addVertexProperty(key, value)
    }

    /**
     * Override value method to look in vertex properties.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <V> value(key: String): V? {
        checkRemoved()
        val properties = vertexProperties[key]
        return if (properties != null && properties.isNotEmpty()) {
            properties.first().value() as V?
        } else {
            null
        }
    }

    /**
     * Override keys method to return keys from vertex properties.
     */
    override fun keys(): Set<String> {
        checkRemoved()
        return vertexProperties.keys.toSet()
    }

    /**
     * Add a vertex property with specified cardinality and meta-properties.
     */
    fun <V> property(key: String, value: V, cardinality: VertexProperty.Cardinality, vararg keyValues: Any?): VertexProperty<V> {
        checkRemoved()
        ElementHelper.validateProperty(key, value)

        val metaProperties = ElementHelper.asMap(keyValues)
        return addVertexProperty(key, value, metaProperties, cardinality)
    }

    @Suppress("UNCHECKED_CAST")
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
                result.addAll(properties as List<VertexProperty<V>>)
            }
        }

        return result.iterator()
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

        // Handle cardinality
        when (cardinality) {
            VertexProperty.Cardinality.SINGLE -> {
                // Remove existing properties with this key
                // Clear the list directly instead of using removeVertexProperty
                // to avoid removing the key from vertexProperties map
                propertyList.forEach { prop ->
                    // Update vertex index for removed property
                    elementGraph.vertexIndex.autoUpdate(key, null, prop.value(), this)
                    // Mark property as removed
                    prop.markPropertyRemoved()
                }
                propertyList.clear()
            }
            VertexProperty.Cardinality.SET -> {
                // Check for duplicate values in SET cardinality
                val existingValues = propertyList.map { it.value() }.toSet()
                if (value in existingValues) {
                    throw Vertex.Exceptions.multiplePropertiesExistForProvidedKey(key)
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

            if (propertyList.isEmpty()) {
                vertexProperties.remove(key)
            }

            // Update vertex index
            elementGraph.vertexIndex.autoUpdate(key, null, oldValue, this)

            // Mark vertex property as removed
            vertexProperty.markRemoved()
        }
    }

    /**
     * Get vertex property by key and value (for SET cardinality lookups).
     */
    fun <V> getVertexProperty(key: String, value: V): TinkerVertexProperty<V>? {
        checkRemoved()

        @Suppress("UNCHECKED_CAST")
        val properties = vertexProperties[key] as? List<TinkerVertexProperty<V>>
        return properties?.firstOrNull { it.value() == value }
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
            if (propertyList.isNotEmpty()) {
                // For multiple properties with the same key, return the first one
                // This matches the behavior of value() method
                result[key] = propertyList.first()
            }
        }
        return result
    }

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
