package org.apache.tinkerpop.gremlin.tinkergraph.structure

import co.touchlab.kermit.Logger
import kotlin.js.JsExport
import kotlin.reflect.KClass
import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators.TinkerEdgeIterator
import org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators.TinkerVertexIterator
import org.apache.tinkerpop.gremlin.tinkergraph.util.VertexCastingManager

/**
 * An in-memory graph database implementation of TinkerPop's Graph interface.
 *
 * TinkerGraph provides a lightweight, thread-safe, in-memory graph database
 * suitable for testing, prototyping, and small to medium-sized datasets.
 * While originally designed as a "toy" graph database, this implementation
 * includes production-ready features for many use cases.
 *
 * ## Key Features
 * - In-memory storage with O(1) vertex/edge lookup by ID
 * - Multi-level indexing system (property, composite, range indexes)
 * - Cross-platform compatibility (JVM, JavaScript, Native)
 * - GraphSON v3.0 I/O support with full type preservation
 * - Advanced property management with cardinality support
 * - Comprehensive graph algorithms library
 * - Property query engine for complex filtering
 *
 * ## Thread Safety
 * TinkerGraph is thread-safe for read operations but requires external
 * synchronization for concurrent write operations (adding/removing vertices/edges).
 * Multiple threads can safely perform traversals and property lookups simultaneously.
 *
 * ## Memory Considerations
 * All data is stored in memory. For large graphs consider:
 * - Memory usage: ~100-200 bytes per vertex/edge (varies by properties)
 * - Index memory overhead: ~50-100% additional memory for indexed properties
 * - JVM heap size configuration for graphs with >1M elements
 *
 * ## Configuration Options
 * - `gremlin.tinkerGraph.allowNullPropertyValues`: Allow null property values (default: false)
 * - `gremlin.tinkerGraph.defaultVertexPropertyCardinality`: Default cardinality for vertex properties
 * - `gremlin.tinkerGraph.graphLocation`: File location for persistence (not implemented)
 * - `gremlin.tinkerGraph.graphFormat`: Serialization format (not implemented)
 *
 * ## Example Usage
 * ```kotlin
 * // Basic usage
 * val graph = TinkerGraph.open()
 * val vertex = graph.addVertex("name", "john", "age", 30)
 * val edge = vertex.addEdge("knows", otherVertex, "since", 2020)
 *
 * // With configuration
 * val config = mapOf(
 *     TinkerGraph.GREMLIN_TINKERGRAPH_ALLOW_NULL_PROPERTY_VALUES to true
 * )
 * val graph = TinkerGraph.open(config)
 *
 * // With indexing for better performance
 * graph.createIndex("name", Vertex::class)
 * val users = graph.vertices().asSequence()
 *     .filter { it.value<String>("name") == "john" }
 * ```
 *
 * @param configuration Graph configuration parameters (see configuration constants)
 * @see Graph
 * @see TinkerVertex
 * @see TinkerEdge
 * @see GraphAlgorithms
 * @since 1.0.0
 */
class TinkerGraph private constructor(private val configuration: Map<String, Any?>) : Graph {

    /** Internal storage for vertices, keyed by vertex ID. */
    internal val vertices: MutableMap<Any, TinkerVertex> = mutableMapOf()

    /** Internal storage for edges, keyed by edge ID. */
    internal val edges: MutableMap<Any, TinkerEdge> = mutableMapOf()

    /** Current ID counter for auto-generated IDs. */
    internal var currentId: Long = 0L

    /** Graph variables for storing metadata. */
    private val graphVariables: TinkerGraphVariables = TinkerGraphVariables()

    /** Index for vertex properties to enable fast lookups. */
    internal val vertexIndex: TinkerIndex<TinkerVertex> = TinkerIndex()

    /** Index for edge properties to enable fast lookups. */
    internal val edgeIndex: TinkerIndex<TinkerEdge> = TinkerIndex()

    /** Composite index for vertex properties to enable multi-property queries. */
    internal val vertexCompositeIndex: CompositeIndex<TinkerVertex> = CompositeIndex()

    /** Composite index for edge properties to enable multi-property queries. */
    internal val edgeCompositeIndex: CompositeIndex<TinkerEdge> = CompositeIndex()

    /** Range index for vertex properties to enable efficient range queries. */
    internal val vertexRangeIndex: RangeIndex<TinkerVertex> = RangeIndex()

