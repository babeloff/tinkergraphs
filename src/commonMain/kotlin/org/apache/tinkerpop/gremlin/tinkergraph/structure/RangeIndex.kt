package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.platform.Platform

/**
 * RangeIndex provides efficient range query capabilities for comparable property values.
 * It maintains sorted structures to enable fast range-based lookups without scanning all elements.
 *
 * @param T the type of element being indexed (TinkerVertex or TinkerEdge)
 */
class RangeIndex<T : Element> {

    /**
     * Map of property key to sorted index data structure.
     * Each index maintains a sorted map from property values to sets of elements.
     */
    private val sortedIndices: MutableMap<String, MutableMap<Comparable<Any>, MutableSet<T>>> = mutableMapOf()

    /**
     * Set of keys that are currently range-indexed.
     */
    private val rangeIndexedKeys: MutableSet<String> = mutableSetOf()

    /**
     * Cache for frequently accessed range queries to improve performance.
     */
    private val queryCache: MutableMap<RangeQuery, Set<T>> = mutableMapOf()

    /**
     * Maximum size for the query cache to prevent memory leaks.
     */
    private var maxCacheSize = 1000

    /**
     * Create a range index for the specified property key.
     * Only comparable values will be indexed for range queries.
     *
     * @param key the property key to range-index
     */
    fun createRangeIndex(key: String) {
        if (!rangeIndexedKeys.contains(key)) {
            rangeIndexedKeys.add(key)
            sortedIndices[key] = Platform.createSortedMap()
        }
    }

    /**
     * Drop the range index for the specified property key.
     *
     * @param key the property key to drop from range indexing
     */
    fun dropRangeIndex(key: String) {
        if (rangeIndexedKeys.contains(key)) {
            rangeIndexedKeys.remove(key)
            sortedIndices.remove(key)
            clearCacheForKey(key)
        }
    }

    /**
     * Get all currently range-indexed keys.
     *
     * @return set of range-indexed property keys
     */
    fun getRangeIndexedKeys(): Set<String> {
        return rangeIndexedKeys.toSet()
    }

    /**
     * Check if a property key is range-indexed.
     *
     * @param key the property key to check
     * @return true if the key is range-indexed
     */
    fun isRangeIndexed(key: String): Boolean {
        return rangeIndexedKeys.contains(key)
    }

    /**
     * Perform a range query on indexed property values.
     *
     * @param key the property key
     * @param minValue the minimum value (inclusive if includeMin is true)
     * @param maxValue the maximum value (inclusive if includeMax is true)
     * @param includeMin whether to include the minimum value
     * @param includeMax whether to include the maximum value
     * @return set of elements within the specified range
     */
    fun rangeQuery(
        key: String,
        minValue: Comparable<Any>? = null,
        maxValue: Comparable<Any>? = null,
        includeMin: Boolean = true,
        includeMax: Boolean = true
    ): Set<T> {
        val sortedIndex = sortedIndices[key] ?: return emptySet()

        val query = RangeQuery(key, minValue, maxValue, includeMin, includeMax)

        // Check cache first
        queryCache[query]?.let { return it }

        val result = mutableSetOf<T>()

        // Handle different range scenarios
        when {
            minValue == null && maxValue == null -> {
                // No bounds - return all elements
                sortedIndex.values.forEach { result.addAll(it) }
            }
            minValue == null -> {
                // Only upper bound
                val upperBound = if (includeMax) maxValue else maxValue
                sortedIndex.entries.forEach { (value, elements) ->
                    val withinRange = if (includeMax) {
                        value <= upperBound!!
                    } else {
                        value < upperBound!!
                    }
                    if (withinRange) result.addAll(elements)
                }
            }
            maxValue == null -> {
                // Only lower bound
                val lowerBound = if (includeMin) minValue else minValue
                sortedIndex.entries.forEach { (value, elements) ->
                    val withinRange = if (includeMin) {
                        value >= lowerBound
                    } else {
                        value > lowerBound
                    }
                    if (withinRange) result.addAll(elements)
                }
            }
            else -> {
                // Both bounds
                sortedIndex.entries.forEach { (value, elements) ->
                    val aboveMin = if (includeMin) {
                        value >= minValue
                    } else {
                        value > minValue
                    }
                    val belowMax = if (includeMax) {
                        value <= maxValue
                    } else {
                        value < maxValue
                    }
                    if (aboveMin && belowMax) result.addAll(elements)
                }
            }
        }

        // Cache the result if cache is not full
        if (queryCache.size < maxCacheSize) {
            queryCache[query] = result.toSet()
        }

        return result
    }

    /**
     * Get elements with exact value (optimized for range-indexed properties).
     *
     * @param key the property key
     * @param value the exact value to match
     * @return set of elements with the exact value
     */
    fun exactQuery(key: String, value: Comparable<Any>): Set<T> {
        val sortedIndex = sortedIndices[key] ?: return emptySet()
        return sortedIndex[value]?.toSet() ?: emptySet()
    }

