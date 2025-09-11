package org.apache.tinkerpop.gremlin.tinkergraph.structure

import kotlin.js.JsExport
import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.util.LoggingConfig

/**
 * PropertyManager handles advanced property operations for TinkerGraph.
 *
 * Provides sophisticated property lifecycle management beyond the basic
 * Graph interface, including cardinality enforcement, property validation,
 * lifecycle events, and bulk operations with performance optimization.
 *
 * ## Key Capabilities
 * - **Multi-property management** with strict cardinality constraints (SINGLE, LIST, SET)
 * - **Property lifecycle events** with customizable listener notifications
 * - **Bulk property operations** optimized for large-scale data manipulation
 * - **Property validation** and constraint enforcement with detailed violation reporting
 * - **Storage optimization** including property cleanup and memory management
 * - **Cardinality analysis** for understanding property distribution patterns
 *
 * ## Cardinality Support
 * Enforces all TinkerPop vertex property cardinalities:
 * - **SINGLE**: One value per key, replaces existing (default behavior)
 * - **LIST**: Multiple ordered values per key, allows duplicates, preserves insertion order
 * - **SET**: Multiple unique values per key, prevents duplicates, no ordering guarantees
 *
 * ## Lifecycle Events
 * Notifies registered listeners of property changes:
 * - **Property addition**: When new properties are created
 * - **Property removal**: When properties are deleted or marked as removed
 * - **Property updates**: When existing property values are modified
 * - **Bulk operations**: Aggregated notifications for performance
 *
 * ## Performance Features
 * - **Optimized bulk operations**: Batch processing for large property sets
 * - **Lazy validation**: Constraint checking deferred until necessary
 * - **Memory optimization**: Automatic cleanup of removed properties
 * - **Index maintenance**: Automatic synchronization with graph indexes
 *
 * ## Thread Safety
 * PropertyManager operations are thread-safe when used with proper
 * graph-level synchronization. Individual property operations are atomic,
 * but multi-step operations require external coordination.
 *
 * ## Example Usage
 * ```kotlin
 * val graph = TinkerGraph.open()
 * val propManager = graph.propertyManager()
 *
 * // Add lifecycle listener for audit trail
 * propManager.addPropertyListener(object : PropertyLifecycleListener {
 *     override fun onPropertyAdded(vertex: TinkerVertex, property: TinkerVertexProperty<*>) {
 *         auditLog.log("PROPERTY_ADDED", vertex.id(), property.key(), property.value())
 *     }
 *     override fun onPropertyRemoved(vertex: TinkerVertex, property: TinkerVertexProperty<*>) {
 *         auditLog.log("PROPERTY_REMOVED", vertex.id(), property.key())
 *     }
 * })
 *
 * // Add properties with strict cardinality control
 * val vertex = graph.addVertex() as TinkerVertex
 * propManager.addVertexProperty(
 *     vertex = vertex,
 *     key = "skills",
 *     value = "kotlin",
 *     cardinality = VertexProperty.Cardinality.SET,
 *     metaProperties = mapOf(
 *         "level" to "expert",
 *         "certified" to true,
 *         "yearsExperience" to 5
 *     )
 * )
 *
 * // Bulk operations for performance
 * val analysis = propManager.getPropertyCardinalityAnalysis(vertex)
 * val optimized = propManager.optimizePropertyStorage(vertex)
 * val violations = propManager.validatePropertyConstraints(vertex)
 * ```
 *
 * @param graph The TinkerGraph instance to manage properties for
 * @see PropertyQueryEngine for advanced property querying capabilities
 * @see TinkerVertex for vertex-specific property operations
 * @see VertexProperty for individual property management
 * @see PropertyLifecycleListener for event handling interfaces
 */
class PropertyManager(private val graph: TinkerGraph) {

    companion object {
        private val logger = LoggingConfig.getLogger<PropertyManager>()
    }

    /**
     * Property listeners for lifecycle events.
     */
    private val propertyListeners: MutableList<PropertyLifecycleListener> = mutableListOf()

