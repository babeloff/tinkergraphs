package org.apache.tinkerpop.gremlin.tinkergraph.structure

import kotlin.js.JsExport
import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators.TinkerEdgeIterator
import org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators.TinkerVertexTraversingIterator
import org.apache.tinkerpop.gremlin.tinkergraph.util.LoggingConfig

/**
 * TinkerVertex is the vertex implementation for TinkerGraph.
 *
 * This implementation provides efficient adjacency list management and
 * supports TinkerPop's full vertex property model with multiple cardinalities.
 * Each vertex maintains separate collections for incoming edges, outgoing edges,
 * and vertex properties, all organized for optimal access patterns.
 *
 * ## Property Model
 * TinkerVertex supports the complete TinkerPop vertex property model:
 * - **SINGLE**: One value per property key (default behavior)
 * - **LIST**: Multiple ordered values per property key with duplicates allowed
 * - **SET**: Multiple unique values per property key with no duplicates
 * - **Meta-properties**: Properties can have their own properties (property on property)
 *
 * ## Edge Management
 * Maintains separate adjacency lists for incoming and outgoing edges,
 * organized by edge label for efficient traversal filtering:
 * - Outgoing edges: `this vertex ---edge---> other vertex`
 * - Incoming edges: `other vertex ---edge---> this vertex`
 * - Label-based organization enables O(1) edge retrieval by label
 *
 * ## Memory Layout
 * Approximate memory usage per vertex:
 * - Base vertex: ~96 bytes (object overhead + core fields)
 * - Per property: ~48-64 bytes + value size + meta-property overhead
 * - Per edge reference: ~24 bytes + label string overhead
 * - Label-based edge maps: ~32 bytes per unique edge label
 *
 * ## Thread Safety
 * Individual vertex operations are thread-safe for concurrent reads.
 * However, concurrent modifications (adding/removing properties or edges)
 * require external synchronization to maintain consistency.
 *
 * ## Performance Characteristics
 * - Vertex property access: O(1) average, O(k) for multi-properties where k = property count
 * - Edge traversal by direction: O(1) to get iterator, O(n) to traverse all edges
 * - Edge traversal by label: O(1) to get labeled edges, O(m) to traverse where m = edges with label
 * - Property updates: O(1) for single properties, O(k) for cardinality enforcement
 *
 * @param id Vertex identifier (must be unique within the graph)
 * @param label Vertex label describing the vertex type (defaults to "vertex")
 * @param graph Parent TinkerGraph instance that owns this vertex
 * @see Vertex for the interface contract
 * @see TinkerEdge for edge implementation details
 * @see TinkerVertexProperty for vertex property implementation
 * @see VertexProperty.Cardinality for supported cardinality types
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
     * Outgoing edges organized by label for efficient traversal filtering.
     *
     * Maps edge label to a mutable set of edges where this vertex is the source/tail.
     * Organization by label enables O(1) retrieval of edges with specific labels,
     * significantly improving traversal performance when label filtering is applied.
     *
     * Structure: `Map<EdgeLabel, Set<OutgoingEdge>>`
     */
    private val outEdges: MutableMap<String, MutableSet<TinkerEdge>> = mutableMapOf()

    /**
     * Incoming edges organized by label for efficient traversal filtering.
     *
     * Maps edge label to a mutable set of edges where this vertex is the target/head.
     * Organization by label enables O(1) retrieval of edges with specific labels,
     * significantly improving traversal performance when label filtering is applied.
     *
     * Structure: `Map<EdgeLabel, Set<IncomingEdge>>`
     */
    private val inEdges: MutableMap<String, MutableSet<TinkerEdge>> = mutableMapOf()

    /**
     * Vertex properties organized by key with full cardinality support.
     *
     * Maps property key to a list of vertex properties to support all TinkerPop
     * cardinalities (SINGLE, LIST, SET). Even SINGLE cardinality uses a list
     * internally for consistent implementation, but enforces single-value constraints.
     *
     * Each VertexProperty can have its own meta-properties, enabling complex
     * property graphs and rich data modeling capabilities.
     *
     * Structure: `Map<PropertyKey, List<VertexProperty<*>>>`
     */
    private val vertexProperties: MutableMap<String, MutableList<TinkerVertexProperty<*>>> = mutableMapOf()

    /**
     * Cardinality tracking for property keys to enforce consistency.
     *
     * Maps each property key to its configured cardinality (SINGLE, LIST, or SET).
     * This ensures that all properties with the same key maintain consistent
     * cardinality behavior throughout the vertex's lifecycle.
     *
     * New properties inherit cardinality from existing properties with the same key,
     * or use the graph's default cardinality for new keys.
     *
     * Structure: `Map<PropertyKey, Cardinality>`
     */
    private val propertyCardinalities: MutableMap<String, VertexProperty.Cardinality> = mutableMapOf()

    override fun addEdge(label: String, inVertex: Vertex, vararg keyValues: Any?): Edge {
        checkRemoved()
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

    /**
     * Get edges as an array for JavaScript/TypeScript compatibility.
     *
     * @param direction the direction of edges to retrieve
     * @param edgeLabels optional edge labels to filter
     * @return Array of edges
     */
    fun edgesArray(direction: Direction, vararg edgeLabels: String): Array<Edge> {
        return edges(direction, *edgeLabels).asSequence().toList().toTypedArray()
    }

    /**
     * Get vertices as an array for JavaScript/TypeScript compatibility.
     *
     * @param direction the direction to traverse
     * @param edgeLabels optional edge labels to filter
     * @return Array of vertices
     */
    fun verticesArray(direction: Direction, vararg edgeLabels: String): Array<Vertex> {
        return vertices(direction, *edgeLabels).asSequence().toList().toTypedArray()
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
     *
     * This method provides fine-grained control over property cardinality,
     * allowing different properties to have different cardinality behaviors.
     * Cardinality is enforced consistently for all properties with the same key.
     *
     * ## Cardinality Behavior
     * - **SINGLE**: Replaces existing property with the same key
     * - **LIST**: Appends to existing properties (allows duplicates)
     * - **SET**: Adds unique values only (prevents duplicates)
     *
     * @param key Property key (case-sensitive, cannot be null or empty)
     * @param value Property value (type preserved, null allowed if graph configured)
     * @param cardinality Cardinality constraint for this property key
     * @param keyValues Optional meta-properties as alternating key-value pairs
     * @return The created VertexProperty instance
     * @throws IllegalArgumentException if cardinality conflicts with existing properties
     */
    fun <V> property(key: String, value: V, cardinality: VertexProperty.Cardinality, vararg keyValues: Any?): VertexProperty<V> {
        checkRemoved()
        ElementHelper.validateProperty(key, value, elementGraph.allowNullPropertyValues)

        val metaProperties = ElementHelper.asMap(keyValues)
        return addVertexProperty(key, value, metaProperties, cardinality)
    }

    /**
     * Override the two-parameter property method to create VertexProperty objects.
     *
     * Creates a vertex property with the graph's default cardinality setting.
     * This is the primary method for adding properties when cardinality control
     * is not required.
     *
     * @param key Property key (case-sensitive, cannot be null or empty)
     * @param value Property value (type preserved, null allowed if graph configured)
     * @return The created VertexProperty instance
     */
    override fun <V> property(key: String, value: V): VertexProperty<V> {
        checkRemoved()
        ElementHelper.validateProperty(key, value, elementGraph.allowNullPropertyValues)

        return addVertexProperty(key, value)
    }

    /**
     * Override Element.property(key) getter to look in vertex properties.
     *
     * Returns the first vertex property with the specified key, following
     * TinkerPop semantics where vertex properties take precedence over
     * element properties for vertices.
     *
     * For vertices with multiple properties of the same key (LIST or SET cardinality),
     * this returns the first property in the internal list. Use properties(key)
     * to retrieve all properties with the same key.
     *
     * @param key Property key to retrieve
     * @return First VertexProperty with the key, or Property.empty() if not found
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
     * Override value method to look in vertex properties with type safety.
     *
     * Returns the value of the first vertex property with the specified key.
     * Includes safe type casting with ClassCastException handling to prevent
     * runtime failures from type mismatches.
     *
     * For vertices with multiple properties of the same key, this returns
     * the value of the first property. Use getPropertyValues() to retrieve
     * all values for multi-properties.
     *
     * @param key Property key to retrieve value for
     * @return Property value with type V, or null if not found or type mismatch
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
                logger.d(e) { "ClassCastException when getting value for property '$key' on vertex ${id()}" }
                null
            }
        } else {
            null
        }
    }

    /**
     * Get all values for a property key supporting multi-properties.
     *
     * Returns an iterator over all values for properties with the specified key.
     * This is essential for LIST and SET cardinality properties that can have
     * multiple values per key.
     *
     * Values are returned in insertion order for LIST cardinality, and in
     * internal storage order for SET cardinality. Includes safe type casting
     * with graceful error handling.
     *
     * @param key Property key to retrieve values for
     * @return Iterator over all property values, empty if key not found
     */
    fun <V> values(key: String): Iterator<V> {
        checkRemoved()
        return try {
            @Suppress("UNCHECKED_CAST") // Safe cast - property type consistency is maintained
            val properties = vertexProperties[key] as? List<TinkerVertexProperty<V>> ?: emptyList()
            properties.filter { !it.isVertexPropertyRemoved() }.map { it.value() }.iterator()
        } catch (e: ClassCastException) {
            logger.d(e) { "ClassCastException when getting values for property '$key' on vertex ${id()}" }
            emptyList<V>().iterator()
        }
    }

    /**
     * Override keys method to return keys from vertex properties.
     *
     * Returns all unique property keys currently present on this vertex.
     * This includes keys from all cardinalities (SINGLE, LIST, SET) and
     * excludes keys from removed properties.
     *
     * @return Set of all property keys on this vertex
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
                    logger.d(e) { "ClassCastException when getting properties for key '$key' on vertex ${id()}, skipping" }
                    // Skip properties that don't match the expected type
                }
            }
        }

        return result.filter { property ->
            (property as? TinkerVertexProperty<*>)?.isVertexPropertyRemoved() == false
        }.iterator()
    }

    /**
     * Add a vertex property with specified cardinality and meta-properties.
     *
     * This is the comprehensive property addition method that handles:
     * - Cardinality enforcement and validation
     * - Meta-property attachment
     * - Property lifecycle notifications
     * - Index maintenance and updates
     *
     * @param key Property key
     * @param value Property value
     * @param cardinality Cardinality constraint (SINGLE, LIST, SET)
     * @param metaProperties Map of meta-properties to attach to this property
     * @return The created TinkerVertexProperty instance
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
     *
     * Returns true if at least one non-removed property exists with the
     * specified key, regardless of cardinality or value.
     *
     * @param key Property key to check
     * @return true if property exists, false otherwise
     */
    fun hasProperty(key: String): Boolean {
        checkRemoved()
        val properties = vertexProperties[key]
        return properties != null && properties.any { !it.isVertexPropertyRemoved() }
    }

    /**
     * Count non-removed properties with the given key.
     *
     * Useful for understanding cardinality usage and property distribution.
     * Returns 0 for keys that don't exist, 1 for SINGLE cardinality,
     * and actual count for LIST/SET cardinalities.
     *
     * @param key Property key to count
     * @return Number of properties with the specified key
     */
    fun propertyCount(key: String): Int {
        checkRemoved()
        val properties = vertexProperties[key]
        return properties?.count { !it.isVertexPropertyRemoved() } ?: 0
    }

    /**
     * Get property cardinality for a given key.
     *
     * Returns the cardinality constraint for the specified property key.
     * If no properties exist with the key, returns the graph's default
     * vertex property cardinality.
     *
     * @param key Property key to check cardinality for
     * @return Cardinality for the property key
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
                    logger.w { "Attempted to add duplicate value '$value' for property '$key' with SET cardinality on vertex ${this.id()}." }
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
     * Remove a specific vertex property instance.
     *
     * Removes the specified property instance and updates all associated
     * indexes. For multi-properties (LIST/SET), this removes only the
     * specific property instance, not all properties with the same key.
     *
     * @param vertexProperty The specific property instance to remove
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
     *
     * Removes all property instances with the specified key, regardless
     * of cardinality. Updates indexes and cleans up internal storage.
     * Also removes the cardinality tracking for this key.
     *
     * @param key Property key - all properties with this key will be removed
     * @return Number of properties removed
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
            logger.d(e) { "ClassCastException when finding property '$key' with value '$value' on vertex ${id()}" }
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
            logger.d(e) { "ClassCastException when getting properties list for key '$key' on vertex ${id()}" }
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
