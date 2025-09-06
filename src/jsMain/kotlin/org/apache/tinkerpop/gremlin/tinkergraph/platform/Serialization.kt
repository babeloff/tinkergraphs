package org.apache.tinkerpop.gremlin.tinkergraph.platform

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.util.SafeCasting
import kotlin.js.Promise

/**
 * Clean serialization utilities for TinkerGraph in JavaScript environments.
 *
 * This provides a simple, efficient way to serialize and deserialize TinkerGraph
 * instances to/from JavaScript-native JSON format, optimized for storage and
 * network transmission.
 */
object GraphSerializer {

    private const val FORMAT_VERSION = "1.0"

    /**
     * Serialize a TinkerGraph to a JavaScript object.
     *
     * @param graph The graph to serialize
     * @return A JavaScript object containing all graph data
     */
    fun serialize(graph: TinkerGraph): dynamic {
        val result = js("{}")

        result._version = FORMAT_VERSION
        result._type = "TinkerGraph"
        result._timestamp = JSDate().toISOString()

        // Serialize vertices
        result.vertices = js("[]")
        graph.vertices().forEach { vertex ->
            result.vertices.push(serializeVertex(vertex as TinkerVertex))
        }

        // Serialize edges
        result.edges = js("[]")
        graph.edges().forEach { edge ->
            result.edges.push(serializeEdge(edge as TinkerEdge))
        }

        // Serialize variables
        result.variables = serializeVariables(graph.variables())

        return result
    }

    /**
     * Deserialize a TinkerGraph from a JavaScript object.
     *
     * @param data The JavaScript object containing graph data
     * @return Promise that resolves to the deserialized graph
     */
    fun deserialize(data: dynamic): Promise<TinkerGraph> {
        return Promise { resolve, reject ->
            try {
                validateFormat(data)

                val graph = TinkerGraph.open()

                // Create a map to track vertices by ID for edge creation
                val vertexMap = mutableMapOf<String, TinkerVertex>()

                // Deserialize vertices first
                if (data.vertices != null) {
                    val verticesArray = data.vertices.unsafeCast<Array<dynamic>>()
                    verticesArray.forEach { vertexData ->
                        val vertex = deserializeVertex(graph, vertexData)
                        vertexMap[vertex.id().toString()] = vertex
                    }
                }

                // Deserialize edges
                if (data.edges != null) {
                    val edgesArray = data.edges.unsafeCast<Array<dynamic>>()
                    edgesArray.forEach { edgeData ->
                        deserializeEdge(graph, edgeData, vertexMap)
                    }
                }

                // Deserialize variables
                if (data.variables != null) {
                    deserializeVariables(graph.variables(), data.variables)
                }

                resolve(graph)

            } catch (e: Exception) {
                reject(StorageException("Failed to deserialize graph: ${e.message}", e))
            }
        }
    }

    /**
     * Serialize to a JSON string.
     */
    fun toJson(graph: TinkerGraph): String {
        return JSON.stringify(serialize(graph))
    }

    /**
     * Deserialize from a JSON string.
     */
    fun fromJson(json: String): Promise<TinkerGraph> {
        return Promise { resolve, reject ->
            try {
                val data = JSON.parse(json)
                deserialize(data).then(
                    onFulfilled = { graph -> resolve(graph) },
                    onRejected = { error -> reject(error) }
                )
            } catch (e: Exception) {
                reject(StorageException("Failed to parse JSON: ${e.message}", e))
            }
        }
    }

    // === Private Serialization Methods ===

