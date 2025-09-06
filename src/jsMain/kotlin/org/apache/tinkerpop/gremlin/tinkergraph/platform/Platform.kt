package org.apache.tinkerpop.gremlin.tinkergraph.platform

/**
 * JavaScript-specific implementation of platform abstraction for TinkerGraph.
 *
 * This implementation provides JavaScript runtime compatibility for platform-specific
 * operations including time management, data structure creation, and formatting utilities.
 * Key considerations for JavaScript platform:
 * - Uses JavaScript Date API for time operations
 * - Limited support for sorted data structures (falls back to regular maps)
 * - Implements busy-wait for sleep operations due to JavaScript's async nature
 * - Handles JavaScript number precision limitations for time calculations
 *
 * @see org.apache.tinkerpop.gremlin.tinkergraph.platform.Platform
 */
actual object Platform {
    /**
     * Get the current time in milliseconds since Unix epoch.
     *
     * Uses JavaScript's Date.now() function which returns the number of milliseconds
     * since January 1, 1970, 00:00:00 UTC. Includes error handling for potential
     * JavaScript runtime issues.
     *
     * @return Current time in milliseconds, or 0L if an error occurs
     */
    actual fun currentTimeMillis(): Long {
        return try {
            kotlin.js.Date.now().toLong()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Create a sorted map implementation for JavaScript platform.
     *
     * JavaScript doesn't have built-in sorted maps like Java's TreeMap, so this
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
     * JavaScript Map maintains insertion order by default, so the accessOrder parameter
     * is ignored. The initialCapacity and loadFactor parameters are also not used
     * since JavaScript Maps handle capacity management internally.
     *
     * @param initialCapacity ignored in JavaScript implementation
     * @param loadFactor ignored in JavaScript implementation
     * @param accessOrder ignored in JavaScript implementation
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
     * Uses JavaScript's native toFixed() method to format the number.
     * Includes error handling to fall back to standard toString() if formatting fails.
     *
     * @param value the double value to format
     * @return formatted percentage string (e.g., "25.50") or toString() fallback
     */
    actual fun formatPercentage(value: Double): String {
        return try {
            value.asDynamic().toFixed(2).unsafeCast<String>()
        } catch (e: Exception) {
            value.toString()
        }
    }

    /**
     * Sleep for the specified number of milliseconds using busy waiting.
     *
     * JavaScript doesn't have synchronous sleep functionality, so this implementation
     * uses a busy wait loop. This is not ideal for performance but necessary for
     * cross-platform compatibility in synchronous test scenarios.
     *
     * Note: In production code, consider using asynchronous alternatives where possible.
     *
     * @param millis the number of milliseconds to sleep
     */
    actual fun sleep(millis: Long) {
        // JavaScript doesn't have synchronous sleep, so we use a simple busy wait
        // This is not ideal but necessary for cross-platform compatibility in tests
        val start = kotlin.js.Date.now()
        val target = start + millis.toDouble()
        while (kotlin.js.Date.now() < target) {
            // Busy wait
        }
    }

    /**
     * Helper method to safely calculate time differences in JavaScript.
     *
     * JavaScript numbers can lose precision with very large values due to the
     * IEEE 754 double-precision floating-point format. This method uses standard
     * Kotlin arithmetic which is generally safe for typical time differences.
     *
     * @param start the start time in milliseconds
     * @param end the end time in milliseconds
     * @return the time difference in milliseconds, or 0L if calculation fails
     */
    actual fun timeDifference(start: Long, end: Long): Long {
        return try {
            // Use standard Kotlin arithmetic which is safe in JavaScript
            end - start
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Helper method to safely compare time values in JavaScript.
     *
     * Provides a safe comparison operation for time values, handling potential
     * JavaScript precision issues. Uses standard Kotlin comparison operators
     * with error handling fallback.
     *
     * @param duration the duration value to compare
     * @param threshold the threshold value for comparison
     * @return true if duration > threshold, false otherwise or if comparison fails
     */
    actual fun timeComparison(duration: Long, threshold: Long): Boolean {
        return try {
            // Use standard Kotlin comparison which is safe in JavaScript
            duration > threshold
        } catch (e: Exception) {
            false
        }
    }
}
