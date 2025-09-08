package org.apache.tinkerpop.gremlin.tinkergraph.io.graphson

import org.apache.tinkerpop.gremlin.tinkergraph.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.platform.GraphSerializer
import kotlin.js.Promise

/**
 * JavaScript-specific integration for GraphSON v3.0 with existing serialization infrastructure.
 *
 * This class provides seamless integration between the native GraphSON v3.0 implementation
 * and the existing JavaScript serialization utilities, ensuring compatibility with
 * browser storage, Node.js file systems, and web APIs.
 */
object GraphSONJSIntegration {

    private val mapper = GraphSONMapper.create()

    /**
     * Serializes a TinkerGraph to GraphSON v3.0 format with JavaScript optimization.
     * This method provides a drop-in replacement for the existing GraphSerializer.serialize()
     * method while using proper GraphSON v3.0 format instead of custom JSON.
     */
    fun serializeToGraphSON(graph: TinkerGraph): dynamic {
        val graphsonString = mapper.writeGraph(graph)

        // Parse as JavaScript object for compatibility with existing storage systems
        return try {
            JSON.parse(graphsonString)
        } catch (e: Exception) {
            console.warn("Failed to parse GraphSON as JavaScript object, falling back to string", e)
            val result = js("{}")
            result.graphson = graphsonString
            result.format = "graphson-v3"
            result
        }
    }

    /**
     * Deserializes a TinkerGraph from GraphSON v3.0 format with JavaScript optimization.
     * This method provides a drop-in replacement for the existing GraphSerializer.deserialize()
     * method while supporting proper GraphSON v3.0 format.
     */
    fun deserializeFromGraphSON(data: dynamic): Promise<TinkerGraph> {
        return Promise { resolve, reject ->
            try {
                val graphsonString = when {
                    // Handle native GraphSON v3.0 objects
                    js("typeof data === 'object' && data !== null && data.version === '3.0'") as Boolean -> {
                        JSON.stringify(data)
                    }
                    // Handle wrapped GraphSON format
                    js("typeof data === 'object' && data !== null && data.format === 'graphson-v3'") as Boolean -> {
                        data.graphson.unsafeCast<String>()
                    }
                    // Handle direct string format
                    js("typeof data === 'string'") as Boolean -> {
                        data.unsafeCast<String>()
                    }
                    // Fallback to existing serializer for backward compatibility
                    else -> {
                        GraphSerializer.deserialize(data).then(
                            onFulfilled = { graph -> resolve(graph) },
                            onRejected = { error -> reject(error) }
                        )
                        return@Promise
                    }
                }

                val graph = mapper.readGraph(graphsonString)
                resolve(graph)
            } catch (e: Exception) {
                // Fallback to existing serializer for backward compatibility
                try {
                    GraphSerializer.deserialize(data).then(
                        onFulfilled = { graph -> resolve(graph) },
                        onRejected = { fallbackError ->
                            reject(GraphSONException("Both GraphSON and fallback deserialization failed. GraphSON error: ${e.message}, Fallback error: $fallbackError", e))
                        }
                    )
                } catch (fallbackException: Exception) {
                    reject(GraphSONException("GraphSON deserialization failed: ${e.message}", e))
                }
            }
        }
    }

    /**
     * Converts GraphSON v3.0 data to a format compatible with existing JavaScript storage systems.
     * This ensures that GraphSON data can be stored using IndexedDB, localStorage, or file systems
     * without modification to existing storage code.
     */
    fun toStorageFormat(graph: TinkerGraph): dynamic {
        val result = js("{}")
        result.format = "graphson-v3.0"
        result.version = "3.0"
        result.timestamp = js("new Date().toISOString()")
        result.data = JSON.stringify(serializeToGraphSON(graph))
        return result
    }

    /**
     * Restores a TinkerGraph from storage format, supporting both GraphSON v3.0 and legacy formats.
     */
    fun fromStorageFormat(storageData: dynamic): Promise<TinkerGraph> {
        return Promise { resolve, reject ->
            try {
                val format = js("storageData.format") as? String

                when (format) {
                    "graphson-v3.0" -> {
                        val data = js("JSON.parse(storageData.data)")
                        deserializeFromGraphSON(data).then(
                            onFulfilled = { graph -> resolve(graph) },
                            onRejected = { error -> reject(error) }
                        )
                    }
                    else -> {
                        // Handle legacy storage formats
                        val data = js("storageData.data || storageData")
                        GraphSerializer.deserialize(data).then(
                            onFulfilled = { graph -> resolve(graph) },
                            onRejected = { error -> reject(error) }
                        )
                    }
                }
            } catch (e: Exception) {
                reject(GraphSONException("Failed to restore graph from storage format: ${e.message}", e))
            }
        }
    }

