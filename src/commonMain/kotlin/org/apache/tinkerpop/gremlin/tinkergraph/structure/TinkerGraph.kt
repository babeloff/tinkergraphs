package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*

/**
 * An in-memory graph database implementation of TinkerPop's Graph interface.
 * TinkerGraph is a toy graph database that is useful for testing and learning purposes.
 */
class TinkerGraph private constructor(
    private val configuration: Map<String, Any?>
) : Graph {

    /**
     * Internal storage for vertices, keyed by vertex ID.
     */
    internal val vertices: MutableMap<Any, TinkerVertex> = mutableMapOf()

    /**
     * Internal storage for edges, keyed by edge ID.
     */
    internal val edges: MutableMap<Any, TinkerEdge> = mutableMapOf()

    /**
     * Current ID counter for auto-generated IDs.
     */
    internal var currentId: Long = 0L

    /**
     * Graph variables for storing metadata.
     */
    private val graphVariables: TinkerGraphVariables = TinkerGraphVariables()

    /**
     * Index for vertex properties to enable fast lookups.
     */
    internal val vertexIndex: TinkerIndex<TinkerVertex> = TinkerIndex()

    /**
     * Index for edge properties to enable fast lookups.
     */
    internal val edgeIndex: TinkerIndex<TinkerEdge> = TinkerIndex()

    /**
     * Whether to allow null property values.
     */
    internal val allowNullPropertyValues: Boolean =
        configuration[GREMLIN_TINKERGRAPH_ALLOW_NULL_PROPERTY_VALUES] as? Boolean ?: false

    /**
     * Default cardinality for vertex properties.
     */
    internal val defaultVertexPropertyCardinality: VertexProperty.Cardinality =
        VertexProperty.Cardinality.valueOf(
            configuration[GREMLIN_TINKERGRAPH_DEFAULT_VERTEX_PROPERTY_CARDINALITY] as? String
                ?: VertexProperty.Cardinality.SINGLE.name
        )

    /**
     * Graph features describing capabilities.
     */
    private val graphFeatures: TinkerGraphFeatures = TinkerGraphFeatures()

    override fun addVertex(vararg keyValues: Any?): Vertex {
        val properties = ElementHelper.asMap(keyValues)
        return addVertex(properties)
    }

    override fun addVertex(properties: Map<String, Any?>): Vertex {
        val id = ElementHelper.getIdValue(properties) ?: getNextId()
        val label = ElementHelper.getLabelValue(properties) ?: Vertex.DEFAULT_LABEL

        // Check if vertex with this ID already exists
        if (vertices.containsKey(id)) {
            throw Graph.Exceptions.vertexWithIdAlreadyExists(id)
        }

        // Create the vertex
        val vertex = TinkerVertex(id, label, this)
        vertices[id] = vertex

        // Add properties (excluding reserved keys)
        ElementHelper.attachProperties(vertex, ElementHelper.removeReservedKeys(properties))

        return vertex
    }

    override fun vertex(id: Any?): Vertex? {
        return vertices[id]
    }

    override fun vertices(vararg vertexIds: Any?): Iterator<Vertex> {
        return if (vertexIds.isEmpty()) {
            vertices.values.iterator()
        } else {
            vertexIds.mapNotNull { vertices[it] }.iterator()
        }
    }

    override fun edge(id: Any?): Edge? {
        return edges[id]
    }

    override fun edges(vararg edgeIds: Any?): Iterator<Edge> {
        return if (edgeIds.isEmpty()) {
            edges.values.iterator()
        } else {
            edgeIds.mapNotNull { edges[it] }.iterator()
        }
    }

    /**
     * Add an edge between two vertices.
     */
    internal fun addEdge(
        outVertex: TinkerVertex,
        inVertex: TinkerVertex,
        label: String,
        properties: Map<String, Any?>
    ): TinkerEdge {
        val id = ElementHelper.getIdValue(properties) ?: getNextId()

        // Check if edge with this ID already exists
        if (edges.containsKey(id)) {
            throw Graph.Exceptions.edgeWithIdAlreadyExists(id)
        }

        // Create the edge
        val edge = TinkerEdge(id, label, outVertex, inVertex, this)
        edges[id] = edge

        // Add to vertex adjacency lists
        outVertex.addOutEdge(edge)
        inVertex.addInEdge(edge)

        // Add properties (excluding reserved keys)
        ElementHelper.attachProperties(edge, ElementHelper.removeReservedKeys(properties))

        return edge
    }

    /**
     * Remove a vertex from the graph.
     */
    internal fun removeVertex(vertex: TinkerVertex) {
        // Remove all incident edges first
        val incidentEdges = mutableSetOf<TinkerEdge>()
        incidentEdges.addAll(vertex.getOutEdges())
        incidentEdges.addAll(vertex.getInEdges())

        incidentEdges.forEach { edge ->
            removeEdge(edge)
        }

        // Remove from vertex index
        vertexIndex.removeElement(vertex)

        // Remove from vertices map
        vertices.remove(vertex.id())

        // Mark as removed
        vertex.markRemoved()
    }

    /**
     * Remove an edge from the graph.
     */
    internal fun removeEdge(edge: TinkerEdge) {
        // Remove from vertex adjacency lists
        edge.outVertex().removeOutEdge(edge)
        edge.inVertex().removeInEdge(edge)

        // Remove from edge index
        edgeIndex.removeElement(edge)

        // Remove from edges map
        edges.remove(edge.id())

        // Mark as removed
        edge.markRemoved()
    }

    /**
     * Generate the next available ID.
     */
    internal fun getNextId(): Any {
        return ++currentId
    }

    override fun features(): Graph.Features {
        return graphFeatures
    }

    override fun variables(): Graph.Variables {
        return graphVariables
    }

    override fun configuration(): Map<String, Any?> {
        return configuration.toMap()
    }

    override fun close() {
        // TinkerGraph is in-memory, so no cleanup is needed
        // In a persistent implementation, this would save to disk
    }

    /**
     * Create an index for faster property lookups.
     */
    fun createIndex(key: String, elementClass: Class<out Element>) {
        when (elementClass.simpleName) {
            "Vertex", "TinkerVertex" -> vertexIndex.createKeyIndex(key)
            "Edge", "TinkerEdge" -> edgeIndex.createKeyIndex(key)
            else -> throw IllegalArgumentException("Class is not indexable: ${elementClass.simpleName}")
        }
    }

    /**
     * Drop an index.
     */
    fun dropIndex(key: String, elementClass: Class<out Element>) {
        when (elementClass.simpleName) {
            "Vertex", "TinkerVertex" -> vertexIndex.dropKeyIndex(key)
            "Edge", "TinkerEdge" -> edgeIndex.dropKeyIndex(key)
            else -> throw IllegalArgumentException("Class is not indexable: ${elementClass.simpleName}")
        }
    }

    /**
     * Get the indexed keys for a given element class.
     */
    fun getIndexedKeys(elementClass: Class<out Element>): Set<String> {
        return when (elementClass.simpleName) {
            "Vertex", "TinkerVertex" -> vertexIndex.getIndexedKeys()
            "Edge", "TinkerEdge" -> edgeIndex.getIndexedKeys()
            else -> throw IllegalArgumentException("Class is not indexable: ${elementClass.simpleName}")
        }
    }

    companion object {
        // Configuration keys
        const val GREMLIN_TINKERGRAPH_ALLOW_NULL_PROPERTY_VALUES = "gremlin.tinkerGraph.allowNullPropertyValues"
        const val GREMLIN_TINKERGRAPH_DEFAULT_VERTEX_PROPERTY_CARDINALITY = "gremlin.tinkerGraph.defaultVertexPropertyCardinality"
        const val GREMLIN_TINKERGRAPH_GRAPH_LOCATION = "gremlin.tinkerGraph.graphLocation"
        const val GREMLIN_TINKERGRAPH_GRAPH_FORMAT = "gremlin.tinkerGraph.graphFormat"

        /**
         * Create a new TinkerGraph instance.
         */
        fun open(): TinkerGraph = open(emptyMap())

        /**
         * Create a new TinkerGraph instance with configuration.
         */
        fun open(configuration: Map<String, Any?>): TinkerGraph {
            return TinkerGraph(configuration)
        }
    }

    /**
     * TinkerGraph-specific features implementation.
     */
    private class TinkerGraphFeatures : Graph.Features {
        private val graphFeatures = TinkerGraphGraphFeatures()
        private val vertexFeatures = TinkerGraphVertexFeatures()
        private val edgeFeatures = TinkerGraphEdgeFeatures()

        override fun graph(): Graph.Features.GraphFeatures = graphFeatures
        override fun vertex(): Graph.Features.VertexFeatures = vertexFeatures
        override fun edge(): Graph.Features.EdgeFeatures = edgeFeatures
    }

    private class TinkerGraphGraphFeatures : Graph.Features.GraphFeatures {
        override fun supportsComputer(): Boolean = false
        override fun supportsPersistence(): Boolean = false
        override fun supportsConcurrentAccess(): Boolean = false
        override fun supportsTransactions(): Boolean = false
        override fun supportsThreadedTransactions(): Boolean = false
    }

    private class TinkerGraphVertexFeatures : Graph.Features.VertexFeatures {
        override fun supportsMetaProperties(): Boolean = true
        override fun supportsMultiProperties(): Boolean = true
        override fun supportsUserSuppliedIds(): Boolean = true
        override fun supportsNumericIds(): Boolean = true
        override fun supportsStringIds(): Boolean = true
        override fun supportsUuidIds(): Boolean = true
        override fun supportsCustomIds(): Boolean = true
        override fun supportsAnyIds(): Boolean = true
    }

    private class TinkerGraphEdgeFeatures : Graph.Features.EdgeFeatures {
        override fun supportsUserSuppliedIds(): Boolean = true
        override fun supportsNumericIds(): Boolean = true
        override fun supportsStringIds(): Boolean = true
        override fun supportsUuidIds(): Boolean = true
        override fun supportsCustomIds(): Boolean = true
        override fun supportsAnyIds(): Boolean = true
    }


}
