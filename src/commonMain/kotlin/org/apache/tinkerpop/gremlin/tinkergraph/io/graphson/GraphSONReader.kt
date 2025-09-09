package org.apache.tinkerpop.gremlin.tinkergraph.io.graphson

import kotlinx.serialization.json.*
import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.util.LoggingConfig

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

    private val logger = LoggingConfig.getLogger<GraphSONReader>()

    /**
     * Deserializes a TinkerGraph from a GraphSON v3.0 JSON string.
     */
    fun readGraph(graphsonString: String): TinkerGraph {
        return readGraph(graphsonString, IdConflictStrategy.DEFAULT)
    }

    /**
     * Deserializes a TinkerGraph from a GraphSON v3.0 JSON string with specified ID conflict strategy.
     */
    fun readGraph(graphsonString: String, idConflictStrategy: IdConflictStrategy): TinkerGraph {
        val graphObject = try {
            json.parseToJsonElement(graphsonString).jsonObject
        } catch (e: Exception) {
            throw GraphSONException("Failed to parse GraphSON JSON", e)
        }

        val graph = TinkerGraph.open()
        return readGraphInto(graphsonString, graph, idConflictStrategy)
    }

    /**
     * Deserializes GraphSON v3.0 data into an existing TinkerGraph.
     */
    fun readGraphInto(graphsonString: String, targetGraph: TinkerGraph, idConflictStrategy: IdConflictStrategy): TinkerGraph {
        val graphObject = try {
            json.parseToJsonElement(graphsonString).jsonObject
        } catch (e: Exception) {
            throw GraphSONException("Failed to parse GraphSON JSON", e)
        }

        logger.i { "Reading GraphSON into graph with ${targetGraph.vertices().asSequence().count()} existing vertices using strategy: ${idConflictStrategy.name}" }

        // Track ID remapping for edges that reference remapped vertices
        val vertexIdRemapping = mutableMapOf<Any, Any>()
        val vertexMap = mutableMapOf<Any, TinkerVertex>()

        // Read vertices first
        graphObject["vertices"]?.jsonArray?.forEach { vertexElement ->
            val vertex = readVertex(targetGraph, vertexElement.jsonObject, idConflictStrategy, vertexIdRemapping)
            vertexMap[vertex.id()] = vertex
        }

        // Read edges
        graphObject["edges"]?.jsonArray?.forEach { edgeElement ->
            readEdge(targetGraph, edgeElement.jsonObject, vertexMap, vertexIdRemapping, idConflictStrategy)
        }

        // Read variables
        graphObject["variables"]?.jsonObject?.let { variablesObject ->
            readVariables(targetGraph.variables(), variablesObject)
        }

        logger.i { "Completed GraphSON import. Graph now has ${targetGraph.vertices().asSequence().count()} vertices and ${targetGraph.edges().asSequence().count()} edges" }
        return targetGraph
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
        return readVertex(graph, vertexObject, IdConflictStrategy.DEFAULT, mutableMapOf())
    }

    private fun readVertex(
        graph: TinkerGraph,
        vertexObject: JsonObject,
        idConflictStrategy: IdConflictStrategy,
        vertexIdRemapping: MutableMap<Any, Any>
    ): TinkerVertex {
        val type = vertexObject[GraphSONTypes.TYPE_KEY]?.jsonPrimitive?.content
        if (type != GraphSONTypes.TYPE_VERTEX) {
            throw MalformedGraphSONException("Expected vertex type, got: $type")
        }

        val valueObject = vertexObject[GraphSONTypes.VALUE_KEY]?.jsonObject
            ?: throw MalformedGraphSONException("Missing value object in vertex")

        val id = readTypedValue(valueObject["id"]
            ?: throw MalformedGraphSONException("Missing vertex id"))
            ?: throw MalformedGraphSONException("Vertex id cannot be null")

        val label = valueObject["label"]?.jsonPrimitive?.content
            ?: throw MalformedGraphSONException("Missing vertex label")

        val vertex = createVertexWithConflictResolution(graph, id, label, idConflictStrategy, vertexIdRemapping)

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
        return readEdge(graph, edgeObject, vertexMap, mutableMapOf(), IdConflictStrategy.DEFAULT)
    }

    private fun readEdge(
        graph: TinkerGraph,
        edgeObject: JsonObject,
        vertexMap: Map<Any, TinkerVertex>,
        vertexIdRemapping: Map<Any, Any>,
        idConflictStrategy: IdConflictStrategy
    ): TinkerEdge {
        val type = edgeObject[GraphSONTypes.TYPE_KEY]?.jsonPrimitive?.content
        if (type != GraphSONTypes.TYPE_EDGE) {
            throw MalformedGraphSONException("Expected edge type, got: $type")
        }

        val valueObject = edgeObject[GraphSONTypes.VALUE_KEY]?.jsonObject
            ?: throw MalformedGraphSONException("Missing value object in edge")

        val id = readTypedValue(valueObject["id"]
            ?: throw MalformedGraphSONException("Missing edge id"))
            ?: throw MalformedGraphSONException("Edge id cannot be null")

        val label = valueObject["label"]?.jsonPrimitive?.content
            ?: throw MalformedGraphSONException("Missing edge label")

        val originalInVId = readTypedValue(valueObject["inV"]
            ?: throw MalformedGraphSONException("Missing inV in edge"))
            ?: throw MalformedGraphSONException("InV id cannot be null")

        val originalOutVId = readTypedValue(valueObject["outV"]
            ?: throw MalformedGraphSONException("Missing outV in edge"))
            ?: throw MalformedGraphSONException("OutV id cannot be null")

        // Apply vertex ID remapping if vertices were remapped during conflict resolution
        val inVId = vertexIdRemapping[originalInVId] ?: originalInVId
        val outVId = vertexIdRemapping[originalOutVId] ?: originalOutVId

        val inVertex = vertexMap[inVId]
            ?: graph.vertex(inVId) as? TinkerVertex
            ?: throw GraphSONException("Cannot find inVertex with id: $inVId")

        val outVertex = vertexMap[outVId]
            ?: graph.vertex(outVId) as? TinkerVertex
            ?: throw GraphSONException("Cannot find outVertex with id: $outVId")

        val edge = createEdgeWithConflictResolution(outVertex, inVertex, label, id, idConflictStrategy)

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

    /**
     * Creates a vertex with conflict resolution based on the specified strategy.
     */
    private fun createVertexWithConflictResolution(
            graph: TinkerGraph,
            originalId: Any,
            label: String,
            strategy: IdConflictStrategy,
            vertexIdRemapping: MutableMap<Any, Any>
        ): TinkerVertex {
            val existingVertex = graph.vertex(originalId) as? TinkerVertex

            if (existingVertex == null) {
                // No conflict - create vertex with original ID
                logger.d { "Creating vertex with original ID: $originalId" }
                return graph.addVertex("id", originalId, "label", label) as TinkerVertex
            }

            // Handle conflict based on strategy
            return when (strategy) {
                IdConflictStrategy.STRICT -> {
                    logger.w { "Vertex ID conflict: $originalId (STRICT mode)" }
                    throw Graph.Exceptions.vertexWithIdAlreadyExists(originalId)
                }

                IdConflictStrategy.GENERATE_NEW_ID -> {
                    val newId = (graph as TinkerGraph).getNextId()
                    vertexIdRemapping[originalId] = newId
                    logger.i { "Vertex ID conflict resolved: $originalId -> $newId (GENERATE_NEW_ID)" }
                    graph.addVertex("id", newId, "label", label) as TinkerVertex
                }

                IdConflictStrategy.MERGE_PROPERTIES -> {
                    logger.i { "Vertex ID conflict resolved: merging properties for ID $originalId (MERGE_PROPERTIES)" }
                    // Return existing vertex - properties will be merged in readVertex
                    existingVertex
                }

                IdConflictStrategy.REPLACE_ELEMENT -> {
                    logger.i { "Vertex ID conflict resolved: replacing vertex with ID $originalId (REPLACE_ELEMENT)" }
                    // Remove existing vertex and all its edges
                    existingVertex.edges(org.apache.tinkerpop.gremlin.structure.Direction.BOTH).asSequence().toList().forEach { edge ->
                        edge.remove()
                    }
                    existingVertex.remove()
                    // Create new vertex with same ID
                    graph.addVertex("id", originalId, "label", label) as TinkerVertex
                }
            }
        }

    /**
     * Creates an edge with conflict resolution based on the specified strategy.
     */
    private fun createEdgeWithConflictResolution(
            outVertex: TinkerVertex,
            inVertex: TinkerVertex,
            label: String,
            originalId: Any,
            strategy: IdConflictStrategy
        ): TinkerEdge {
        val graph = outVertex.graph() as TinkerGraph
        val existingEdge = graph.edge(originalId) as? TinkerEdge

        if (existingEdge == null) {
            // No conflict - create edge with original ID
            logger.d { "Creating edge with original ID: $originalId" }
            return outVertex.addEdge(label, inVertex, "id", originalId) as TinkerEdge
        }

        // Handle conflict based on strategy
        return when (strategy) {
            IdConflictStrategy.STRICT -> {
                logger.w { "Edge ID conflict: $originalId (STRICT mode)" }
                throw Graph.Exceptions.edgeWithIdAlreadyExists(originalId)
            }

            IdConflictStrategy.GENERATE_NEW_ID -> {
                val newId = graph.getNextId()
                logger.i { "Edge ID conflict resolved: $originalId -> $newId (GENERATE_NEW_ID)" }
                outVertex.addEdge(label, inVertex, "id", newId) as TinkerEdge
            }

            IdConflictStrategy.MERGE_PROPERTIES -> {
                logger.i { "Edge ID conflict resolved: merging properties for ID $originalId (MERGE_PROPERTIES)" }
                // Return existing edge - properties will be merged in readEdge
                existingEdge
            }

            IdConflictStrategy.REPLACE_ELEMENT -> {
                logger.i { "Edge ID conflict resolved: replacing edge with ID $originalId (REPLACE_ELEMENT)" }
                // Remove existing edge and create new one
                existingEdge.remove()
                outVertex.addEdge(label, inVertex, "id", originalId) as TinkerEdge
            }
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
