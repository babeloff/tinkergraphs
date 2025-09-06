package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.platform.Platform

/**
 * Native-specific implementation of IndexCache with basic timestamp support.
 * Uses Kotlin/Native collections and platform-specific time calculations.
 *
 * @param T the type of element being cached (TinkerVertex or TinkerEdge)
 */
actual class IndexCache<T : Element> actual constructor() {

    /**
     * Cache storage using standard mutable map.
     */
    private val cache = mutableMapOf<String, CacheEntry<T>>()

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
     * Cache entry with timestamp.
     */
    private data class CacheEntry<T : Element>(
        val result: Set<T>,
        val timestamp: Long = Platform.currentTimeMillis()
    )

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
        val entry = CacheEntry(result.toSet())

        cache[cacheKey] = entry
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
     * Clean up expired entries using platform time calculations.
     */
    actual fun cleanupExpired() {
        val currentTime = Platform.currentTimeMillis()
        val toRemove = cache.entries
            .filter { (_, entry) ->
                val timeDiff = Platform.timeDifference(entry.timestamp, currentTime)
                Platform.timeComparison(timeDiff, maxAge)
            }
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
     * Native implementation provides platform-appropriate estimation.
     */
    actual fun estimateMemoryUsage(): Long {
        // Basic estimation for native platforms
        val averageKeySize = 64 // Estimated average key size in bytes
        val averageValueSize = 128 // Estimated average value size per element
        val entryOverhead = 48 // Estimated object overhead per entry

        var totalSize = 0L
        cache.forEach { (key, entry) ->
            totalSize += key.length * 2 // Assume UTF-16 encoding
            totalSize += entry.result.size * averageValueSize
            totalSize += entryOverhead
            totalSize += 8L // timestamp size
        }

        return totalSize
    }

    /**
     * Get optimization recommendations for the cache.
     * Native implementation provides basic recommendations.
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

        val avgResultSetSize = if (cache.isNotEmpty()) {
            cache.values.map { it.result.size }.average()
        } else 0.0

        if (avgResultSetSize > 100) {
            recommendations.add("Large average result set size (${avgResultSetSize.toInt()}) - consider result limiting")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Cache performance appears optimal")
        }

        return recommendations
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
     * Check if a cache entry has expired using platform time calculations.
     */
    private fun isExpired(entry: CacheEntry<T>): Boolean {
        val currentTime = Platform.currentTimeMillis()
        val timeDiff = Platform.timeDifference(entry.timestamp, currentTime)
        return Platform.timeComparison(timeDiff, maxAge)
    }

    /**
     * Evict entries if cache exceeds maximum size.
     * Uses simple strategy by removing entries with oldest timestamps.
     */
    private fun evictIfNecessary() {
        while (cache.size > maxSize) {
            val oldestEntry = cache.entries.minByOrNull { it.value.timestamp }
            if (oldestEntry != null) {
                cache.remove(oldestEntry.key)
                evictions++
            } else {
                break // Safety check
            }
        }
    }

    /**
     * Invalidate cache entries related to a specific property key.
     */
    actual fun invalidateKey(propertyKey: String) {
        val toRemove = cache.keys.filter { cacheKey ->
            cacheKey.contains(propertyKey)
        }
        toRemove.forEach { cache.remove(it) }
    }

    /**
     * Invalidate cache entries for a specific index type.
     */
    actual fun invalidateIndexType(indexType: IndexType) {
        val toRemove = cache.keys.filter {
            it.startsWith("${indexType.name}:")
        }
        toRemove.forEach { cache.remove(it) }
    }

    /**
     * Invalidate all cache entries containing a specific element.
     */
    actual fun invalidateElement(element: T) {
        val toRemove = mutableListOf<String>()
        cache.entries.forEach { (key, entry) ->
            if (element in entry.result) {
                toRemove.add(key)
            }
        }
        toRemove.forEach {
            cache.remove(it)
            evictions++
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
