package org.apache.tinkerpop.gremlin.tinkergraph.platform

/**
 * JavaScript implementation of platform abstraction.
 */
actual object Platform {
    /**
     * Get the current time in milliseconds since epoch.
     */
    actual fun currentTimeMillis(): Long {
        return js("Date.now()") as Long
    }

    /**
     * Create a sorted map implementation.
     * JavaScript doesn't have built-in sorted maps, so we use a regular map
     * and sort keys when needed.
     */
    actual fun <K : Comparable<K>, V> createSortedMap(): MutableMap<K, V> {
        return mutableMapOf<K, V>()
    }

    /**
     * Create a linked hash map with specified initial capacity, load factor, and access order.
     * JavaScript Map maintains insertion order by default.
     */
    actual fun <K, V> createLinkedHashMap(
        initialCapacity: Int,
        loadFactor: Float,
        accessOrder: Boolean
    ): MutableMap<K, V> {
        return mutableMapOf<K, V>()
    }

    /**
     * Format a double value as a percentage string with 2 decimal places.
     */
    actual fun formatPercentage(value: Double): String {
        return value.asDynamic().toFixed(2) as String
    }

    /**
     * Sleep for the specified number of milliseconds.
     */
    actual fun sleep(millis: Long) {
        // JavaScript doesn't have synchronous sleep, so we use a busy wait
        // This is not ideal but necessary for cross-platform compatibility in tests
        val start = js("Date.now()") as Long
        while (js("Date.now()") as Long - start < millis) {
            // Busy wait
        }
    }
}
