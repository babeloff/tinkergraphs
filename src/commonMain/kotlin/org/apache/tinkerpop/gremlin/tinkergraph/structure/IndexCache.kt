package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*

/**
 * Platform-specific IndexCache implementation for memory optimization of frequently accessed index entries.
 * Uses platform-appropriate caching strategies and timestamp handling.
 *
 * @param T the type of element being cached (TinkerVertex or TinkerEdge)
 */
expect class IndexCache<T : Element> {

    constructor()

    /**
     * Put a query result in the cache.
     *
     * @param indexType the type of index used
     * @param key the cache key identifying the query
     * @param result the query result to cache
     */
    fun put(indexType: IndexType, key: String, result: Set<T>)

    /**
     * Put a query result with parameters in the cache.
     *
     * @param indexType the type of index used
     * @param key the cache key identifying the query
     * @param parameters additional query parameters
     * @param result the query result to cache
     */
    fun put(indexType: IndexType, key: String, parameters: Map<String, Any?>, result: Set<T>)

    /**
     * Get a cached query result.
     *
     * @param indexType the type of index used
     * @param key the cache key identifying the query
     * @return the cached result or null if not found or expired
     */
    fun get(indexType: IndexType, key: String): Set<T>?

    /**
     * Get a cached query result with parameters.
     *
     * @param indexType the type of index used
     * @param key the cache key identifying the query
     * @param parameters additional query parameters
     * @return the cached result or null if not found or expired
     */
    fun get(indexType: IndexType, key: String, parameters: Map<String, Any?>): Set<T>?

    /**
     * Check if the cache contains an entry for the given key.
     *
     * @param indexType the type of index
     * @param key the cache key
     * @return true if the cache contains a valid entry
     */
    fun contains(indexType: IndexType, key: String): Boolean

    /**
     * Check if the cache contains an entry for the given key with parameters.
     *
     * @param indexType the type of index
     * @param key the cache key
     * @param parameters additional query parameters
     * @return true if the cache contains a valid entry
     */
    fun contains(indexType: IndexType, key: String, parameters: Map<String, Any?>): Boolean

    /**
     * Remove an entry from the cache.
     *
     * @param indexType the type of index
     * @param key the cache key to remove
     */
    fun remove(indexType: IndexType, key: String)

    /**
     * Remove an entry from the cache with parameters.
     *
     * @param indexType the type of index
     * @param key the cache key to remove
     * @param parameters additional query parameters
     */
    fun remove(indexType: IndexType, key: String, parameters: Map<String, Any?>)

    /**
     * Clear all cache entries.
     */
    fun clear()

    /**
     * Get the current cache size.
     */
    fun size(): Int

    /**
     * Set the maximum age for cache entries in milliseconds.
     *
     * @param maxAge maximum age in milliseconds
     */
    fun setMaxAge(maxAge: Long)

    /**
     * Set the maximum number of cache entries.
     *
     * @param maxSize maximum number of entries
     */
    fun setMaxSize(maxSize: Int)

    /**
     * Clean up expired entries.
     */
    fun cleanupExpired()

    /**
     * Get cache statistics.
     */
    fun getStatistics(): CacheStatistics

    /**
     * Estimate memory usage of the cache in bytes.
     *
     * @return estimated memory usage in bytes
     */
    fun estimateMemoryUsage(): Long

    /**
     * Get optimization recommendations for the cache.
     *
     * @return list of optimization recommendations
     */
    fun getOptimizationRecommendations(): List<String>

    /**
     * Invalidate cache entries related to a specific property key.
     * This should be called when properties are modified.
     *
     * @param propertyKey the property key that was modified
     */
    fun invalidateKey(propertyKey: String)

    /**
     * Invalidate cache entries for a specific index type.
     *
     * @param indexType the type of index to invalidate
     */
    fun invalidateIndexType(indexType: IndexType)

    /**
     * Invalidate all cache entries containing a specific element.
     * This should be called when elements are modified or removed.
     *
     * @param element the element that was modified or removed
     */
    fun invalidateElement(element: T)

    companion object {
        /**
         * Create a new IndexCache instance.
         */
        fun <T : Element> create(): IndexCache<T>
    }
}
