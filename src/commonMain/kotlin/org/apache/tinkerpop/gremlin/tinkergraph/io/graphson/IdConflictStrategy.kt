package org.apache.tinkerpop.gremlin.tinkergraph.io.graphson

/**
 * Defines strategies for handling ID conflicts during GraphSON deserialization.
 *
 * When deserializing GraphSON data into a graph that may already contain elements
 * with the same IDs, different strategies can be applied to resolve these conflicts.
 *
 * This addresses the critical limitation identified in Task 3.6.2 evaluation where
 * strict ID enforcement prevented standard GraphSON import workflows.
 *
 * @since 1.0.0
 */
enum class IdConflictStrategy {
    /**
     * Strict mode - throws exception if an element with the same ID already exists.
     *
     * This is the traditional behavior that maintains data integrity by preventing
     * any ID conflicts. Use this when you need to ensure no duplicate IDs exist.
     *
     * **Use Cases**:
     * - Importing into empty graphs where no conflicts are expected
     * - Data validation scenarios where conflicts indicate errors
     * - When maintaining strict referential integrity is critical
     *
     * **Behavior**:
     * - Vertices: Throws `Graph.Exceptions.vertexWithIdAlreadyExists(id)`
     * - Edges: Throws `Graph.Exceptions.edgeWithIdAlreadyExists(id)`
     */
    STRICT,

    /**
     * Generate new IDs for conflicting elements (DEFAULT).
     *
     * When an ID conflict is detected, automatically generates a new unique ID
     * for the incoming element while preserving all other properties and relationships.
     * This is the most user-friendly option for most import scenarios.
     *
     * **Use Cases**:
     * - Merging multiple GraphSON files
     * - Importing into existing graphs
     * - Round-trip serialization on same graph instance
     * - Incremental graph loading
     *
     * **Behavior**:
     * - Vertices: Creates new vertex with auto-generated ID, maintains all properties
     * - Edges: Creates new edge with auto-generated ID, updates vertex references if needed
     * - Logs all ID remapping actions for transparency
     */
    GENERATE_NEW_ID,

    /**
     * Merge properties when IDs conflict.
     *
     * When an ID conflict occurs, merges the properties from the incoming element
     * with the existing element. Conflicting property values are overwritten with
     * the new values from the GraphSON data.
     *
     * **Use Cases**:
     * - Updating existing graph data with new property values
     * - Incremental data updates from external sources
     * - Synchronizing graph state with external systems
     *
     * **Behavior**:
     * - Vertices: Merges vertex properties, overwrites conflicting values
     * - Edges: Merges edge properties, maintains original vertex connections
     * - Preserves existing element structure while updating data
     */
    MERGE_PROPERTIES,

    /**
     * Replace existing elements when IDs conflict.
     *
     * When an ID conflict occurs, completely replaces the existing element with
     * the new element from GraphSON data. This includes removing all existing
     * properties and relationships.
     *
     * **Use Cases**:
     * - Complete data refresh from authoritative source
     * - Replacing outdated elements with fresh data
     * - When GraphSON data represents the complete truth
     *
     * **Behavior**:
     * - Vertices: Removes existing vertex and all its edges, creates new vertex
     * - Edges: Removes existing edge, creates new edge with new properties
     * - **Warning**: This can break graph connectivity if not used carefully
     */
    REPLACE_ELEMENT;

    /**
     * Returns a human-readable description of this strategy.
     */
    fun getDescription(): String = when (this) {
        STRICT -> "Throws exception on ID conflicts"
        GENERATE_NEW_ID -> "Generates new unique IDs for conflicting elements"
        MERGE_PROPERTIES -> "Merges properties of conflicting elements"
        REPLACE_ELEMENT -> "Replaces existing elements with new ones"
    }

    /**
     * Returns true if this strategy modifies existing elements in the graph.
     */
    fun modifiesExistingElements(): Boolean = when (this) {
        STRICT, GENERATE_NEW_ID -> false
        MERGE_PROPERTIES, REPLACE_ELEMENT -> true
    }

    /**
     * Returns true if this strategy can potentially break existing graph connectivity.
     */
    fun canBreakConnectivity(): Boolean = when (this) {
        REPLACE_ELEMENT -> true
        else -> false
    }

    companion object {
        /**
         * The default strategy used when none is explicitly specified.
         *
         * GENERATE_NEW_ID is chosen as the default because it provides the most
         * user-friendly behavior for common GraphSON import scenarios while
         * maintaining data integrity.
         */
        val DEFAULT = GENERATE_NEW_ID

        /**
         * Returns the strategy that best matches the given name (case-insensitive).
         *
         * @param name The strategy name to match
         * @return The matching strategy, or null if no match found
         */
        fun fromString(name: String): IdConflictStrategy? {
            return values().find { it.name.equals(name, ignoreCase = true) }
        }

        /**
         * Returns all available strategies with their descriptions.
         */
        fun getAllWithDescriptions(): Map<IdConflictStrategy, String> {
            return values().associateWith { it.getDescription() }
        }
    }
}
