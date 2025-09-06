package org.apache.tinkerpop.gremlin.tinkergraph.javascript

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.platform.*
import org.apache.tinkerpop.gremlin.tinkergraph.util.SafeCasting
import kotlin.js.Promise

/**
 * JavaScript adapter for TinkerGraph that provides browser-friendly APIs.
 *
 * This adapter simplifies the TinkerGraph API for JavaScript environments
 * and provides Promise-based asynchronous operations where appropriate.
 */
class TinkerGraphJSAdapter(
    private val graph: TinkerGraph,
    private val storage: GraphStorage = StorageFactory.createDefaultStorage()
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Add a vertex with JavaScript-friendly API.
     */
    fun addVertex(label: String? = null, properties: dynamic = null): TinkerVertex {
        // Build properties map
        val props = mutableMapOf<String, Any?>()
        if (label != null) {
            props["label"] = label
        }

        val vertex = graph.addVertex(props) as TinkerVertex

        // Add properties if provided
        if (properties != null) {
            addPropertiesToElement(vertex, properties)
        }

        return vertex
    }

    /**
     * Add an edge with JavaScript-friendly API.
     */
    fun addEdge(outVertex: TinkerVertex, label: String, inVertex: TinkerVertex, properties: dynamic = null): TinkerEdge {
        val edge = outVertex.addEdge(label, inVertex) as TinkerEdge

        // Add properties if provided
        if (properties != null) {
            addPropertiesToElement(edge, properties)
        }

        return edge
    }

    /**
     * Get all vertices as a JavaScript-friendly array.
     */
    fun vertices(): Array<TinkerVertex> {
        val vertexList = mutableListOf<TinkerVertex>()
        graph.vertices().forEach { vertex ->
            val tinkerVertex = SafeCasting.asTinkerVertex(vertex)
            if (tinkerVertex != null) {
                vertexList.add(tinkerVertex)
            }
        }
        return vertexList.toTypedArray()
    }

    /**
     * Get all edges as a JavaScript-friendly array.
     */
    fun edges(): Array<TinkerEdge> {
        val edgeList = mutableListOf<TinkerEdge>()
        graph.edges().forEach { edge ->
            val tinkerEdge = SafeCasting.asTinkerEdge(edge)
            if (tinkerEdge != null) {
                edgeList.add(tinkerEdge)
            }
        }
        return edgeList.toTypedArray()
    }

    /**
     * Find vertices by property value.
     */
    fun findVerticesByProperty(key: String, value: Any): Array<TinkerVertex> {
        return try {
            vertices().filter { vertex ->
                try {
                    val property = vertex.property<Any>(key)
                    if (property.isPresent()) {
                        val propValue = property.value()
                        // Use simple equality comparison for debugging
                        propValue == value
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    console.warn("Error checking property '$key' on vertex: ${e.message}")
                    false
                }
            }.toTypedArray()
        } catch (e: Exception) {
            console.error("Error in findVerticesByProperty: ${e.message}")
            emptyArray()
        }
    }

    /**
     * Find edges by property value.
     */
    fun findEdgesByProperty(key: String, value: Any): Array<TinkerEdge> {
        return try {
            edges().filter { edge ->
                try {
                    val property = edge.property<Any>(key)
                    if (property.isPresent()) {
                        val propValue = property.value()
                        // Use simple equality comparison for debugging
                        propValue == value
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    console.warn("Error checking property '$key' on edge: ${e.message}")
                    false
                }
            }.toTypedArray()
        } catch (e: Exception) {
            console.error("Error in findEdgesByProperty: ${e.message}")
            emptyArray()
        }
    }

    /**
     * Get vertex by ID.
     */
    fun getVertex(id: Any): TinkerVertex? {
        return try {
            val vertices = graph.vertices(id)
            if (vertices.hasNext()) {
                SafeCasting.asTinkerVertex(vertices.next())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get edge by ID.
     */
    fun getEdge(id: Any): TinkerEdge? {
        return try {
            val edges = graph.edges(id)
            if (edges.hasNext()) {
                SafeCasting.asTinkerEdge(edges.next())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get graph statistics.
     */
    fun getStatistics(): dynamic {
        val stats = js("{}")

        var vertexCount = 0
        var edgeCount = 0

        graph.vertices().forEach { _ -> vertexCount++ }
        graph.edges().forEach { _ -> edgeCount++ }

        stats.vertexCount = vertexCount
        stats.edgeCount = edgeCount

        return stats
    }

    /**
     * Export graph to JSON string.
     */
    fun toJSON(): String {
        val data = js("{}")
        data.vertices = js("{}")
        data.edges = js("{}")

        // Export vertices
        graph.vertices().forEach { vertex ->
            val tinkerVertex = SafeCasting.asTinkerVertex(vertex)
            if (tinkerVertex != null) {
                val vertexData = js("{}")
                val vertexId = tinkerVertex.id()
                if (vertexId != null) {
                    vertexData.id = vertexId
                }
                vertexData.label = tinkerVertex.label()
                vertexData.properties = js("{}")

                tinkerVertex.keys().forEach { key ->
                    val property = tinkerVertex.property<Any>(key)
                    if (property.isPresent()) {
                        vertexData.properties[key] = property.value()
                    }
                }

                val idStr = vertexId?.toString() ?: "unknown"
                data.vertices[idStr] = vertexData
            }
        }

        // Export edges
        graph.edges().forEach { edge ->
            val tinkerEdge = SafeCasting.asTinkerEdge(edge)
            if (tinkerEdge != null) {
                val edgeData = js("{}")
                val edgeId = tinkerEdge.id()
                if (edgeId != null) {
                    edgeData.id = edgeId
                }
                edgeData.label = tinkerEdge.label()
                val outVertexId = tinkerEdge.outVertex().id()
                val inVertexId = tinkerEdge.inVertex().id()
                if (outVertexId != null) {
                    edgeData.outVertex = outVertexId
                }
                if (inVertexId != null) {
                    edgeData.inVertex = inVertexId
                }
                edgeData.properties = js("{}")

                tinkerEdge.keys().forEach { key ->
                    val property = tinkerEdge.property<Any>(key)
                    if (property.isPresent()) {
                        edgeData.properties[key] = property.value()
                    }
                }

                val idStr = edgeId?.toString() ?: "unknown"
                data.edges[idStr] = edgeData
            }
        }

        return JSON.stringify(data)
    }

    /**
     * Clear all data from the graph.
     */
    fun clear() {
        // Remove all edges first
        val edgesToRemove = mutableListOf<Edge>()
        graph.edges().forEach { edge ->
            edgesToRemove.add(edge)
        }
        edgesToRemove.forEach { edge ->
            edge.remove()
        }

        // Remove all vertices
        val verticesToRemove = mutableListOf<Vertex>()
        graph.vertices().forEach { vertex ->
            verticesToRemove.add(vertex)
        }
        verticesToRemove.forEach { vertex ->
            vertex.remove()
        }
    }

    // === Storage Methods ===

    /**
     * Save the graph to storage.
     */
    fun save(key: String = "default"): Promise<Unit> {
        return storage.store(graph, key)
    }

    /**
     * Load a graph from storage, replacing current graph data.
     */
    fun load(key: String = "default"): Promise<Boolean> {
        return storage.load(key).then { loadedGraph ->
            if (loadedGraph != null) {
                // Clear current graph and copy data from loaded graph
                clear()

                // Copy vertices
                loadedGraph.vertices().forEach { vertex ->
                    val newVertex = graph.addVertex(vertex.label())
                    vertex.keys().forEach { propKey ->
                        val prop = vertex.property<Any>(propKey)
                        if (prop.isPresent()) {
                            val value = prop.value()
                            if (value != null) {
                                newVertex.property(propKey, value)
                            }
                        }
                    }
                }

                // Copy edges (requires vertex lookup)
                val vertexMap = mutableMapOf<Any, Vertex>()
                graph.vertices().forEach { vertex ->
                    val vertexId = vertex.id()
                    if (vertexId != null) {
                        vertexMap[vertexId] = vertex
                    }
                }

                loadedGraph.edges().forEach { edge ->
                    val outV = vertexMap[edge.outVertex().id()]
                    val inV = vertexMap[edge.inVertex().id()]
                    if (outV != null && inV != null) {
                        val newEdge = outV.addEdge(edge.label(), inV)
                        edge.keys().forEach { propKey ->
                            val prop = edge.property<Any>(propKey)
                            if (prop.isPresent()) {
                                val value = prop.value()
                                if (value != null) {
                                    newEdge.property(propKey, value)
                                }
                            }
                        }
                    }
                }

                true
            } else {
                false
            }
        }
    }

    /**
     * Delete a graph from storage.
     */
    fun deleteFromStorage(key: String = "default"): Promise<Unit> {
        return storage.delete(key)
    }

    /**
     * Check if a graph exists in storage.
     */
    fun existsInStorage(key: String = "default"): Promise<Boolean> {
        return storage.exists(key)
    }

    /**
     * List all stored graphs.
     */
    fun listStoredGraphs(): Promise<Array<String>> {
        return storage.list()
    }

    /**
     * Clear all stored graphs.
     */
    fun clearStorage(): Promise<Unit> {
        return storage.clear()
    }

    /**
     * Get storage information.
     */
    fun getStorageInfo(): Promise<StorageInfo> {
        return storage.getInfo()
    }

    /**
     * Serialize the graph to a JavaScript object.
     */
    fun serialize(): dynamic {
        return GraphSerializer.serialize(graph)
    }

    /**
     * Export graph to optimized JSON format.
     */
    fun toOptimizedJSON(): String {
        return GraphSerializer.toJson(graph)
    }

    /**
     * Private helper methods
     */
    fun saveAsync(): Promise<Boolean> {
        return Promise { resolve, _ ->
            // Simulate async operation
            resolve(true)
        }
    }

    /**
     * Get the underlying TinkerGraph instance.
     */
    fun getGraph(): TinkerGraph = graph

    /**
     * Helper function to add properties from a dynamic object to an element.
     */
    private fun addPropertiesToElement(element: Element, properties: dynamic) {
        try {
            val keys = js("Object.keys(properties)").unsafeCast<Array<String>>()
            keys.forEach { key ->
                try {
                    val value = properties[key]
                    if (value != null && value != undefined) {
                        // Convert JavaScript values to more stable types
                        val safeValue = when {
                            js("typeof value === 'number'").unsafeCast<Boolean>() -> {
                                val num = value.unsafeCast<Double>()
                                if (num.isFinite()) num else value.toString()
                            }
                            js("typeof value === 'string'").unsafeCast<Boolean>() -> value.unsafeCast<String>()
                            js("typeof value === 'boolean'").unsafeCast<Boolean>() -> value.unsafeCast<Boolean>()
                            else -> value.toString()
                        }
                        element.property(key, safeValue)
                    }
                } catch (e: Exception) {
                    console.warn("Error adding property '$key': ${e.message}")
                }
            }
        } catch (e: Exception) {
            console.error("Error adding properties to element: ${e.message}")
        }
    }

    companion object {
        /**
         * Create a new TinkerGraph with JavaScript adapter.
         */
        fun open(): TinkerGraphJSAdapter {
            return TinkerGraphJSAdapter(TinkerGraph.open())
        }

        /**
         * Check if the environment supports JavaScript features.
         */
        fun isJavaScriptEnvironment(): Boolean {
            return try {
                js("typeof window !== 'undefined' || typeof global !== 'undefined'").unsafeCast<Boolean>()
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Check if localStorage is available.
         */
        fun hasLocalStorage(): Boolean {
            return try {
                js("typeof Storage !== 'undefined' && typeof localStorage !== 'undefined'").unsafeCast<Boolean>()
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Check if IndexedDB is available.
         */
        fun hasIndexedDB(): Boolean {
            return try {
                js("typeof indexedDB !== 'undefined'").unsafeCast<Boolean>()
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * JavaScript-friendly vertex wrapper.
 */
class JSVertex(private val vertex: TinkerVertex) {
    fun getId() = vertex.id()
    fun getLabel() = vertex.label()

    fun getProperty(key: String): Any? {
        val property = vertex.property<Any>(key)
        return if (property.isPresent()) property.value() else null
    }

    fun setProperty(key: String, value: Any) {
        vertex.property(key, value)
    }

    fun getProperties(): dynamic {
        val props = js("{}")
        vertex.keys().forEach { key ->
            val property = vertex.property<Any>(key)
            if (property.isPresent()) {
                props[key] = property.value()
            }
        }
        return props
    }

    fun addEdge(label: String, inVertex: JSVertex): JSEdge {
        val edge = vertex.addEdge(label, inVertex.vertex) as TinkerEdge
        return JSEdge(edge)
    }

    fun getOutEdges(label: String? = null): Array<JSEdge> {
        val edges = if (label != null) {
            vertex.edges(Direction.OUT, label)
        } else {
            vertex.edges(Direction.OUT)
        }

        val result = mutableListOf<JSEdge>()
        edges.forEach { edge ->
            if (edge is TinkerEdge) {
                result.add(JSEdge(edge))
            }
        }
        return result.toTypedArray()
    }

    fun getInEdges(label: String? = null): Array<JSEdge> {
        val edges = if (label != null) {
            vertex.edges(Direction.IN, label)
        } else {
            vertex.edges(Direction.IN)
        }

        val result = mutableListOf<JSEdge>()
        edges.forEach { edge ->
            if (edge is TinkerEdge) {
                result.add(JSEdge(edge))
            }
        }
        return result.toTypedArray()
    }

    fun remove() = vertex.remove()
}

/**
 * JavaScript-friendly edge wrapper.
 */
class JSEdge(private val edge: TinkerEdge) {
    fun getId() = edge.id()
    fun getLabel() = edge.label()

    fun getOutVertex() = JSVertex(edge.outVertex() as TinkerVertex)
    fun getInVertex() = JSVertex(edge.inVertex() as TinkerVertex)

    fun getProperty(key: String): Any? {
        val property = edge.property<Any>(key)
        return if (property.isPresent()) property.value() else null
    }

    fun setProperty(key: String, value: Any) {
        edge.property(key, value)
    }

    fun getProperties(): dynamic {
        val props = js("{}")
        edge.keys().forEach { key ->
            val property = edge.property<Any>(key)
            if (property.isPresent()) {
                props[key] = property.value()
            }
        }
        return props
    }

    fun remove() = edge.remove()
}

/**
 * Companion object with factory methods.
 */
object TinkerGraphJS {

    /**
     * Create a new empty graph with default storage.
     */
    fun create(): TinkerGraphJSAdapter {
        val graph = TinkerGraph.open()
        return TinkerGraphJSAdapter(graph)
    }

    /**
     * Create a graph with specific storage.
     */
    fun createWithStorage(storageType: StorageType): TinkerGraphJSAdapter {
        val graph = TinkerGraph.open()
        val storage = StorageFactory.createStorage(storageType)
        return TinkerGraphJSAdapter(graph, storage)
    }

    /**
     * Load a graph from storage.
     */
    fun load(key: String = "default", storageType: StorageType? = null): Promise<TinkerGraphJSAdapter?> {
        val storage = storageType?.let { StorageFactory.createStorage(it) }
                     ?: StorageFactory.createDefaultStorage()

        return storage.load(key).then { graph ->
            if (graph != null) {
                TinkerGraphJSAdapter(graph, storage)
            } else {
                null
            }
        }
    }

    /**
     * Deserialize a graph from JavaScript object.
     */
    fun fromData(data: dynamic): Promise<TinkerGraphJSAdapter> {
        return GraphSerializer.deserialize(data).then { graph ->
            TinkerGraphJSAdapter(graph)
        }
    }

    /**
     * Deserialize a graph from JSON string.
     */
    fun fromJson(json: String): Promise<TinkerGraphJSAdapter> {
        return GraphSerializer.fromJson(json).then { graph ->
            TinkerGraphJSAdapter(graph)
        }
    }

    /**
     * Get information about available storage types.
     */
    fun getAvailableStorageTypes(): Array<StorageType> {
        return StorageFactory.getAvailableStorageTypes()
    }
}