    /**
     * Provides a unified serialization method that automatically chooses between GraphSON v3.0
     * and legacy format based on configuration or environment.
     */
    fun serialize(graph: TinkerGraph, useGraphSON: Boolean = true): dynamic {
        return if (useGraphSON) {
            serializeToGraphSON(graph)
        } else {
            GraphSerializer.serialize(graph)
        }
    }

    /**
     * Provides a unified deserialization method that automatically detects the format
     * and uses the appropriate deserializer.
     */
    fun deserialize(data: dynamic): Promise<TinkerGraph> {
        return Promise { resolve, reject ->
            try {
                // Try to detect GraphSON v3.0 format
                val isGraphSON = when {
                    js("typeof data === 'object' && data !== null && data.version === '3.0'") as Boolean -> true
                    js("typeof data === 'object' && data !== null && data.format === 'graphson-v3'") as Boolean -> true
                    js("typeof data === 'string' && data.includes('\"version\":\"3.0\"')") as Boolean -> true
                    else -> false
                }

                if (isGraphSON) {
                    deserializeFromGraphSON(data).then(
                        onFulfilled = { graph -> resolve(graph) },
                        onRejected = { error ->
                            // Fallback to legacy format if GraphSON fails
                            GraphSerializer.deserialize(data).then(
                                onFulfilled = { graph -> resolve(graph) },
                                onRejected = { fallbackError -> reject(error) }
                            )
                        }
                    )
                } else {
                    GraphSerializer.deserialize(data).then(
                        onFulfilled = { graph -> resolve(graph) },
                        onRejected = { error -> reject(error) }
                    )
                }
            } catch (e: Exception) {
                reject(GraphSONException("Serialization format detection failed: ${e.message}", e))
            }
        }
    }

    /**
     * Migrates existing graphs from legacy JSON format to GraphSON v3.0 format.
     * This is useful for upgrading stored graphs to the new format.
     */
    fun migrateToGraphSON(legacyData: dynamic): Promise<dynamic> {
        return Promise { resolve, reject ->
            GraphSerializer.deserialize(legacyData).then(
                onFulfilled = { graph ->
                    try {
                        val graphsonData = serializeToGraphSON(graph)
                        resolve(graphsonData)
                    } catch (e: Exception) {
                        reject(GraphSONException("Migration to GraphSON failed: ${e.message}", e))
                    }
                },
                onRejected = { error -> reject(error) }
            )
        }
    }

    /**
     * Validates whether data is in GraphSON v3.0 format.
     */
    fun isGraphSONFormat(data: dynamic): Boolean {
        return try {
            when {
                js("typeof data === 'object' && data !== null && data.version === '3.0'") as Boolean -> true
                js("typeof data === 'object' && data !== null && data.format === 'graphson-v3'") as Boolean -> true
                js("typeof data === 'string' && data.includes('\"version\":\"3.0\"')") as Boolean -> true
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets format information for debugging and monitoring.
     */
    fun getFormatInfo(data: dynamic): dynamic {
        return try {
            val result = js("{}")
            result.isGraphSON = isGraphSONFormat(data)
            result.dataType = js("typeof data")
            result.hasVersion = js("data && data.version !== undefined")
            result.version = js("data && data.version")
            result.hasFormat = js("data && data.format !== undefined")
            result.format = js("data && data.format")
            result.size = if (js("typeof data === 'string'") as Boolean) {
                js("data.length")
            } else {
                js("JSON.stringify(data).length")
            }
            result
        } catch (e: Exception) {
            val errorResult = js("{}")
            errorResult.error = e.message
            errorResult.dataType = js("typeof data")
            errorResult
        }
    }
}

/**
 * Extension functions to make GraphSON integration seamless with existing JavaScript code.
 */
fun TinkerGraph.toGraphSONJS(): dynamic = GraphSONJSIntegration.serializeToGraphSON(this)

fun TinkerGraph.toStorageFormatJS(): dynamic = GraphSONJSIntegration.toStorageFormat(this)
