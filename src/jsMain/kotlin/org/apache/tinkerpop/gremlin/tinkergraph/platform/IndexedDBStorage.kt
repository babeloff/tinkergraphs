package org.apache.tinkerpop.gremlin.tinkergraph.platform

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import kotlin.js.Promise

/**
 * IndexedDB-based storage implementation for TinkerGraph.
 *
 * This storage uses the browser's IndexedDB API to persist graph data with
 * better performance and capacity than localStorage. IndexedDB is ideal for
 * larger graphs and provides transaction support.
 *
 * Features:
 * - Asynchronous operations (non-blocking)
 * - Much larger storage capacity than localStorage
 * - Transaction support for data consistency
 * - Indexed queries for better performance
 * - Automatic schema versioning
 *
 * Database Schema:
 * - Object Store: "graphs" with key-value pairs
 * - Index: "timestamp" for temporal queries
 * - Index: "size" for size-based queries
 */
class IndexedDBStorage(
    private val databaseName: String = "TinkerGraphDB",
    private val databaseVersion: Double = 1.0
) : GraphStorage {

    companion object {
        private const val GRAPHS_STORE = "graphs"
        private const val METADATA_STORE = "metadata"
    }

    private var database: IDBDatabase? = null

    override fun store(graph: TinkerGraph, key: String): Promise<Unit> {
        return ensureDatabase().then { db ->
            Promise<Unit> { resolve, reject ->
                try {
                    val transaction = db.transaction(arrayOf(GRAPHS_STORE, METADATA_STORE), "readwrite")
                    val graphStore = transaction.objectStore(GRAPHS_STORE)
                    val metadataStore = transaction.objectStore(METADATA_STORE)

                    // Serialize graph
                    val serializedGraph = GraphSerializer.serialize(graph)
                    val jsonData = JSON.stringify(serializedGraph)

                    // Create metadata
                    val metadata = js("{}")
                    metadata.key = key
                    metadata.timestamp = JSDate().toISOString()
                    metadata.version = "1.0"
                    metadata.size = jsonData.length
                    metadata.vertexCount = countVertices(graph)
                    metadata.edgeCount = countEdges(graph)

                    // Store graph data
                    val graphRequest = graphStore.put(jsonData, key)
                    val metadataRequest = metadataStore.put(metadata, key)

                    var completed = 0
                    val onComplete = {
                        completed++
                        if (completed == 2) {
                            resolve(Unit)
                        }
                    }

                    graphRequest.onsuccess = { onComplete() }
                    graphRequest.onerror = { reject(StorageException("Failed to store graph data: ${graphRequest.error}")) }

                    metadataRequest.onsuccess = { onComplete() }
                    metadataRequest.onerror = { reject(StorageException("Failed to store metadata: ${metadataRequest.error}")) }

                    transaction.onerror = { reject(StorageException("Transaction failed")) }

                } catch (e: Exception) {
                    reject(StorageException("Failed to store graph '$key': ${e.message}", e))
                }
            }
        }
    }

    override fun load(key: String): Promise<TinkerGraph?> {
        return ensureDatabase().then { db ->
            Promise<TinkerGraph?> { resolve, reject ->
                try {
                    val transaction = db.transaction(GRAPHS_STORE, "readonly")
                    val store = transaction.objectStore(GRAPHS_STORE)
                    val request = store.get(key)

                    request.onsuccess = {
                        val jsonData = request.result
                        if (jsonData != null && jsonData != js("undefined")) {
                            try {
                                val graphData = JSON.parse(jsonData.unsafeCast<String>())
                                GraphSerializer.deserialize(graphData).then(
                                    onFulfilled = { graph -> resolve(graph) },
                                    onRejected = { error -> reject(error) }
                                )
                            } catch (e: Exception) {
                                reject(StorageCorruptionException("Failed to parse graph data: ${e.message}"))
                            }
                        } else {
                            resolve(null)
                        }
                    }

                    request.onerror = {
                        reject(StorageException("Failed to load graph '$key'"))
                    }

                } catch (e: Exception) {
                    reject(StorageException("Failed to load graph '$key': ${e.message}", e))
                }
            }
        }.unsafeCast<Promise<TinkerGraph?>>()
    }

    override fun exists(key: String): Promise<Boolean> {
        return ensureDatabase().then { db ->
            Promise<Boolean> { resolve, reject ->
                try {
                    val transaction = db.transaction(METADATA_STORE, "readonly")
                    val store = transaction.objectStore(METADATA_STORE)
                    val request = store.get(key)

                    request.onsuccess = {
                        resolve(request.result != null && request.result != js("undefined"))
                    }

                    request.onerror = {
                        reject(StorageException("Failed to check existence of '$key'"))
                    }

                } catch (e: Exception) {
                    reject(StorageException("Failed to check existence of '$key': ${e.message}", e))
                }
            }
        }.unsafeCast<Promise<Boolean>>()
    }

    override fun delete(key: String): Promise<Unit> {
        return ensureDatabase().then { db ->
            Promise<Unit> { resolve, reject ->
                try {
                    val transaction = db.transaction(arrayOf(GRAPHS_STORE, METADATA_STORE), "readwrite")
                    val graphStore = transaction.objectStore(GRAPHS_STORE)
                    val metadataStore = transaction.objectStore(METADATA_STORE)

                    val graphRequest = graphStore.delete(key)
                    val metadataRequest = metadataStore.delete(key)

                    var completed = 0
                    val onComplete = {
                        completed++
                        if (completed == 2) {
                            resolve(Unit)
                        }
                    }

                    graphRequest.onsuccess = { onComplete() }
                    graphRequest.onerror = { reject(StorageException("Failed to delete graph data: ${graphRequest.error}")) }

                    metadataRequest.onsuccess = { onComplete() }
                    metadataRequest.onerror = { reject(StorageException("Failed to delete metadata: ${metadataRequest.error}")) }

                } catch (e: Exception) {
                    reject(StorageException("Failed to delete graph '$key': ${e.message}", e))
                }
            }
        }
    }

    override fun list(): Promise<Array<String>> {
        return ensureDatabase().then { db ->
            Promise<Array<String>> { resolve, reject ->
                try {
                    val transaction = db.transaction(METADATA_STORE, "readonly")
                    val store = transaction.objectStore(METADATA_STORE)
                    val request = store.openCursor()

                    val keys = mutableListOf<String>()

                    request.onsuccess = { event ->
                        val cursor = request.result?.unsafeCast<IDBCursor>()
                        if (cursor != null) {
                            keys.add(cursor.key.toString())
                            cursor.`continue`()
                        } else {
                            resolve(keys.toTypedArray())
                        }
                    }

                    request.onerror = {
                        reject(StorageException("Failed to list graphs"))
                    }

                } catch (e: Exception) {
                    reject(StorageException("Failed to list graphs: ${e.message}", e))
                }
            }
        }.unsafeCast<Promise<Array<String>>>()
    }

    override fun clear(): Promise<Unit> {
        return ensureDatabase().then { db ->
            Promise<Unit> { resolve, reject ->
                try {
                    val transaction = db.transaction(arrayOf(GRAPHS_STORE, METADATA_STORE), "readwrite")
                    val graphStore = transaction.objectStore(GRAPHS_STORE)
                    val metadataStore = transaction.objectStore(METADATA_STORE)

                    val graphRequest = graphStore.clear()
                    val metadataRequest = metadataStore.clear()

                    var completed = 0
                    val onComplete = {
                        completed++
                        if (completed == 2) {
                            resolve(Unit)
                        }
                    }

                    graphRequest.onsuccess = { onComplete() }
                    graphRequest.onerror = { reject(StorageException("Failed to clear graphs: ${graphRequest.error}")) }

                    metadataRequest.onsuccess = { onComplete() }
                    metadataRequest.onerror = { reject(StorageException("Failed to clear metadata: ${metadataRequest.error}")) }

                } catch (e: Exception) {
                    reject(StorageException("Failed to clear storage: ${e.message}", e))
                }
            }
        }
    }

    override fun getInfo(): Promise<StorageInfo> {
        return ensureDatabase().then { db ->
            Promise<StorageInfo> { resolve, reject ->
                try {
                    val transaction = db.transaction(METADATA_STORE, "readonly")
                    val store = transaction.objectStore(METADATA_STORE)
                    val request = store.openCursor()

                    var graphCount = 0
                    var totalSize = 0L
                    val graphKeys = mutableListOf<String>()

                    request.onsuccess = { event ->
                        val cursor = request.result?.unsafeCast<IDBCursor>()
                        if (cursor != null) {
                            val metadata = cursor.value
                            graphCount++
                            graphKeys.add(metadata.key.unsafeCast<String>())
                            totalSize += metadata.size?.unsafeCast<Int>()?.toLong() ?: 0L
                            cursor.`continue`()
                        } else {
                            val info = StorageInfo(
                                type = "IndexedDB",
                                available = true,
                                capacity = null, // IndexedDB has dynamic capacity
                                used = totalSize,
                                graphCount = graphCount,
                                metadata = mapOf(
                                    "databaseName" to databaseName,
                                    "databaseVersion" to databaseVersion,
                                    "graphKeys" to graphKeys
                                )
                            )
                            resolve(info)
                        }
                    }

                    request.onerror = {
                        reject(StorageException("Failed to get storage info"))
                    }

                } catch (e: Exception) {
                    reject(StorageException("Failed to get storage info: ${e.message}", e))
                }
            }
        }.unsafeCast<Promise<StorageInfo>>()
    }

    /**
     * Get metadata for all graphs.
     */
    fun getAllMetadata(): Promise<Array<dynamic>> {
        return ensureDatabase().then { db ->
            Promise<Array<dynamic>> { resolve, reject ->
                try {
                    val transaction = db.transaction(METADATA_STORE, "readonly")
                    val store = transaction.objectStore(METADATA_STORE)
                    val request = store.openCursor()

                    val metadata = mutableListOf<dynamic>()

                    request.onsuccess = { event ->
                        val cursor = request.result?.unsafeCast<IDBCursor>()
                        if (cursor != null) {
                            metadata.add(cursor.value)
                            cursor.`continue`()
                        } else {
                            resolve(metadata.toTypedArray())
                        }
                    }

                    request.onerror = {
                        reject(StorageException("Failed to get metadata"))
                    }

                } catch (e: Exception) {
                    reject(StorageException("Failed to get metadata: ${e.message}", e))
                }
            }
        }.unsafeCast<Promise<Array<dynamic>>>()
    }

    /**
     * Close the database connection.
     */
    fun close() {
        database?.close()
        database = null
    }

    // === Private Helper Methods ===

    private fun ensureDatabase(): Promise<IDBDatabase> {
        return if (database != null) {
            Promise.resolve(database!!)
        } else {
            openDatabase()
        }
    }

    private fun openDatabase(): Promise<IDBDatabase> {
        return Promise { resolve, reject ->
            try {
                if (!isIndexedDBAvailable()) {
                    reject(StorageUnavailableException("IndexedDB is not available"))
                    return@Promise
                }

                val request = indexedDB.open(databaseName, databaseVersion)

                request.onsuccess = {
                    database = request.result.unsafeCast<IDBDatabase>()
                    resolve(database!!)
                }

                request.onerror = {
                    reject(StorageException("Failed to open database"))
                }

                request.onupgradeneeded = { event ->
                    val db = request.result.unsafeCast<IDBDatabase>()
                    val upgradeEvent = event.unsafeCast<IDBVersionChangeEvent>()

                    try {
                        // Create object stores if they don't exist
                        if (!db.objectStoreNames.contains(GRAPHS_STORE)) {
                            val graphStore = db.createObjectStore(GRAPHS_STORE)
                        }

                        if (!db.objectStoreNames.contains(METADATA_STORE)) {
                            val metadataStore = db.createObjectStore(METADATA_STORE)

                            // Create indexes for better query performance
                            metadataStore.createIndex("timestamp", "timestamp", js("{ unique: false }"))
                            metadataStore.createIndex("size", "size", js("{ unique: false }"))
                            metadataStore.createIndex("vertexCount", "vertexCount", js("{ unique: false }"))
                            metadataStore.createIndex("edgeCount", "edgeCount", js("{ unique: false }"))
                        }
                    } catch (e: Exception) {
                        reject(StorageException("Failed to upgrade database schema: ${e.message}", e))
                    }
                }

                request.onblocked = {
                    reject(StorageException("Database upgrade blocked - close other tabs/windows"))
                }

            } catch (e: Exception) {
                reject(StorageException("Failed to open IndexedDB: ${e.message}", e))
            }
        }
    }

    private fun countVertices(graph: TinkerGraph): Int {
        var count = 0
        graph.vertices().forEach { count++ }
        return count
    }

    private fun countEdges(graph: TinkerGraph): Int {
        var count = 0
        graph.edges().forEach { count++ }
        return count
    }
}
