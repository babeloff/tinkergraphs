package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*

/**
 * PropertyManager handles advanced property operations for TinkerGraph including
 * multi-property management, cardinality enforcement, and property lifecycle.
 */
class PropertyManager(private val graph: TinkerGraph) {

    /**
     * Property listeners for lifecycle events.
     */
    private val propertyListeners: MutableList<PropertyLifecycleListener> = mutableListOf()

    /**
     * Add a property to a vertex with full cardinality and meta-property support.
     */
    fun <V> addVertexProperty(
        vertex: TinkerVertex,
        key: String,
        value: V,
        cardinality: VertexProperty.Cardinality = VertexProperty.Cardinality.SINGLE,
        metaProperties: Map<String, Any?> = emptyMap(),
        id: Any? = null
    ): TinkerVertexProperty<V> {
        // Validate property
        ElementHelper.validateProperty(key, value)

        // Check graph features
        if (!graph.features().vertex().supportsMultiProperties() &&
            cardinality != VertexProperty.Cardinality.SINGLE) {
            throw VertexProperty.Exceptions.multiPropertiesNotSupported()
        }

        if (metaProperties.isNotEmpty() && !graph.features().vertex().supportsMetaProperties()) {
            throw VertexProperty.Exceptions.metaPropertiesNotSupported()
        }

        // Get existing properties for this key
        val existingProperties = vertex.getVertexProperties<V>(key)

        // Handle cardinality enforcement
        when (cardinality) {
            VertexProperty.Cardinality.SINGLE -> {
                // Remove all existing properties with this key
                existingProperties.forEach { prop ->
                    notifyPropertyRemoved(vertex, prop)
                    vertex.removeVertexProperty(prop)
                }
            }
            VertexProperty.Cardinality.SET -> {
                // Check for duplicate values
                val existingValues = existingProperties.map { it.value() }.toSet()
                if (value in existingValues) {
                    throw VertexProperty.Exceptions.identicalMultiPropertiesNotSupported()
                }
            }
            VertexProperty.Cardinality.LIST -> {
                // LIST allows duplicates, no additional checks needed
            }
        }

        // Use the vertex's addVertexProperty method which handles all the integration
        val vertexProperty = vertex.addVertexProperty(key, value, metaProperties, cardinality)

        // Notify listeners
        notifyPropertyAdded(vertex, vertexProperty)

        return vertexProperty
    }

    /**
     * Remove a vertex property with lifecycle notifications.
     */
    fun removeVertexProperty(vertex: TinkerVertex, vertexProperty: TinkerVertexProperty<*>) {
        // Notify before removal
        notifyPropertyRemoved(vertex, vertexProperty)

        // Remove from vertex
        vertex.removeVertexProperty(vertexProperty)
    }

    /**
     * Remove all properties with a given key from a vertex.
     */
    fun removeVertexProperties(vertex: TinkerVertex, key: String): Int {
        val properties = vertex.getVertexProperties<Any>(key)
        var removedCount = 0

        properties.forEach { prop ->
            notifyPropertyRemoved(vertex, prop)
            vertex.removeVertexProperty(prop)
            removedCount++
        }

        return removedCount
    }

    /**
     * Update a vertex property value with cardinality handling.
     */
    fun <V> updateVertexProperty(
        vertex: TinkerVertex,
        key: String,
        oldValue: V?,
        newValue: V,
        cardinality: VertexProperty.Cardinality = VertexProperty.Cardinality.SINGLE
    ): TinkerVertexProperty<V> {
        val existingProperties = vertex.getVertexProperties<V>(key)

        when (cardinality) {
            VertexProperty.Cardinality.SINGLE -> {
                // Remove existing property and add new one
                existingProperties.forEach { prop ->
                    notifyPropertyRemoved(vertex, prop)
                    vertex.removeVertexProperty(prop)
                }
                return addVertexProperty(vertex, key, newValue, cardinality)
            }
            VertexProperty.Cardinality.SET -> {
                // Remove old value if it exists, add new value
                if (oldValue != null) {
                    val oldProperty = existingProperties.firstOrNull { it.value() == oldValue }
                    if (oldProperty != null) {
                        notifyPropertyRemoved(vertex, oldProperty)
                        vertex.removeVertexProperty(oldProperty)
                    }
                }
                return addVertexProperty(vertex, key, newValue, cardinality)
            }
            VertexProperty.Cardinality.LIST -> {
                // For LIST, this is essentially an add operation
                return addVertexProperty(vertex, key, newValue, cardinality)
            }
        }
    }

    /**
     * Query properties by key and value with filtering options.
     */
    fun <V> queryVertexProperties(
        vertex: TinkerVertex,
        key: String? = null,
        value: V? = null,
        hasMetaProperties: Boolean? = null
    ): List<TinkerVertexProperty<V>> {
        val allProperties = if (key != null) {
            vertex.getVertexProperties<V>(key)
        } else {
            vertex.keys().flatMap { k -> vertex.getVertexProperties<V>(k) }
        }

        return allProperties.filter { prop ->
            (value == null || prop.value() == value) &&
            (hasMetaProperties == null || prop.hasMetaProperties() == hasMetaProperties)
        }
    }

