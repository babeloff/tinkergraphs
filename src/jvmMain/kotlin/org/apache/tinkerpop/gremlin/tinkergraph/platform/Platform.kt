package org.apache.tinkerpop.gremlin.tinkergraph.platform

import java.util.*

/**
 * JVM implementation of platform abstraction.
 */
actual object Platform {
    /**
     * Get the current time in milliseconds since epoch.
     */
    actual fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }

    /**
     * Create a sorted map implementation.
     */
    actual fun <K : Comparable<K>, V> createSortedMap(): MutableMap<K, V> {
        return TreeMap<K, V>()
    }

    /**
     * Create a linked hash map with specified initial capacity, load factor, and access order.
     */
    actual fun <K, V> createLinkedHashMap(
        initialCapacity: Int,
        loadFactor: Float,
        accessOrder: Boolean
    ): MutableMap<K, V> {
        return LinkedHashMap<K, V>(initialCapacity, loadFactor, accessOrder)
    }

    /**
     * Format a double value as a percentage string with 2 decimal places.
     */
    actual fun formatPercentage(value: Double): String {
        return "%.2f".format(value)
    }

    /**
     * Sleep for the specified number of milliseconds.
     */
    actual fun sleep(millis: Long) {
        Thread.sleep(millis)
    }
}
