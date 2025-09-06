package org.apache.tinkerpop.gremlin.tinkergraph.platform

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import kotlin.js.Promise

/**
 * Simple in-memory storage implementation for TinkerGraph.
 *
 * This storage keeps all graph data in memory and does not persist between
 * application restarts. It's useful for testing, temporary storage, or
 * scenarios where persistence is not required.
 *
 * Features:
 * - Fast access (no I/O operations)
 * - No size limitations (except available memory)
 * - Thread-safe operations
 * - Immediate availability
 */
class MemoryStorage : GraphStorage {

    private val graphs = mutableMapOf<String, dynamic>()

    override fun store(graph: TinkerGraph, key: String): Promise<Unit> {
        return Promise { resolve, reject ->
            try {
                val serializedGraph = GraphSerializer.serialize(graph)
                graphs[key] = serializedGraph
                resolve(Unit)
            } catch (e: Exception) {
                reject(StorageException("Failed to store graph '$key': ${e.message}", e))
            }
        }
    }

    override fun load(key: String): Promise<TinkerGraph?> {
        return Promise { resolve, reject ->
            try {
                val serializedGraph = graphs[key]
                if (serializedGraph != null) {
                    GraphSerializer.deserialize(serializedGraph).then(
                        onFulfilled = { graph -> resolve(graph) },
                        onRejected = { error -> reject(error) }
                    )
                } else {
                    resolve(null)
                }
            } catch (e: Exception) {
                reject(StorageException("Failed to load graph '$key': ${e.message}", e))
            }
        }
    }

    override fun exists(key: String): Promise<Boolean> {
        return Promise.resolve(graphs.containsKey(key))
    }

    override fun delete(key: String): Promise<Unit> {
        return Promise { resolve, reject ->
            try {
                graphs.remove(key)
                resolve(Unit)
            } catch (e: Exception) {
                reject(StorageException("Failed to delete graph '$key': ${e.message}", e))
            }
        }
    }

    override fun list(): Promise<Array<String>> {
        return Promise.resolve(graphs.keys.toTypedArray())
    }

    override fun clear(): Promise<Unit> {
        return Promise { resolve, reject ->
            try {
                graphs.clear()
                resolve(Unit)
            } catch (e: Exception) {
                reject(StorageException("Failed to clear storage: ${e.message}", e))
            }
        }
    }

    override fun getInfo(): Promise<StorageInfo> {
        return Promise { resolve, reject ->
            try {
                // Calculate approximate size
                var totalSize = 0L
                graphs.values.forEach { graph ->
                    val json = JSON.stringify(graph)
                    totalSize += json.length * 2 // Rough estimate (UTF-16)
                }

                val info = StorageInfo(
                    type = "Memory",
                    available = true,
                    capacity = null, // No fixed capacity
                    used = totalSize,
                    graphCount = graphs.size,
                    metadata = mapOf(
                        "keys" to graphs.keys.toList(),
                        "memoryType" to "JavaScript Heap"
                    )
                )

                resolve(info)
            } catch (e: Exception) {
                reject(StorageException("Failed to get storage info: ${e.message}", e))
            }
        }
    }

    /**
     * Get the raw serialized data for a graph (useful for debugging).
     */
    fun getRawData(key: String): dynamic? = graphs[key]

    /**
     * Get all stored keys as a regular list.
     */
    fun getKeys(): List<String> = graphs.keys.toList()

    /**
     * Check if storage is empty.
     */
    fun isEmpty(): Boolean = graphs.isEmpty()

    /**
     * Get the number of stored graphs.
     */
    fun size(): Int = graphs.size

    /**
     * Clone storage contents to another MemoryStorage instance.
     */
    fun cloneTo(target: MemoryStorage) {
        target.graphs.clear()
        graphs.forEach { (key, value) ->
            target.graphs[key] = value
        }
    }
}