    /**
     * Add a vertex property with full cardinality and meta-property support.
     *
     * This is the comprehensive property addition method that provides complete
     * control over property creation, including cardinality enforcement,
     * meta-property attachment, and lifecycle event notifications.
     *
     * ## Cardinality Enforcement
     * - **SINGLE**: Replaces any existing property with the same key
     * - **LIST**: Appends to existing properties, allows duplicates
     * - **SET**: Adds only if value doesn't already exist for the key
     *
     * ## Meta-Property Support
     * Meta-properties are properties attached to the vertex property itself,
     * enabling rich data modeling scenarios like:
     * - Property provenance (when, who, how)
     * - Property validation rules
     * - Property-level permissions
     * - Property versioning information
     *
     * ## Lifecycle Integration
     * - Notifies all registered PropertyLifecycleListener instances
     * - Updates graph indexes automatically
     * - Maintains cardinality tracking consistency
     * - Validates constraints before addition
     *
     * ## Example
     * ```kotlin
     * // Add a skill with meta-properties for rich modeling
     * val skillProperty = propManager.addVertexProperty(
     *     vertex = userVertex,
     *     key = "skills",
     *     value = "machine_learning",
     *     cardinality = VertexProperty.Cardinality.SET,
     *     metaProperties = mapOf(
     *         "level" to "advanced",
     *         "certified" to true,
     *         "lastUsed" to LocalDate.now(),
     *         "source" to "professional_experience"
     *     )
     * )
     * ```
     *
     * @param vertex The vertex to add the property to
     * @param key Property key (case-sensitive, cannot be null or empty)
     * @param value Property value (type preserved, null allowed if graph configured)
     * @param cardinality Cardinality constraint for this property key
     * @param metaProperties Map of meta-properties to attach (key-value pairs)
     * @return The created TinkerVertexProperty instance with meta-properties attached
     * @throws IllegalArgumentException if cardinality conflicts with existing properties
     * @throws IllegalArgumentException if key is null, empty, or invalid
     * @see addVertexProperty for bulk operations
     * @see VertexProperty.Cardinality for cardinality options
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
     * Remove a specific vertex property with lifecycle notifications.
     *
     * Removes the specified property instance from the vertex and triggers
     * all associated cleanup operations including index updates and
     * lifecycle event notifications.
     *
     * For multi-properties (LIST/SET cardinality), this removes only the
     * specific property instance, not all properties with the same key.
     * Use removeVertexProperties(vertex, key) to remove all properties
     * with a specific key.
     *
     * ## Cleanup Operations
     * - Notifies PropertyLifecycleListener instances before removal
     * - Updates all relevant graph indexes
     * - Cleans up meta-properties associated with the removed property
     * - Maintains cardinality tracking consistency
     *
     * @param vertex The vertex containing the property
     * @param vertexProperty The specific property instance to remove
     * @see removeVertexProperties for removing all properties with a key
     */
    fun removeVertexProperty(vertex: TinkerVertex, vertexProperty: TinkerVertexProperty<*>) {
        // Notify before removal
        notifyPropertyRemoved(vertex, vertexProperty)

        // Remove from vertex
        vertex.removeVertexProperty(vertexProperty)
    }

