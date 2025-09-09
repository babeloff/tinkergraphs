@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package org.apache.tinkerpop.gremlin.tinkergraph.platform

import kotlinx.cinterop.*
import kotlin.experimental.ExperimentalNativeApi
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerEdge
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.Edge

/**
 * Native bindings for Python integration using ctypes.
 *
 * These functions provide C-compatible entry points that can be called
 * from Python using the ctypes library.
 */

@CName("tinkergraph_create")
fun createTinkerGraph(): COpaquePointer? {
    return try {
        StableRef.create(TinkerGraph.open()).asCPointer()
    } catch (e: Exception) {
        null
    }
}

@CName("tinkergraph_destroy")
fun destroyTinkerGraph(graphPtr: COpaquePointer?) {
    try {
        graphPtr?.asStableRef<TinkerGraph>()?.dispose()
    } catch (e: Exception) {
        // Ignore errors during cleanup
    }
}

@CName("tinkergraph_add_vertex")
fun addVertex(graphPtr: COpaquePointer?, id: CPointer<ByteVar>?): COpaquePointer? {
    return try {
        val graph = graphPtr?.asStableRef<TinkerGraph>()?.get() ?: return null
        val idStr = id?.toKString()

        val vertex = if (idStr != null) {
            graph.addVertex("id", idStr)
        } else {
            graph.addVertex()
        }
        StableRef.create(vertex).asCPointer()
    } catch (e: Exception) {
        null
    }
}

@CName("tinkergraph_add_vertex_with_properties")
fun addVertexWithProperties(
    graphPtr: COpaquePointer?,
    id: CPointer<ByteVar>?,
    propertyKeys: CPointer<CPointerVar<ByteVar>>?,
    propertyValues: CPointer<CPointerVar<ByteVar>>?,
    propertyCount: Int
): COpaquePointer? {
    return try {
        val graph = graphPtr?.asStableRef<TinkerGraph>()?.get() ?: return null
        val idStr = id?.toKString()

        val properties = mutableListOf<Any?>()
        if (idStr != null) {
            properties.add("id")
            properties.add(idStr)
        }

        if (propertyKeys != null && propertyValues != null && propertyCount > 0) {
            for (i in 0 until propertyCount) {
                val key = propertyKeys[i]?.toKString() ?: continue
                val value = propertyValues[i]?.toKString() ?: continue
                properties.add(key)
                properties.add(value)
            }
        }

        val vertex = graph.addVertex(*properties.toTypedArray())
        StableRef.create(vertex).asCPointer()
    } catch (e: Exception) {
        null
    }
}

@CName("tinkergraph_add_edge")
fun addEdge(
    graphPtr: COpaquePointer?,
    label: CPointer<ByteVar>?,
    outVertexPtr: COpaquePointer?,
    inVertexPtr: COpaquePointer?
): COpaquePointer? {
    return try {
        val graph = graphPtr?.asStableRef<TinkerGraph>()?.get() ?: return null
        val labelStr = label?.toKString() ?: return null
        val outVertex = outVertexPtr?.asStableRef<Vertex>()?.get() as? TinkerVertex ?: return null
        val inVertex = inVertexPtr?.asStableRef<Vertex>()?.get() as? TinkerVertex ?: return null

        val edge = graph.addEdge(outVertex, inVertex, labelStr, emptyMap())
        StableRef.create(edge).asCPointer()
    } catch (e: Exception) {
        null
    }
}

@CName("tinkergraph_add_edge_with_properties")
fun addEdgeWithProperties(
    graphPtr: COpaquePointer?,
    label: CPointer<ByteVar>?,
    outVertexPtr: COpaquePointer?,
    inVertexPtr: COpaquePointer?,
    propertyKeys: CPointer<CPointerVar<ByteVar>>?,
    propertyValues: CPointer<CPointerVar<ByteVar>>?,
    propertyCount: Int
): COpaquePointer? {
    return try {
        val graph = graphPtr?.asStableRef<TinkerGraph>()?.get() ?: return null
        val labelStr = label?.toKString() ?: return null
        val outVertex = outVertexPtr?.asStableRef<Vertex>()?.get() as? TinkerVertex ?: return null
        val inVertex = inVertexPtr?.asStableRef<Vertex>()?.get() as? TinkerVertex ?: return null

        val properties = mutableMapOf<String, Any?>()
        if (propertyKeys != null && propertyValues != null && propertyCount > 0) {
            for (i in 0 until propertyCount) {
                val key = propertyKeys[i]?.toKString() ?: continue
                val value = propertyValues[i]?.toKString() ?: continue
                properties[key] = value
            }
        }

        val edge = graph.addEdge(outVertex, inVertex, labelStr, properties)
        StableRef.create(edge).asCPointer()
    } catch (e: Exception) {
        null
    }
}

