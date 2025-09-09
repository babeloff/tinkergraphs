package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.util.SafeCasting
import org.apache.tinkerpop.gremlin.tinkergraph.util.VertexCastingManager
import org.apache.tinkerpop.gremlin.tinkergraph.util.LoggingConfig

/**
 * PropertyQueryEngine provides advanced querying capabilities for properties in TinkerGraph.
 * It supports complex property filtering, range queries, and composite queries.
 *
 * This implementation uses liberal input parameters and handles vertex casting internally
 * to eliminate ClassCastException issues, especially on the JavaScript platform.
 */
class PropertyQueryEngine(private val graph: TinkerGraph) {

    companion object {
        private val logger = LoggingConfig.getLogger<PropertyQueryEngine>()
    }

    /**
     * Base interface for property criteria.
     */
    sealed interface PropertyCriterion

    /**
     * Exact value match criterion.
     */
    data class ExactCriterion(val key: String, val value: Any?) : PropertyCriterion

    /**
     * Range criterion for numeric values.
     * Follows TinkerPop semantics: [min, max) - inclusive on min, exclusive on max.
     */
    data class RangeCriterion(
        val key: String,
        val minValue: Number?,
        val maxValue: Number?,
        val includeMin: Boolean = true,
        val includeMax: Boolean = false
    ) : PropertyCriterion

    /**
     * Property exists criterion.
     */
    data class ExistsCriterion(val key: String) : PropertyCriterion

    /**
     * Property does not exist criterion.
     */
    data class NotExistsCriterion(val key: String) : PropertyCriterion

    /**
     * String contains or collection contains criterion.
     */
    data class ContainsCriterion(
        val key: String,
        val substring: String? = null,
        val element: Any? = null,
        val ignoreCase: Boolean = false
    ) : PropertyCriterion

    /**
     * Regular expression match criterion.
     */
    data class RegexCriterion(
        val key: String,
        val pattern: Regex
    ) : PropertyCriterion

    /**
     * Composite criterion combining multiple criteria with logical operators.
     */
    data class CompositeCriterion(
        val operator: LogicalOperator,
        val criteria: List<PropertyCriterion>
    ) : PropertyCriterion

    /**
     * Logical operators for combining criteria.
     */
    enum class LogicalOperator {
        AND, OR, NOT
    }

    /**
     * Property aggregation operations.
     */
    enum class PropertyAggregation {
        COUNT, DISTINCT_COUNT, MIN, MAX, SUM, AVERAGE
    }

    /**
     * Statistics for a property across the graph.
     */
    data class GraphPropertyStats(
        val propertyCount: Int,
        val vertexCount: Int,
        val hasMetaProperties: Boolean,
        val cardinalityDistribution: Map<VertexProperty.Cardinality, Int>
    )

    /**
     * Query vertices by property criteria with support for multiple conditions.
     * Uses optimized indices and caching for better performance.
     *
     * Returns Vertex interface types to avoid casting issues at call sites.
     * Internal casting is handled safely by VertexCastingManager.
     */
    fun queryVertices(criteria: List<PropertyCriterion>): Iterator<Vertex> {
        // Use optimizer to record statistics and get optimization plan
        if (criteria.isNotEmpty()) {
            graph.optimizeVertexQuery(criteria)
        }

        // Use centralized vertex casting for safety
        val allVertices = safeGetVertices()

        val filteredVertices = allVertices.filter { vertex ->
            criteria.all { criterion -> evaluateCriterion(vertex, criterion) }
        }

        // Convert to Vertex interface to avoid casting issues at call sites
        return filteredVertices.asSequence().map { it as Vertex }.iterator()
    }

    /**
     * Single criterion convenience method.
     */
    fun queryVertices(criterion: PropertyCriterion): Iterator<Vertex> {
        return queryVertices(listOf(criterion))
    }

    /**
     * Query vertex properties that satisfy given criteria.
     *
     * @param vertex the vertex to query (accepts Vertex interface)
     * @param criteria property criteria to evaluate
     */
    fun <V> queryVertexProperties(
        vertex: Vertex,
        criteria: List<PropertyCriterion>
    ): List<VertexProperty<V>> {
        val tinkerVertex = VertexCastingManager.tryGetTinkerVertex(vertex) ?: return emptyList()

        return tinkerVertex.properties<V>().asSequence().filter { property ->
            criteria.any { criterion -> evaluatePropertyCriterion(property, criterion) }
        }.toList()
    }