    /**
     * Get elements with values greater than the specified value.
     *
     * @param key the property key
     * @param value the threshold value
     * @param inclusive whether to include the threshold value
     * @return set of elements with values greater than (or equal to) the threshold
     */
    fun greaterThan(key: String, value: Comparable<Any>, inclusive: Boolean = false): Set<T> {
        return rangeQuery(key, minValue = value, includeMin = inclusive)
    }

    /**
     * Get elements with values less than the specified value.
     *
     * @param key the property key
     * @param value the threshold value
     * @param inclusive whether to include the threshold value
     * @return set of elements with values less than (or equal to) the threshold
     */
    fun lessThan(key: String, value: Comparable<Any>, inclusive: Boolean = false): Set<T> {
        return rangeQuery(key, maxValue = value, includeMax = inclusive)
    }

    /**
     * Get the minimum value for a range-indexed property.
     *
     * @param key the property key
     * @return the minimum value, or null if no values exist
     */
    fun getMinValue(key: String): Comparable<Any>? {
        val sortedIndex = sortedIndices[key] ?: return null
        return if (sortedIndex.isEmpty()) null else sortedIndex.keys.first()
    }

    /**
     * Get the maximum value for a range-indexed property.
     *
     * @param key the property key
     * @return the maximum value, or null if no values exist
     */
    fun getMaxValue(key: String): Comparable<Any>? {
        val sortedIndex = sortedIndices[key] ?: return null
        return if (sortedIndex.isEmpty()) null else sortedIndex.keys.last()
    }

    /**
     * Get all distinct values for a range-indexed property in sorted order.
     *
     * @param key the property key
     * @return list of all values in sorted order
     */
    fun getSortedValues(key: String): List<Comparable<Any>> {
        val sortedIndex = sortedIndices[key] ?: return emptyList()
        return sortedIndex.keys.toList()
    }

    /**
     * Count elements within a range.
     *
     * @param key the property key
     * @param minValue the minimum value (inclusive if includeMin is true)
     * @param maxValue the maximum value (inclusive if includeMax is true)
     * @param includeMin whether to include the minimum value
     * @param includeMax whether to include the maximum value
     * @return count of elements within the range
     */
    fun countInRange(
        key: String,
        minValue: Comparable<Any>? = null,
        maxValue: Comparable<Any>? = null,
        includeMin: Boolean = true,
        includeMax: Boolean = true
    ): Int {
        return rangeQuery(key, minValue, maxValue, includeMin, includeMax).size
    }

    /**
     * Automatically update the range index when an element's property changes.
     *
     * @param key the property key that changed
     * @param newValue the new property value (null if property was removed)
     * @param oldValue the old property value (null if property was added)
     * @param element the element whose property changed
     */
    fun autoUpdate(key: String, newValue: Any?, oldValue: Any?, element: T) {
        if (!isRangeIndexed(key)) return

        val sortedIndex = sortedIndices[key] ?: return

        // Clear cache entries that might be affected by this change
        clearCacheForKey(key)

        // Remove element from old value index
        val oldComparable = safeComparable(oldValue)
        if (oldComparable != null) {
            val oldSet = sortedIndex[oldComparable]
            if (oldSet != null) {
                oldSet.remove(element)
                if (oldSet.isEmpty()) {
                    sortedIndex.remove(oldComparable)
                }
            }
        }

        // Add element to new value index
        val newComparable = safeComparable(newValue)
        if (newComparable != null) {
            val newSet = sortedIndex.getOrPut(newComparable) { mutableSetOf() }
            newSet.add(element)
        }
    }

    /**
     * Add an element to range indices for all of its comparable properties.
     *
     * @param element the element to index
     */
    fun addElement(element: T) {
        rangeIndexedKeys.forEach { key ->
            try {
                val value = element.value<Any>(key)
                val comparable = safeComparable(value)
                if (comparable != null) {
                    autoUpdate(key, value, null, element)
                }
            } catch (e: Exception) {
                // Property doesn't exist or isn't accessible
            }
        }
    }

    /**
     * Remove an element from all range indices.
     *
     * @param element the element to remove from indices
     */
    fun removeElement(element: T) {
        rangeIndexedKeys.forEach { key ->
            try {
                val value = element.value<Any>(key)
                val comparable = safeComparable(value)
                if (comparable != null) {
                    autoUpdate(key, null, value, element)
                }
            } catch (e: Exception) {
                // Property doesn't exist or isn't accessible
            }
        }
    }

    /**
     * Rebuild the range index for a specific key by scanning all provided elements.
     *
     * @param key the property key to rebuild
     * @param elements all elements to scan for this property
     */
    fun rebuildRangeIndex(key: String, elements: Collection<T>) {
        if (!isRangeIndexed(key)) return

        // Clear existing index for this key
        sortedIndices[key] = Platform.createSortedMap()
        clearCacheForKey(key)

        // Rebuild from elements
        elements.forEach { element ->
            try {
                val value = element.value<Any>(key)
                val comparable = safeComparable(value)
                if (comparable != null) {
                    autoUpdate(key, value, null, element)
                }
            } catch (e: Exception) {
                // Property doesn't exist or isn't accessible
            }
        }
    }

