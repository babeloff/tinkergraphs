package org.apache.tinkerpop.gremlin.tinkergraph.platform

import kotlinx.cinterop.convert
import platform.posix.gettimeofday
import platform.posix.timeval
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Native implementation of platform abstraction.
 */
@OptIn(ExperimentalForeignApi::class)
actual object Platform {
    /**
     * Get the current time in milliseconds since epoch.
     */
    actual fun currentTimeMillis(): Long {
        memScoped {
            val timeVal = alloc<timeval>()
            gettimeofday(timeVal.ptr, null)
            return timeVal.tv_sec.convert<Long>() * 1000L + timeVal.tv_usec.convert<Long>() / 1000L
        }
    }

    /**
     * Create a sorted map implementation.
     * Native doesn't have built-in sorted maps, so we use a regular map
     * and sort keys when needed.
     */
    actual fun <K : Comparable<K>, V> createSortedMap(): MutableMap<K, V> {
        return mutableMapOf<K, V>()
    }

    /**
     * Create a linked hash map with specified initial capacity, load factor, and access order.
     * Native uses regular mutable maps which maintain insertion order.
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
        // Simple formatting for native - multiply by 100 and format
        val percentage = (value * 100.0).toInt() / 100.0
        return "${percentage.toInt()}.${((percentage % 1.0) * 100).toInt().toString().padStart(2, '0')}"
    }

    /**
     * Sleep for the specified number of milliseconds.
     */
    actual fun sleep(millis: Long) {
        // Native implementation using busy wait
        val start = currentTimeMillis()
        while (currentTimeMillis() - start < millis) {
            // Busy wait
        }
    }
}
