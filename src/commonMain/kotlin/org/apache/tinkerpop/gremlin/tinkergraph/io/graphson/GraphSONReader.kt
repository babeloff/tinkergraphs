package org.apache.tinkerpop.gremlin.tinkergraph.io.graphson

import kotlinx.serialization.json.*
import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.*

/**
 * GraphSON v3.0 reader implementation for deserializing TinkerGraph structures.
 *
 * This implementation follows the Apache TinkerPop GraphSON v3.0 specification
 * for deserializing graph data with proper type preservation.
 *
 * Reference: https://tinkerpop.apache.org/docs/current/dev/io/#graphson-3d0
 */
class GraphSONReader {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Deserializes a TinkerGraph from a GraphSON v3.0 JSON string.
     */
    fun readGraph(graphsonString: String): TinkerGraph {
        val graphObject = try {
            json.parseToJsonElement(graphsonString).jsonObject
        } catch (e: Exception) {
            throw GraphSONException("Failed to parse GraphSON JSON", e)
        }

        val graph = TinkerGraph.open()

        // Read vertices first
        val vertexMap = mutableMapOf<Any, TinkerVertex>()
        graphObject["vertices"]?.jsonArray?.forEach { vertexElement ->
            val vertex = readVertex(graph, vertexElement.jsonObject)
            vertexMap[vertex.id()] = vertex
        }

        // Read edges
        graphObject["edges"]?.jsonArray?.forEach { edgeElement ->
            readEdge(graph, edgeElement.jsonObject, vertexMap)
        }

        // Read variables
        graphObject["variables"]?.jsonObject?.let { variablesObject ->
            readVariables(graph.variables(), variablesObject)
        }

        return graph
    }

    /**
     * Deserializes a single vertex from GraphSON format.
     */
    fun readVertex(graphsonString: String): GraphSONVertex {
        val vertexObject = try {
            json.parseToJsonElement(graphsonString).jsonObject
        } catch (e: Exception) {
            throw GraphSONException("Failed to parse GraphSON JSON", e)
        }
        return parseGraphSONVertex(vertexObject)
    }

    /**
     * Deserializes a single edge from GraphSON format.
     */
    fun readEdge(graphsonString: String): GraphSONEdge {
        val edgeObject = try {
            json.parseToJsonElement(graphsonString).jsonObject
        } catch (e: Exception) {
            throw GraphSONException("Failed to parse GraphSON JSON", e)
        }
        return parseGraphSONEdge(edgeObject)
    }

    /**
     * Deserializes any typed value from GraphSON format.
     */
    fun readValue(graphsonString: String): Any? {
        val valueElement = try {
            json.parseToJsonElement(graphsonString)
        } catch (e: Exception) {
            throw GraphSONException("Failed to parse GraphSON JSON", e)
        }
        return readTypedValue(valueElement)
    }

    // === Private Deserialization Methods ===

    private fun readVertex(graph: TinkerGraph, vertexObject: JsonObject): TinkerVertex {
        val type = vertexObject[GraphSONTypes.TYPE_KEY]?.jsonPrimitive?.content
        if (type != GraphSONTypes.TYPE_VERTEX) {
            throw MalformedGraphSONException("Expected vertex type, got: $type")
        }

        val valueObject = vertexObject[GraphSONTypes.VALUE_KEY]?.jsonObject
            ?: throw MalformedGraphSONException("Missing value object in vertex")

        val id = readTypedValue(valueObject["id"]
            ?: throw MalformedGraphSONException("Missing vertex id"))

        val label = valueObject["label"]?.jsonPrimitive?.content
            ?: throw MalformedGraphSONException("Missing vertex label")

        val vertex = graph.addVertex("id", id, "label", label)

        // Read properties
        valueObject["properties"]?.jsonObject?.forEach { (propKey, propArrayElement) ->
            val propertyArray = propArrayElement.jsonArray
            // Use LIST cardinality if there are multiple properties with the same key
            val cardinality = if (propertyArray.size > 1) {
                VertexProperty.Cardinality.LIST
            } else {
                VertexProperty.Cardinality.SINGLE
            }
            propertyArray.forEach { propElement ->
                readVertexProperty(vertex as TinkerVertex, propElement.jsonObject, cardinality)
            }
        }

        return vertex as TinkerVertex
    }