    /**
     * Get property cardinality analysis for a vertex.
     */
    fun getPropertyCardinalityAnalysis(vertex: TinkerVertex): Map<String, CardinalityInfo> {
        val analysis = mutableMapOf<String, CardinalityInfo>()

        vertex.getActivePropertyKeys().forEach { key ->
            val properties = vertex.getVertexProperties<Any>(key)
            val values = properties.map { it.value() }
            val uniqueValues = values.toSet()

            analysis[key] = CardinalityInfo(
                totalProperties = properties.size,
                uniqueValues = uniqueValues.size,
                suggestedCardinality = when {
                    properties.size <= 1 -> VertexProperty.Cardinality.SINGLE
                    uniqueValues.size == values.size -> VertexProperty.Cardinality.LIST
                    else -> VertexProperty.Cardinality.SET
                },
                hasMetaProperties = properties.any { it.hasMetaProperties() }
            )
        }

        return analysis
    }

    /**
     * Optimize property storage by cleaning up removed properties.
     */
    fun optimizePropertyStorage(vertex: TinkerVertex): OptimizationResult {
        var cleanedProperties = 0
        var cleanedKeys = 0

        // Access vertex properties through reflection or internal methods
        val propertyMap = vertex.getPropertyStatistics()

        propertyMap.forEach { (key, stats) ->
            if (stats.activeCount == 0) {
                vertex.removeProperties(key)
                cleanedKeys++
            } else if (stats.totalCount > stats.activeCount) {
                // There are removed properties that could be cleaned
                cleanedProperties += (stats.totalCount - stats.activeCount)
            }
        }

        return OptimizationResult(cleanedProperties, cleanedKeys)
    }

    /**
     * Validate property constraints for a vertex.
     */
    fun validatePropertyConstraints(vertex: TinkerVertex): List<PropertyConstraintViolation> {
        val violations = mutableListOf<PropertyConstraintViolation>()

        vertex.getActivePropertyKeys().forEach { key ->
            val properties = vertex.getVertexProperties<Any>(key)
            val cardinality = vertex.getPropertyCardinality(key)

            when (cardinality) {
                VertexProperty.Cardinality.SINGLE -> {
                    if (properties.size > 1) {
                        violations.add(PropertyConstraintViolation(
                            key = key,
                            violation = "Multiple properties found for SINGLE cardinality",
                            propertyCount = properties.size
                        ))
                    }
                }
                VertexProperty.Cardinality.SET -> {
                    val values = properties.map { it.value() }
                    val uniqueValues = values.toSet()
                    if (values.size != uniqueValues.size) {
                        violations.add(PropertyConstraintViolation(
                            key = key,
                            violation = "Duplicate values found for SET cardinality",
                            propertyCount = properties.size
                        ))
                    }
                }
                VertexProperty.Cardinality.LIST -> {
                    // LIST allows duplicates, no constraints to validate
                }
            }
        }

        return violations
    }

    /**
     * Add a property lifecycle listener.
     */
    fun addPropertyListener(listener: PropertyLifecycleListener) {
        propertyListeners.add(listener)
    }

    /**
     * Remove a property lifecycle listener.
     */
    fun removePropertyListener(listener: PropertyLifecycleListener): Boolean {
        return propertyListeners.remove(listener)
    }

    /**
     * Notify listeners of property addition.
     */
    private fun notifyPropertyAdded(vertex: TinkerVertex, property: TinkerVertexProperty<*>) {
        propertyListeners.forEach { listener ->
            try {
                listener.onPropertyAdded(vertex, property)
            } catch (e: Exception) {
                // Log error but don't fail the operation
                println("Error in property listener: ${e.message}")
            }
        }
    }

    /**
     * Notify listeners of property removal.
     */
    private fun notifyPropertyRemoved(vertex: TinkerVertex, property: TinkerVertexProperty<*>) {
        propertyListeners.forEach { listener ->
            try {
                listener.onPropertyRemoved(vertex, property)
            } catch (e: Exception) {
                // Log error but don't fail the operation
                println("Error in property listener: ${e.message}")
            }
        }
    }

    /**
     * Data class for cardinality analysis information.
     */
    data class CardinalityInfo(
        val totalProperties: Int,
        val uniqueValues: Int,
        val suggestedCardinality: VertexProperty.Cardinality,
        val hasMetaProperties: Boolean
    )

    /**
     * Data class for optimization results.
     */
    data class OptimizationResult(
        val cleanedProperties: Int,
        val cleanedKeys: Int
    )

    /**
     * Data class for property constraint violations.
     */
    data class PropertyConstraintViolation(
        val key: String,
        val violation: String,
        val propertyCount: Int
    )

    /**
     * Interface for property lifecycle listeners.
     */
    interface PropertyLifecycleListener {
        /**
         * Called when a property is added to a vertex.
         */
        fun onPropertyAdded(vertex: TinkerVertex, property: TinkerVertexProperty<*>)

        /**
         * Called when a property is removed from a vertex.
         */
        fun onPropertyRemoved(vertex: TinkerVertex, property: TinkerVertexProperty<*>)
    }

    /**
     * Default implementation of PropertyLifecycleListener for debugging.
     */
    class DebugPropertyListener : PropertyLifecycleListener {
        override fun onPropertyAdded(vertex: TinkerVertex, property: TinkerVertexProperty<*>) {
            println("Property added: vertex=${vertex.id()}, key=${property.key()}, value=${property.value()}")
        }

        override fun onPropertyRemoved(vertex: TinkerVertex, property: TinkerVertexProperty<*>) {
            println("Property removed: vertex=${vertex.id()}, key=${property.key()}, value=${property.value()}")
        }
    }
}
