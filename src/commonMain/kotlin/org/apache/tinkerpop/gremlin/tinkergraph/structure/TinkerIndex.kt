package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*

/**
 * TinkerIndex provides indexing capabilities for fast property-based lookups on graph elements.
 * It maintains indices for specific property keys to enable efficient queries.
 *
 * @param T the type of element being indexed (TinkerVertex or TinkerEdge)
 */
class TinkerIndex<T : Element> {

    /**
     * Map of property key to index data structure.
     * Each index maps property values to sets of elements that have that value.
     */
    private val indices: MutableMap<String, MutableMap<Any?, MutableSet<T>>> = mutableMapOf()

    /**
     * Set of keys that are currently indexed.
     */
    private val indexedKeys: MutableSet<String> = mutableSetOf()

    /**
     * Create an index for the specified property key.
     * If an index already exists for this key, this is a no-op.
     *
     * @param key the property key to index
     */
    fun createKeyIndex(key: String) {
        if (!indexedKeys.contains(key)) {
            indexedKeys.add(key)
            indices[key] = mutableMapOf()
        }
    }

    /**
     * Drop the index for the specified property key.
     * If no index exists for this key, this is a no-op.
     *
     * @param key the property key to drop from indexing
     */
    fun dropKeyIndex(key: String) {
        if (indexedKeys.contains(key)) {
            indexedKeys.remove(key)
            indices.remove(key)
        }
    }

    /**
     * Get all currently indexed keys.
     *
     * @return set of indexed property keys
     */
    fun getIndexedKeys(): Set<String> {
        return indexedKeys.toSet()
    }

    /**
     * Check if a property key is indexed.
     *
     * @param key the property key to check
     * @return true if the key is indexed
     */
    fun isIndexed(key: String): Boolean {
        return indexedKeys.contains(key)
    }

    /**
     * Get all elements that have the specified property value for the given key.
     * Only works if the key is indexed.
     *
     * @param key the property key
     * @param value the property value to search for
     * @return set of elements with the specified property value, or empty set if key is not indexed
     */
    fun get(key: String, value: Any?): Set<T> {
        val index = indices[key] ?: return emptySet()
        return index[value]?.toSet() ?: emptySet()
    }

    /**
     * Get all elements that have any of the specified property values for the given key.
     * Only works if the key is indexed.
     *
     * @param key the property key
     * @param values the property values to search for
     * @return set of elements with any of the specified property values
     */
    fun get(key: String, values: Collection<Any?>): Set<T> {
        if (!isIndexed(key)) return emptySet()

        val result = mutableSetOf<T>()
        values.forEach { value ->
            result.addAll(get(key, value))
        }
        return result
    }

    /**
     * Get all elements indexed under the specified key, regardless of value.
     *
     * @param key the property key
     * @return set of all elements that have this property
     */
    fun getAllForKey(key: String): Set<T> {
        val index = indices[key] ?: return emptySet()
        val result = mutableSetOf<T>()
        index.values.forEach { elements ->
            result.addAll(elements)
        }
        return result
    }

    /**
     * Count the number of distinct values for a property key.
     *
     * @param key the property key
     * @return number of distinct values, or 0 if key is not indexed
     */
    fun countValues(key: String): Int {
        return indices[key]?.size ?: 0
    }

    /**
     * Count the number of elements with the specified property value.
     *
     * @param key the property key
     * @param value the property value
     * @return number of elements with this value, or 0 if key is not indexed
     */
    fun count(key: String, value: Any?): Int {
        return indices[key]?.get(value)?.size ?: 0
    }

    /**
     * Get all distinct values for a property key.
     *
     * @param key the property key
     * @return set of all values for this key, or empty set if key is not indexed
     */
    fun getValues(key: String): Set<Any?> {
        return indices[key]?.keys?.toSet() ?: emptySet()
    }

    /**
     * Automatically update the index when an element's property changes.
     * This should be called whenever a property is added, updated, or removed.
     *
     * @param key the property key that changed
     * @param newValue the new property value (null if property was removed)
     * @param oldValue the old property value (null if property was added)
     * @param element the element whose property changed
     */
    fun autoUpdate(key: String, newValue: Any?, oldValue: Any?, element: T) {
        if (!isIndexed(key)) return

        val index = indices[key] ?: return

        // Remove element from old value index
        if (oldValue != null) {
            val oldSet = index[oldValue]
            if (oldSet != null) {
                oldSet.remove(element)
                if (oldSet.isEmpty()) {
                    index.remove(oldValue)
                }
            }
        }

        // Add element to new value index
        if (newValue != null) {
            val newSet = index.getOrPut(newValue) { mutableSetOf() }
            newSet.add(element)
        }
    }

    /**
     * Add an element to the index for all of its indexed properties.
     * This should be called when an element is added to the graph.
     *
     * @param element the element to index
     */
    fun addElement(element: T) {
        indexedKeys.forEach { key ->
            val value = element.value<Any?>(key)
            if (value != null) {
                autoUpdate(key, value, null, element)
            }
        }
    }

    /**
     * Remove an element from all indices.
     * This should be called when an element is removed from the graph.
     *
     * @param element the element to remove from indices
     */
    fun removeElement(element: T) {
        indexedKeys.forEach { key ->
            val value = element.value<Any?>(key)
            if (value != null) {
                autoUpdate(key, null, value, element)
            }
        }
    }

    /**
     * Rebuild the index for a specific key by scanning all provided elements.
     * This is useful when the index gets out of sync or when adding an index
     * to existing data.
     *
     * @param key the property key to rebuild
     * @param elements all elements to scan for this property
     */
    fun rebuildIndex(key: String, elements: Collection<T>) {
        if (!isIndexed(key)) return

        // Clear existing index for this key
        indices[key] = mutableMapOf()

        // Rebuild from elements
        elements.forEach { element ->
            val value = element.value<Any?>(key)
            if (value != null) {
                autoUpdate(key, value, null, element)
            }
        }
    }

    /**
     * Clear all indices and indexed keys.
     */
    fun clear() {
        indices.clear()
        indexedKeys.clear()
    }

    /**
     * Get statistics about the index.
     *
     * @return map of statistics
     */
    fun getStatistics(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        stats["indexedKeyCount"] = indexedKeys.size
        stats["totalIndexEntries"] = indices.values.sumOf { it.size }
        stats["totalIndexedElements"] = indices.values.sumOf { index ->
            index.values.sumOf { it.size }
        }

        val keyStats = mutableMapOf<String, Map<String, Any>>()
        indexedKeys.forEach { key ->
            val index = indices[key] ?: return@forEach
            keyStats[key] = mapOf(
                "distinctValues" to index.size,
                "totalElements" to index.values.sumOf { it.size }
            )
        }
        stats["keyStatistics"] = keyStats

        return stats
    }

    /**
     * Check if the index is empty (no indexed keys).
     *
     * @return true if no keys are indexed
     */
    fun isEmpty(): Boolean {
        return indexedKeys.isEmpty()
    }

    /**
     * Get the total number of elements across all indices.
     * Note: Elements may be counted multiple times if they appear in multiple indices.
     *
     * @return total element count across all indices
     */
    fun getTotalElementCount(): Int {
        return indices.values.sumOf { index ->
            index.values.sumOf { it.size }
        }
    }

    override fun toString(): String {
        return "TinkerIndex[indexedKeys=${indexedKeys.size}, totalEntries=${getTotalElementCount()}]"
    }
}