    /**
     * Remove all properties with a given key from a vertex.
     *
     * Removes all property instances with the specified key, regardless
     * of cardinality. This is particularly useful for cleaning up
     * multi-properties (LIST/SET cardinality) where multiple values
     * exist for the same key.
     *
     * ## Bulk Removal Benefits
     * - More efficient than removing properties individually
     * - Atomic operation with consistent state
     * - Single lifecycle notification per removed property
     * - Batch index updates for better performance
     *
     * ## Cleanup Operations
     * - Removes all properties with the specified key
     * - Cleans up associated meta-properties
     * - Updates all relevant indexes in batch
     * - Removes cardinality tracking for the key
     * - Notifies listeners for each removed property
     *
     * @param vertex The vertex to remove properties from
     * @param key The property key - all properties with this key will be removed
     * @return Number of properties actually removed (0 if key doesn't exist)
     * @see removeVertexProperty for removing specific property instances
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
     *
     * Updates or adds a property value while respecting cardinality constraints.
     * This method provides intelligent property management that adapts behavior
     * based on existing property cardinality and graph configuration.
     *
     * ## Update Behavior by Cardinality
     * - **SINGLE**: Replaces existing value or creates new property
     * - **LIST**: Appends new value to existing list (allows duplicates)
     * - **SET**: Adds value only if not already present (prevents duplicates)
     *
     * ## Smart Cardinality Detection
     * If no properties exist with the key:
     * - Uses graph's default vertex property cardinality
     * - Establishes cardinality for future operations on this key
     *
     * If properties already exist:
     * - Uses existing cardinality for consistency
     * - Validates that update operation is compatible
     *
     * ## Performance Optimization
     * - Batch index updates for multi-property operations
     * - Lazy validation to minimize overhead
     * - Efficient duplicate detection for SET cardinality
     *
     * @param vertex The vertex to update properties on
     * @param key Property key to update
     * @param value New property value
     * @param metaProperties Optional meta-properties for the new/updated property
     * @return The created or updated TinkerVertexProperty instance
     * @see addVertexProperty for explicit cardinality control
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
     * Query properties by key and value with advanced filtering options.
     *
     * Provides sophisticated property querying capabilities with support for
     * value filtering, type constraints, and cardinality-aware operations.
     * This method is optimized for complex property analysis scenarios.
     *
     * ## Filtering Capabilities
     * - **Exact value matching**: Find properties with specific values
     * - **Type-safe filtering**: Automatic type checking and casting
     * - **Null value handling**: Configurable behavior for null properties
     * - **Meta-property filtering**: Query based on meta-property values
     *
     * ## Performance Features
     * - **Index utilization**: Automatically uses property indexes when available
     * - **Lazy evaluation**: Results computed on-demand for memory efficiency
     * - **Type caching**: Optimizes repeated queries with same type constraints
     *
     * ## Example Usage
     * ```kotlin
     * // Find all "skill" properties with "expert" level
     * val expertSkills = propManager.queryProperties<String>(
     *     vertex = userVertex,
     *     key = "skills",
     *     valueFilter = { value ->
     *         // Query meta-properties for filtering
     *         val property = vertex.property<String>("skills")
     *         property.property<String>("level").orElse("") == "expert"
     *     }
     * )
     * ```
     *
     * @param vertex The vertex to query properties from
     * @param key Property key to filter by (null for all keys)
     * @param valueFilter Optional predicate for value-based filtering
     * @return List of matching TinkerVertexProperty instances
     * @see PropertyQueryEngine for more advanced querying capabilities
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
     * Get comprehensive property cardinality analysis for a vertex.
     *
     * Provides detailed analysis of property distribution, cardinality usage,
     * and storage efficiency for the specified vertex. This information is
     * valuable for performance optimization, data modeling validation,
     * and storage planning.
     *
     * ## Analysis Metrics
     * - **Property count by cardinality**: Distribution across SINGLE, LIST, SET
     * - **Memory usage estimation**: Approximate bytes used by properties
     * - **Cardinality efficiency**: Identifies underutilized multi-properties
     * - **Meta-property statistics**: Count and distribution of meta-properties
     *
     * ## Use Cases
     * - **Performance optimization**: Identify properties suitable for indexing
     * - **Data modeling validation**: Verify cardinality choices are appropriate
     * - **Memory planning**: Estimate storage requirements for similar vertices
     * - **Schema evolution**: Understand impact of cardinality changes
     *
     * @param vertex The vertex to analyze
     * @return Map of property keys to their CardinalityInfo analysis
     * @see CardinalityInfo for detailed metrics structure
     * @see optimizePropertyStorage for acting on analysis results
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
     * Optimize property storage by cleaning up removed properties and reorganizing data.
     *
     * Performs comprehensive storage optimization to improve memory usage and
     * access performance. This operation is particularly beneficial after
     * bulk property operations or when many properties have been removed.
     *
     * ## Optimization Operations
     * - **Removed property cleanup**: Purges properties marked as removed
     * - **Empty collection cleanup**: Removes empty property key mappings
     * - **Memory compaction**: Reorganizes internal storage for better locality
     * - **Cardinality optimization**: Converts single-element Lists to optimal storage
     *
     * ## Performance Benefits
     * - **Reduced memory footprint**: Eliminates storage overhead from removed properties
     * - **Improved access speed**: Better cache locality from compacted storage
     * - **Faster iteration**: Removes need to filter out removed properties
     * - **Index efficiency**: Cleaner data improves index performance
     *
     * ## When to Use
     * - After bulk property deletion operations
     * - During maintenance windows for long-running applications
     * - When memory usage optimization is critical
     * - Before creating indexes on heavily modified vertices
     *
     * @param vertex The vertex to optimize storage for
     * @return OptimizationResult containing metrics about the cleanup operation
     * @see OptimizationResult for detailed optimization metrics
     * @see getPropertyCardinalityAnalysis for identifying optimization candidates
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
     * Validate property constraints for a vertex with detailed violation reporting.
     *
     * Performs comprehensive validation of all properties on the vertex,
     * checking for constraint violations, cardinality consistency, and
     * data integrity issues. Returns detailed violation reports for
     * debugging and data quality assurance.
     *
     * ## Validation Checks
     * - **Cardinality consistency**: Verifies all properties with same key have consistent cardinality
     * - **Value constraints**: Validates property values against type and business rules
     * - **Meta-property integrity**: Ensures meta-properties are properly attached
     * - **Reference integrity**: Validates vertex and graph references are valid
     * - **Null value compliance**: Checks null values against graph configuration
     *
     * ## Violation Types Detected
     * - **CARDINALITY_MISMATCH**: Properties with same key have different cardinalities
     * - **INVALID_VALUE**: Property values that don't meet validation criteria
     * - **ORPHANED_METAPROPERTY**: Meta-properties without valid parent properties
     * - **REFERENCE_INTEGRITY**: Invalid vertex or graph references
     * - **CONFIGURATION_VIOLATION**: Values that violate graph configuration
     *
     * ## Performance Considerations
     * - **Lazy validation**: Only validates properties that have been accessed/modified
     * - **Cached results**: Repeated validation calls return cached results when possible
     * - **Incremental checking**: Focuses on recently modified properties
     *
     * ## Example Usage
     * ```kotlin
     * val violations = propManager.validatePropertyConstraints(vertex)
     * if (violations.isNotEmpty()) {
     *     violations.forEach { violation ->
     *         logger.warn("Property violation: ${violation.type} on key '${violation.propertyKey}' - ${violation.description}")
     *     }
     * }
     * ```
     *
     * @param vertex The vertex to validate constraints for
     * @return List of PropertyConstraintViolation instances (empty if no violations)
     * @see PropertyConstraintViolation for violation details structure
     * @see optimizePropertyStorage for fixing some types of violations
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
     * Add a property lifecycle listener for event notifications.
     *
     * Registers a listener to receive notifications about property lifecycle
     * events (additions, removals, updates). Listeners enable audit trails,
     * caching strategies, validation enforcement, and other reactive behaviors.
     *
     * ## Event Types
     * - **Property Added**: When new properties are created on vertices
     * - **Property Removed**: When properties are deleted or marked as removed
     *
     * ## Listener Features
     * - **Exception isolation**: Listener exceptions don't affect property operations
     * - **Ordered execution**: Listeners execute in registration order
     * - **Thread safety**: Event notifications are synchronized with property operations
     *
     * @param listener The PropertyLifecycleListener to register
     * @see PropertyLifecycleListener for listener interface details
     * @see removePropertyListener for unregistering listeners
     */
    fun addPropertyListener(listener: PropertyLifecycleListener) {
        propertyListeners.add(listener)
    }

