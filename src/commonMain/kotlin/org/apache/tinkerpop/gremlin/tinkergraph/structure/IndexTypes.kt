package org.apache.tinkerpop.gremlin.tinkergraph.structure

/**
 * Enumeration of index types supported by TinkerGraph for different caching and query strategies.
 *
 * Each index type provides optimized access patterns for specific query scenarios:
 * - Single property indices for exact value lookups
 * - Composite indices for multi-property queries
 * - Range indices for ordered value queries and range scans
 *
 * @see TinkerIndex
 * @see CompositeIndex
 * @see RangeIndex
 */
enum class IndexType {
    /**
     * Single property index for exact value matching on individual properties.
     * Optimized for queries like `vertex.property("name") == "John"`.
     */
    SINGLE_PROPERTY,

    /**
     * Composite index for multi-property queries with exact value matching.
     * Optimized for queries involving multiple properties simultaneously,
     * such as `vertex.property("name") == "John" AND vertex.property("age") == 30`.
     */
    COMPOSITE,

    /**
     * Range index for ordered value queries and range scans.
     * Optimized for queries like `vertex.property("age") > 25 AND vertex.property("age") < 65`
     * and supports efficient sorting operations.
     */
    RANGE
}

/**
 * Type alias for cache statistics data structure.
 *
 * Provides a convenient Map-based interface for accessing cache performance metrics
 * including hit rates, miss counts, eviction statistics, and memory usage information.
 *
 * Example usage:
 * ```kotlin
 * val stats: CacheStatistics = indexCache.getStatistics()
 * val hitRate = stats["hitRate"] as Double
 * val totalQueries = stats["totalQueries"] as Long
 * ```
 *
 * @see IndexCache
 */
typealias CacheStatistics = Map<String, Any>