    private fun serializeVertex(vertex: TinkerVertex): dynamic {
        val result = js("{}")

        result.id = serializeId(vertex.id())
        result.label = vertex.label()
        result.properties = js("[]")

        // Group properties by key (handling multi-properties)
        val propertyGroups = mutableMapOf<String, MutableList<TinkerVertexProperty<*>>>()
        vertex.properties<Any>().forEach { prop ->
            val key = prop.key()
            val tinkerProp = SafeCasting.asTinkerVertexProperty(prop)
            if (tinkerProp != null) {
                if (!propertyGroups.containsKey(key)) {
                    propertyGroups[key] = mutableListOf()
                }
                @Suppress("UNCHECKED_CAST")
                propertyGroups[key]!!.add(tinkerProp as TinkerVertexProperty<*>)
            }
        }

        // Serialize property groups
        propertyGroups.forEach { (key, properties) ->
            val propGroup = js("{}")
            propGroup.key = key
            propGroup.values = js("[]")

            properties.forEach { prop ->
                val propValue = js("{}")
                val propValueData = prop.value()
                if (propValueData != null) {
                    propValue.value = serializeValue(propValueData)
                }

                // Add meta-properties if any
                val metaProps = js("{}")
                var hasMetaProps = false
                prop.properties<Any>().forEach { metaProp ->
                    metaProps[metaProp.key()] = serializeValue(metaProp.value())
                    hasMetaProps = true
                }

                if (hasMetaProps) {
                    propValue.properties = metaProps
                }

                propGroup.values.push(propValue)
            }

            result.properties.push(propGroup)
        }

        return result
    }

    private fun serializeEdge(edge: TinkerEdge): dynamic {
        val result = js("{}")

        val edgeId = edge.id()
        if (edgeId != null) {
            result.id = serializeId(edgeId)
        }
        result.label = edge.label()
        val outVertexId = edge.outVertex().id()
        if (outVertexId != null) {
            result.outV = serializeId(outVertexId)
        }
        val inVertexId = edge.inVertex().id()
        if (inVertexId != null) {
            result.inV = serializeId(inVertexId)
        }
        result.properties = js("{}")

        // Serialize edge properties
        edge.properties<Any>().forEach { prop ->
            val value = prop.value()
            if (value != null) {
                result.properties[prop.key()] = serializeValue(value)
            }
        }

        return result
    }

    private fun serializeVariables(variables: Graph.Variables): dynamic {
        val result = js("{}")

        variables.keys().forEach { key ->
            try {
                val optionalValue = variables.get<Any>(key)
                if (optionalValue != null && js("typeof optionalValue.get === 'function'").unsafeCast<Boolean>()) {
                    val value = optionalValue.asDynamic().get()
                    if (value != null) {
                        result[key] = serializeValue(value)
                    }
                }
            } catch (e: Exception) {
                // Skip variables that cannot be serialized
                console.warn("Could not serialize variable '$key': ${e.message}")
            }
        }

        return result
    }

    private fun serializeId(id: Any): dynamic {
        return when (id) {
            is String -> js("{ type: 'string', value: id }")
            is Int -> js("{ type: 'int', value: id }")
            is Long -> js("{ type: 'long', value: id.toString() }")
            is Double -> js("{ type: 'double', value: id }")
            else -> js("{ type: 'string', value: id.toString() }")
        }
    }

    private fun serializeValue(value: Any?): dynamic {
        return when (value) {
            null -> null
            is String -> value
            is Boolean -> value
            is Int -> value
            is Long -> value.toDouble() // JavaScript only has Number type
            is Float -> value.toDouble()
            is Double -> value
            is Array<*> -> {
                val array = js("[]")
                value.forEach { item ->
                    array.push(serializeValue(item))
                }
                array
            }
            is Collection<*> -> {
                val array = js("[]")
                value.forEach { item ->
                    array.push(serializeValue(item))
                }
                array
            }
            is Map<*, *> -> {
                val obj = js("{}")
                value.forEach { (k, v) ->
                    obj[k.toString()] = serializeValue(v)
                }
                obj
            }
            else -> value.toString()
        }
    }

    // === Private Deserialization Methods ===

    private fun deserializeVertex(graph: TinkerGraph, data: dynamic): TinkerVertex {
        val id = deserializeId(data.id)
        val label = data.label.unsafeCast<String>()

        val vertex = graph.addVertex(label) as TinkerVertex

        // Set vertex ID if possible (this would require TinkerGraph modification)
        // For now, we use the auto-generated ID

        // Deserialize properties
        if (data.properties != null) {
            val propertiesArray = data.properties.unsafeCast<Array<dynamic>>()
            propertiesArray.forEach { propGroup ->
                val key = propGroup.key.unsafeCast<String>()
                val values = propGroup.values.unsafeCast<Array<dynamic>>()

                values.forEach { propValue ->
                    val value = deserializeValue(propValue.value)
                    val vertexProp = vertex.property(key, value)

                    // Add meta-properties if any
                    if (propValue.properties != null) {
                        val metaPropsObj = propValue.properties
                        val metaKeys = js("Object.keys(metaPropsObj)").unsafeCast<Array<String>>()
                        metaKeys.forEach { metaKey ->
                            val metaValue = deserializeValue(metaPropsObj[metaKey])
                            vertexProp.property(metaKey, metaValue)
                        }
                    }
                }
            }
        }

        return vertex
    }