    private fun readEdge(graph: TinkerGraph, edgeObject: JsonObject, vertexMap: Map<Any, TinkerVertex>): TinkerEdge {
        val type = edgeObject[GraphSONTypes.TYPE_KEY]?.jsonPrimitive?.content
        if (type != GraphSONTypes.TYPE_EDGE) {
            throw MalformedGraphSONException("Expected edge type, got: $type")
        }

        val valueObject = edgeObject[GraphSONTypes.VALUE_KEY]?.jsonObject
            ?: throw MalformedGraphSONException("Missing value object in edge")

        val id = readTypedValue(valueObject["id"]
            ?: throw MalformedGraphSONException("Missing edge id"))

        val label = valueObject["label"]?.jsonPrimitive?.content
            ?: throw MalformedGraphSONException("Missing edge label")

        val inVId = readTypedValue(valueObject["inV"]
            ?: throw MalformedGraphSONException("Missing inV in edge"))

        val outVId = readTypedValue(valueObject["outV"]
            ?: throw MalformedGraphSONException("Missing outV in edge"))

        val inVertex = vertexMap[inVId]
            ?: throw GraphSONException("Cannot find inVertex with id: $inVId")

        val outVertex = vertexMap[outVId]
            ?: throw GraphSONException("Cannot find outVertex with id: $outVId")

        val edge = outVertex.addEdge(label, inVertex, "id", id)

        // Read properties
        valueObject["properties"]?.jsonObject?.forEach { (propKey, propElement) ->
            val property = readProperty(propElement.jsonObject)
            edge.property(property.key, property.value.value)
        }

        return edge as TinkerEdge
    }

    private fun readVertexProperty(vertex: TinkerVertex, propertyObject: JsonObject, cardinality: VertexProperty.Cardinality = VertexProperty.Cardinality.SINGLE): VertexProperty<*> {
        val type = propertyObject[GraphSONTypes.TYPE_KEY]?.jsonPrimitive?.content
        if (type != GraphSONTypes.TYPE_VERTEX_PROPERTY) {
            throw MalformedGraphSONException("Expected vertex property type, got: $type")
        }

        val valueObject = propertyObject[GraphSONTypes.VALUE_KEY]?.jsonObject
            ?: throw MalformedGraphSONException("Missing value object in vertex property")

        val id = readTypedValue(valueObject["id"]
            ?: throw MalformedGraphSONException("Missing vertex property id"))

        val value = readTypedValue(valueObject["value"]
            ?: throw MalformedGraphSONException("Missing vertex property value"))

        val label = valueObject["label"]?.jsonPrimitive?.content
            ?: throw MalformedGraphSONException("Missing vertex property label")

        val vertexProperty = vertex.property(label, value, cardinality, "id", id)

        // Read meta properties
        valueObject["properties"]?.jsonObject?.forEach { (metaPropKey, metaPropElement) ->
            val metaProperty = readProperty(metaPropElement.jsonObject)
            vertexProperty.property(metaProperty.key, metaProperty.value.value)
        }

        return vertexProperty
    }

    private fun readProperty(propertyObject: JsonObject): GraphSONProperty {
        val type = propertyObject[GraphSONTypes.TYPE_KEY]?.jsonPrimitive?.content
        if (type != GraphSONTypes.TYPE_PROPERTY) {
            throw MalformedGraphSONException("Expected property type, got: $type")
        }

        val valueObject = propertyObject[GraphSONTypes.VALUE_KEY]?.jsonObject
            ?: throw MalformedGraphSONException("Missing value object in property")

        val key = valueObject["key"]?.jsonPrimitive?.content
            ?: throw MalformedGraphSONException("Missing property key")

        val value = readTypedValue(valueObject["value"]
            ?: throw MalformedGraphSONException("Missing property value"))

        return GraphSONProperty(key, GraphSONTypedValue(type, value))
    }

