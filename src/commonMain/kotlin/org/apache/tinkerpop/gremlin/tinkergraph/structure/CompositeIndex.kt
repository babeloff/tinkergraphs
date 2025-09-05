package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*

/**
 * CompositeIndex provides indexing capabilities for multiple properties simultaneously.
 * This enables efficient queries that filter on multiple property keys at once.
 *
 * @param T the type of element being indexed (TinkerVertex or TinkerEdge)
 */
class CompositeIndex<T : Element> {

    /**
     * Map of composite key combinations to index data structures.
     * The composite key is a list of property keys in order.
     * Each index maps composite values to sets of elements that have those values.
     */
    private val indices: MutableMap<List<String>, MutableMap<List<Any?>, MutableSet<T>>> = mutableMapOf()

    /**
     * Set of composite keys that are currently indexed.
     */
    private val indexedCompositeKeys: MutableSet<List<String>> = mutableSetOf()

    /**
     * Map to track individual key participation in composite indices.
     */
    private val keyParticipation: MutableMap<String, MutableSet<List<String>>> = mutableMapOf()

    /**
     * Create a composite index for the specified property keys.
     * If a composite index already exists for these keys, this is a no-op.
     *
     * @param keys the property keys to index together (order matters)
     */
    fun createCompositeIndex(keys: List<String>) {
        require(keys.isNotEmpty()) { "Composite index must have at least one key" }
        require(keys.size > 1) { "Composite index must have more than one key (use TinkerIndex for single keys)" }
        require(keys.toSet().size == keys.size) { "Composite index keys must be unique" }

        if (!indexedCompositeKeys.contains(keys)) {
            indexedCompositeKeys.add(keys)
            indices[keys] = mutableMapOf()

            // Track key participation
            keys.forEach { key ->
                keyParticipation.getOrPut(key) { mutableSetOf() }.add(keys)
            }
        }
    }

    /**
     * Create a composite index for the specified property keys (vararg convenience).
     */
    fun createCompositeIndex(vararg keys: String) {
        createCompositeIndex(keys.toList())
    }

    /**
     * Drop the composite index for the specified property keys.
     * If no composite index exists for these keys, this is a no-op.
     *
     * @param keys the property keys to drop from composite indexing
     */
    fun dropCompositeIndex(keys: List<String>) {
        if (indexedCompositeKeys.contains(keys)) {
            indexedCompositeKeys.remove(keys)
            indices.remove(keys)

            // Update key participation
            keys.forEach { key ->
                keyParticipation[key]?.remove(keys)
                if (keyParticipation[key]?.isEmpty() == true) {
                    keyParticipation.remove(key)
                }
            }
        }
    }

    /**
     * Drop the composite index for the specified property keys (vararg convenience).
     */
    fun dropCompositeIndex(vararg keys: String) {
        dropCompositeIndex(keys.toList())
    }

    /**
     * Get all currently indexed composite key combinations.
     *
     * @return set of composite key combinations
     */
    fun getIndexedCompositeKeys(): Set<List<String>> {
        return indexedCompositeKeys.toSet()
    }

    /**
     * Check if a composite key combination is indexed.
     *
     * @param keys the composite key combination to check
     * @return true if the composite key is indexed
     */
    fun isCompositeIndexed(keys: List<String>): Boolean {
        return indexedCompositeKeys.contains(keys)
    }

    /**
     * Check if a composite key combination is indexed (vararg convenience).
     */
    fun isCompositeIndexed(vararg keys: String): Boolean {
        return isCompositeIndexed(keys.toList())
    }

    /**
     * Get composite indices that can be used for a partial key match.
     * Returns composite indices where the query keys are a prefix of the indexed keys.
     *
     * @param queryKeys the keys being queried
     * @return list of composite key combinations that can be used (sorted by specificity)
     */
    fun getApplicableCompositeIndices(queryKeys: List<String>): List<List<String>> {
        return indexedCompositeKeys
            .filter { compositeKeys ->
                // Check if queryKeys is a prefix of compositeKeys
                queryKeys.size <= compositeKeys.size &&
                compositeKeys.take(queryKeys.size) == queryKeys
            }
            .sortedBy { it.size } // Prefer more specific indices first
    }

    /**
     * Get elements that match the specified composite property values.
     * Only works if the exact composite key combination is indexed.
     *
     * @param keys the property keys in order
     * @param values the property values in the same order as keys
     * @return set of elements with the specified composite property values
     */
    fun get(keys: List<String>, values: List<Any?>): Set<T> {
        require(keys.size == values.size) { "Keys and values must have the same size" }

        val index = indices[keys] ?: return emptySet()
        return index[values]?.toSet() ?: emptySet()
    }