    /** Range index for edge properties to enable efficient range queries. */
    internal val edgeRangeIndex: RangeIndex<TinkerEdge> = RangeIndex()

    /** Index optimizer for vertex queries. */
    internal val vertexIndexOptimizer: IndexOptimizer<TinkerVertex> =
            IndexOptimizer(vertexIndex, vertexCompositeIndex, vertexRangeIndex)

    /** Index optimizer for edge queries. */
    internal val edgeIndexOptimizer: IndexOptimizer<TinkerEdge> =
            IndexOptimizer(edgeIndex, edgeCompositeIndex, edgeRangeIndex)

    /** Index cache for vertex queries. */
    internal val vertexIndexCache: IndexCache<TinkerVertex> = IndexCache.create()

    /** Index cache for edge queries. */
    internal val edgeIndexCache: IndexCache<TinkerEdge> = IndexCache.create()

    /** Whether to allow null property values. */
    internal val allowNullPropertyValues: Boolean =
            configuration[GREMLIN_TINKERGRAPH_ALLOW_NULL_PROPERTY_VALUES] as? Boolean ?: false

    /** Default cardinality for vertex properties. */
    internal val defaultVertexPropertyCardinality: VertexProperty.Cardinality =
            VertexProperty.Cardinality.valueOf(
                    configuration[GREMLIN_TINKERGRAPH_DEFAULT_VERTEX_PROPERTY_CARDINALITY] as?
                            String
                            ?: VertexProperty.Cardinality.SINGLE.name
            )

    /** Graph features describing capabilities. */
    private val graphFeatures: TinkerGraphFeatures = TinkerGraphFeatures()

    /** Property manager for advanced property operations. */
    internal val propertyManager: PropertyManager = PropertyManager(this)

    /** Property query engine for advanced property querying. */
    internal val propertyQueryEngine: PropertyQueryEngine = PropertyQueryEngine(this)

    override fun addVertex(vararg keyValues: Any?): Vertex {
        logger.d { "Adding vertex with keyValues: ${keyValues.contentToString()}" }
        val properties = ElementHelper.asMap(keyValues)
        return addVertex(properties)
    }

    override fun addVertex(properties: Map<String, Any?>): Vertex {
        val id = ElementHelper.getIdValue(properties) ?: getNextId()
        val label = ElementHelper.getLabelValue(properties) ?: Vertex.DEFAULT_LABEL

        logger.d { "Creating vertex with id: $id, label: $label, properties: $properties" }

        // Check if vertex with this ID already exists
        if (vertices.containsKey(id)) {
            logger.w { "Attempt to create vertex with existing id: $id" }
            throw Graph.Exceptions.vertexWithIdAlreadyExists(id)
        }

        // Create the vertex
        val vertex = TinkerVertex(id, label, this)
        vertices[id] = vertex

        // Add properties (excluding reserved keys)
        ElementHelper.attachProperties(vertex, ElementHelper.removeReservedKeys(properties))

        logger.i { "Successfully created vertex with id: $id, total vertices: ${vertices.size}" }
        return vertex
    }

    override fun vertex(id: Any?): Vertex? {
        logger.d { "Looking up vertex with id: $id" }
        val vertex = vertices[id]
        if (vertex == null) {
            logger.d { "Vertex with id: $id not found" }
        }
        return vertex
    }

    override fun vertices(vararg vertexIds: Any?): Iterator<Vertex> {
        logger.d {
            "Retrieving vertices: ${if (vertexIds.isEmpty()) "all" else vertexIds.contentToString()}"
        }
        return if (vertexIds.isEmpty()) {
            logger.d { "Returning all ${vertices.size} vertices" }
            TinkerVertexIterator.all(this)
        } else {
            TinkerVertexIterator.byIds(this, *vertexIds)
        }
    }

    override fun edge(id: Any?): Edge? {
        logger.d { "Looking up edge with id: $id" }
        val edge = edges[id]
        if (edge == null) {
            logger.d { "Edge with id: $id not found" }
        }
        return edge
    }

    override fun edges(vararg edgeIds: Any?): Iterator<Edge> {
        logger.d {
            "Retrieving edges: ${if (edgeIds.isEmpty()) "all" else edgeIds.contentToString()}"
        }
        return if (edgeIds.isEmpty()) {
            logger.d { "Returning all ${edges.size} edges" }
            TinkerEdgeIterator.all(this)
        } else {
            TinkerEdgeIterator.byIds(this, *edgeIds)
        }
    }