    /**
     * Range query for numeric properties using optimized range index.
     * Follows TinkerPop semantics: [min, max) - inclusive on min, exclusive on max by default.
     */
    fun queryVerticesByRange(
        key: String,
        minValue: Number?,
        maxValue: Number?,
        includeMin: Boolean = true,
        includeMax: Boolean = false
    ): Iterator<Vertex> {
        val cacheKey = "range_${key}_${minValue}_${maxValue}_${includeMin}_${includeMax}"

        // Check cache first
        val cached = graph.vertexIndexCache.get(IndexType.RANGE, cacheKey)
        if (cached != null) {
            return cached.iterator()
        }

        // Use range index if available
        val result: Set<Vertex> = if (graph.vertexRangeIndex.isRangeIndexed(key)) {
            val minComparable = RangeIndex.safeComparable(minValue)
            val maxComparable = RangeIndex.safeComparable(maxValue)
            graph.vertexRangeIndex.rangeQuery(key, minComparable, maxComparable, includeMin, includeMax).toSet()
        } else {
            // Fall back to criterion-based query
            val criterion = RangeCriterion(key, minValue, maxValue, includeMin, includeMax)
            queryVertices(listOf(criterion)).asSequence().toSet()
        }

        // Cache result
        graph.vertexIndexCache.put(IndexType.RANGE, cacheKey, result.map { it as TinkerVertex }.toSet())

        return result.iterator()
    }

    /**
     * Query vertices that have properties with specific meta-properties.
     */
    fun queryVerticesByMetaProperty(
        propertyKey: String,
        metaPropertyKey: String,
        metaPropertyValue: Any?
    ): Iterator<Vertex> {
        val allVertices = safeGetVertices()

        val filteredVertices = allVertices.filter { vertex ->
            val properties = vertex.getVertexProperties<Any>(propertyKey)
            properties.any { property ->
                if (metaPropertyValue == null) {
                    property.property<Any>(metaPropertyKey).isPresent()
                } else {
                    property.value<Any>(metaPropertyKey) == metaPropertyValue
                }
            }
        }

        return filteredVertices.asSequence().map { it as Vertex }.iterator()
    }

    /**
     * Query vertices by property cardinality.
     */
    fun queryVerticesByCardinality(
        key: String,
        cardinality: VertexProperty.Cardinality
    ): Iterator<Vertex> {
        val allVertices = safeGetVertices()

        val filteredVertices = allVertices.filter { vertex ->
            vertex.getPropertyCardinality(key) == cardinality
        }

        return filteredVertices.asSequence().map { it as Vertex }.iterator()
    }

    /**
     * Get aggregated values for a specific property across all vertices.
     */
    fun aggregateProperties(key: String, aggregation: PropertyAggregation): Any {
        val allVertices = safeGetVertices()

        val values = allVertices.flatMap { vertex ->
            vertex.getVertexProperties<Any>(key).map { it.value() }
        }.toList()

        return when (aggregation) {
            PropertyAggregation.COUNT -> values.size
            PropertyAggregation.DISTINCT_COUNT -> values.toSet().size
            PropertyAggregation.MIN -> {
                val numericValues = values.mapNotNull { value ->
                    when (value) {
                        is Number -> value.toDouble()
                        else -> null
                    }
                }
                if (numericValues.isNotEmpty()) numericValues.minOrNull() ?: 0.0 else 0.0
            }
            PropertyAggregation.MAX -> {
                val numericValues = values.mapNotNull { value ->
                    when (value) {
                        is Number -> value.toDouble()
                        else -> null
                    }
                }
                if (numericValues.isNotEmpty()) numericValues.maxOrNull() ?: 0.0 else 0.0
            }
            PropertyAggregation.SUM -> {
                values.sumOf { value ->
                    when (value) {
                        is Number -> value.toDouble()
                        else -> 0.0
                    }
                }
            }
            PropertyAggregation.AVERAGE -> {
                val sum = values.sumOf { value ->
                    when (value) {
                        is Number -> value.toDouble()
                        else -> 0.0
                    }
                }
                if (values.isEmpty()) 0.0 else sum / values.size
            }
        }
    }

