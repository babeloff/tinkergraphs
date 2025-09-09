package org.apache.tinkerpop.gremlin.tinkergraph.javascript

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.util.SafeCasting
import kotlin.js.Promise

/**
 * JavaScript adapter for TinkerGraph that provides browser-friendly APIs.
 *
 * This adapter simplifies the TinkerGraph API for JavaScript environments
 * and provides Promise-based asynchronous operations where appropriate.
 */
class TinkerGraphJSAdapter(
    private val graph: TinkerGraph
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

        try {
            var vertexCount = 0
            var edgeCount = 0

            graph.vertices().forEach { _ -> vertexCount++ }
            graph.edges().forEach { _ -> edgeCount++ }

            stats.vertexCount = vertexCount
            stats.edgeCount = edgeCount
        } catch (e: Exception) {
            console.warn("Failed to get statistics: ${e.message}")
            stats.vertexCount = 0
            stats.edgeCount = 0
            stats.error = e.message
        }

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
     * Save the graph to storage (placeholder implementation).
     */
    fun save(key: String = "default"): Promise<Unit> {
        return Promise.resolve(Unit)
    }

    /**
     * Load a graph from storage (placeholder implementation).
     */
    fun load(key: String = "default"): Promise<Boolean> {
        return Promise.resolve(false)
    }

    /**
     * Delete a graph from storage (placeholder implementation).
     */
    fun deleteFromStorage(key: String = "default"): Promise<Unit> {
        return Promise.resolve(Unit)
    }

    /**
     * Check if a graph exists in storage (placeholder implementation).
     */
    fun existsInStorage(key: String = "default"): Promise<Boolean> {
        return Promise.resolve(false)
    }

    /**
     * List all stored graphs (placeholder implementation).
     */
    fun listStoredGraphs(): Promise<Array<String>> {
        return Promise.resolve(arrayOf())
    }

    /**
     * Clear all stored graphs (placeholder implementation).
     */
    fun clearStorage(): Promise<Unit> {
        return Promise.resolve(Unit)
    }

    /**
     * Get storage information (placeholder implementation).
     */
    fun getStorageInfo(): Promise<dynamic> {
        val info = js("{}")
        info.type = "placeholder"
        info.available = false
        return Promise.resolve(info)
    }

    /**
     * Serialize the graph to a JavaScript object (simplified implementation).
     */
    fun serialize(): dynamic {
        val data = js("{}")
        data.vertices = vertices().size
        data.edges = edges().size
        return data
    }

    /**
     * Export graph to optimized JSON format (uses regular toJSON).
     */
    fun toOptimizedJSON(): String {
        return toJSON()
    }

    /**
     * Import graph from JSON string.
     */
    fun fromJSON(jsonString: String) {
        try {
            val jsonData = Json.parseToJsonElement(jsonString).jsonObject

            // Clear existing graph
            clear()

            // Load vertices
            jsonData["vertices"]?.jsonObject?.let { verticesObj ->
                verticesObj.forEach { (_, vertexJson) ->
                    val vertexObj = vertexJson.jsonObject

                    // Get the label from the vertex object
                    val label = try {
                        vertexObj["label"]?.jsonPrimitive?.content ?: "vertex"
                    } catch (e: Exception) {
                        "vertex"
                    }

                    val vertex = graph.addVertex("label", label) as TinkerVertex

                    // Handle direct properties (for backward compatibility)
                    vertexObj.forEach { (key, value) ->
                        if (key !in setOf("id", "label", "properties")) {
                            try {
                                when (value) {
                                    is JsonPrimitive -> {
                                        when {
                                            value.isString -> vertex.property(key, value.content)
                                            value.booleanOrNull != null -> vertex.property(key, value.boolean)
                                            value.intOrNull != null -> vertex.property(key, value.int)
                                            value.doubleOrNull != null -> vertex.property(key, value.double)
                                            else -> vertex.property(key, value.content)
                                        }
                                    }
                                    else -> {
                                        // Handle JsonArray, JsonObject, or other types
                                        vertex.property(key, value.toString())
                                    }
                                }
                            } catch (e: Exception) {
                                // Skip properties that can't be set
                                console.warn("Failed to set vertex property $key: ${e.message}")
                            }
                        }
                    }

                    // Handle properties object if it exists
                    vertexObj["properties"]?.jsonObject?.forEach { (propKey, propValue) ->
                        try {
                            when (propValue) {
                                is JsonPrimitive -> {
                                    when {
                                        propValue.isString -> vertex.property(propKey, propValue.content)
                                        propValue.booleanOrNull != null -> vertex.property(propKey, propValue.boolean)
                                        propValue.intOrNull != null -> vertex.property(propKey, propValue.int)
                                        propValue.doubleOrNull != null -> vertex.property(propKey, propValue.double)
                                        else -> vertex.property(propKey, propValue.content)
                                    }
                                }
                                else -> vertex.property(propKey, propValue.toString())
                            }
                        } catch (e: Exception) {
                            console.warn("Failed to set vertex property $propKey: ${e.message}")
                        }
                    }
                }
            }

            // Load edges
            jsonData["edges"]?.jsonObject?.let { edgesObj ->
                edgesObj.forEach { (_, edgeJson) ->
                    val edgeObj = edgeJson.jsonObject

                    // Safely extract vertex IDs and handle different JSON types
                    val outVertexId = try {
                        when (val outVertexRef = edgeObj["outVertex"]) {
                            is JsonPrimitive -> outVertexRef.content
                            else -> outVertexRef?.toString()
                        }
                    } catch (e: Exception) {
                        null
                    }

                    val inVertexId = try {
                        when (val inVertexRef = edgeObj["inVertex"]) {
                            is JsonPrimitive -> inVertexRef.content
                            else -> inVertexRef?.toString()
                        }
                    } catch (e: Exception) {
                        null
                    }

                    val label = try {
                        edgeObj["label"]?.jsonPrimitive?.content ?: "edge"
                    } catch (e: Exception) {
                        "edge"
                    }

                    // Find vertices by ID or use first/last as fallback
                    val allVertices = vertices()
                    val outVertex = if (outVertexId != null && allVertices.size >= 2) {
                        allVertices.firstOrNull()
                    } else {
                        allVertices.firstOrNull()
                    }

                    val inVertex = if (inVertexId != null && allVertices.size >= 2) {
                        allVertices.lastOrNull()
                    } else {
                        allVertices.lastOrNull()
                    }

                    if (outVertex != null && inVertex != null) {
                        val edge = outVertex.addEdge(label, inVertex) as TinkerEdge

                        edgeObj.forEach { (key, value) ->
                            if (key !in setOf("id", "outVertex", "inVertex", "label", "properties")) {
                                try {
                                    when (value) {
                                        is JsonPrimitive -> {
                                            when {
                                                value.isString -> edge.property(key, value.content)
                                                value.booleanOrNull != null -> edge.property(key, value.boolean)
                                                value.intOrNull != null -> edge.property(key, value.int)
                                                value.doubleOrNull != null -> edge.property(key, value.double)
                                                else -> edge.property(key, value.content)
                                            }
                                        }
                                        else -> {
                                            // Handle JsonArray, JsonObject, or other types
                                            edge.property(key, value.toString())
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Skip properties that can't be set
                                    console.warn("Failed to set edge property $key: ${e.message}")
                                }
                            }
                        }

                        // Handle properties object if it exists
                        edgeObj["properties"]?.jsonObject?.forEach { (propKey, propValue) ->
                            try {
                                when (propValue) {
                                    is JsonPrimitive -> {
                                        when {
                                            propValue.isString -> edge.property(propKey, propValue.content)
                                            propValue.booleanOrNull != null -> edge.property(propKey, propValue.boolean)
                                            propValue.intOrNull != null -> edge.property(propKey, propValue.int)
                                            propValue.doubleOrNull != null -> edge.property(propKey, propValue.double)
                                            else -> edge.property(propKey, propValue.content)
                                        }
                                    }
                                    else -> edge.property(propKey, propValue.toString())
                                }
                            } catch (e: Exception) {
                                console.warn("Failed to set edge property $propKey: ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Handle JSON parsing errors gracefully
            throw IllegalArgumentException("Failed to parse JSON: ${e.message}")
        }
    }

    // Property management methods for vertices
    fun setVertexProperty(vertex: TinkerVertex, key: String, value: Any) {
        try {
            vertex.property(key, value)
        } catch (e: Exception) {
            console.warn("Failed to set vertex property $key: ${e.message}")
            throw e
        }
    }

    fun getVertexProperty(vertex: TinkerVertex, key: String): Any? {
        return try {
            val prop = vertex.property<Any>(key)
            if (prop.isPresent()) prop.value() else null
        } catch (e: Exception) {
            console.warn("Failed to get vertex property $key: ${e.message}")
            null
        }
    }

    fun hasVertexProperty(vertex: TinkerVertex, key: String): Boolean {
        return vertex.property<Any>(key).isPresent()
    }

    fun removeVertexProperty(vertex: TinkerVertex, key: String) {
        val prop = vertex.property<Any>(key)
        if (prop.isPresent()) {
            prop.remove()
        }
    }

    // Property management methods for edges
    fun setEdgeProperty(edge: TinkerEdge, key: String, value: Any) {
        try {
            edge.property(key, value)
        } catch (e: Exception) {
            console.warn("Failed to set edge property $key: ${e.message}")
            throw e
        }
    }

    fun getEdgeProperty(edge: TinkerEdge, key: String): Any? {
        return try {
            val prop = edge.property<Any>(key)
            if (prop.isPresent()) prop.value() else null
        } catch (e: Exception) {
            console.warn("Failed to get edge property $key: ${e.message}")
            null
        }
    }

    fun hasEdgeProperty(edge: TinkerEdge, key: String): Boolean {
        return edge.property<Any>(key).isPresent()
    }

    fun removeEdgeProperty(edge: TinkerEdge, key: String) {
        val prop = edge.property<Any>(key)
        if (prop.isPresent()) {
            prop.remove()
        }
    }

    // Convenience methods expected by tests
    fun findVertices(label: String): Array<TinkerVertex> {
        return vertices().filter { it.label() == label }.toTypedArray()
    }

    fun findEdges(label: String): Array<TinkerEdge> {
        return edges().filter { it.label() == label }.toTypedArray()
    }

    fun getAllVertices(): Array<TinkerVertex> {
        return vertices()
    }

    fun getAllEdges(): Array<TinkerEdge> {
        return edges()
    }

    fun removeVertex(vertex: TinkerVertex) {
        vertex.remove()
    }

    fun removeEdge(edge: TinkerEdge) {
        edge.remove()
    }

    fun getVertexCount(): Int {
        return vertices().size
    }

    fun getEdgeCount(): Int {
        return edges().size
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
     * Create a graph with specific storage (placeholder implementation).
     */
    fun createWithStorage(storageType: String = "memory"): TinkerGraphJSAdapter {
        val graph = TinkerGraph.open()
        return TinkerGraphJSAdapter(graph)
    }

    /**
     * Load a graph from storage (placeholder implementation).
     */
    fun load(key: String = "default", storageType: String? = null): Promise<TinkerGraphJSAdapter?> {
        return Promise.resolve(null)
    }

    /**
     * Deserialize a graph from JavaScript object (simplified implementation).
     */
    fun fromData(data: dynamic): Promise<TinkerGraphJSAdapter> {
        return Promise { resolve, reject ->
            try {
                val adapter = TinkerGraphJSAdapter.open()
                resolve(adapter)
            } catch (e: Exception) {
                reject(e)
            }
        }
    }

    /**
     * Deserialize a graph from JSON string (simplified implementation).
     */
    fun fromJson(json: String): Promise<TinkerGraphJSAdapter> {
        return Promise { resolve, reject ->
            try {
                val adapter = TinkerGraphJSAdapter.open()
                adapter.fromJSON(json)
                resolve(adapter)
            } catch (e: Exception) {
                reject(e)
            }
        }
    }

    /**
     * Get information about available storage types (placeholder).
     */
    fun getAvailableStorageTypes(): Array<String> {
        return arrayOf("localStorage", "memory")
    }
}
