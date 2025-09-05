package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*

/**
 * IndexCache provides memory optimization for frequently accessed index entries.
 * It uses LRU (Least Recently Used) caching strategy to manage memory usage efficiently.
 *
 * @param T the type of element being cached (TinkerVertex or TinkerEdge)
 */
class IndexCache<T : Element> {

    /**
     * LRU cache for index query results.
     */
    private val cache = LinkedHashMap<CacheKey, CacheEntry<T>>(16, 0.75f, true)

    /**
     * Maximum number of entries to keep in cache.
     */
    private var maxSize = 1000

    /**
     * Maximum age for cache entries in milliseconds.
     */
    private var maxAge = 300_000L // 5 minutes

    /**
     * Statistics tracking cache performance.
     */
    private var hits = 0L
    private var misses = 0L
    private var evictions = 0L

    /**
     * Put a query result in the cache.
     *
     * @param indexType the type of index used
     * @param key the cache key identifying the query
     * @param result the query result to cache
     */
    fun put(indexType: IndexType, key: String, result: Set<T>) {
        put(indexType, key, emptyMap(), result)
    }

    /**
     * Put a query result with parameters in the cache.
     *
     * @param indexType the type of index used
     * @param key the cache key identifying the query
     * @param parameters additional query parameters
     * @param result the query result to cache
     */
    fun put(indexType: IndexType, key: String, parameters: Map<String, Any?>, result: Set<T>) {
        val cacheKey = CacheKey(indexType, key, parameters)
        val entry = CacheEntry(result.toSet(), System.currentTimeMillis())

        // Add to cache
        cache[cacheKey] = entry

        // Evict old entries if necessary
        evictIfNecessary()
    }

    /**
     * Get a cached query result.
     *
     * @param indexType the type of index used
     * @param key the cache key identifying the query
     * @return cached result, or null if not found or expired
     */
    fun get(indexType: IndexType, key: String): Set<T>? {
        return get(indexType, key, emptyMap())
    }

    /**
     * Get a cached query result with parameters.
     *
     * @param indexType the type of index used
     * @param key the cache key identifying the query
     * @param parameters additional query parameters
     * @return cached result, or null if not found or expired
     */
    fun get(indexType: IndexType, key: String, parameters: Map<String, Any?>): Set<T>? {
        val cacheKey = CacheKey(indexType, key, parameters)
        val entry = cache[cacheKey]

        return when {
            entry == null -> {
                misses++
                null
            }
            isExpired(entry) -> {
                cache.remove(cacheKey)
                misses++
                null
            }
            else -> {
                hits++
                entry.result
            }
        }
    }

    /**
     * Check if a query result is cached and valid.
     *
     * @param indexType the type of index used
     * @param key the cache key identifying the query
     * @param parameters additional query parameters
     * @return true if the result is cached and not expired
     */
    fun contains(indexType: IndexType, key: String, parameters: Map<String, Any?> = emptyMap()): Boolean {
        val cacheKey = CacheKey(indexType, key, parameters)
        val entry = cache[cacheKey] ?: return false
        return !isExpired(entry)
    }

    /**
     * Invalidate cache entries related to a specific property key.
     * This should be called when properties are modified.
     *
     * @param propertyKey the property key that was modified
     */
    fun invalidateKey(propertyKey: String) {
        val toRemove = cache.keys.filter { cacheKey ->
            cacheKey.key == propertyKey ||
            cacheKey.parameters.containsKey(propertyKey) ||
            cacheKey.key.contains(propertyKey)
        }
        toRemove.forEach { cache.remove(it) }
    }

    /**
     * Invalidate cache entries for a specific index type.
     *
     * @param indexType the type of index to invalidate
     */
    fun invalidateIndexType(indexType: IndexType) {
        val toRemove = cache.keys.filter { it.indexType == indexType }
        toRemove.forEach { cache.remove(it) }
    }

    /**
     * Invalidate all cache entries containing a specific element.
     * This should be called when elements are modified or removed.
     *
     * @param element the element that was modified or removed
     */
    fun invalidateElement(element: T) {
        val toRemove = mutableListOf<CacheKey>()
        cache.entries.forEach { (key, entry) ->
            if (element in entry.result) {
                toRemove.add(key)
            }
        }
        toRemove.forEach { cache.remove(it) }
    }

    /**
     * Clear all cached entries.
     */
    fun clear() {
        val removed = cache.size
        cache.clear()
        evictions += removed
    }

    /**
     * Remove expired entries from the cache.
     */
    fun cleanupExpired() {
        val currentTime = System.currentTimeMillis()
        val toRemove = cache.entries
            .filter { (_, entry) -> currentTime - entry.timestamp > maxAge }
            .map { it.key }

        toRemove.forEach {
            cache.remove(it)
            evictions++
        }
    }