    /**
     * Get elements that match the specified composite property values (vararg convenience).
     */
    fun get(keys: List<String>, vararg values: Any?): Set<T> {
        return get(keys, values.toList())
    }

    /**
     * Get elements that match partial composite property values.
     * Uses the longest applicable composite index based on the provided keys.
     *
     * @param partialKeys the property keys (must be a prefix of some composite index)
     * @param partialValues the property values in the same order as partialKeys
     * @return set of elements that match the partial composite criteria
     */
    fun getPartial(partialKeys: List<String>, partialValues: List<Any?>): Set<T> {
        require(partialKeys.size == partialValues.size) { "Partial keys and values must have the same size" }

        val applicableIndices = getApplicableCompositeIndices(partialKeys)
        if (applicableIndices.isEmpty()) return emptySet()

        // Use the most specific applicable index (smallest one)
        val bestIndex = applicableIndices.first()
        val index = indices[bestIndex] ?: return emptySet()

        val result = mutableSetOf<T>()
        index.entries.forEach { (compositeValue, elements) ->
            // Check if the first partialKeys.size values match our partial criteria
            if (compositeValue.size >= partialKeys.size &&
                compositeValue.take(partialKeys.size) == partialValues) {
                result.addAll(elements)
            }
        }

        return result
    }

    /**
     * Get all elements indexed under any composite key that includes the specified key.
     *
     * @param key the property key
     * @return set of all elements that have this property in any composite index
     */
    fun getAllForKey(key: String): Set<T> {
        val result = mutableSetOf<T>()

        keyParticipation[key]?.forEach { compositeKeys ->
            val index = indices[compositeKeys]
            if (index != null) {
                index.values.forEach { elements ->
                    result.addAll(elements)
                }
            }
        }

        return result
    }

    /**
     * Count the number of distinct composite values for a composite key combination.
     *
     * @param keys the composite property keys
     * @return number of distinct composite values, or 0 if not indexed
     */
    fun countCompositeValues(keys: List<String>): Int {
        return indices[keys]?.size ?: 0
    }

    /**
     * Count the number of elements with the specified composite property values.
     *
     * @param keys the composite property keys
     * @param values the composite property values
     * @return number of elements with these composite values, or 0 if not indexed
     */
    fun count(keys: List<String>, values: List<Any?>): Int {
        require(keys.size == values.size) { "Keys and values must have the same size" }
        return indices[keys]?.get(values)?.size ?: 0
    }

    /**
     * Get all distinct composite values for a composite key combination.
     *
     * @param keys the composite property keys
     * @return set of all composite values for these keys, or empty set if not indexed
     */
    fun getCompositeValues(keys: List<String>): Set<List<Any?>> {
        return indices[keys]?.keys?.toSet() ?: emptySet()
    }

    /**
     * Automatically update the composite index when an element's property changes.
     * This should be called whenever a property is added, updated, or removed.
     *
     * @param changedKey the property key that changed
     * @param element the element whose property changed
     */
    fun autoUpdate(changedKey: String, element: T) {
        // Find all composite indices that include this key
        val affectedComposites = keyParticipation[changedKey] ?: return

        affectedComposites.forEach { compositeKeys ->
            val index = indices[compositeKeys] ?: return@forEach

            // Remove element from all current entries in this composite index
            val toRemove = mutableListOf<List<Any?>>()
            index.entries.forEach { (compositeValue, elements) ->
                if (elements.remove(element) && elements.isEmpty()) {
                    toRemove.add(compositeValue)
                }
            }
            toRemove.forEach { index.remove(it) }

            // Add element back with current property values
            val compositeValue = compositeKeys.mapNotNull { key ->
                try {
                    element.value<Any?>(key)
                } catch (e: Exception) {
                    null // Property doesn't exist
                }
            }

            // Only add if all properties in the composite exist
            if (compositeValue.size == compositeKeys.size) {
                val elementSet = index.getOrPut(compositeValue) { mutableSetOf() }
                elementSet.add(element)
            }
        }
    }

    /**
     * Add an element to all applicable composite indices.
     * This should be called when an element is added to the graph.
     *
     * @param element the element to index
     */
    fun addElement(element: T) {
        indexedCompositeKeys.forEach { compositeKeys ->
            val compositeValue = mutableListOf<Any?>()
            var hasAllProperties = true

            compositeKeys.forEach { key ->
                try {
                    val value = element.value<Any?>(key)
                    compositeValue.add(value)
                } catch (e: Exception) {
                    hasAllProperties = false
                }
            }

            if (hasAllProperties) {
                val index = indices[compositeKeys] ?: return@forEach
                val elementSet = index.getOrPut(compositeValue) { mutableSetOf() }
                elementSet.add(element)
            }
        }
    }

