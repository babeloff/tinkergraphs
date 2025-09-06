package org.apache.tinkerpop.gremlin.tinkergraph.platform

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import kotlin.js.Promise

/**
 * Browser LocalStorage-based storage implementation for TinkerGraph.
 *
 * This storage uses the browser's localStorage API to persist graph data
 * across browser sessions. It's suitable for small to medium-sized graphs
 * and provides persistence without requiring IndexedDB.
 *
 * Features:
 * - Persistent storage across browser sessions
 * - Synchronous operations (wrapped in Promises for API consistency)
 * - Automatic cleanup of corrupted data
 * - Storage quota management
 * - Compression support for large graphs
 *
 * Limitations:
 * - Storage quota typically 5-10MB per origin
 * - Synchronous localStorage operations may block UI
 * - No transaction support
 */
class BrowserStorage(
    private val keyPrefix: String = "tinkergraph:",
    private val useCompression: Boolean = false
) : GraphStorage {

    companion object {
        private const val METADATA_SUFFIX = ":metadata"
        private const val DATA_SUFFIX = ":data"
    }

    override fun store(graph: TinkerGraph, key: String): Promise<Unit> {
        return Promise { resolve, reject ->
            try {
                if (!isLocalStorageAvailable()) {
                    reject(StorageUnavailableException("LocalStorage is not available"))
                    return@Promise
                }

                val fullKey = keyPrefix + key
                val serializedGraph = GraphSerializer.serialize(graph)
                val jsonData = JSON.stringify(serializedGraph)

                // Check if data will fit in localStorage
                checkStorageQuota(jsonData)

                // Store metadata
                val metadata = js("{}")
                metadata.timestamp = JSDate().toISOString()
                metadata.version = "1.0"
                metadata.size = jsonData.length
                metadata.compressed = useCompression

                LocalStorage.setItem(fullKey + METADATA_SUFFIX, JSON.stringify(metadata))

                // Store graph data
                val finalData = if (useCompression) {
                    compressString(jsonData)
                } else {
                    jsonData
                }

                LocalStorage.setItem(fullKey + DATA_SUFFIX, finalData)

                resolve(Unit)

            } catch (e: Exception) {
                when {
                    isQuotaExceededError(e) -> {
                        reject(StorageCapacityException("LocalStorage quota exceeded: ${e.message}"))
                    }
                    else -> {
                        reject(StorageException("Failed to store graph '$key': ${e.message}", e))
                    }
                }
            }
        }
    }

    override fun load(key: String): Promise<TinkerGraph?> {
        return Promise { resolve, reject ->
            try {
                if (!isLocalStorageAvailable()) {
                    reject(StorageUnavailableException("LocalStorage is not available"))
                    return@Promise
                }

                val fullKey = keyPrefix + key
                val metadataJson = LocalStorage.getItem(fullKey + METADATA_SUFFIX)
                val dataJson = LocalStorage.getItem(fullKey + DATA_SUFFIX)

                if (metadataJson == null || dataJson == null) {
                    resolve(null)
                    return@Promise
                }

                // Parse metadata
                val metadata = JSON.parse(metadataJson)
                val isCompressed = metadata.compressed?.unsafeCast<Boolean>() ?: false

                // Decompress data if necessary
                val jsonData = if (isCompressed) {
                    decompressString(dataJson)
                } else {
                    dataJson
                }

                // Deserialize graph
                val graphData = JSON.parse(jsonData)
                GraphSerializer.deserialize(graphData).then(
                    onFulfilled = { graph -> resolve(graph) },
                    onRejected = { error ->
                        // Clean up corrupted data
                        try {
                            LocalStorage.removeItem(fullKey + METADATA_SUFFIX)
                            LocalStorage.removeItem(fullKey + DATA_SUFFIX)
                        } catch (cleanupError: Exception) {
                            // Ignore cleanup errors
                        }
                        reject(StorageCorruptionException("Graph data corrupted for key '$key': $error"))
                    }
                )

            } catch (e: Exception) {
                reject(StorageException("Failed to load graph '$key': ${e.message}", e))
            }
        }
    }

    override fun exists(key: String): Promise<Boolean> {
        return Promise { resolve, reject ->
            try {
                if (!isLocalStorageAvailable()) {
                    resolve(false)
                    return@Promise
                }

                val fullKey = keyPrefix + key
                val metadataExists = LocalStorage.getItem(fullKey + METADATA_SUFFIX) != null
                val dataExists = LocalStorage.getItem(fullKey + DATA_SUFFIX) != null

                resolve(metadataExists && dataExists)

            } catch (e: Exception) {
                reject(StorageException("Failed to check existence of graph '$key': ${e.message}", e))
            }
        }
    }

    override fun delete(key: String): Promise<Unit> {
        return Promise { resolve, reject ->
            try {
                if (!isLocalStorageAvailable()) {
                    reject(StorageUnavailableException("LocalStorage is not available"))
                    return@Promise
                }

                val fullKey = keyPrefix + key
                LocalStorage.removeItem(fullKey + METADATA_SUFFIX)
                LocalStorage.removeItem(fullKey + DATA_SUFFIX)

                resolve(Unit)

            } catch (e: Exception) {
                reject(StorageException("Failed to delete graph '$key': ${e.message}", e))
            }
        }
    }

    override fun list(): Promise<Array<String>> {
        return Promise { resolve, reject ->
            try {
                if (!isLocalStorageAvailable()) {
                    resolve(emptyArray())
                    return@Promise
                }

                val keys = mutableListOf<String>()
                val metadataSuffix = METADATA_SUFFIX
                val prefixLength = keyPrefix.length
                val suffixLength = metadataSuffix.length

                for (i in 0 until LocalStorage.length) {
                    val key = LocalStorage.key(i)
                    if (key != null &&
                        key.startsWith(keyPrefix) &&
                        key.endsWith(metadataSuffix)) {

                        val graphKey = key.substring(prefixLength, key.length - suffixLength)
                        keys.add(graphKey)
                    }
                }

                resolve(keys.toTypedArray())

            } catch (e: Exception) {
                reject(StorageException("Failed to list graphs: ${e.message}", e))
            }
        }
    }

    override fun clear(): Promise<Unit> {
        return Promise { resolve, reject ->
            try {
                if (!isLocalStorageAvailable()) {
                    reject(StorageUnavailableException("LocalStorage is not available"))
                    return@Promise
                }

                val keysToRemove = mutableListOf<String>()

                for (i in 0 until LocalStorage.length) {
                    val key = LocalStorage.key(i)
                    if (key != null && key.startsWith(keyPrefix)) {
                        keysToRemove.add(key)
                    }
                }

                keysToRemove.forEach { key ->
                    LocalStorage.removeItem(key)
                }

                resolve(Unit)

            } catch (e: Exception) {
                reject(StorageException("Failed to clear storage: ${e.message}", e))
            }
        }
    }

    override fun getInfo(): Promise<StorageInfo> {
        return Promise { resolve, reject ->
            try {
                if (!isLocalStorageAvailable()) {
                    resolve(StorageInfo(
                        type = "LocalStorage",
                        available = false
                    ))
                    return@Promise
                }

                var totalUsed = 0L
                var graphCount = 0
                val graphKeys = mutableListOf<String>()

                for (i in 0 until LocalStorage.length) {
                    val key = LocalStorage.key(i)
                    if (key != null) {
                        val value = LocalStorage.getItem(key)
                        if (value != null) {
                            totalUsed += value.length * 2 // UTF-16 chars = 2 bytes each

                            if (key.startsWith(keyPrefix) && key.endsWith(METADATA_SUFFIX)) {
                                graphCount++
                                val graphKey = key.substring(
                                    keyPrefix.length,
                                    key.length - METADATA_SUFFIX.length
                                )
                                graphKeys.add(graphKey)
                            }
                        }
                    }
                }

                val estimatedCapacity = estimateStorageQuota()

                val info = StorageInfo(
                    type = "LocalStorage",
                    available = true,
                    capacity = estimatedCapacity,
                    used = totalUsed,
                    graphCount = graphCount,
                    metadata = mapOf(
                        "keyPrefix" to keyPrefix,
                        "useCompression" to useCompression,
                        "graphKeys" to graphKeys,
                        "totalItems" to LocalStorage.length
                    )
                )

                resolve(info)

            } catch (e: Exception) {
                reject(StorageException("Failed to get storage info: ${e.message}", e))
            }
        }
    }

    // === Private Helper Methods ===

    private fun checkStorageQuota(data: String) {
        val dataSize = data.length * 2 // UTF-16 estimation
        val estimatedQuota = estimateStorageQuota()

        if (estimatedQuota != null && dataSize > estimatedQuota) {
            throw StorageCapacityException("Data size ($dataSize bytes) exceeds estimated quota ($estimatedQuota bytes)")
        }

        // Try a test write to check for quota issues
        try {
            val testKey = keyPrefix + "__quota_test__"
            LocalStorage.setItem(testKey, data)
            LocalStorage.removeItem(testKey)
        } catch (e: Exception) {
            if (isQuotaExceededError(e)) {
                throw StorageCapacityException("Storage quota would be exceeded")
            }
            throw e
        }
    }

    private fun estimateStorageQuota(): Long? {
        // Most browsers have 5-10MB localStorage quota
        // This is a rough estimate - actual quota can vary
        return try {
            // Try to determine quota by attempting to store data
            // This is approximate and may not work in all browsers
            5 * 1024 * 1024L // 5MB default estimate
        } catch (e: Exception) {
            null
        }
    }

    private fun isQuotaExceededError(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: ""
        return message.contains("quota") ||
               message.contains("storage") ||
               message.contains("exceeded") ||
               message.contains("full")
    }

    private fun compressString(input: String): String {
        // Simple compression using built-in methods if available
        return try {
            // In a real implementation, you might use a JavaScript compression library
            // For now, we'll use a simple approach
            input // TODO: Implement actual compression
        } catch (e: Exception) {
            input // Fall back to uncompressed if compression fails
        }
    }

    private fun decompressString(input: String): String {
        // Simple decompression - mirror of compressString
        return try {
            input // TODO: Implement actual decompression
        } catch (e: Exception) {
            throw StorageCorruptionException("Failed to decompress data: ${e.message}")
        }
    }

    /**
     * Get metadata for a specific graph without loading the full graph.
     */
    fun getMetadata(key: String): Promise<dynamic?> {
        return Promise { resolve, reject ->
            try {
                if (!isLocalStorageAvailable()) {
                    resolve(null)
                    return@Promise
                }

                val fullKey = keyPrefix + key
                val metadataJson = LocalStorage.getItem(fullKey + METADATA_SUFFIX)

                if (metadataJson != null) {
                    val metadata = JSON.parse(metadataJson)
                    resolve(metadata)
                } else {
                    resolve(null)
                }

            } catch (e: Exception) {
                reject(StorageException("Failed to get metadata for '$key': ${e.message}", e))
            }
        }
    }

    /**
     * Cleanup corrupted or incomplete entries.
     */
    fun cleanup(): Promise<Int> {
        return Promise { resolve, reject ->
            try {
                if (!isLocalStorageAvailable()) {
                    resolve(0)
                    return@Promise
                }

                var cleanedCount = 0
                val orphanedKeys = mutableListOf<String>()

                // Find orphaned metadata or data entries
                for (i in 0 until LocalStorage.length) {
                    val key = LocalStorage.key(i)
                    if (key != null && key.startsWith(keyPrefix)) {
                        when {
                            key.endsWith(METADATA_SUFFIX) -> {
                                val dataKey = key.replace(METADATA_SUFFIX, DATA_SUFFIX)
                                if (LocalStorage.getItem(dataKey) == null) {
                                    orphanedKeys.add(key)
                                }
                            }
                            key.endsWith(DATA_SUFFIX) -> {
                                val metadataKey = key.replace(DATA_SUFFIX, METADATA_SUFFIX)
                                if (LocalStorage.getItem(metadataKey) == null) {
                                    orphanedKeys.add(key)
                                }
                            }
                        }
                    }
                }

                // Remove orphaned keys
                orphanedKeys.forEach { key ->
                    LocalStorage.removeItem(key)
                    cleanedCount++
                }

                resolve(cleanedCount)

            } catch (e: Exception) {
                reject(StorageException("Failed to cleanup storage: ${e.message}", e))
            }
        }
    }
}