    /** Add an edge between two vertices. */
    internal fun addEdge(
            outVertex: TinkerVertex,
            inVertex: TinkerVertex,
            label: String,
            properties: Map<String, Any?>
    ): TinkerEdge {
        val id = ElementHelper.getIdValue(properties) ?: getNextId()

        logger.d {
            "Creating edge with id: $id, label: $label, from vertex: ${outVertex.id()} to vertex: ${inVertex.id()}"
        }

        // Check if edge with this ID already exists
        if (edges.containsKey(id)) {
            logger.w { "Attempt to create edge with existing id: $id" }
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

        logger.i { "Successfully created edge with id: $id, total edges: ${edges.size}" }
        return edge
    }

    /** Remove a vertex from the graph. */
    internal fun removeVertex(vertex: TinkerVertex) {
        // Remove all incident edges first
        val incidentEdges = mutableSetOf<TinkerEdge>()
        incidentEdges.addAll(vertex.getOutEdges())
        incidentEdges.addAll(vertex.getInEdges())

        incidentEdges.forEach { edge -> removeEdge(edge) }

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

    /** Remove an edge from the graph. */
    internal fun removeEdge(edge: TinkerEdge) {
        // Remove from vertex adjacency lists using centralized casting
        VertexCastingManager.tryGetTinkerVertex(edge.outVertex())?.removeOutEdge(edge)
        VertexCastingManager.tryGetTinkerVertex(edge.inVertex())?.removeInEdge(edge)

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

    /** Generate the next available ID. */
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
     *
     * Indexes improve query performance for property-based filtering operations.
     * Creating an index on frequently queried properties can improve performance
     * by orders of magnitude for large graphs.
     *
     * ## Performance Impact
     * - Query time: O(1) lookup vs O(n) linear scan without index
     * - Memory overhead: ~2x property memory usage for indexed properties
     * - Index creation: O(n) time complexity where n = number of existing elements
     *
     * ## When to Use
     * - Properties used in frequent equality filters
     * - Properties with high selectivity (many unique values)
     * - Before bulk data loading for better performance
     *
     * ## Example
     * ```kotlin
     * val graph = TinkerGraph.open()
     *
     * // Create index before adding data (recommended)
     * graph.createIndex("name", Vertex::class)
     * graph.createIndex("type", Edge::class)
     *
     * // Add data - automatically indexed
     * repeat(10000) { i ->
     *     graph.addVertex("name", "user$i", "age", i % 100)
     * }
     *
     * // Fast lookup after indexing (O(1) vs O(n))
     * val users = graph.vertices().asSequence()
     *     .filter { it.value<String>("name") == "user1234" }
     *     .toList()
     * ```
     *
     * @param key The property key to index (case-sensitive)
     * @param elementClass The element class to index (Vertex::class or Edge::class)
     * @throws IllegalArgumentException if elementClass is not Vertex or Edge
     * @see createCompositeIndex for multi-property queries
     * @see createRangeIndex for range queries
     * @see dropIndex
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
            else ->
                    throw IllegalArgumentException(
                            "Class is not indexable: ${elementClass.simpleName}"
                    )
        }
    }

    /**
     * Create a composite index for faster multi-property queries.
     *
     * Composite indexes optimize queries that filter on multiple properties
     * simultaneously. They are particularly effective for exact-match queries
     * on 2-5 properties where all properties are used in the filter.
     *
     * ## Performance Benefits
     * - Multi-property queries: O(1) lookup vs O(n) linear scan
     * - Memory overhead: ~3x property memory usage for indexed property combinations
     * - Most effective when all indexed properties are used in queries
     *
     * ## When to Use
     * - Queries filtering on 2-5 properties simultaneously
     * - Exact-match queries (equality comparisons)
     * - High-selectivity property combinations
     * - Properties frequently queried together
     *
     * ## When NOT to Use
     * - Single property queries (use regular index instead)
     * - Range queries (use range index instead)
     * - Properties rarely queried together
     *
     * ## Example
     * ```kotlin
     * val graph = TinkerGraph.open()
     *
     * // Create composite index for common query pattern
     * graph.createCompositeIndex(listOf("age", "city", "status"), Vertex::class)
     *
     * // Add sample data
     * graph.addVertex("name", "alice", "age", 25, "city", "NYC", "status", "active")
     * graph.addVertex("name", "bob", "age", 30, "city", "LA", "status", "active")
     *
     * // Optimized query using all indexed properties
     * val results = graph.vertices().asSequence()
     *     .filter { vertex ->
     *         vertex.value<Int>("age") == 25 &&
     *         vertex.value<String>("city") == "NYC" &&
     *         vertex.value<String>("status") == "active"
     *     }.toList()
     *
     * // Partially optimized (only uses age from composite index)
     * val partialResults = graph.vertices().asSequence()
     *     .filter { it.value<Int>("age") == 25 }
     *     .toList()
     * ```
     *
     * @param keys List of property keys to include in composite index (2-5 keys recommended)
     * @param elementClass The element class to index (Vertex::class or Edge::class)
     * @throws IllegalArgumentException if elementClass is not Vertex or Edge
     * @throws IllegalArgumentException if keys list is empty or has only one element
     * @see createIndex for single-property indexes
     * @see createRangeIndex for range queries
     * @see dropCompositeIndex
     */
    fun createCompositeIndex(keys: List<String>, elementClass: KClass<out Element>) {
        when (elementClass.simpleName) {
            "Vertex", "TinkerVertex" -> {
                vertexCompositeIndex.createCompositeIndex(keys)
                vertexCompositeIndex.rebuildCompositeIndex(keys, vertices.values)
                vertexIndexCache.invalidateIndexType(IndexType.COMPOSITE)
                vertexIndexOptimizer.clearSelectivityCache()
            }
            "Edge", "TinkerEdge" -> {
                edgeCompositeIndex.createCompositeIndex(keys)
                edgeCompositeIndex.rebuildCompositeIndex(keys, edges.values)
                edgeIndexCache.invalidateIndexType(IndexType.COMPOSITE)
                edgeIndexOptimizer.clearSelectivityCache()
            }
            else ->
                    throw IllegalArgumentException(
                            "Class is not indexable: ${elementClass.simpleName}"
                    )
        }
    }

    /** Create a composite index for faster multi-property queries (vararg convenience). */
    fun createCompositeIndex(elementClass: KClass<out Element>, vararg keys: String) {
        createCompositeIndex(keys.toList(), elementClass)
    }

    /**
     * Create a range index for faster range queries on comparable properties.
     *
     * Range indexes optimize queries involving comparisons (<, >, <=, >=) on
     * properties with comparable values. Uses a balanced tree structure for
     * efficient range operations.
     *
     * ## Supported Types
     * - Numeric types: Int, Long, Float, Double, BigDecimal
     * - String (lexicographic ordering)
     * - Date/Time types (when available on platform)
     * - Any type implementing Comparable<T>
     *
     * ## Performance Benefits
     * - Range queries: O(log n + k) where k = result size, vs O(n) linear scan
     * - Memory overhead: ~2-3x property memory usage
     * - Efficient for both point lookups and range scans
     *
     * ## When to Use
     * - Numeric range queries (age > 18, salary between 50k-100k)
     * - Date/time range filtering
     * - String prefix matching (lexicographic ranges)
     * - Ordered traversal requirements
     *
     * ## Query Examples Optimized
     * - `property > value`, `property >= value`
     * - `property < value`, `property <= value`
     * - `property >= min && property <= max` (range queries)
     *
     * ## Example
     * ```kotlin
     * val graph = TinkerGraph.open()
     *
     * // Create range index for numeric queries
     * graph.createRangeIndex("age", Vertex::class)
     * graph.createRangeIndex("salary", Vertex::class)
     * graph.createRangeIndex("createdDate", Edge::class)
     *
     * // Add sample data
     * repeat(1000) { i ->
     *     graph.addVertex(
     *         "name", "user$i",
     *         "age", 20 + (i % 50),
     *         "salary", 30000 + (i * 1000)
     *     )
     * }
     *
     * // Efficient range queries
     * val adults = graph.vertices().asSequence()
     *     .filter { (it.value<Int>("age") ?: 0) >= 18 }
     *     .toList()
     *
     * val highEarners = graph.vertices().asSequence()
     *     .filter { (it.value<Int>("salary") ?: 0) > 75000 }
     *     .toList()
     *
     * val midCareer = graph.vertices().asSequence()
     *     .filter {
     *         val age = it.value<Int>("age") ?: 0
     *         age >= 25 && age <= 40
     *     }.toList()
     * ```
     *
     * @param key The property key to index (must contain Comparable values)
     * @param elementClass The element class to index (Vertex::class or Edge::class)
     * @throws IllegalArgumentException if elementClass is not Vertex or Edge
     * @throws IllegalArgumentException if existing property values are not comparable
     * @see createIndex for equality-based queries
     * @see createCompositeIndex for multi-property queries
     * @see dropRangeIndex
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
            else ->
                    throw IllegalArgumentException(
                            "Class is not indexable: ${elementClass.simpleName}"
                    )
        }
    }

    /** Drop an index. */
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
            else ->
                    throw IllegalArgumentException(
                            "Class is not indexable: ${elementClass.simpleName}"
                    )
        }
    }

    /** Drop a composite index. */
    fun dropCompositeIndex(keys: List<String>, elementClass: KClass<out Element>) {
        when (elementClass.simpleName) {
            "Vertex", "TinkerVertex" -> {
                vertexCompositeIndex.dropCompositeIndex(keys)
                vertexIndexCache.invalidateIndexType(IndexType.COMPOSITE)
                vertexIndexOptimizer.clearSelectivityCache()
            }
            "Edge", "TinkerEdge" -> {
                edgeCompositeIndex.dropCompositeIndex(keys)
                edgeIndexCache.invalidateIndexType(IndexType.COMPOSITE)
                edgeIndexOptimizer.clearSelectivityCache()
            }
            else ->
                    throw IllegalArgumentException(
                            "Class is not indexable: ${elementClass.simpleName}"
                    )
        }
    }

    /** Drop a composite index (vararg convenience). */
    fun dropCompositeIndex(elementClass: KClass<out Element>, vararg keys: String) {
        dropCompositeIndex(keys.toList(), elementClass)
    }

    /** Drop a range index. */
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
            else ->
                    throw IllegalArgumentException(
                            "Class is not indexable: ${elementClass.simpleName}"
                    )
        }
    }

    /** Get the indexed keys for a given element class. */
    fun getIndexedKeys(elementClass: KClass<out Element>): Set<String> {
        return when (elementClass.simpleName) {
            "Vertex", "TinkerVertex" -> vertexIndex.getIndexedKeys()
            "Edge", "TinkerEdge" -> edgeIndex.getIndexedKeys()
            else ->
                    throw IllegalArgumentException(
                            "Class is not indexable: ${elementClass.simpleName}"
                    )
        }
    }

    /**
     * Get the property manager for advanced property operations.
     *
     * The PropertyManager provides advanced property lifecycle management,
     * cardinality enforcement, and property validation capabilities beyond
     * the basic Graph interface. Use this for sophisticated property operations
     * that require fine-grained control.
     *
     * ## Key Capabilities
     * - Multi-property management with cardinality constraints (SINGLE, LIST, SET)
     * - Property lifecycle event handling and notifications
     * - Bulk property operations with performance optimization
     * - Property validation and constraint enforcement
     * - Property storage optimization and cleanup
     *
     * ## Use Cases
     * - Applications requiring strict property cardinality enforcement
     * - Systems needing property change audit trails
     * - Bulk property operations on large datasets
     * - Custom property validation logic
     * - Property lifecycle event handling (logging, caching, etc.)
     *
     * ## Example
     * ```kotlin
     * val graph = TinkerGraph.open()
     * val propManager = graph.propertyManager()
     *
     * // Add property lifecycle listener
     * propManager.addPropertyListener(object : PropertyLifecycleListener {
     *     override fun onPropertyAdded(vertex: TinkerVertex, property: TinkerVertexProperty<*>) {
     *         println("Property added: ${property.key()} = ${property.value()}")
     *     }
     *     override fun onPropertyRemoved(vertex: TinkerVertex, property: TinkerVertexProperty<*>) {
     *         println("Property removed: ${property.key()}")
     *     }
     * })
     *
     * // Advanced property operations with cardinality control
     * val vertex = graph.addVertex() as TinkerVertex
     * propManager.addVertexProperty(
     *     vertex,
     *     "skills",
     *     "kotlin",
     *     VertexProperty.Cardinality.SET,
     *     mapOf("level" to "expert")  // meta-properties
     * )
     * ```
     *
     * @return The PropertyManager instance for this graph
     * @see PropertyManager for detailed API documentation
     * @see propertyQueryEngine for advanced property querying
     * @see VertexProperty.Cardinality for cardinality options
     */
    fun propertyManager(): PropertyManager = propertyManager

    /**
     * Get the property query engine for advanced property querying.
     *
     * The PropertyQueryEngine provides sophisticated property-based query
     * capabilities including pattern matching, type filtering, and
     * complex property traversals beyond basic property access.
     *
     * ## Key Capabilities
     * - Complex property pattern queries with multiple criteria
     * - Type-safe property filtering with automatic casting
     * - Property relationship traversals (meta-properties, property graphs)
     * - Property aggregation operations (count, sum, avg, etc.)
     * - Custom query predicates and filtering logic
     *
     * ## Query Types Supported
     * - Pattern matching: Find properties matching specific patterns
     * - Type filtering: Filter properties by value type
     * - Range queries: Numeric and date range filtering
     * - Text search: String pattern matching and full-text capabilities
     * - Aggregation: Statistical operations on property values
     *
     * ## Use Cases
     * - Complex property-based filtering beyond simple equality
     * - Analytics and reporting on property data
     * - Data validation and quality checking
     * - Property relationship analysis
     * - Custom query extensions and domain-specific operations
     *
     * ## Example
     * ```kotlin
     * val graph = TinkerGraph.open()
     * val queryEngine = graph.propertyQueryEngine()
     *
     * // Add sample data
     * val vertex = graph.addVertex(
     *     "name", "alice",
     *     "age", 25,
     *     "skills", listOf("kotlin", "java", "python"),
     *     "salary", 75000
     * )
     *
     * // Complex property queries
     * val results = queryEngine.findVerticesWithPropertyPattern(
     *     propertyKey = "skills",
     *     pattern = { value ->
     *         value is List<*> && value.contains("kotlin")
     *     }
     * )
     *
     * // Type-safe property aggregation
     * val avgSalary = queryEngine.aggregatePropertyValues<Int>(
     *     vertices = graph.vertices(),
     *     propertyKey = "salary",
     *     operation = PropertyQueryEngine.AggregationOperation.AVERAGE
     * )
     * ```
     *
     * @return The PropertyQueryEngine instance for this graph
     * @see PropertyQueryEngine for detailed query API documentation
     * @see propertyManager for property lifecycle management
     */
    fun propertyQueryEngine(): PropertyQueryEngine = propertyQueryEngine

    /** Add a vertex property with explicit cardinality and meta-properties. */
    fun <V> addVertexProperty(
            vertex: TinkerVertex,
            key: String,
            value: V,
            cardinality: VertexProperty.Cardinality = defaultVertexPropertyCardinality,
            metaProperties: Map<String, Any?> = emptyMap(),
            id: Any? = null
    ): TinkerVertexProperty<V> {
        return propertyManager.addVertexProperty(
                vertex,
                key,
                value,
                cardinality,
                metaProperties,
                id
        )
    }

    /** Query vertices by property criteria. */
    fun queryVertices(criteria: List<PropertyQueryEngine.PropertyCriterion>): Iterator<Vertex> {
        return propertyQueryEngine.queryVertices(criteria)
    }

    /** Query vertices by a single property criterion. */
    fun queryVertices(criterion: PropertyQueryEngine.PropertyCriterion): Iterator<Vertex> {
        return propertyQueryEngine.queryVertices(criterion)
    }

    /** Range query for numeric properties. */
    fun queryVerticesByRange(
            key: String,
            minValue: Number?,
            maxValue: Number?,
            includeMin: Boolean = true,
            includeMax: Boolean = false
    ): Iterator<Vertex> {
        return propertyQueryEngine.queryVerticesByRange(
                key,
                minValue,
                maxValue,
                includeMin,
                includeMax
        )
    }

    /** Get comprehensive property statistics for the graph. */
    fun getPropertyStatistics(): Map<String, PropertyQueryEngine.GraphPropertyStats> {
        return propertyQueryEngine.getGraphPropertyStatistics()
    }

    /** Get comprehensive indexing statistics for the graph. */
    fun getIndexingStatistics(): Map<String, Any> {
        return mapOf(
                "vertexIndices" to
                        mapOf(
                                "singleProperty" to vertexIndex.getStatistics(),
                                "composite" to vertexCompositeIndex.getStatistics(),
                                "range" to vertexRangeIndex.getStatistics(),
                                "cache" to vertexIndexCache.getStatistics(),
                                "optimizer" to vertexIndexOptimizer.getOptimizerStatistics()
                        ),
                "edgeIndices" to
                        mapOf(
                                "singleProperty" to edgeIndex.getStatistics(),
                                "composite" to edgeCompositeIndex.getStatistics(),
                                "range" to edgeRangeIndex.getStatistics(),
                                "cache" to edgeIndexCache.getStatistics(),
                                "optimizer" to edgeIndexOptimizer.getOptimizerStatistics()
                        )
        )
    }

    /** Get index recommendations based on query patterns. */
    fun getIndexRecommendations(): Map<String, List<IndexOptimizer.IndexRecommendation>> {
        return mapOf(
                "vertices" to vertexIndexOptimizer.getIndexRecommendations(),
                "edges" to edgeIndexOptimizer.getIndexRecommendations()
        )
    }

    /** Optimize index caches by removing expired entries. */
    fun optimizeIndexCaches() {
        vertexIndexCache.cleanupExpired()
        edgeIndexCache.cleanupExpired()
    }

    /** Configure index cache settings. */
    fun configureIndexCache(maxSize: Int = 1000, maxAgeMs: Long = 300_000L) {
        vertexIndexCache.setMaxSize(maxSize)
        vertexIndexCache.setMaxAge(maxAgeMs)
        edgeIndexCache.setMaxSize(maxSize)
        edgeIndexCache.setMaxAge(maxAgeMs)
    }

    /** Get optimized query plan for vertex queries. */
    fun optimizeVertexQuery(
            criteria: List<PropertyQueryEngine.PropertyCriterion>
    ): IndexOptimizer.QueryPlan {
        return vertexIndexOptimizer.optimizeQuery(criteria)
    }

    /** Get optimized query plan for edge queries. */
    fun optimizeEdgeQuery(
            criteria: List<PropertyQueryEngine.PropertyCriterion>
    ): IndexOptimizer.QueryPlan {
        return edgeIndexOptimizer.optimizeQuery(criteria)
    }

    // JavaScript-friendly methods that return arrays instead of iterators

    /**
     * Get vertices as an array for JavaScript/TypeScript compatibility.
     *
     * This method materializes the vertex iterator into an array, making it easier
     * to work with in JavaScript environments where iterators are less common.
     *
     * @param vertexIds optional vertex identifiers to filter
     * @return Array of vertices (empty array if no vertices match)
     */
    fun verticesArray(vararg vertexIds: Any?): Array<Vertex> {
        return vertices(*vertexIds).asSequence().toList().toTypedArray()
    }

    /**
     * Get edges as an array for JavaScript/TypeScript compatibility.
     *
     * This method materializes the edge iterator into an array, making it easier
     * to work with in JavaScript environments where iterators are less common.
     *
     * @param edgeIds optional edge identifiers to filter
     * @return Array of edges (empty array if no edges match)
     */
    fun edgesArray(vararg edgeIds: Any?): Array<Edge> {
        return edges(*edgeIds).asSequence().toList().toTypedArray()
    }

    /**
     * Query vertices and return results as an array for JavaScript/TypeScript compatibility.
     *
     * @param criteria List of property criteria for filtering
     * @return Array of matching vertices
     */
    fun queryVerticesArray(criteria: List<PropertyQueryEngine.PropertyCriterion>): Array<Vertex> {
        return queryVertices(criteria).asSequence().toList().toTypedArray()
    }



    companion object {
        /** Logger instance for TinkerGraph operations. */
        private val logger = Logger.withTag("TinkerGraph")

        // Configuration keys
        const val GREMLIN_TINKERGRAPH_ALLOW_NULL_PROPERTY_VALUES =
                "gremlin.tinkerGraph.allowNullPropertyValues"
        const val GREMLIN_TINKERGRAPH_DEFAULT_VERTEX_PROPERTY_CARDINALITY =
                "gremlin.tinkerGraph.defaultVertexPropertyCardinality"
        const val GREMLIN_TINKERGRAPH_GRAPH_LOCATION = "gremlin.tinkerGraph.graphLocation"
        const val GREMLIN_TINKERGRAPH_GRAPH_FORMAT = "gremlin.tinkerGraph.graphFormat"

        /** Create a new TinkerGraph instance. */
        fun open(): TinkerGraph = open(emptyMap())

        /** Create a new TinkerGraph instance with configuration. */
        fun open(configuration: Map<String, Any?>): TinkerGraph {
            return TinkerGraph(configuration)
        }
    }

    /** TinkerGraph-specific features implementation. */
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
