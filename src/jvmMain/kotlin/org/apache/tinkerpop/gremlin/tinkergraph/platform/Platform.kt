package org.apache.tinkerpop.gremlin.tinkergraph.platform

import java.text.DecimalFormat
import java.util.TreeMap
import java.util.LinkedHashMap

/**
 * JVM-specific platform implementation.
 */
actual object Platform {
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()

    actual fun <K : Comparable<K>, V> createSortedMap(): MutableMap<K, V> {
        return TreeMap<K, V>()
    }

    actual fun <K, V> createLinkedHashMap(
        initialCapacity: Int,
        loadFactor: Float,
        accessOrder: Boolean
    ): MutableMap<K, V> {
        return LinkedHashMap<K, V>(initialCapacity, loadFactor, accessOrder)
    }

    actual fun formatPercentage(value: Double): String {
        val formatter = DecimalFormat("#0.00%")
        return formatter.format(value)
    }

    actual fun sleep(millis: Long) {
        Thread.sleep(millis)
    }

    actual fun timeDifference(start: Long, end: Long): Long {
        return end - start
    }

    actual fun timeComparison(duration: Long, threshold: Long): Boolean {
        return duration > threshold
    }
}
