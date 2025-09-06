package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*

/**
 * JavaScript-specific implementation of IndexCache that avoids complex timestamp arithmetic.
 * Uses simplified caching strategy to prevent JavaScript type conversion issues.
 *
 * @param T the type of element being cached (TinkerVertex or TinkerEdge)
 */
actual class IndexCache<T : Element> actual constructor() {

    /**
     * Simple cache storage without complex timestamp calculations.
     * Uses JavaScript Map for better performance and compatibility.
     */
    private val cache = mutableMapOf<String, CacheEntry<T>>()

    /**
     * Maximum number of entries to keep in cache.
     */
    private var maxSize = 1000

    /**
     * Maximum age for cache entries in milliseconds.
     */
    private var maxAge = 300_000L // 5 minutes default

    /**
     * Statistics tracking.
     */
    private var hits = 0L
    private var misses = 0L
    private var evictions = 0L

    /**
     * Cache entry with JavaScript timestamp handling.
     */
    private data class CacheEntry<T : Element>(
        val result: Set<T>,
        val timestamp: Long = kotlin.js.Date.now().toLong(),
        val insertionOrder: Int = nextOrder++
    ) {
        companion object {
            private var nextOrder = 0
        }
    }

    /**
     * Put a query result in the cache.
     */
    actual fun put(indexType: IndexType, key: String, result: Set<T>) {
        put(indexType, key, emptyMap(), result)
    }

    /**
     * Put a query result with parameters in the cache.
     */
    actual fun put(indexType: IndexType, key: String, parameters: Map<String, Any?>, result: Set<T>) {
        val cacheKey = createCacheKey(indexType, key, parameters)
        cache[cacheKey] = CacheEntry(result.toSet())

        // Simple size-based eviction without timestamp complications
        evictIfNecessary()
    }

    /**
     * Get a cached query result.
     */
    actual fun get(indexType: IndexType, key: String): Set<T>? {
        return get(indexType, key, emptyMap())
    }

    /**
     * Get a cached query result with parameters.
     */
    actual fun get(indexType: IndexType, key: String, parameters: Map<String, Any?>): Set<T>? {
        val cacheKey = createCacheKey(indexType, key, parameters)
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
     * Check if the cache contains an entry for the given key.
     */
    actual fun contains(indexType: IndexType, key: String): Boolean {
        return contains(indexType, key, emptyMap())
    }

    /**
     * Check if the cache contains an entry for the given key with parameters.
     */
    actual fun contains(indexType: IndexType, key: String, parameters: Map<String, Any?>): Boolean {
        val cacheKey = createCacheKey(indexType, key, parameters)
        val entry = cache[cacheKey] ?: return false
        return !isExpired(entry)
    }

    /**
     * Remove an entry from the cache.
     */
    actual fun remove(indexType: IndexType, key: String) {
        remove(indexType, key, emptyMap())
    }

    /**
     * Remove an entry from the cache with parameters.
     */
    actual fun remove(indexType: IndexType, key: String, parameters: Map<String, Any?>) {
        val cacheKey = createCacheKey(indexType, key, parameters)
        cache.remove(cacheKey)
    }

    /**
     * Clear all cache entries.
     */
    actual fun clear() {
        val removed = cache.size
        cache.clear()
        evictions += removed
    }

    /**
     * Get the current cache size.
     */
    actual fun size(): Int {
        return cache.size
    }

    /**
     * Set the maximum age for cache entries.
     */
    actual fun setMaxAge(maxAge: Long) {
        require(maxAge > 0) { "Max age must be positive" }
        this.maxAge = maxAge
        cleanupExpired()
    }

    /**
     * Set the maximum number of cache entries.
     */
    actual fun setMaxSize(maxSize: Int) {
        require(maxSize > 0) { "Max size must be positive" }
        this.maxSize = maxSize
        evictIfNecessary()
    }

    /**
     * Clean up expired entries using JavaScript Date.now().
     */
    actual fun cleanupExpired() {
        val currentTime = kotlin.js.Date.now().toLong()
        val toRemove = cache.entries
            .filter { (_, entry) -> currentTime - entry.timestamp > maxAge }
            .map { it.key }

        toRemove.forEach {
            cache.remove(it)
            evictions++
        }
    }

    /**
     * Get cache statistics.
     */
    actual fun getStatistics(): CacheStatistics {
        val total = hits + misses
        val hitRatio = if (total > 0) hits.toDouble() / total else 0.0

        return mapOf(
            "size" to cache.size,
            "hits" to hits,
            "misses" to misses,
            "evictions" to evictions,
            "hitRatio" to hitRatio,
            "maxSize" to maxSize,
            "maxAgeMs" to maxAge
        )
    }

    /**
     * Estimate memory usage of the cache in bytes.
     * JavaScript implementation provides basic estimation.
     */
    actual fun estimateMemoryUsage(): Long {
        // Simple estimation based on entry count and average key/value size
        // In JavaScript, this is approximate due to dynamic typing
        val averageKeySize = 50 // Estimated average key size in characters
        val averageValueSize = 100 // Estimated average value size per element
        val entryOverhead = 64 // Estimated object overhead per entry

        var totalSize = 0L
        cache.forEach { (key, entry) ->
            totalSize += key.length * 2 // Assume 2 bytes per character
            totalSize += entry.result.size * averageValueSize
            totalSize += entryOverhead
        }

        return totalSize
    }

    /**
     * Get optimization recommendations for the cache.
     * JavaScript implementation provides basic recommendations.
     */
    actual fun getOptimizationRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()

        val hitRatio = if (hits + misses > 0) hits.toDouble() / (hits + misses) else 0.0

        if (hitRatio < 0.5) {
            recommendations.add("Low hit ratio (${(hitRatio * 100).toInt()}%) - consider reviewing query patterns")
        }

        if (cache.size >= maxSize * 0.9) {
            recommendations.add("Cache near capacity (${cache.size}/$maxSize) - consider increasing max size")
        }

        if (evictions > hits) {
            recommendations.add("High eviction rate - consider increasing cache size or TTL")
        }

        val avgResultSetSize = cache.values.map { it.result.size }.average()
        if (avgResultSetSize > 100) {
            recommendations.add("Large result sets being cached - consider result set size limits")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Cache performance appears optimal")
        }

        return recommendations
    }

    /**
     * Invalidate cache entries related to a specific property key.
     * In JavaScript, uses simple key matching to avoid complex operations.
     */
    actual fun invalidateKey(propertyKey: String) {
        val toRemove = cache.keys.filter { cacheKey ->
            cacheKey.contains(propertyKey)
        }
        toRemove.forEach {
            cache.remove(it)
        }
    }

    /**
     * Invalidate cache entries for a specific index type.
     */
    actual fun invalidateIndexType(indexType: IndexType) {
        val toRemove = cache.keys.filter {
            it.startsWith("${indexType.name}:")
        }
        toRemove.forEach {
            cache.remove(it)
        }
    }

    /**
     * Invalidate all cache entries containing a specific element.
     * In JavaScript, this is simplified to avoid complex element comparisons.
     */
    actual fun invalidateElement(element: T) {
        // Simple approach: clear all cache since element comparison is complex in JS
        val removed = cache.size
        cache.clear()
        evictions += removed
    }

    /**
     * Create a unique cache key from index type, key, and parameters.
     */
    private fun createCacheKey(indexType: IndexType, key: String, parameters: Map<String, Any?>): String {
        val paramString = if (parameters.isEmpty()) {
            ""
        } else {
            parameters.entries
                .sortedBy { it.key }
                .joinToString(",") { "${it.key}=${it.value}" }
        }
        return "${indexType.name}:$key:$paramString"
    }

    /**
     * Check if a cache entry has expired using JavaScript timestamps.
     */
    private fun isExpired(entry: CacheEntry<T>): Boolean {
        val currentTime = kotlin.js.Date.now().toLong()
        return currentTime - entry.timestamp > maxAge
    }

    /**
     * Evict entries if cache exceeds maximum size.
     * Uses simple FIFO eviction based on insertion order.
     */
    private fun evictIfNecessary() {
        while (cache.size > maxSize) {
            // Find the entry with the lowest insertion order (oldest)
            val oldestEntry = cache.entries.minByOrNull { it.value.insertionOrder }
            if (oldestEntry != null) {
                cache.remove(oldestEntry.key)
                evictions++
            } else {
                break // Safety check
            }
        }
    }

    actual companion object {
        /**
         * Create a new IndexCache instance.
         */
        actual fun <T : Element> create(): IndexCache<T> {
            return IndexCache()
        }
    }
}