    /**
     * Set the maximum cache size.
     *
     * @param size maximum number of entries to keep
     */
    fun setMaxSize(size: Int) {
        require(size > 0) { "Max size must be positive" }
        maxSize = size
        evictIfNecessary()
    }

    /**
     * Set the maximum age for cache entries.
     *
     * @param ageMillis maximum age in milliseconds
     */
    fun setMaxAge(ageMillis: Long) {
        require(ageMillis > 0) { "Max age must be positive" }
        maxAge = ageMillis
        cleanupExpired()
    }

    /**
     * Get cache statistics.
     *
     * @return map containing cache performance statistics
     */
    fun getStatistics(): Map<String, Any> {
        val totalRequests = hits + misses
        return mapOf(
            "size" to cache.size,
            "maxSize" to maxSize,
            "maxAgeMs" to maxAge,
            "hits" to hits,
            "misses" to misses,
            "evictions" to evictions,
            "hitRate" to if (totalRequests > 0) hits.toDouble() / totalRequests else 0.0,
            "missRate" to if (totalRequests > 0) misses.toDouble() / totalRequests else 0.0
        )
    }

    /**
     * Reset cache statistics.
     */
    fun resetStatistics() {
        hits = 0L
        misses = 0L
        evictions = 0L
    }

    /**
     * Get memory usage estimate in bytes.
     * This is a rough estimate based on typical object sizes.
     *
     * @return estimated memory usage in bytes
     */
    fun estimateMemoryUsage(): Long {
        var estimate = 0L

        cache.entries.forEach { (key, entry) ->
            // Estimate key size
            estimate += key.estimateSize()

            // Estimate entry size (result set + timestamp)
            estimate += 8 // timestamp (Long)
            estimate += entry.result.size * 100 // rough estimate per element
        }

        // Add overhead for LinkedHashMap structure
        estimate += cache.size * 32 // rough estimate for map entry overhead

        return estimate
    }

    /**
     * Get the current cache hit rate.
     *
     * @return hit rate as a percentage (0.0 to 1.0)
     */
    fun getHitRate(): Double {
        val totalRequests = hits + misses
        return if (totalRequests > 0) hits.toDouble() / totalRequests else 0.0
    }

    /**
     * Check if cache is healthy (good hit rate, not too many evictions).
     *
     * @return true if cache performance is good
     */
    fun isHealthy(): Boolean {
        val hitRate = getHitRate()
        val evictionRate = if (cache.size > 0) evictions.toDouble() / cache.size else 0.0

        return hitRate > 0.3 && evictionRate < 2.0 // Thresholds can be tuned
    }

    /**
     * Get recommendations for cache optimization.
     *
     * @return list of optimization recommendations
     */
    fun getOptimizationRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        val hitRate = getHitRate()
        val stats = getStatistics()

        when {
            hitRate < 0.2 -> recommendations.add("Consider increasing cache size or reducing max age - low hit rate (${"%.2f".format(hitRate * 100)}%)")
            hitRate > 0.8 && cache.size < maxSize * 0.5 -> recommendations.add("Cache size could potentially be reduced - high hit rate with low utilization")
        }

        val memoryUsage = estimateMemoryUsage()
        if (memoryUsage > 50_000_000) { // 50MB threshold
            recommendations.add("High memory usage detected (${memoryUsage / 1_000_000}MB) - consider reducing cache size")
        }

        val evictionRate = evictions.toDouble() / maxOf(hits + misses, 1)
        if (evictionRate > 0.5) {
            recommendations.add("High eviction rate - consider increasing cache size or max age")
        }

        return recommendations
    }

    /**
     * Evict entries if cache exceeds maximum size.
     */
    private fun evictIfNecessary() {
        while (cache.size > maxSize) {
            val eldest = cache.entries.first()
            cache.remove(eldest.key)
            evictions++
        }
    }

    /**
     * Check if a cache entry is expired.
     */
    private fun isExpired(entry: CacheEntry<T>): Boolean {
        return System.currentTimeMillis() - entry.timestamp > maxAge
    }

    /**
     * Data class representing a cache key.
     */
    private data class CacheKey(
        val indexType: IndexType,
        val key: String,
        val parameters: Map<String, Any?>
    ) {
        fun estimateSize(): Long {
            var size = 0L
            size += indexType.name.length * 2 // rough char size
            size += key.length * 2
            size += parameters.size * 50 // rough estimate for parameters
            return size
        }
    }

    /**
     * Data class representing a cache entry.
     */
    private data class CacheEntry<T : Element>(
        val result: Set<T>,
        val timestamp: Long
    )

    /**
     * Types of indices for cache organization.
     */
    enum class IndexType {
        SINGLE_PROPERTY,
        COMPOSITE,
        RANGE,
        FULL_SCAN
    }

    override fun toString(): String {
        val stats = getStatistics()
        return "IndexCache[size=${cache.size}, hitRate=${"%.2f".format(getHitRate() * 100)}%, memoryMB=${estimateMemoryUsage() / 1_000_000}]"
    }
}