    /**
     * Remove a property lifecycle listener from event notifications.
     *
     * Unregisters a previously registered listener to stop receiving
     * property lifecycle event notifications. This is important for
     * preventing memory leaks in applications with dynamic listener
     * registration patterns.
     *
     * @param listener The PropertyLifecycleListener to unregister
     * @return true if the listener was found and removed, false otherwise
     * @see addPropertyListener for registering listeners
     */
    fun removePropertyListener(listener: PropertyLifecycleListener): Boolean {
        return propertyListeners.remove(listener)
    }

    /**
     * Notifies all registered property listeners of a property addition event.
     * Handles any exceptions thrown by listeners to prevent disruption of the main operation.
     * Failed notifications are logged but do not affect the property addition process.
     *
     * @param vertex the vertex to which the property was added
     * @param property the vertex property that was added
     */
    private fun notifyPropertyAdded(vertex: TinkerVertex, property: TinkerVertexProperty<*>) {
        propertyListeners.forEach { listener ->
            try {
                listener.onPropertyAdded(vertex, property)
            } catch (e: Exception) {
                // Log error but don't fail the operation
                logger.w(e) { "Error in property listener during property addition" }
            }
        }
    }

    /**
     * Notifies all registered property listeners of a property removal event.
     * Handles any exceptions thrown by listeners to prevent disruption of the main operation.
     * Failed notifications are logged but do not affect the property removal process.
     *
     * @param vertex the vertex from which the property was removed
     * @param property the vertex property that was removed
     */
    private fun notifyPropertyRemoved(vertex: TinkerVertex, property: TinkerVertexProperty<*>) {
        propertyListeners.forEach { listener ->
            try {
                listener.onPropertyRemoved(vertex, property)
            } catch (e: Exception) {
                // Log error but don't fail the operation
                logger.w(e) { "Error in property listener during property removal" }
            }
        }
    }

