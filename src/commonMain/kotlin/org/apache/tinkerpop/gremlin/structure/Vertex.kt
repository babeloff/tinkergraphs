package org.apache.tinkerpop.gremlin.structure

/**
 * A Vertex maintains pointers to both a set of incoming and outgoing edges.
 * The outgoing edges are those edges for which the vertex is the tail.
 * The incoming edges are those edges for which the vertex is the head.
 * Diagrammatically: ---> vertex <---.
 */
interface Vertex : Element {

    /**
     * Add an outgoing edge to this vertex with a label and target vertex.
     * @param label the edge label
     * @param inVertex the target vertex
     * @param keyValues optional edge properties as key/value pairs
     * @return the created edge
     */
    fun addEdge(label: String, inVertex: Vertex, vararg keyValues: Any?): Edge

    /**
     * Add an outgoing edge to this vertex with a label, target vertex, and properties map.
     * @param label the edge label
     * @param inVertex the target vertex
     * @param properties edge properties as a map
     * @return the created edge
     */
    fun addEdge(label: String, inVertex: Vertex, properties: Map<String, Any?>): Edge

    /**
     * Get the outgoing edges of this vertex.
     * @param direction the direction of edges to retrieve
     * @param edgeLabels optional edge labels to filter
     * @return iterator of edges
     */
    fun edges(direction: Direction, vararg edgeLabels: String): Iterator<Edge>

    /**
     * Get the vertices connected to this vertex.
     * @param direction the direction to traverse
     * @param edgeLabels optional edge labels to filter
     * @return iterator of vertices
     */
    fun vertices(direction: Direction, vararg edgeLabels: String): Iterator<Vertex>

    /**
     * Add or set a property on this vertex.
     * @param key the property key
     * @param value the property value
     * @param keyValues additional properties as key/value pairs
     * @return the created vertex property
     */
    fun <V> property(key: String, value: V, vararg keyValues: Any?): VertexProperty<V>

    /**
     * Get vertex properties by key.
     * @param propertyKeys optional property keys to filter
     * @return iterator of vertex properties
     */
    override fun <V> properties(vararg propertyKeys: String): Iterator<VertexProperty<V>>

    /**
     * Default vertex label
     */
    companion object {
        const val DEFAULT_LABEL = "vertex"
    }

    /**
     * A collection of exceptions that are thrown by Vertices
     */
    object Exceptions {
        fun userSuppliedIdsNotSupported(): UnsupportedOperationException =
            UnsupportedOperationException("Vertex does not support user supplied identifiers")

        fun userSuppliedIdsOfThisTypeNotSupported(): UnsupportedOperationException =
            UnsupportedOperationException("Vertex does not support user supplied identifiers of this type")

        fun vertexRemovalNotSupported(): UnsupportedOperationException =
            UnsupportedOperationException("Vertex removal is not supported")

        fun edgeAdditionNotSupported(): UnsupportedOperationException =
            UnsupportedOperationException("Edge addition is not supported")

        fun multiplePropertiesExistForProvidedKey(key: String): IllegalStateException =
            IllegalStateException("Multiple properties exist for the provided key: $key")
    }
}
