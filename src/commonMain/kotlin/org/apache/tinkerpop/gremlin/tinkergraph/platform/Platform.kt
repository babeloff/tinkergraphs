package org.apache.tinkerpop.gremlin.tinkergraph.platform

/**
 * Platform abstraction for multiplatform compatibility across JVM, JavaScript, and Native targets.
 *
 * This expect object defines the contract for platform-specific implementations that provide
 * common operations with different underlying implementations based on the target platform:
 * - **JVM**: Uses Java standard library and JVM-specific APIs
 * - **JavaScript**: Uses browser/Node.js APIs with JavaScript-specific considerations
 * - **Native**: Uses POSIX APIs and platform-native implementations
 *
 * Each actual implementation handles platform-specific concerns while maintaining
 * a consistent API for the common code.
 *
 * @see org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
 */
expect object Platform {
    /**
     * Get the current time in milliseconds since Unix epoch.
     *
     * Returns the current time as the number of milliseconds since
     * January 1, 1970, 00:00:00 UTC. Each platform implementation
     * uses the most appropriate high-resolution timer available.
     *
     * @return Current time in milliseconds
     */
    fun currentTimeMillis(): Long

    /**
     * Create a sorted map implementation appropriate for the target platform.
     *
     * Returns a map that maintains keys in sorted order. The actual implementation
     * varies by platform:
     * - JVM: Uses TreeMap for true sorted ordering
     * - JavaScript/Native: May fall back to regular Map with manual sorting
     *
     * @param K the key type, must implement Comparable
     * @param V the value type
     * @return A mutable map with sorted key ordering (platform-dependent)
     */
    fun <K : Comparable<K>, V> createSortedMap(): MutableMap<K, V>

    /**
     * Create a linked hash map with specified configuration parameters.
     *
     * Returns a map that maintains insertion order or access order depending on
     * the accessOrder parameter. Platform implementations may ignore some parameters
     * if not supported by the underlying collection framework.
     *
     * @param initialCapacity the initial capacity of the map (default: 16)
     * @param loadFactor the load factor for resizing (default: 0.75f)
     * @param accessOrder true for access-order, false for insertion-order (default: false)
     * @return A mutable map with predictable iteration order
     */
    fun <K, V> createLinkedHashMap(
        initialCapacity: Int = 16,
        loadFactor: Float = 0.75f,
        accessOrder: Boolean = false
    ): MutableMap<K, V>

    /**
     * Format a double value as a percentage string with exactly 2 decimal places.
     *
     * Converts a decimal value (e.g., 0.2550) to a percentage string (e.g., "25.50").
     * Each platform implementation uses appropriate formatting utilities to ensure
     * consistent output across all targets.
     *
     * @param value the decimal value to format (typically between 0.0 and 1.0)
     * @return formatted percentage string with 2 decimal places
     */
    fun formatPercentage(value: Double): String

    /**
     * Sleep for the specified number of milliseconds.
     *
     * Blocks the current thread for approximately the specified duration.
     * Platform implementations may vary:
     * - JVM: Uses Thread.sleep()
     * - JavaScript: Uses busy-wait (no native sync sleep)
     * - Native: Uses platform-specific sleep or busy-wait
     *
     * @param millis the number of milliseconds to sleep
     */
    fun sleep(millis: Long)

    /**
     * Helper method to safely calculate time differences across platforms.
     *
     * Computes the difference between two time values while handling potential
     * platform-specific precision or overflow issues. This is particularly
     * important for JavaScript where large numbers may lose precision.
     *
     * @param start the start time in milliseconds
     * @param end the end time in milliseconds
     * @return the time difference (end - start) in milliseconds
     */
    fun timeDifference(start: Long, end: Long): Long

    /**
     * Helper method to safely compare time values across platforms.
     *
     * Performs a greater-than comparison between duration and threshold values
     * while handling potential platform-specific precision issues. Provides
     * consistent comparison behavior across all target platforms.
     *
     * @param duration the duration value to compare
     * @param threshold the threshold value for comparison
     * @return true if duration > threshold, false otherwise
     */
    fun timeComparison(duration: Long, threshold: Long): Boolean
}