    private fun readVariables(variables: Graph.Variables, variablesObject: JsonObject) {
        variablesObject.forEach { (key, valueElement) ->
            val value = readTypedValue(valueElement)
            variables.set(key, value)
        }
    }

    private fun readTypedValue(element: JsonElement): Any? {
        return when (element) {
            is JsonObject -> {
                val type = element[GraphSONTypes.TYPE_KEY]?.jsonPrimitive?.content
                    ?: throw MalformedGraphSONException("Missing type in typed value")
                val value = element[GraphSONTypes.VALUE_KEY]

                when (type) {
                    GraphSONTypes.TYPE_NULL -> null

                    GraphSONTypes.TYPE_INT32 -> {
                        val primitive = value?.jsonPrimitive
                        when {
                            primitive == null -> throw MalformedGraphSONException("Invalid Int32 value")
                            primitive.isString -> throw MalformedGraphSONException("Invalid Int32 value")
                            else -> try {
                                // Try int first, fall back to double for JavaScript compatibility
                                primitive.int
                            } catch (e: NumberFormatException) {
                                try {
                                    primitive.double.toInt()
                                } catch (e2: Exception) {
                                    throw MalformedGraphSONException("Invalid Int32 value: ${primitive.content}")
                                }
                            }
                        }
                    }

                    GraphSONTypes.TYPE_INT64 -> {
                        val primitive = value?.jsonPrimitive
                        when {
                            primitive == null -> throw MalformedGraphSONException("Invalid Int64 value")
                            primitive.isString -> throw MalformedGraphSONException("Invalid Int64 value")
                            else -> try {
                                // Try long first, fall back to double for JavaScript compatibility
                                primitive.long
                            } catch (e: NumberFormatException) {
                                try {
                                    primitive.double.toLong()
                                } catch (e2: Exception) {
                                    throw MalformedGraphSONException("Invalid Int64 value: ${primitive.content}")
                                }
                            }
                        }
                    }

                    GraphSONTypes.TYPE_FLOAT -> value?.jsonPrimitive?.float
                        ?: throw MalformedGraphSONException("Invalid Float value")

                    GraphSONTypes.TYPE_DOUBLE -> value?.jsonPrimitive?.double
                        ?: throw MalformedGraphSONException("Invalid Double value")

                    GraphSONTypes.TYPE_BOOLEAN -> value?.jsonPrimitive?.boolean
                        ?: throw MalformedGraphSONException("Invalid Boolean value")

                    GraphSONTypes.TYPE_STRING -> value?.jsonPrimitive?.content
                        ?: throw MalformedGraphSONException("Invalid String value")

                    GraphSONTypes.TYPE_BYTE -> {
                        val primitive = value?.jsonPrimitive
                        when {
                            primitive == null -> throw MalformedGraphSONException("Invalid Byte value")
                            primitive.isString -> throw MalformedGraphSONException("Invalid Byte value")
                            else -> {
                                val content = primitive.content
                                try {
                                    if (content.contains('.')) {
                                        // Handle decimal representation (e.g., "127.0")
                                        content.toDouble().toInt().toByte()
                                    } else {
                                        // Handle integer representation
                                        content.toInt().toByte()
                                    }
                                } catch (e: NumberFormatException) {
                                    throw MalformedGraphSONException("Invalid Byte value: $content")
                                }
                            }
                        }
                    }

                    GraphSONTypes.TYPE_SHORT -> {
                        val primitive = value?.jsonPrimitive
                        when {
                            primitive == null -> throw MalformedGraphSONException("Invalid Short value")
                            primitive.isString -> throw MalformedGraphSONException("Invalid Short value")
                            else -> {
                                val content = primitive.content
                                try {
                                    if (content.contains('.')) {
                                        // Handle decimal representation (e.g., "32767.0")
                                        content.toDouble().toInt().toShort()
                                    } else {
                                        // Handle integer representation
                                        content.toInt().toShort()
                                    }
                                } catch (e: NumberFormatException) {
                                    throw MalformedGraphSONException("Invalid Short value: $content")
                                }
                            }
                        }
                    }

                    GraphSONTypes.TYPE_LIST -> {
                        val arrayValue = value?.jsonArray
                            ?: throw MalformedGraphSONException("Invalid List value")
                        arrayValue.map { readTypedValue(it) }
                    }

                    GraphSONTypes.TYPE_SET -> {
                        val arrayValue = value?.jsonArray
                            ?: throw MalformedGraphSONException("Invalid Set value")
                        arrayValue.map { readTypedValue(it) }.toSet()
                    }

                    GraphSONTypes.TYPE_MAP -> {
                        val arrayValue = value?.jsonArray
                            ?: throw MalformedGraphSONException("Invalid Map value")
                        val map = mutableMapOf<Any?, Any?>()
                        var i = 0
                        while (i < arrayValue.size - 1) {
                            val key = readTypedValue(arrayValue[i])
                            val mapValue = readTypedValue(arrayValue[i + 1])
                            map[key] = mapValue
                            i += 2
                        }
                        map
                    }

                    GraphSONTypes.TYPE_BLOB -> {
                        val stringValue = value?.jsonPrimitive?.content
                            ?: throw MalformedGraphSONException("Invalid Blob value")
                        stringValue.split(",").map { it.toByte() }.toByteArray()
                    }

                    GraphSONTypes.TYPE_DIRECTION -> {
                        val directionValue = value?.jsonPrimitive?.content
                            ?: throw MalformedGraphSONException("Invalid Direction value")
                        when (directionValue) {
                            GraphSONTypes.DIRECTION_OUT -> Direction.OUT
                            GraphSONTypes.DIRECTION_IN -> Direction.IN
                            GraphSONTypes.DIRECTION_BOTH -> Direction.BOTH
                            else -> throw MalformedGraphSONException("Unknown direction: $directionValue")
                        }
                    }

                    GraphSONTypes.TYPE_CARDINALITY -> {
                        val cardinalityValue = value?.jsonPrimitive?.content
                            ?: throw MalformedGraphSONException("Invalid Cardinality value")
        when (cardinalityValue) {
            GraphSONTypes.CARDINALITY_SINGLE -> "single"
            GraphSONTypes.CARDINALITY_LIST -> "list"
            GraphSONTypes.CARDINALITY_SET -> "set"
            else -> throw MalformedGraphSONException("Unknown cardinality: $cardinalityValue")
        }
                    }

                    else -> throw UnsupportedGraphSONTypeException(type ?: "null")
                }
            }

            is JsonPrimitive -> {
                // Handle untyped primitives as strings
                element.contentOrNull
            }

            is JsonArray -> {
                // Handle untyped arrays as lists
                element.map { readTypedValue(it) }
            }

            else -> null
        }
    }

