package org.apache.tinkerpop.gremlin.tinkergraph.io.graphson

import kotlinx.serialization.json.*
import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.*

/**
 * GraphSON v3.0 writer implementation for serializing TinkerGraph structures.
 *
 * This implementation follows the Apache TinkerPop GraphSON v3.0 specification
 * for serializing graph data with proper type preservation.
 *
 * Reference: https://tinkerpop.apache.org/docs/current/dev/io/#graphson-3d0
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class GraphSONWriter {

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /**
     * Serializes a TinkerGraph to a GraphSON v3.0 JSON string.
     */
    fun writeGraph(graph: TinkerGraph): String {
        val graphObject = buildJsonObject {
            put("version", GraphSONTypes.VERSION)
            put("vertices", writeVertices(graph))
            put("edges", writeEdges(graph))
            if (graph.variables().keys().isNotEmpty()) {
                put("variables", writeVariables(graph.variables()))
            }
        }

        return json.encodeToString(JsonObject.serializer(), graphObject)
    }

    /**
     * Serializes a single vertex to GraphSON format.
     */
    fun writeVertex(vertex: Vertex): String {
        val vertexObject = writeVertexObject(vertex)
        return json.encodeToString(JsonObject.serializer(), vertexObject)
    }

    /**
     * Serializes a single edge to GraphSON format.
     */
    fun writeEdge(edge: Edge): String {
        val edgeObject = writeEdgeObject(edge)
        return json.encodeToString(JsonObject.serializer(), edgeObject)
    }

    /**
     * Serializes any value to GraphSON typed format.
     */
    fun writeValue(value: Any?): String {
        val typedValue = writeTypedValue(value)
        return json.encodeToString(JsonElement.serializer(), typedValue)
    }

    // === Private Serialization Methods ===

    private fun writeVertices(graph: TinkerGraph): JsonArray {
        return buildJsonArray {
            graph.vertices().forEach { vertex ->
                add(writeVertexObject(vertex))
            }
        }
    }

    private fun writeEdges(graph: TinkerGraph): JsonArray {
        return buildJsonArray {
            graph.edges().forEach { edge ->
                add(writeEdgeObject(edge))
            }
        }
    }

    private fun writeVertexObject(vertex: Vertex): JsonObject {
        return buildJsonObject {
            put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_VERTEX)
            put(GraphSONTypes.VALUE_KEY, buildJsonObject {
                put("id", writeTypedValue(vertex.id()))
                put("label", vertex.label())

                // Write properties
                val properties = mutableMapOf<String, JsonArray>()
                vertex.properties<Any>().forEach { property ->
                    val key = property.key()
                    if (properties[key] == null) {
                        properties[key] = buildJsonArray {}
                    }
                    properties[key] = buildJsonArray {
                        properties[key]!!.forEach { add(it) }
                        add(writeVertexPropertyObject(property))
                    }
                }

                if (properties.isNotEmpty()) {
                    put("properties", buildJsonObject {
                        properties.forEach { (key, propArray) ->
                            put(key, propArray)
                        }
                    })
                }
            })
        }
    }

    private fun writeEdgeObject(edge: Edge): JsonObject {
        return buildJsonObject {
            put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_EDGE)
            put(GraphSONTypes.VALUE_KEY, buildJsonObject {
                put("id", writeTypedValue(edge.id()))
                put("label", edge.label())
                put("inV", writeTypedValue(edge.inVertex().id()))
                put("outV", writeTypedValue(edge.outVertex().id()))
                put("inVLabel", edge.inVertex().label())
                put("outVLabel", edge.outVertex().label())

                // Write properties
                val properties = mutableMapOf<String, JsonElement>()
                edge.properties<Any>().forEach { property ->
                    properties[property.key()] = writePropertyObject(property)
                }

                if (properties.isNotEmpty()) {
                    put("properties", buildJsonObject {
                        properties.forEach { (key, value) ->
                            put(key, value)
                        }
                    })
                }
            })
        }
    }

    private fun writeVertexPropertyObject(property: VertexProperty<*>): JsonObject {
        return buildJsonObject {
            put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_VERTEX_PROPERTY)
            put(GraphSONTypes.VALUE_KEY, buildJsonObject {
                put("id", writeTypedValue(property.id()))
                put("value", writeTypedValue(property.value()))
                put("label", property.key())

                // Write meta properties
                val metaProperties = mutableMapOf<String, JsonElement>()
                property.properties<Any>().forEach { metaProp ->
                    metaProperties[metaProp.key()] = writePropertyObject(metaProp)
                }

                if (metaProperties.isNotEmpty()) {
                    put("properties", buildJsonObject {
                        metaProperties.forEach { (key, value) ->
                            put(key, value)
                        }
                    })
                }
            })
        }
    }

    private fun writePropertyObject(property: Property<*>): JsonObject {
        return buildJsonObject {
            put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_PROPERTY)
            put(GraphSONTypes.VALUE_KEY, buildJsonObject {
                put("key", property.key())
                put("value", writeTypedValue(property.value()))
            })
        }
    }

    private fun writeVariables(variables: Graph.Variables): JsonObject {
        return buildJsonObject {
            variables.keys().forEach { key ->
                put(key, writeTypedValue(variables.get<Any?>(key)))
            }
        }
    }

    private fun writeTypedValue(value: Any?): JsonElement {
        return when (value) {
            null -> buildJsonObject {
                put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_NULL)
                put(GraphSONTypes.VALUE_KEY, JsonNull)
            }

            // Check more specific types first (Double, Float) before general types (Int)
            // This is critical for JavaScript platform where all numeric types return true for multiple type checks
            is Double -> buildJsonObject {
                put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_DOUBLE)
                put(GraphSONTypes.VALUE_KEY, value)
            }

            is Float -> buildJsonObject {
                put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_FLOAT)
                put(GraphSONTypes.VALUE_KEY, value)
            }

            is Long -> buildJsonObject {
                put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_INT64)
                put(GraphSONTypes.VALUE_KEY, value)
            }

            is Int -> buildJsonObject {
                put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_INT32)
                put(GraphSONTypes.VALUE_KEY, value)
            }

            is Boolean -> buildJsonObject {
                put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_BOOLEAN)
                put(GraphSONTypes.VALUE_KEY, value)
            }

            is String -> buildJsonObject {
                // Handle cardinality strings specially
                if (value == "single" || value == "list" || value == "set") {
                    put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_CARDINALITY)
                    put(GraphSONTypes.VALUE_KEY, value)
                } else {
                    put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_STRING)
                    put(GraphSONTypes.VALUE_KEY, value)
                }
            }

            is Byte -> buildJsonObject {
                put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_BYTE)
                put(GraphSONTypes.VALUE_KEY, value.toInt())
            }

            is Short -> buildJsonObject {
                put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_SHORT)
                put(GraphSONTypes.VALUE_KEY, value.toInt())
            }

            is List<*> -> buildJsonObject {
                put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_LIST)
                put(GraphSONTypes.VALUE_KEY, buildJsonArray {
                    value.forEach { item ->
                        add(writeTypedValue(item))
                    }
                })
            }

            is Set<*> -> buildJsonObject {
                put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_SET)
                put(GraphSONTypes.VALUE_KEY, buildJsonArray {
                    value.forEach { item ->
                        add(writeTypedValue(item))
                    }
                })
            }

            is Map<*, *> -> buildJsonObject {
                put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_MAP)
                put(GraphSONTypes.VALUE_KEY, buildJsonArray {
                    value.entries.forEach { (key, mapValue) ->
                        add(writeTypedValue(key))
                        add(writeTypedValue(mapValue))
                    }
                })
            }

            is ByteArray -> buildJsonObject {
                put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_BLOB)
                put(GraphSONTypes.VALUE_KEY, value.joinToString(",") { it.toString() })
            }

            // For vertices and edges, just write their ID reference
            is Vertex -> writeTypedValue(value.id())
            is Edge -> writeTypedValue(value.id())

            // Direction enum
            is Direction -> buildJsonObject {
                put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_DIRECTION)
                put(GraphSONTypes.VALUE_KEY, value.toString())
            }

            // For other types, convert to string representation
            else -> buildJsonObject {
                put(GraphSONTypes.TYPE_KEY, GraphSONTypes.TYPE_STRING)
                put(GraphSONTypes.VALUE_KEY, value.toString())
            }
        }
    }
}