    /**
     * Rebuild all range indices by scanning the provided elements.
     *
     * @param elements all elements to scan for range properties
     */
    fun rebuildAllRangeIndices(elements: Collection<T>) {
        rangeIndexedKeys.forEach { key ->
            rebuildRangeIndex(key, elements)
        }
    }

    /**
     * Clear all range indices and indexed keys.
     */
    fun clear() {
        sortedIndices.clear()
        rangeIndexedKeys.clear()
        queryCache.clear()
    }

    /**
     * Set the maximum size for the query cache.
     *
     * @param size the maximum number of cached queries
     */
    fun setMaxCacheSize(size: Int) {
        require(size >= 0) { "Cache size must be non-negative" }
        maxCacheSize = size
        if (queryCache.size > maxCacheSize) {
            // Remove oldest entries (simple implementation - could be improved with LRU)
            val toRemove = queryCache.size - maxCacheSize
            val keysToRemove = queryCache.keys.take(toRemove)
            keysToRemove.forEach { queryCache.remove(it) }
        }
    }

    /**
     * Clear cache entries for a specific key.
     */
    private fun clearCacheForKey(key: String) {
        queryCache.keys.removeAll { it.key == key }
    }

    /**
     * Get statistics about the range index.
     *
     * @return map of statistics
     */
    fun getStatistics(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        stats["rangeIndexedKeyCount"] = rangeIndexedKeys.size
        stats["totalRangeEntries"] = sortedIndices.values.sumOf { it.size }
        stats["totalIndexedElements"] = sortedIndices.values.sumOf { index ->
            index.values.sumOf { it.size }
        }
        stats["cacheSize"] = queryCache.size
        stats["maxCacheSize"] = maxCacheSize

        val keyStats = mutableMapOf<String, Map<String, Any?>>()
        rangeIndexedKeys.forEach { key ->
            val index = sortedIndices[key] ?: return@forEach
            keyStats[key] = mapOf(
                "distinctValues" to index.size,
                "totalElements" to index.values.sumOf { it.size },
                "minValue" to getMinValue(key),
                "maxValue" to getMaxValue(key)
            )
        }
        stats["keyStatistics"] = keyStats

        return stats
    }

    /**
     * Check if the range index is empty (no indexed keys).
     *
     * @return true if no keys are range-indexed
     */
    fun isEmpty(): Boolean {
        return rangeIndexedKeys.isEmpty()
    }

    /**
     * Get the total number of elements across all range indices.
     * Note: Elements may be counted multiple times if they appear in multiple indices.
     *
     * @return total element count across all range indices
     */
    fun getTotalElementCount(): Int {
        return sortedIndices.values.sumOf { index ->
            index.values.sumOf { it.size }
        }
    }

    override fun toString(): String {
        return "RangeIndex[rangeIndexedKeys=${rangeIndexedKeys.size}, totalEntries=${getTotalElementCount()}, cacheSize=${queryCache.size}]"
    }

    /**
     * Data class representing a range query for caching purposes.
     */
    private data class RangeQuery(
        val key: String,
        val minValue: Comparable<Any>?,
        val maxValue: Comparable<Any>?,
        val includeMin: Boolean,
        val includeMax: Boolean
    )

    companion object {
        /**
         * Safely converts a value to Comparable<Any> if possible.
         *
         * @param value the value to convert
         * @return the value as Comparable<Any> if it implements Comparable, null otherwise
         */
        fun <V> safeComparable(value: V?): Comparable<Any>? {
            return when (value) {
                null -> null
                is Comparable<*> -> {
                    try {
                        @Suppress("UNCHECKED_CAST") // This is safe because we checked is Comparable<*>
                        value as Comparable<Any>
                    } catch (e: ClassCastException) {
                        null
                    }
                }
                else -> null
            }
        }

        /**
         * Safely performs a range query with automatic type conversion.
         *
         * @param rangeIndex the range index to query
         * @param key the property key
         * @param minValue the minimum value (will be safely converted)
         * @param maxValue the maximum value (will be safely converted)
         * @param includeMin whether to include the minimum value
         * @param includeMax whether to include the maximum value
         * @return set of elements within the range, or empty set if conversion fails
         */
        fun <T : Element, V> safeRangeQuery(
            rangeIndex: RangeIndex<T>,
            key: String,
            minValue: V? = null,
            maxValue: V? = null,
            includeMin: Boolean = true,
            includeMax: Boolean = true
        ): Set<T> {
            val safeMin = safeComparable(minValue)
            val safeMax = safeComparable(maxValue)

            // If we couldn't convert the values, return empty set
            if ((minValue != null && safeMin == null) || (maxValue != null && safeMax == null)) {
                return emptySet()
            }

            return rangeIndex.rangeQuery(key, safeMin, safeMax, includeMin, includeMax)
        }
    }
}