    /**
     * Get property statistics across the entire graph.
     */
    fun getGraphPropertyStatistics(): Map<String, GraphPropertyStats> {
        val stats = mutableMapOf<String, GraphPropertyStats>()
        val allVertices = safeGetVertices().toList()

        // Collect all property keys
        val allKeys = allVertices.flatMap { it.getActivePropertyKeys() }.toSet()

        allKeys.forEach { key ->
            val propertyCount = allVertices.sumOf { vertex ->
                vertex.propertyCount(key)
            }

            val vertexCount = allVertices.count { vertex ->
                vertex.hasProperty(key)
            }

            val hasMetaProperties = allVertices.any { vertex ->
                vertex.getVertexProperties<Any>(key).any { it.hasMetaProperties() }
            }

            val cardinalityDistribution = allVertices.groupBy { vertex ->
                vertex.getPropertyCardinality(key)
            }.mapValues { it.value.count() }

            stats[key] = GraphPropertyStats(
                propertyCount = propertyCount,
                vertexCount = vertexCount,
                hasMetaProperties = hasMetaProperties,
                cardinalityDistribution = cardinalityDistribution
            )
        }

        return stats
    }

    // Companion object for criterion factory methods
    companion object {
        fun exact(key: String, value: Any?) = ExactCriterion(key, value)
        fun range(key: String, min: Number?, max: Number?, includeMin: Boolean = true, includeMax: Boolean = false) =
            RangeCriterion(key, min, max, includeMin, includeMax)
        fun exists(key: String) = ExistsCriterion(key)
        fun notExists(key: String) = NotExistsCriterion(key)
        fun contains(key: String, substring: String, ignoreCase: Boolean = false) =
            ContainsCriterion(key, substring = substring, ignoreCase = ignoreCase)
        fun containsElement(key: String, element: Any?) =
            ContainsCriterion(key, element = element)
        fun regex(key: String, pattern: String) = RegexCriterion(key, Regex(pattern))
        fun and(vararg criteria: PropertyCriterion) = CompositeCriterion(LogicalOperator.AND, criteria.toList())
        fun or(vararg criteria: PropertyCriterion) = CompositeCriterion(LogicalOperator.OR, criteria.toList())
        fun not(criterion: PropertyCriterion) = CompositeCriterion(LogicalOperator.NOT, listOf(criterion))
    }

    /**
     * Evaluates a criterion against a vertex.
     *
     * @param vertex the TinkerVertex to evaluate
     * @param criterion the property criterion to evaluate
     * @return true if the vertex satisfies the criterion, false otherwise
     */
    private fun evaluateCriterion(vertex: TinkerVertex, criterion: PropertyCriterion): Boolean {
        try {
            return when (criterion) {
                is ExactCriterion -> {
                    val properties = vertex.getVertexProperties<Any>(criterion.key)
                    properties.any { it.value() == criterion.value }
                }
                is RangeCriterion -> {
                    val properties = vertex.getVertexProperties<Any>(criterion.key)
                    properties.any { property ->
                        val value = property.value()
                        if (value !is Number || (criterion.minValue == null && criterion.maxValue == null)) {
                            false
                        } else {
                            val numberValue = value.toDouble()
                            val minCheck = criterion.minValue?.let { min ->
                                val minDouble = min.toDouble()
                                if (criterion.includeMin) numberValue >= minDouble else numberValue > minDouble
                            } ?: true
                            val maxCheck = criterion.maxValue?.let { max ->
                                val maxDouble = max.toDouble()
                                if (criterion.includeMax) numberValue <= maxDouble else numberValue < maxDouble
                            } ?: true
                            minCheck && maxCheck
                        }
                    }
                }
                is ExistsCriterion -> {
                    vertex.getVertexProperties<Any>(criterion.key).isNotEmpty()
                }
                is NotExistsCriterion -> {
                    vertex.getVertexProperties<Any>(criterion.key).isEmpty()
                }
                is ContainsCriterion -> {
                    val properties = vertex.getVertexProperties<Any>(criterion.key)
                    properties.any { property ->
                        val value = property.value()
                        when {
                            criterion.substring != null && value is String -> {
                                value.contains(criterion.substring, criterion.ignoreCase)
                            }
                            criterion.element != null && value is Collection<*> -> {
                                value.contains(criterion.element)
                            }
                            else -> false
                        }
                    }
                }
                is RegexCriterion -> {
                    val properties = vertex.getVertexProperties<Any>(criterion.key)
                    properties.any { property ->
                        val value = property.value()
                        value is String && criterion.pattern.matches(value)
                    }
                }
                is CompositeCriterion -> {
                    when (criterion.operator) {
                        LogicalOperator.AND -> criterion.criteria.all { evaluateCriterion(vertex, it) }
                        LogicalOperator.OR -> criterion.criteria.any { evaluateCriterion(vertex, it) }
                        LogicalOperator.NOT -> !evaluateCriterion(vertex, criterion.criteria.first())
                    }
                }
            }
        } catch (e: Exception) {
            // Handle property access errors gracefully
            return false
        }
    }