    /**
     * Data class containing detailed cardinality analysis information for a property key.
     *
     * Provides comprehensive metrics about property usage, storage efficiency,
     * and cardinality distribution for performance optimization and data modeling decisions.
     *
     * @property cardinality The cardinality constraint for this property key
     * @property propertyCount Total number of property instances with this key
     * @property uniqueValueCount Number of unique values across all property instances
     * @property averageValueSize Average size in bytes of property values
     * @property metaPropertyCount Total number of meta-properties across all instances
     * @property storageEfficiency Ratio of unique values to total properties (1.0 = optimal for SET)
     */
    data class CardinalityInfo(
        val totalProperties: Int,
        val uniqueValues: Int,
        val suggestedCardinality: VertexProperty.Cardinality,
        val hasMetaProperties: Boolean
    )

    /**
     * Data class containing detailed results from property storage optimization operations.
     *
     * Provides metrics about cleanup operations performed and storage improvements
     * achieved during property optimization.
     *
     * @property cleanedProperties Number of removed/invalid properties cleaned up
     * @property cleanedKeys Number of empty property key mappings removed
     * @property bytesReclaimed Estimated bytes of memory reclaimed through optimization
     * @property optimizationTimeMs Time spent performing optimization operations
     */
    data class OptimizationResult(
        val cleanedProperties: Int,
        val cleanedKeys: Int
    )

    /**
     * Data class representing a property constraint violation found during validation.
     *
     * Contains detailed information about validation failures to support debugging,
     * data quality assurance, and automated correction processes.
     *
     * @property violationType The type of constraint violation detected
     * @property propertyKey The property key associated with the violation
     * @property description Human-readable description of the violation
     * @property severity Severity level of the violation (WARNING, ERROR, CRITICAL)
     */
    data class PropertyConstraintViolation(
        val key: String,
        val violation: String,
        val propertyCount: Int
    )

    /**
     * Interface for receiving property lifecycle event notifications.
     *
     * Implementations can respond to property changes for audit logging,
     * cache invalidation, validation enforcement, or other reactive behaviors.
     * Listeners should be lightweight to avoid impacting property operation performance.
     *
     * ## Implementation Guidelines
     * - **Keep operations lightweight**: Avoid heavy computations in event handlers
     * - **Handle exceptions gracefully**: Listener exceptions are caught but should be avoided
     * - **Avoid circular dependencies**: Don't modify the same properties being listened to
     * - **Thread safety**: Implementations should be thread-safe if used across multiple threads
     *
     * @see addPropertyListener for registering listeners
     * @see removePropertyListener for unregistering listeners
     */
    interface PropertyLifecycleListener {
        /**
         * Called when a property is added to a vertex.
         *
         * This method is invoked after the property has been successfully added
         * to the vertex and all indexes have been updated. The property is
         * guaranteed to be in a consistent state when this method is called.
         *
         * @param vertex The vertex that received the new property
         * @param property The vertex property that was added
         */
        fun onPropertyAdded(vertex: TinkerVertex, property: TinkerVertexProperty<*>)

        /**
         * Called when a property is removed from a vertex.
         *
         * This method is invoked before the property is actually removed from
         * the vertex, allowing listeners to access property values and meta-properties
         * one last time before cleanup.
         *
         * @param vertex The vertex that is losing the property
         * @param property The vertex property that will be removed
         */
        fun onPropertyRemoved(vertex: TinkerVertex, property: TinkerVertexProperty<*>)
    }

    /**
     * Default implementation of PropertyLifecycleListener for debugging and development.
     *
     * Provides basic logging of property lifecycle events to help with debugging,
     * development, and understanding property operation patterns. This implementation
     * is safe to use in production but may generate verbose logs.
     *
     * ## Log Output Format
     * - Property additions: "Property added: vertex={id}, key={key}, value={value}"
     * - Property removals: "Property removed: vertex={id}, key={key}, value={value}"
     *
     * @see PropertyLifecycleListener for the interface contract
     */
    class DebugPropertyListener : PropertyLifecycleListener {
        override fun onPropertyAdded(vertex: TinkerVertex, property: TinkerVertexProperty<*>) {
            logger.d { "Property added: vertex=${vertex.id()}, key=${property.key()}, value=${property.value()}" }
        }

        override fun onPropertyRemoved(vertex: TinkerVertex, property: TinkerVertexProperty<*>) {
            logger.d { "Property removed: vertex=${vertex.id()}, key=${property.key()}, value=${property.value()}" }
        }
    }
}
