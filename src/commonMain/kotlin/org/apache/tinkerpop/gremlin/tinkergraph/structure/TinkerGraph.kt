package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators.TinkerVertexIterator
import org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators.TinkerEdgeIterator
import kotlin.reflect.KClass

/**
 * An in-memory graph database implementation of TinkerPop's Graph interface.
 * TinkerGraph is a toy graph database that is useful for testing and learning purposes.
 *
 * This implementation provides:
 * - In-memory storage for vertices and edges
 * - Property indexing for fast lookups
 * - Composite indexing for multi-property queries
 * - Cross-platform logging integration via KmLogging
 *
 * @param configuration Graph configuration parameters
 * @since 1.0.0
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
     * Composite index for vertex properties to enable multi-property queries.
     */
    internal val vertexCompositeIndex: CompositeIndex<TinkerVertex> = CompositeIndex()

    /**
     * Composite index for edge properties to enable multi-property queries.
     */
    internal val edgeCompositeIndex: CompositeIndex<TinkerEdge> = CompositeIndex()

    /**
     * Range index for vertex properties to enable efficient range queries.
     */
    internal val vertexRangeIndex: RangeIndex<TinkerVertex> = RangeIndex()

    /**
     * Range index for edge properties to enable efficient range queries.
     */
    internal val edgeRangeIndex: RangeIndex<TinkerEdge> = RangeIndex()

    /**
     * Index optimizer for vertex queries.
     */
    internal val vertexIndexOptimizer: IndexOptimizer<TinkerVertex> =
        IndexOptimizer(vertexIndex, vertexCompositeIndex, vertexRangeIndex)

    /**
     * Index optimizer for edge queries.
     */
    internal val edgeIndexOptimizer: IndexOptimizer<TinkerEdge> =
        IndexOptimizer(edgeIndex, edgeCompositeIndex, edgeRangeIndex)

    /**
     * Index cache for vertex queries.
     */
    internal val vertexIndexCache: IndexCache<TinkerVertex> = IndexCache()

    /**
     * Index cache for edge queries.
     */
    internal val edgeIndexCache: IndexCache<TinkerEdge> = IndexCache()

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

    /**
     * Property manager for advanced property operations.
     */
    internal val propertyManager: PropertyManager = PropertyManager(this)

    /**
     * Property query engine for advanced property querying.
     */
    internal val propertyQueryEngine: PropertyQueryEngine = PropertyQueryEngine(this)

    override fun addVertex(vararg keyValues: Any?): Vertex {
        logger.debug { "Adding vertex with keyValues: ${keyValues.contentToString()}" }
        val properties = ElementHelper.asMap(keyValues)
        return addVertex(properties)
    }

    override fun addVertex(properties: Map<String, Any?>): Vertex {
        val id = ElementHelper.getIdValue(properties) ?: getNextId()
        val label = ElementHelper.getLabelValue(properties) ?: Vertex.DEFAULT_LABEL

        logger.debug { "Creating vertex with id: $id, label: $label, properties: $properties" }

        // Check if vertex with this ID already exists
        if (vertices.containsKey(id)) {
            logger.warn { "Attempt to create vertex with existing id: $id" }
            throw Graph.Exceptions.vertexWithIdAlreadyExists(id)
        }

        // Create the vertex
        val vertex = TinkerVertex(id, label, this)
        vertices[id] = vertex

        // Add properties (excluding reserved keys)
        ElementHelper.attachProperties(vertex, ElementHelper.removeReservedKeys(properties))

        logger.info { "Successfully created vertex with id: $id, total vertices: ${vertices.size}" }
        return vertex
    }

    override fun vertex(id: Any?): Vertex? {
        logger.debug { "Looking up vertex with id: $id" }
        val vertex = vertices[id]
        if (vertex == null) {
            logger.debug { "Vertex with id: $id not found" }
        }
        return vertex
    }

    override fun vertices(vararg vertexIds: Any?): Iterator<Vertex> {
        logger.debug { "Retrieving vertices: ${if (vertexIds.isEmpty()) "all" else vertexIds.contentToString()}" }
        return if (vertexIds.isEmpty()) {
            logger.debug { "Returning all ${vertices.size} vertices" }
            TinkerVertexIterator.all(this)
        } else {
            TinkerVertexIterator.byIds(this, *vertexIds)
        }
    }

    override fun edge(id: Any?): Edge? {
        logger.debug { "Looking up edge with id: $id" }
        val edge = edges[id]
        if (edge == null) {
            logger.debug { "Edge with id: $id not found" }
        }
        return edge
    }

    override fun edges(vararg edgeIds: Any?): Iterator<Edge> {
        logger.debug { "Retrieving edges: ${if (edgeIds.isEmpty()) "all" else edgeIds.contentToString()}" }
        return if (edgeIds.isEmpty()) {
            logger.debug { "Returning all ${edges.size} edges" }
            TinkerEdgeIterator.all(this)
        } else {
            TinkerEdgeIterator.byIds(this, *edgeIds)
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

        logger.debug { "Creating edge with id: $id, label: $label, from vertex: ${outVertex.id()} to vertex: ${inVertex.id()}" }

        // Check if edge with this ID already exists
        if (edges.containsKey(id)) {
            logger.warn { "Attempt to create edge with existing id: $id" }
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

        logger.info { "Successfully created edge with id: $id, total edges: ${edges.size}" }
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
        vertexCompositeIndex.removeElement(vertex)
        vertexRangeIndex.removeElement(vertex)
        vertexIndexCache.invalidateElement(vertex)

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
        (edge.outVertex() as TinkerVertex).removeOutEdge(edge)
        (edge.inVertex() as TinkerVertex).removeInEdge(edge)

        // Remove from edge index
        edgeIndex.removeElement(edge)
        edgeCompositeIndex.removeElement(edge)
        edgeRangeIndex.removeElement(edge)
        edgeIndexCache.invalidateElement(edge)

        // Remove from edges map
        edges.remove(edge.id())

        // Mark as removed
        edge.markEdgeRemoved()
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
    fun createIndex(key: String, elementClass: KClass<out Element>) {
        when (elementClass.simpleName) {
            "Vertex", "TinkerVertex" -> {
                vertexIndex.createKeyIndex(key)
                vertexIndex.rebuildIndex(key, vertices.values)
                vertexIndexCache.invalidateKey(key)
                vertexIndexOptimizer.clearSelectivityCache()
            }
            "Edge", "TinkerEdge" -> {
                edgeIndex.createKeyIndex(key)
                edgeIndex.rebuildIndex(key, edges.values)
                edgeIndexCache.invalidateKey(key)
                edgeIndexOptimizer.clearSelectivityCache()
            }
            else -> throw IllegalArgumentException("Class is not indexable: ${elementClass.simpleName}")
        }
    }

    /**
     * Create a composite index for faster multi-property queries.
     */
    fun createCompositeIndex(keys: List<String>, elementClass: KClass<out Element>) {
        when (elementClass.simpleName) {
            "Vertex", "TinkerVertex" -> {
                vertexCompositeIndex.createCompositeIndex(keys)
                vertexCompositeIndex.rebuildCompositeIndex(keys, vertices.values)
                vertexIndexCache.invalidateIndexType(IndexCache.IndexType.COMPOSITE)
                vertexIndexOptimizer.clearSelectivityCache()
            }
            "Edge", "TinkerEdge" -> {
                edgeCompositeIndex.createCompositeIndex(keys)
                edgeCompositeIndex.rebuildCompositeIndex(keys, edges.values)
                edgeIndexCache.invalidateIndexType(IndexCache.IndexType.COMPOSITE)
                edgeIndexOptimizer.clearSelectivityCache()
            }
            else -> throw IllegalArgumentException("Class is not indexable: ${elementClass.simpleName}")
        }
    }

    /**
     * Create a composite index for faster multi-property queries (vararg convenience).
     */
    fun createCompositeIndex(elementClass: KClass<out Element>, vararg keys: String) {
        createCompositeIndex(keys.toList(), elementClass)
    }

    /**
     * Create a range index for faster range queries on comparable properties.
     */
    fun createRangeIndex(key: String, elementClass: KClass<out Element>) {
        when (elementClass.simpleName) {
            "Vertex", "TinkerVertex" -> {
                vertexRangeIndex.createRangeIndex(key)
                vertexRangeIndex.rebuildRangeIndex(key, vertices.values)
                vertexIndexCache.invalidateKey(key)
                vertexIndexOptimizer.clearSelectivityCache()
            }
            "Edge", "TinkerEdge" -> {
                edgeRangeIndex.createRangeIndex(key)
                edgeRangeIndex.rebuildRangeIndex(key, edges.values)
                edgeIndexCache.invalidateKey(key)
                edgeIndexOptimizer.clearSelectivityCache()
            }
            else -> throw IllegalArgumentException("Class is not indexable: ${elementClass.simpleName}")
        }
    }

    /**
     * Drop an index.
     */
    fun dropIndex(key: String, elementClass: KClass<out Element>) {
        when (elementClass.simpleName) {
            "Vertex", "TinkerVertex" -> {
                vertexIndex.dropKeyIndex(key)
                vertexIndexCache.invalidateKey(key)
                vertexIndexOptimizer.clearSelectivityCache()
            }
            "Edge", "TinkerEdge" -> {
                edgeIndex.dropKeyIndex(key)
                edgeIndexCache.invalidateKey(key)
                edgeIndexOptimizer.clearSelectivityCache()
            }
            else -> throw IllegalArgumentException("Class is not indexable: ${elementClass.simpleName}")
        }
    }

    /**
     * Drop a composite index.
     */
    fun dropCompositeIndex(keys: List<String>, elementClass: KClass<out Element>) {
        when (elementClass.simpleName) {
            "Vertex", "TinkerVertex" -> {
                vertexCompositeIndex.dropCompositeIndex(keys)
                vertexIndexCache.invalidateIndexType(IndexCache.IndexType.COMPOSITE)
                vertexIndexOptimizer.clearSelectivityCache()
            }
            "Edge", "TinkerEdge" -> {
                edgeCompositeIndex.dropCompositeIndex(keys)
                edgeIndexCache.invalidateIndexType(IndexCache.IndexType.COMPOSITE)
                edgeIndexOptimizer.clearSelectivityCache()
            }
            else -> throw IllegalArgumentException("Class is not indexable: ${elementClass.simpleName}")
        }
    }

    /**
     * Drop a composite index (vararg convenience).
     */
    fun dropCompositeIndex(elementClass: KClass<out Element>, vararg keys: String) {
        dropCompositeIndex(keys.toList(), elementClass)
    }

    /**
     * Drop a range index.
     */
    fun dropRangeIndex(key: String, elementClass: KClass<out Element>) {
        when (elementClass.simpleName) {
            "Vertex", "TinkerVertex" -> {
                vertexRangeIndex.dropRangeIndex(key)
                vertexIndexCache.invalidateKey(key)
                vertexIndexOptimizer.clearSelectivityCache()
            }
            "Edge", "TinkerEdge" -> {
                edgeRangeIndex.dropRangeIndex(key)
                edgeIndexCache.invalidateKey(key)
                edgeIndexOptimizer.clearSelectivityCache()
            }
            else -> throw IllegalArgumentException("Class is not indexable: ${elementClass.simpleName}")
        }
    }

    /**
     * Get the indexed keys for a given element class.
     */
    fun getIndexedKeys(elementClass: KClass<out Element>): Set<String> {
        return when (elementClass.simpleName) {
            "Vertex", "TinkerVertex" -> vertexIndex.getIndexedKeys()
            "Edge", "TinkerEdge" -> edgeIndex.getIndexedKeys()
            else -> throw IllegalArgumentException("Class is not indexable: ${elementClass.simpleName}")
        }
    }

    /**
     * Get the property manager for advanced property operations.
     */
    fun propertyManager(): PropertyManager = propertyManager

    /**
     * Get the property query engine for advanced property querying.
     */
    fun propertyQueryEngine(): PropertyQueryEngine = propertyQueryEngine

    /**
     * Add a vertex property with explicit cardinality and meta-properties.
     */
    fun <V> addVertexProperty(
        vertex: TinkerVertex,
        key: String,
        value: V,
        cardinality: VertexProperty.Cardinality = defaultVertexPropertyCardinality,
        metaProperties: Map<String, Any?> = emptyMap(),
        id: Any? = null
    ): TinkerVertexProperty<V> {
        return propertyManager.addVertexProperty(vertex, key, value, cardinality, metaProperties, id)
    }

    /**
     * Query vertices by property criteria.
     */
    fun queryVertices(criteria: List<PropertyQueryEngine.PropertyCriterion>): Iterator<TinkerVertex> {
        return propertyQueryEngine.queryVertices(criteria)
    }

    /**
     * Query vertices by a single property criterion.
     */
    fun queryVertices(criterion: PropertyQueryEngine.PropertyCriterion): Iterator<TinkerVertex> {
        return propertyQueryEngine.queryVertices(criterion)
    }

    /**
     * Range query for numeric properties.
     */
    fun queryVerticesByRange(
        key: String,
        minValue: Number?,
        maxValue: Number?,
        inclusive: Boolean = true
    ): Iterator<TinkerVertex> {
        return propertyQueryEngine.queryVerticesByRange(key, minValue, maxValue, inclusive)
    }

    /**
     * Get comprehensive property statistics for the graph.
     */
    fun getPropertyStatistics(): Map<String, PropertyQueryEngine.GraphPropertyStats> {
        return propertyQueryEngine.getGraphPropertyStatistics()
    }

    /**
     * Get comprehensive indexing statistics for the graph.
     */
    fun getIndexingStatistics(): Map<String, Any> {
        return mapOf(
            "vertexIndices" to mapOf(
                "singleProperty" to vertexIndex.getStatistics(),
                "composite" to vertexCompositeIndex.getStatistics(),
                "range" to vertexRangeIndex.getStatistics(),
                "cache" to vertexIndexCache.getStatistics(),
                "optimizer" to vertexIndexOptimizer.getOptimizerStatistics()
            ),
            "edgeIndices" to mapOf(
                "singleProperty" to edgeIndex.getStatistics(),
                "composite" to edgeCompositeIndex.getStatistics(),
                "range" to edgeRangeIndex.getStatistics(),
                "cache" to edgeIndexCache.getStatistics(),
                "optimizer" to edgeIndexOptimizer.getOptimizerStatistics()
            )
        )
    }

    /**
     * Get index recommendations based on query patterns.
     */
    fun getIndexRecommendations(): Map<String, List<IndexOptimizer.IndexRecommendation>> {
        return mapOf(
            "vertices" to vertexIndexOptimizer.getIndexRecommendations(),
            "edges" to edgeIndexOptimizer.getIndexRecommendations()
        )
    }

    /**
     * Optimize index caches by removing expired entries.
     */
    fun optimizeIndexCaches() {
        vertexIndexCache.cleanupExpired()
        edgeIndexCache.cleanupExpired()
    }

    /**
     * Configure index cache settings.
     */
    fun configureIndexCache(
        maxSize: Int = 1000,
        maxAgeMs: Long = 300_000L
    ) {
        vertexIndexCache.setMaxSize(maxSize)
        vertexIndexCache.setMaxAge(maxAgeMs)
        edgeIndexCache.setMaxSize(maxSize)
        edgeIndexCache.setMaxAge(maxAgeMs)
    }

    /**
     * Get optimized query plan for vertex queries.
     */
    fun optimizeVertexQuery(criteria: List<PropertyQueryEngine.PropertyCriterion>): IndexOptimizer.QueryPlan {
        return vertexIndexOptimizer.optimizeQuery(criteria)
    }

    /**
     * Get optimized query plan for edge queries.
     */
    fun optimizeEdgeQuery(criteria: List<PropertyQueryEngine.PropertyCriterion>): IndexOptimizer.QueryPlan {
        return edgeIndexOptimizer.optimizeQuery(criteria)
    }

    companion object {
        /**
         * Logger instance for TinkerGraph operations.
         */
        private val logger = KotlinLogging.logger {}

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
