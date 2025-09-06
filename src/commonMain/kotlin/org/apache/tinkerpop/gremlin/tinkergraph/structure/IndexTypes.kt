package org.apache.tinkerpop.gremlin.tinkergraph.structure

/**
 * Index types for different caching strategies.
 */
enum class IndexType {
    SINGLE_PROPERTY,
    COMPOSITE,
    RANGE
}

/**
 * Cache statistics type alias for Map-based access.
 */
typealias CacheStatistics = Map<String, Any>
