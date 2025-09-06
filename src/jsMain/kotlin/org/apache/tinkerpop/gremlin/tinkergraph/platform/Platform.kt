package org.apache.tinkerpop.gremlin.tinkergraph.platform

/**
 * JavaScript implementation of platform abstraction.
 */
actual object Platform {
    /**
     * Get the current time in milliseconds since epoch.
     */
    actual fun currentTimeMillis(): Long {
        return try {
            kotlin.js.Date.now().toLong()
        } catch (e: Exception) {
            0L
        }
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
        return try {
            value.asDynamic().toFixed(2).unsafeCast<String>()
        } catch (e: Exception) {
            value.toString()
        }
    }

    /**
     * Sleep for the specified number of milliseconds.
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
     * JavaScript numbers can lose precision with large values, so we ensure
     * the calculation is done safely.
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
