package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.factory.TinkerGraphFactory
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * JVM-specific serialization support for TinkerGraph using Java Serializable.
 * Provides efficient serialization and deserialization of graph elements and entire graphs.
 */
object JvmSerialization {

    /**
     * Serializable wrapper for TinkerGraph data.
     */
    data class SerializableGraph(
        val vertices: Map<Any, SerializableVertex>,
        val edges: Map<Any, SerializableEdge>,
        val configuration: Map<String, Any> = emptyMap(),
        val metadata: GraphMetadata = GraphMetadata()
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    /**
     * Serializable representation of a vertex.
     */
    data class SerializableVertex(
        val id: Any,
        val label: String,
        val properties: Map<String, List<SerializableProperty>>,
        val outEdges: Set<Any> = emptySet(),
        val inEdges: Set<Any> = emptySet()
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    /**
     * Serializable representation of an edge.
     */
    data class SerializableEdge(
        val id: Any,
        val label: String,
        val outVertexId: Any,
        val inVertexId: Any,
        val properties: Map<String, SerializableProperty>
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    /**
     * Serializable representation of a property.
     */
    data class SerializableProperty(
        val key: String,
        val value: Any,
        val id: Any? = null
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    /**
     * Graph metadata for serialization context.
     */
    data class GraphMetadata(
        val version: String = "1.0.0",
        val timestamp: Long = System.currentTimeMillis(),
        val vertexCount: Int = 0,
        val edgeCount: Int = 0,
        val checksums: Map<String, String> = emptyMap()
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    /**
     * Serialize a TinkerGraph to a byte array.
     */
    fun serializeGraph(graph: TinkerGraph): ByteArray {
        val serializableGraph = convertToSerializable(graph)

        return ByteArrayOutputStream().use { byteOut ->
            ObjectOutputStream(byteOut).use { objectOut ->
                objectOut.writeObject(serializableGraph)
            }
            byteOut.toByteArray()
        }
    }

    /**
     * Serialize a TinkerGraph to a file.
     */
    fun serializeGraphToFile(graph: TinkerGraph, filePath: String, compressed: Boolean = true) {
        val path = Paths.get(filePath)
        Files.createDirectories(path.parent ?: Paths.get("."))

        val serializableGraph = convertToSerializable(graph)

        Files.newOutputStream(path).use { fileOut ->
            val outputStream = if (compressed) {
                GZIPOutputStream(fileOut)
            } else {
                fileOut
            }

            outputStream.use { stream ->
                ObjectOutputStream(stream).use { objectOut ->
                    objectOut.writeObject(serializableGraph)
                }
            }
        }
    }

    /**
     * Deserialize a TinkerGraph from a byte array.
     */
    fun deserializeGraph(data: ByteArray): TinkerGraph {
        val serializableGraph = ByteArrayInputStream(data).use { byteIn ->
            ObjectInputStream(byteIn).use { objectIn ->
                objectIn.readObject() as SerializableGraph
            }
        }

        return convertFromSerializable(serializableGraph)
    }

    /**
     * Deserialize a TinkerGraph from a file.
     */
    fun deserializeGraphFromFile(filePath: String, compressed: Boolean = true): TinkerGraph {
        val path = Paths.get(filePath)

        val serializableGraph = Files.newInputStream(path).use { fileIn ->
            val inputStream = if (compressed) {
                GZIPInputStream(fileIn)
            } else {
                fileIn
            }

            inputStream.use { stream ->
                ObjectInputStream(stream).use { objectIn ->
                    objectIn.readObject() as SerializableGraph
                }
            }
        }

        return convertFromSerializable(serializableGraph)
    }

    /**
     * Serialize individual vertex to bytes.
     */
    fun serializeVertex(vertex: Vertex): ByteArray {
        val serializableVertex = convertVertexToSerializable(vertex)

        return ByteArrayOutputStream().use { byteOut ->
            ObjectOutputStream(byteOut).use { objectOut ->
                objectOut.writeObject(serializableVertex)
            }
            byteOut.toByteArray()
        }
    }

    /**
     * Deserialize individual vertex from bytes.
     */
    fun deserializeVertex(data: ByteArray, graph: TinkerGraph): Vertex {
        val serializableVertex = ByteArrayInputStream(data).use { byteIn ->
            ObjectInputStream(byteIn).use { objectIn ->
                objectIn.readObject() as SerializableVertex
            }
        }

        return convertVertexFromSerializable(serializableVertex, graph)
    }

    /**
     * Serialize individual edge to bytes.
     */
    fun serializeEdge(edge: Edge): ByteArray {
        val serializableEdge = convertEdgeToSerializable(edge)

        return ByteArrayOutputStream().use { byteOut ->
            ObjectOutputStream(byteOut).use { objectOut ->
                objectOut.writeObject(serializableEdge)
            }
            byteOut.toByteArray()
        }
    }

    /**
     * Deserialize individual edge from bytes.
     */
    fun deserializeEdge(data: ByteArray, graph: TinkerGraph): Edge {
        val serializableEdge = ByteArrayInputStream(data).use { byteIn ->
            ObjectInputStream(byteIn).use { objectIn ->
                objectIn.readObject() as SerializableEdge
            }
        }

        return convertEdgeFromSerializable(serializableEdge, graph)
    }

    /**
     * Get serialization statistics for a graph.
     */
    fun getSerializationStats(graph: TinkerGraph): Map<String, Any> {
        val vertexCount = graph.vertices().asSequence().count()
        val edgeCount = graph.edges().asSequence().count()

        val sampleSize = minOf(1000, vertexCount)
        val vertices = graph.vertices().asSequence().take(sampleSize).toList()

        val avgPropertiesPerVertex = if (vertices.isNotEmpty()) {
            vertices.map { it.properties<Any>().asSequence().count() }.average()
        } else 0.0

        val estimatedSize = estimateGraphSize(graph)

        return mapOf(
            "vertexCount" to vertexCount,
            "edgeCount" to edgeCount,
            "avgPropertiesPerVertex" to avgPropertiesPerVertex,
            "estimatedSizeBytes" to estimatedSize,
            "estimatedSizeMB" to estimatedSize / (1024.0 * 1024.0),
            "compressionRecommended" to (estimatedSize > 1024 * 1024) // > 1MB
        )
    }

    /**
     * Convert TinkerGraph to serializable representation.
     */
    private fun convertToSerializable(graph: TinkerGraph): SerializableGraph {
        val vertices = mutableMapOf<Any, SerializableVertex>()
        val edges = mutableMapOf<Any, SerializableEdge>()

        // Convert vertices
        graph.vertices().forEach { vertex ->
            val id = vertex.id()
            if (id != null) {
                vertices[id] = convertVertexToSerializable(vertex)
            }
        }

        // Convert edges
        graph.edges().forEach { edge ->
            val id = edge.id()
            if (id != null) {
                edges[id] = convertEdgeToSerializable(edge)
            }
        }

        val metadata = GraphMetadata(
            vertexCount = vertices.size,
            edgeCount = edges.size
        )

        return SerializableGraph(
            vertices = vertices,
            edges = edges,
            metadata = metadata
        )
    }

    /**
     * Convert serializable representation to TinkerGraph.
     */
    private fun convertFromSerializable(serializableGraph: SerializableGraph): TinkerGraph {
        val graph = TinkerGraphFactory.create()

        // First create all vertices
        val vertexMap = mutableMapOf<Any, Vertex>()
        serializableGraph.vertices.forEach { (id, serializableVertex) ->
            val vertex = convertVertexFromSerializable(serializableVertex, graph)
            vertexMap[id] = vertex
        }

        // Then create all edges
        serializableGraph.edges.forEach { (_, serializableEdge) ->
            convertEdgeFromSerializable(serializableEdge, graph, vertexMap)
        }

        return graph
    }

    /**
     * Convert vertex to serializable form.
     */
    private fun convertVertexToSerializable(vertex: Vertex): SerializableVertex {
        val properties = mutableMapOf<String, MutableList<SerializableProperty>>()

        vertex.properties<Any>().forEach { property ->
            val key = property.key()
            val serializableProperty = SerializableProperty(
                key = key,
                value = property.value(),
                id = if (property is VertexProperty) property.id() else null
            )

            properties.computeIfAbsent(key) { mutableListOf() }.add(serializableProperty)
        }

        // Collect edge IDs
        val outEdges = vertex.edges(Direction.OUT).asSequence().mapNotNull { it.id() }.toSet()
        val inEdges = vertex.edges(Direction.IN).asSequence().mapNotNull { it.id() }.toSet()

        return SerializableVertex(
            id = vertex.id() ?: "",
            label = vertex.label(),
            properties = properties,
            outEdges = outEdges,
            inEdges = inEdges
        )
    }

    /**
     * Convert serializable vertex back to TinkerGraph vertex.
     */
    private fun convertVertexFromSerializable(
        serializableVertex: SerializableVertex,
        graph: TinkerGraph,
        existingVertices: Map<Any, Vertex> = emptyMap()
    ): Vertex {
        // Check if vertex already exists
        existingVertices[serializableVertex.id]?.let { return it }

        // Create vertex with properties
        val propertyList = mutableListOf<Any>()
        propertyList.add("id")
        propertyList.add(serializableVertex.id)
        propertyList.add("label")
        propertyList.add(serializableVertex.label)

        // Add all properties
        serializableVertex.properties.forEach { (key, propList) ->
            propList.forEach { property ->
                propertyList.add(key)
                propertyList.add(property.value)
            }
        }

        return graph.addVertex(*propertyList.toTypedArray())
    }

    /**
     * Convert edge to serializable form.
     */
    private fun convertEdgeToSerializable(edge: Edge): SerializableEdge {
        val properties = mutableMapOf<String, SerializableProperty>()

        edge.properties<Any>().forEach { property ->
            properties[property.key()] = SerializableProperty(
                key = property.key(),
                value = property.value()
            )
        }

        return SerializableEdge(
            id = edge.id() ?: "",
            label = edge.label(),
            outVertexId = edge.outVertex().id() ?: "",
            inVertexId = edge.inVertex().id() ?: "",
            properties = properties
        )
    }

    /**
     * Convert serializable edge back to TinkerGraph edge.
     */
    private fun convertEdgeFromSerializable(
        serializableEdge: SerializableEdge,
        graph: TinkerGraph,
        vertexMap: Map<Any, Vertex> = emptyMap()
    ): Edge {
        val outVertex = vertexMap[serializableEdge.outVertexId]
            ?: graph.vertices(serializableEdge.outVertexId).next()
        val inVertex = vertexMap[serializableEdge.inVertexId]
            ?: graph.vertices(serializableEdge.inVertexId).next()

        // Create edge with properties
        val propertyList = mutableListOf<Any>()
        propertyList.add("id")
        propertyList.add(serializableEdge.id)

        serializableEdge.properties.forEach { (key, property) ->
            propertyList.add(key)
            propertyList.add(property.value)
        }

        return outVertex.addEdge(serializableEdge.label, inVertex, *propertyList.toTypedArray())
    }

    /**
     * Estimate the serialized size of a graph in bytes.
     */
    private fun estimateGraphSize(graph: TinkerGraph): Long {
        var estimatedSize = 0L

        // Sample-based estimation for performance
        val sampleVertices = graph.vertices().asSequence().take(100).toList()
        val sampleEdges = graph.edges().asSequence().take(100).toList()

        val avgVertexSize = if (sampleVertices.isNotEmpty()) {
            sampleVertices.map { estimateVertexSize(it) }.average().toLong()
        } else 0L

        val avgEdgeSize = if (sampleEdges.isNotEmpty()) {
            sampleEdges.map { estimateEdgeSize(it) }.average().toLong()
        } else 0L

        val totalVertices = graph.vertices().asSequence().count()
        val totalEdges = graph.edges().asSequence().count()

        estimatedSize += totalVertices * avgVertexSize
        estimatedSize += totalEdges * avgEdgeSize

        // Add overhead for object serialization
        estimatedSize += (estimatedSize * 0.2).toLong() // 20% overhead estimate

        return estimatedSize
    }

    /**
     * Estimate size of a vertex in bytes.
     */
    private fun estimateVertexSize(vertex: Vertex): Int {
        var size = 64 // Base object overhead
        size += vertex.id().toString().length * 2 // ID string
        size += vertex.label().length * 2 // Label string

        vertex.properties<Any>().forEach { property ->
            size += property.key().length * 2 // Property key
            size += estimateValueSize(property.value()) // Property value
        }

        return size
    }

    /**
     * Estimate size of an edge in bytes.
     */
    private fun estimateEdgeSize(edge: Edge): Int {
        var size = 64 // Base object overhead
        size += edge.id().toString().length * 2 // ID string
        size += edge.label().length * 2 // Label string
        size += 16 // Vertex references

        edge.properties<Any>().forEach { property ->
            size += property.key().length * 2 // Property key
            size += estimateValueSize(property.value()) // Property value
        }

        return size
    }

    /**
     * Estimate size of a property value in bytes.
     */
    private fun estimateValueSize(value: Any): Int {
        return when (value) {
            is String -> value.length * 2
            is Int -> 4
            is Long -> 8
            is Double -> 8
            is Float -> 4
            is Boolean -> 1
            is ByteArray -> value.size
            else -> value.toString().length * 2
        }
    }
}