@CName("tinkergraph_vertex_count")
fun getVertexCount(graphPtr: COpaquePointer?): Long {
    return try {
        val graph = graphPtr?.asStableRef<TinkerGraph>()?.get() ?: return 0L
        graph.vertices().asSequence().count().toLong()
    } catch (e: Exception) {
        0L
    }
}

@CName("tinkergraph_edge_count")
fun getEdgeCount(graphPtr: COpaquePointer?): Long {
    return try {
        val graph = graphPtr?.asStableRef<TinkerGraph>()?.get() ?: return 0L
        graph.edges().asSequence().count().toLong()
    } catch (e: Exception) {
        0L
    }
}

@CName("tinkergraph_vertex_id")
fun getVertexId(vertexPtr: COpaquePointer?, buffer: CPointer<ByteVar>?, bufferSize: Int): Int {
    return try {
        val vertex = vertexPtr?.asStableRef<Vertex>()?.get() ?: return -1
        val idStr = vertex.id()?.toString() ?: ""
        val bytes = idStr.encodeToByteArray()

        if (buffer != null && bufferSize > 0) {
            val copySize = minOf(bytes.size, bufferSize - 1)
            bytes.copyInto(buffer, 0, 0, copySize)
            buffer[copySize] = 0 // null terminator
        }

        bytes.size
    } catch (e: Exception) {
        -1
    }
}

@CName("tinkergraph_edge_label")
fun getEdgeLabel(edgePtr: COpaquePointer?, buffer: CPointer<ByteVar>?, bufferSize: Int): Int {
    return try {
        val edge = edgePtr?.asStableRef<Edge>()?.get() ?: return -1
        val labelStr = edge.label()
        val bytes = labelStr.encodeToByteArray()

        if (buffer != null && bufferSize > 0) {
            val copySize = minOf(bytes.size, bufferSize - 1)
            bytes.copyInto(buffer, 0, 0, copySize)
            buffer[copySize] = 0 // null terminator
        }

        bytes.size
    } catch (e: Exception) {
        -1
    }
}

@CName("tinkergraph_destroy_vertex")
fun destroyVertex(vertexPtr: COpaquePointer?) {
    try {
        vertexPtr?.asStableRef<Vertex>()?.dispose()
    } catch (e: Exception) {
        // Ignore errors during cleanup
    }
}

@CName("tinkergraph_destroy_edge")
fun destroyEdge(edgePtr: COpaquePointer?) {
    try {
        edgePtr?.asStableRef<Edge>()?.dispose()
    } catch (e: Exception) {
        // Ignore errors during cleanup
    }
}

@CName("tinkergraph_get_error_message")
fun getLastErrorMessage(buffer: CPointer<ByteVar>?, bufferSize: Int): Int {
    // Simple error handling - in a real implementation, you might want
    // to maintain thread-local error state
    val errorMsg = "Operation failed"
    val bytes = errorMsg.encodeToByteArray()

    if (buffer != null && bufferSize > 0) {
        val copySize = minOf(bytes.size, bufferSize - 1)
        bytes.copyInto(buffer, 0, 0, copySize)
        buffer[copySize] = 0 // null terminator
    }

    return bytes.size
}

private fun ByteArray.copyInto(
    destination: CPointer<ByteVar>,
    destinationOffset: Int,
    startIndex: Int,
    endIndex: Int
) {
    for (i in startIndex until endIndex) {
        destination[destinationOffset + i - startIndex] = this[i]
    }
}