    private fun deserializeEdge(graph: TinkerGraph, data: dynamic, vertexMap: Map<String, TinkerVertex>) {
        val id = deserializeId(data.id)
        val label = data.label.unsafeCast<String>()
        val outVId = deserializeId(data.outV).toString()
        val inVId = deserializeId(data.inV).toString()

        val outVertex = vertexMap[outVId] ?: throw StorageException("Referenced outVertex not found: $outVId")
        val inVertex = vertexMap[inVId] ?: throw StorageException("Referenced inVertex not found: $inVId")

        val edge = outVertex.addEdge(label, inVertex) as TinkerEdge

        // Deserialize edge properties
        if (data.properties != null) {
            val propertiesObj = data.properties
            val propKeys = js("Object.keys(propertiesObj)").unsafeCast<Array<String>>()
            propKeys.forEach { key ->
                val value = deserializeValue(propertiesObj[key])
                edge.property(key, value)
            }
        }
    }

    private fun deserializeVariables(variables: Graph.Variables, data: dynamic) {
        val keys = js("Object.keys(data)").unsafeCast<Array<String>>()
        keys.forEach { key ->
            try {
                val value = deserializeValue(data[key])
                variables.set(key, value)
            } catch (e: Exception) {
                console.warn("Could not deserialize variable '$key': ${e.message}")
            }
        }
    }

    private fun deserializeId(data: dynamic): Any {
        return when (data.type.unsafeCast<String>()) {
            "string" -> data.value.unsafeCast<String>()
            "int" -> data.value.unsafeCast<Int>()
            "long" -> data.value.unsafeCast<String>().toLong()
            "double" -> data.value.unsafeCast<Double>()
            else -> data.value.unsafeCast<String>()
        }
    }

    private fun deserializeValue(data: dynamic): Any? {
        return when {
            data == null || data == js("undefined") -> null
            js("typeof data === 'string'").unsafeCast<Boolean>() -> data.unsafeCast<String>()
            js("typeof data === 'boolean'").unsafeCast<Boolean>() -> data.unsafeCast<Boolean>()
            js("typeof data === 'number'").unsafeCast<Boolean>() -> {
                val num = data.unsafeCast<Double>()
                // Try to preserve integer types when possible
                if (num == num.toInt().toDouble()) {
                    num.toInt()
                } else {
                    num
                }
            }
            js("Array.isArray(data)").unsafeCast<Boolean>() -> {
                val array = data.unsafeCast<Array<dynamic>>()
                array.map { deserializeValue(it) }.toTypedArray()
            }
            js("typeof data === 'object'").unsafeCast<Boolean>() -> {
                val result = mutableMapOf<String, Any?>()
                val keys = js("Object.keys(data)").unsafeCast<Array<String>>()
                keys.forEach { key ->
                    result[key] = deserializeValue(data[key])
                }
                result
            }
            else -> data.toString()
        }
    }

    // === Validation ===

    private fun validateFormat(data: dynamic) {
        if (data._type != "TinkerGraph") {
            throw StorageException("Invalid graph format: expected TinkerGraph, got ${data._type}")
        }

        val version = data._version?.unsafeCast<String>()
        if (version != null && version != FORMAT_VERSION) {
            console.warn("Graph serialized with version $version, current version is $FORMAT_VERSION")
        }

        if (data.vertices == null) {
            throw StorageException("Invalid graph format: missing vertices")
        }

        if (data.edges == null) {
            throw StorageException("Invalid graph format: missing edges")
        }
    }
}

/**
 * Extension functions for easier serialization
 */
fun TinkerGraph.toJson(): String = GraphSerializer.toJson(this)
fun TinkerGraph.serialize(): dynamic = GraphSerializer.serialize(this)

/**
 * Companion functions for deserialization
 */
fun graphFromJson(json: String): Promise<TinkerGraph> = GraphSerializer.fromJson(json)
fun graphFromData(data: dynamic): Promise<TinkerGraph> = GraphSerializer.deserialize(data)