    private fun parseGraphSONVertex(vertexObject: JsonObject): GraphSONVertex {
        val type = vertexObject[GraphSONTypes.TYPE_KEY]?.jsonPrimitive?.content
        if (type != GraphSONTypes.TYPE_VERTEX) {
            throw MalformedGraphSONException("Expected vertex type, got: $type")
        }

        val valueObject = vertexObject[GraphSONTypes.VALUE_KEY]?.jsonObject
            ?: throw MalformedGraphSONException("Missing value object in vertex")

        val id = parseGraphSONTypedValue(valueObject["id"]
            ?: throw MalformedGraphSONException("Missing vertex id"))

        val label = valueObject["label"]?.jsonPrimitive?.content
            ?: throw MalformedGraphSONException("Missing vertex label")

        val properties = mutableMapOf<String, List<GraphSONVertexProperty>>()
        valueObject["properties"]?.jsonObject?.forEach { (propKey, propArrayElement) ->
            val propList = mutableListOf<GraphSONVertexProperty>()
            propArrayElement.jsonArray.forEach { propElement ->
                propList.add(parseGraphSONVertexProperty(propElement.jsonObject))
            }
            properties[propKey] = propList
        }

        return GraphSONVertex(id, label, properties.takeIf { it.isNotEmpty() })
    }