    /**
     * Remove an element from all composite indices.
     * This should be called when an element is removed from the graph.
     *
     * @param element the element to remove from indices
     */
    fun removeElement(element: T) {
        indices.values.forEach { index ->
            val toRemove = mutableListOf<List<Any?>>()
            index.entries.forEach { (compositeValue, elements) ->
                if (elements.remove(element) && elements.isEmpty()) {
                    toRemove.add(compositeValue)
                }
            }
            toRemove.forEach { index.remove(it) }
        }
    }

    /**
     * Rebuild all composite indices by scanning the provided elements.
     * This is useful when indices get out of sync or when adding indices to existing data.
     *
     * @param elements all elements to scan for composite properties
     */
    fun rebuildAllIndices(elements: Collection<T>) {
        // Clear all existing indices
        indices.values.forEach { it.clear() }

        // Rebuild from elements
        elements.forEach { element ->
            addElement(element)
        }
    }

    /**
     * Rebuild a specific composite index by scanning the provided elements.
     *
     * @param keys the composite property keys to rebuild
     * @param elements all elements to scan for these properties
     */
    fun rebuildCompositeIndex(keys: List<String>, elements: Collection<T>) {
        if (!isCompositeIndexed(keys)) return

        // Clear existing index for these keys
        indices[keys] = mutableMapOf()

        // Rebuild from elements
        elements.forEach { element ->
            val compositeValue = mutableListOf<Any?>()
            var hasAllProperties = true

            keys.forEach { key ->
                try {
                    val value = element.value<Any?>(key)
                    compositeValue.add(value)
                } catch (e: Exception) {
                    hasAllProperties = false
                }
            }

            if (hasAllProperties) {
                val index = indices[keys] ?: return@forEach
                val elementSet = index.getOrPut(compositeValue) { mutableSetOf() }
                elementSet.add(element)
            }
        }
    }

    /**
     * Clear all composite indices and indexed composite keys.
     */
    fun clear() {
        indices.clear()
        indexedCompositeKeys.clear()
        keyParticipation.clear()
    }

    /**
     * Get statistics about the composite index.
     *
     * @return map of statistics
     */
    fun getStatistics(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        stats["compositeIndexCount"] = indexedCompositeKeys.size
        stats["totalCompositeEntries"] = indices.values.sumOf { it.size }
        stats["totalIndexedElements"] = indices.values.sumOf { index ->
            index.values.sumOf { it.size }
        }

        val compositeStats = mutableMapOf<List<String>, Map<String, Any>>()
        indexedCompositeKeys.forEach { keys ->
            val index = indices[keys] ?: return@forEach
            compositeStats[keys] = mapOf(
                "distinctCompositeValues" to index.size,
                "totalElements" to index.values.sumOf { it.size },
                "keyCount" to keys.size
            )
        }
        stats["compositeStatistics"] = compositeStats

        val keyParticipationStats = mutableMapOf<String, Int>()
        keyParticipation.forEach { (key, composites) ->
            keyParticipationStats[key] = composites.size
        }
        stats["keyParticipation"] = keyParticipationStats

        return stats
    }

    /**
     * Check if the composite index is empty (no indexed composite keys).
     *
     * @return true if no composite keys are indexed
     */
    fun isEmpty(): Boolean {
        return indexedCompositeKeys.isEmpty()
    }

    /**
     * Get the total number of elements across all composite indices.
     * Note: Elements may be counted multiple times if they appear in multiple indices.
     *
     * @return total element count across all composite indices
     */
    fun getTotalElementCount(): Int {
        return indices.values.sumOf { index ->
            index.values.sumOf { it.size }
        }
    }

    /**
     * Find the best composite index for a given set of query keys.
     * Returns the composite index that provides the most selective filtering.
     *
     * @param queryKeys the keys being queried
     * @return the best composite key combination, or null if none applicable
     */
    fun findBestCompositeIndex(queryKeys: Set<String>): List<String>? {
        val applicable = indexedCompositeKeys.filter { compositeKeys ->
            // Check if all keys in the composite are in the query
            compositeKeys.all { it in queryKeys }
        }

        return if (applicable.isEmpty()) {
            null
        } else {
            // Prefer the composite index with the most keys (most selective)
            applicable.maxByOrNull { it.size }
        }
    }

    override fun toString(): String {
        return "CompositeIndex[compositeIndexes=${indexedCompositeKeys.size}, totalEntries=${getTotalElementCount()}]"
    }
}
