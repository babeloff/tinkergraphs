package org.apache.tinkerpop.gremlin.tinkergraph.platform

/**
 * Platform abstraction for multiplatform compatibility.
 * Provides platform-specific implementations for common operations.
 */
expect object Platform {
    /**
     * Get the current time in milliseconds since epoch.
     */
    fun currentTimeMillis(): Long

    /**
     * Create a sorted map implementation.
     */
    fun <K : Comparable<K>, V> createSortedMap(): MutableMap<K, V>

    /**
     * Create a linked hash map with specified initial capacity, load factor, and access order.
     */
    fun <K, V> createLinkedHashMap(
        initialCapacity: Int = 16,
        loadFactor: Float = 0.75f,
        accessOrder: Boolean = false
    ): MutableMap<K, V>

    /**
     * Format a double value as a percentage string with 2 decimal places.
     */
    fun formatPercentage(value: Double): String

    /**
     * Sleep for the specified number of milliseconds.
     */
    fun sleep(millis: Long)
}