    private fun parseGraphSONEdge(edgeObject: JsonObject): GraphSONEdge {
        val type = edgeObject[GraphSONTypes.TYPE_KEY]?.jsonPrimitive?.content
        if (type != GraphSONTypes.TYPE_EDGE) {
            throw MalformedGraphSONException("Expected edge type, got: $type")
        }

        val valueObject = edgeObject[GraphSONTypes.VALUE_KEY]?.jsonObject
            ?: throw MalformedGraphSONException("Missing value object in edge")

        val id = parseGraphSONTypedValue(valueObject["id"]
            ?: throw MalformedGraphSONException("Missing edge id"))

        val label = valueObject["label"]?.jsonPrimitive?.content
            ?: throw MalformedGraphSONException("Missing edge label")

        val inV = parseGraphSONTypedValue(valueObject["inV"]
            ?: throw MalformedGraphSONException("Missing inV in edge"))

        val outV = parseGraphSONTypedValue(valueObject["outV"]
            ?: throw MalformedGraphSONException("Missing outV in edge"))

        val inVLabel = valueObject["inVLabel"]?.jsonPrimitive?.content
            ?: throw MalformedGraphSONException("Missing inVLabel in edge")

        val outVLabel = valueObject["outVLabel"]?.jsonPrimitive?.content
            ?: throw MalformedGraphSONException("Missing outVLabel in edge")

        val properties = mutableMapOf<String, GraphSONProperty>()
        valueObject["properties"]?.jsonObject?.forEach { (propKey, propElement) ->
            properties[propKey] = parseGraphSONProperty(propElement.jsonObject)
        }

        return GraphSONEdge(id, label, inV, outV, inVLabel, outVLabel, properties.takeIf { it.isNotEmpty() })
    }

    private fun parseGraphSONVertexProperty(propertyObject: JsonObject): GraphSONVertexProperty {
        val type = propertyObject[GraphSONTypes.TYPE_KEY]?.jsonPrimitive?.content
        if (type != GraphSONTypes.TYPE_VERTEX_PROPERTY) {
            throw MalformedGraphSONException("Expected vertex property type, got: $type")
        }

        val valueObject = propertyObject[GraphSONTypes.VALUE_KEY]?.jsonObject
            ?: throw MalformedGraphSONException("Missing value object in vertex property")

        val id = parseGraphSONTypedValue(valueObject["id"]
            ?: throw MalformedGraphSONException("Missing vertex property id"))

        val value = parseGraphSONTypedValue(valueObject["value"]
            ?: throw MalformedGraphSONException("Missing vertex property value"))

        val label = valueObject["label"]?.jsonPrimitive?.content
            ?: throw MalformedGraphSONException("Missing vertex property label")

        val properties = mutableMapOf<String, GraphSONProperty>()
        valueObject["properties"]?.jsonObject?.forEach { (metaPropKey, metaPropElement) ->
            properties[metaPropKey] = parseGraphSONProperty(metaPropElement.jsonObject)
        }

        return GraphSONVertexProperty(id, value, label, properties.takeIf { it.isNotEmpty() })
    }

    private fun parseGraphSONProperty(propertyObject: JsonObject): GraphSONProperty {
        val type = propertyObject[GraphSONTypes.TYPE_KEY]?.jsonPrimitive?.content
        if (type != GraphSONTypes.TYPE_PROPERTY) {
            throw MalformedGraphSONException("Expected property type, got: $type")
        }

        val valueObject = propertyObject[GraphSONTypes.VALUE_KEY]?.jsonObject
            ?: throw MalformedGraphSONException("Missing value object in property")

        val key = valueObject["key"]?.jsonPrimitive?.content
            ?: throw MalformedGraphSONException("Missing property key")

        val value = parseGraphSONTypedValue(valueObject["value"]
            ?: throw MalformedGraphSONException("Missing property value"))

        return GraphSONProperty(key, value)
    }

    private fun parseGraphSONTypedValue(element: JsonElement): GraphSONTypedValue {
        return when (element) {
            is JsonObject -> {
                val type = element[GraphSONTypes.TYPE_KEY]?.jsonPrimitive?.content
                    ?: throw MalformedGraphSONException("Missing type in typed value")
                val value = readTypedValue(element)
                GraphSONTypedValue(type, value)
            }
            else -> throw MalformedGraphSONException("Expected typed value object")
        }
    }
}