    /**
     * Evaluates a property criterion against a specific vertex property.
     * This method is used when working with multi-property vertices where
     * each property instance needs to be evaluated individually.
     *
     * @param property the vertex property to evaluate
     * @param criterion the property criterion to apply
     * @return true if the property satisfies the criterion, false otherwise
     */
    private fun evaluatePropertyCriterion(
        property: VertexProperty<*>,
        criterion: PropertyCriterion
    ): Boolean {
        return try {
            when (criterion) {
                is ExactCriterion -> {
                    property.key() == criterion.key && property.value() == criterion.value
                }
                is RangeCriterion -> {
                    if (property.key() != criterion.key) return false
                    val value = property.value()
                    if (value !is Number) return false

                    val numberValue = value.toDouble()
                    val minCheck = criterion.minValue?.let { min ->
                        val minDouble = min.toDouble()
                        if (criterion.includeMin) numberValue >= minDouble else numberValue > minDouble
                    } ?: true
                    val maxCheck = criterion.maxValue?.let { max ->
                        val maxDouble = max.toDouble()
                        if (criterion.includeMax) numberValue <= maxDouble else numberValue < maxDouble
                    } ?: true
                    minCheck && maxCheck
                }
                is ExistsCriterion -> {
                    property.key() == criterion.key
                }
                is NotExistsCriterion -> {
                    property.key() != criterion.key
                }
                is ContainsCriterion -> {
                    if (property.key() != criterion.key) return false
                    val value = property.value()
                    when {
                        criterion.substring != null && value is String -> {
                            value.contains(criterion.substring, criterion.ignoreCase)
                        }
                        criterion.element != null && value is Collection<*> -> {
                            value.contains(criterion.element)
                        }
                        else -> false
                    }
                }
                is RegexCriterion -> {
                    if (property.key() != criterion.key) return false
                    val value = property.value()
                    value is String && criterion.pattern.matches(value)
                }
                is CompositeCriterion -> {
                    when (criterion.operator) {
                        LogicalOperator.AND -> criterion.criteria.all { evaluatePropertyCriterion(property, it) }
                        LogicalOperator.OR -> criterion.criteria.any { evaluatePropertyCriterion(property, it) }
                        LogicalOperator.NOT -> !evaluatePropertyCriterion(property, criterion.criteria.first())
                    }
                }
            }
        } catch (e: Exception) {
            logger.d(e) { "Exception during property evaluation, returning false" }
            false
        }
    }

    /**
     * Safely retrieves all vertices from the graph using centralized casting.
     * This method eliminates ClassCastException issues by handling casting internally.
     */
    private fun safeGetVertices(): Sequence<TinkerVertex> {
        return try {
            // Cast the graph to Graph interface for VertexCastingManager
            val graphInterface: Graph = graph
            VertexCastingManager.safelyMapVertices(graphInterface.vertices().asSequence())
        } catch (e: Exception) {
            // Graceful degradation - return empty sequence if casting fails completely
            emptySequence<TinkerVertex>()
        }
    }

    /**
     * Safely evaluates a criterion against a vertex using defensive programming.
     * Handles potential casting and property access issues gracefully.
     */
    private fun safeEvaluateCriterion(vertex: Any?, criterion: PropertyCriterion): Boolean {
        val tinkerVertex = VertexCastingManager.tryGetTinkerVertex(vertex) ?: return false
        return try {
            evaluateCriterion(tinkerVertex, criterion)
        } catch (e: Exception) {
            logger.d(e) { "Exception during vertex criterion evaluation, returning false" }
            false
        }
    }
}
