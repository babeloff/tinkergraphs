package org.apache.tinkerpop.gremlin.tinkergraph.platform

import kotlinx.cinterop.convert
import platform.posix.gettimeofday
import platform.posix.timeval
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Native platform implementation of platform abstraction for TinkerGraph.
 *
 * This implementation provides Kotlin/Native runtime compatibility for platform-specific
 * operations including time management, data structure creation, and formatting utilities.
 * Key considerations for Native platform:
 * - Uses POSIX gettimeofday() for high-precision time operations
 * - Limited support for sorted data structures (falls back to regular maps)
 * - Implements busy-wait for sleep operations
 * - Provides custom percentage formatting without external dependencies
 *
 * @see org.apache.tinkerpop.gremlin.tinkergraph.platform.Platform
 */
@OptIn(ExperimentalForeignApi::class)
actual object Platform {
    /**
     * Get the current time in milliseconds since Unix epoch.
     *
     * Uses POSIX gettimeofday() system call to obtain high-precision time information.
     * This provides microsecond precision which is then converted to milliseconds
     * for consistency with other platforms.
     *
     * @return Current time in milliseconds since January 1, 1970, 00:00:00 UTC
     */
    actual fun currentTimeMillis(): Long {
        memScoped {
            val timeVal = alloc<timeval>()
            gettimeofday(timeVal.ptr, null)
            return timeVal.tv_sec.convert<Long>() * 1000L + timeVal.tv_usec.convert<Long>() / 1000L
        }
    }

    /**
     * Create a sorted map implementation for Native platform.
     *
     * Kotlin/Native doesn't have built-in sorted maps like Java's TreeMap, so this
     * implementation returns a regular MutableMap. Sorting operations should be
     * performed explicitly when needed by the calling code.
     *
     * @param K the key type, must be Comparable
     * @param V the value type
     * @return A MutableMap that does not maintain sorted order
     */
    actual fun <K : Comparable<K>, V> createSortedMap(): MutableMap<K, V> {
        return mutableMapOf<K, V>()
    }

    /**
     * Create a linked hash map with specified parameters.
     *
     * Native Kotlin uses regular mutable maps which maintain insertion order by default.
     * The initialCapacity, loadFactor, and accessOrder parameters are ignored since
     * the underlying implementation doesn't support these configurations.
     *
     * @param initialCapacity ignored in Native implementation
     * @param loadFactor ignored in Native implementation
     * @param accessOrder ignored in Native implementation
     * @return A MutableMap that maintains insertion order
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
     *
     * Custom implementation for Native platform that doesn't rely on external
     * formatting libraries. Converts the value to a percentage and formats
     * with exactly 2 decimal places using integer arithmetic.
     *
     * @param value the double value to format (e.g., 0.2550 becomes "25.50")
     * @return formatted percentage string with 2 decimal places
     */
    actual fun formatPercentage(value: Double): String {
        // Simple formatting for native - multiply by 100 and format
        val percentage = (value * 100.0).toInt() / 100.0
        return "${percentage.toInt()}.${((percentage % 1.0) * 100).toInt().toString().padStart(2, '0')}"
    }

    /**
     * Sleep for the specified number of milliseconds using busy waiting.
     *
     * Native implementation uses a busy wait loop since Kotlin/Native doesn't
     * have built-in sleep functionality in the common standard library.
     * This approach is synchronous but not CPU-efficient.
     *
     * Note: In production code, consider using platform-specific sleep APIs
     * or coroutines for better resource utilization.
     *
     * @param millis the number of milliseconds to sleep
     */
    actual fun sleep(millis: Long) {
        // Native implementation using busy wait
        val start = currentTimeMillis()
        while (currentTimeMillis() - start < millis) {
            // Busy wait
        }
    }

    /**
     * Helper method to calculate time differences in Native platform.
     *
     * Provides a simple arithmetic difference between two time values.
     * Native platform has stable Long arithmetic without precision concerns.
     *
     * @param start the start time in milliseconds
     * @param end the end time in milliseconds
     * @return the time difference in milliseconds
     */
    actual fun timeDifference(start: Long, end: Long): Long {
        return end - start
    }

    /**
     * Helper method to compare time values in Native platform.
     *
     * Provides a simple comparison operation for time values using
     * standard Kotlin comparison operators. Native platform has
     * reliable Long comparison without precision issues.
     *
     * @param duration the duration value to compare
     * @param threshold the threshold value for comparison
     * @return true if duration > threshold, false otherwise
     */
    actual fun timeComparison(duration: Long, threshold: Long): Boolean {
        return duration > threshold
    }
}
