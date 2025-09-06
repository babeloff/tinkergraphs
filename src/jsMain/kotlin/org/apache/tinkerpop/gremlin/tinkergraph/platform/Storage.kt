package org.apache.tinkerpop.gremlin.tinkergraph.platform

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import kotlin.js.Promise

/**
 * Clean storage interface for JavaScript platform.
 *
 * This interface provides a simple, promise-based API for persisting and loading
 * TinkerGraph data in JavaScript environments. It abstracts away the complexity
 * of different storage backends while maintaining type safety and proper error handling.
 */
interface GraphStorage {

    /**
     * Store a complete graph to the storage backend.
     * @param graph The TinkerGraph to store
     * @param key Optional key to identify this graph (defaults to "default")
     * @return Promise that resolves when storage is complete
     */
    fun store(graph: TinkerGraph, key: String = "default"): Promise<Unit>

    /**
     * Load a complete graph from the storage backend.
     * @param key Optional key to identify the graph to load (defaults to "default")
     * @return Promise that resolves to the loaded graph, or null if not found
     */
    fun load(key: String = "default"): Promise<TinkerGraph?>

    /**
     * Check if a graph exists in storage.
     * @param key The key to check for
     * @return Promise that resolves to true if the graph exists
     */
    fun exists(key: String = "default"): Promise<Boolean>

    /**
     * Delete a graph from storage.
     * @param key The key of the graph to delete
     * @return Promise that resolves when deletion is complete
     */
    fun delete(key: String = "default"): Promise<Unit>

    /**
     * List all available graph keys.
     * @return Promise that resolves to an array of available keys
     */
    fun list(): Promise<Array<String>>

    /**
     * Clear all stored graphs.
     * @return Promise that resolves when all data is cleared
     */
    fun clear(): Promise<Unit>

    /**
     * Get storage statistics and metadata.
     * @return Promise that resolves to storage information
     */
    fun getInfo(): Promise<StorageInfo>
}

/**
 * Information about the storage backend.
 */
data class StorageInfo(
    val type: String,
    val available: Boolean,
    val capacity: Long? = null,
    val used: Long? = null,
    val graphCount: Int = 0,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Factory for creating appropriate storage instances based on the JavaScript environment.
 */
object StorageFactory {

    /**
     * Create the best available storage for the current JavaScript environment.
     * Priority: IndexedDB > LocalStorage > Memory
     */
    fun createDefaultStorage(): GraphStorage {
        return when {
            isIndexedDBAvailable() -> IndexedDBStorage()
            isLocalStorageAvailable() -> BrowserStorage()
            else -> MemoryStorage()
        }
    }

    /**
     * Create a specific storage type.
     */
    fun createStorage(type: StorageType): GraphStorage {
        return when (type) {
            StorageType.MEMORY -> MemoryStorage()
            StorageType.LOCAL_STORAGE -> {
                if (!isLocalStorageAvailable()) {
                    throw UnsupportedOperationException("LocalStorage is not available")
                }
                BrowserStorage()
            }
            StorageType.INDEXED_DB -> {
                if (!isIndexedDBAvailable()) {
                    throw UnsupportedOperationException("IndexedDB is not available")
                }
                IndexedDBStorage()
            }
            StorageType.NODE_FILE_SYSTEM -> {
                if (!isNodeJSAvailable()) {
                    throw UnsupportedOperationException("Node.js file system is not available")
                }
                NodeFileSystemStorage()
            }
        }
    }

    /**
     * Get information about all available storage types.
     */
    fun getAvailableStorageTypes(): Array<StorageType> {
        val available = mutableListOf<StorageType>()

        // Memory is always available
        available.add(StorageType.MEMORY)

        if (isLocalStorageAvailable()) {
            available.add(StorageType.LOCAL_STORAGE)
        }

        if (isIndexedDBAvailable()) {
            available.add(StorageType.INDEXED_DB)
        }

        if (isNodeJSAvailable()) {
            available.add(StorageType.NODE_FILE_SYSTEM)
        }

        return available.toTypedArray()
    }

    // Environment detection functions
    private fun isLocalStorageAvailable(): Boolean = try {
        js("typeof Storage !== 'undefined' && typeof localStorage !== 'undefined'").unsafeCast<Boolean>()
    } catch (e: Exception) { false }

    private fun isIndexedDBAvailable(): Boolean = try {
        js("typeof indexedDB !== 'undefined'").unsafeCast<Boolean>()
    } catch (e: Exception) { false }

    private fun isNodeJSAvailable(): Boolean = try {
        js("typeof process !== 'undefined' && process.versions && process.versions.node").unsafeCast<Boolean>()
    } catch (e: Exception) { false }
}

/**
 * Available storage backend types.
 */
enum class StorageType {
    MEMORY,
    LOCAL_STORAGE,
    INDEXED_DB,
    NODE_FILE_SYSTEM
}

/**
 * Base exception for storage-related errors.
 */
open class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when storage capacity is exceeded.
 */
class StorageCapacityException(message: String) : StorageException(message)

/**
 * Exception thrown when trying to access unavailable storage.
 */
class StorageUnavailableException(message: String) : StorageException(message)

/**
 * Exception thrown when data corruption is detected.
 */
class StorageCorruptionException(message: String) : StorageException(message)
