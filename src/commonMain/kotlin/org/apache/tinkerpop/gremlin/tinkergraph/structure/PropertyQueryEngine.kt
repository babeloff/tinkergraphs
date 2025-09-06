package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.util.SafeCasting

/**
 * PropertyQueryEngine provides advanced querying capabilities for properties in TinkerGraph.
 * It supports complex property filtering, range queries, and composite queries.
 */
class PropertyQueryEngine(private val graph: TinkerGraph) {

    /**
     * Query vertices by property criteria with support for multiple conditions.
     * Uses optimized indices and caching for better performance.
     */
    fun queryVertices(criteria: List<PropertyCriterion>): Iterator<TinkerVertex> {
        // Try to use optimized query plan first
        val queryPlan = graph.optimizeVertexQuery(criteria)
        val cacheKey = criteria.joinToString("|") { it.toString() }

        // Check cache first
        val cached = graph.vertexIndexCache.get(
            IndexCache.IndexType.COMPOSITE,
            cacheKey,
            mapOf("criteria" to criteria)
        )
        if (cached != null) {
            return cached.iterator()
        }

        // Execute optimized query
        val result = when (val strategy = queryPlan.primaryStrategy) {
            is IndexOptimizer.CompositeIndexStrategy -> {
                executeCompositeQuery(strategy, queryPlan.secondaryFilters)
            }
            is IndexOptimizer.RangeIndexStrategy -> {
                executeRangeQuery(strategy, queryPlan.secondaryFilters)
            }
            is IndexOptimizer.SingleIndexStrategy -> {
                executeSingleQuery(strategy, criteria)
            }
            else -> {
                // Fall back to full scan
                val allVertices = graph.vertices().asSequence().mapNotNull { SafeCasting.asTinkerVertex(it) }
                allVertices.filter { vertex ->
                    criteria.all { criterion ->
                        evaluateCriterion(vertex, criterion)
                    }
                }.toSet()
            }
        }

        // Cache result
        graph.vertexIndexCache.put(
            IndexCache.IndexType.COMPOSITE,
            cacheKey,
            mapOf("criteria" to criteria),
            result
        )

        return result.iterator()
    }

    /**
     * Query vertices by a single property criterion.
     */
    fun queryVertices(criterion: PropertyCriterion): Iterator<TinkerVertex> {
        return queryVertices(listOf(criterion))
    }

    /**
     * Query vertex properties with advanced filtering.
     */
    fun <V> queryVertexProperties(
        vertex: TinkerVertex,
        criteria: List<PropertyCriterion>
    ): List<TinkerVertexProperty<V>> {
        val result = mutableListOf<TinkerVertexProperty<V>>()

        vertex.getActivePropertyKeys().forEach { key ->
            val properties = vertex.getVertexProperties<V>(key)

            properties.forEach { property ->
                if (criteria.all { criterion -> evaluatePropertyCriterion(property, criterion) }) {
                    result.add(property)
                }
            }
        }

        return result
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
    ): Iterator<TinkerVertex> {
        val cacheKey = "range_${key}_${minValue}_${maxValue}_${includeMin}_${includeMax}"

        // Check cache first
        val cached = graph.vertexIndexCache.get(IndexCache.IndexType.RANGE, cacheKey)
        if (cached != null) {
            return cached.iterator()
        }

        // Use range index if available
        val result = if (graph.vertexRangeIndex.isRangeIndexed(key)) {
            val minComparable = RangeIndex.safeComparable(minValue)
            val maxComparable = RangeIndex.safeComparable(maxValue)
            graph.vertexRangeIndex.rangeQuery(key, minComparable, maxComparable, includeMin, includeMax)
        } else {
            // Fall back to criterion-based query
            val criterion = RangeCriterion(key, minValue, maxValue, includeMin, includeMax)
            queryVertices(listOf(criterion)).asSequence().toSet()
        }

        // Cache result
        graph.vertexIndexCache.put(IndexCache.IndexType.RANGE, cacheKey, result)

        return result.iterator()
    }

    /**
     * Query vertices that have properties with specific meta-properties.
     */
    fun queryVerticesByMetaProperty(
        propertyKey: String,
        metaPropertyKey: String,
        metaPropertyValue: Any?
    ): Iterator<TinkerVertex> {
        val allVertices = graph.vertices().asSequence().mapNotNull { SafeCasting.asTinkerVertex(it) }

        val filteredVertices = allVertices.filter { vertex ->
            val properties = vertex.getVertexProperties<Any>(propertyKey)
            properties.any { property ->
                val metaProperty = property.property<Any>(metaPropertyKey)
                metaProperty.isPresent() && metaProperty.value() == metaPropertyValue
            }
        }

        return filteredVertices.iterator()
    }

    /**
     * Query vertices by property cardinality.
     */
    fun queryVerticesByCardinality(
        key: String,
        cardinality: VertexProperty.Cardinality
    ): Iterator<TinkerVertex> {
        val allVertices = graph.vertices().asSequence().mapNotNull { SafeCasting.asTinkerVertex(it) }

        val filteredVertices = allVertices.filter { vertex ->
            vertex.getPropertyCardinality(key) == cardinality
        }

        return filteredVertices.iterator()
    }

    /**
     * Find vertices with duplicate property values (useful for SET cardinality validation).
     */
    fun findVerticesWithDuplicateProperties(key: String): Iterator<TinkerVertex> {
        val allVertices = graph.vertices().asSequence().map { it as TinkerVertex }

        val filteredVertices = allVertices.filter { vertex ->
            val properties = vertex.getVertexProperties<Any>(key)
            val values = properties.map { it.value() }
            values.size != values.toSet().size
        }

        return filteredVertices.iterator()
    }

    /**
     * Complex property aggregation queries.
     */
    fun aggregateProperties(
        key: String,
        aggregation: PropertyAggregation
    ): Any? {
        val allVertices = graph.vertices().asSequence().map { it as TinkerVertex }
        val allValues = allVertices.flatMap { vertex ->
            vertex.getVertexProperties<Any>(key).map { it.value() }
        }.toList()

        return when (aggregation) {
            PropertyAggregation.COUNT -> allValues.size
            PropertyAggregation.DISTINCT_COUNT -> allValues.toSet().size
            PropertyAggregation.MIN -> {
                val numbers = allValues.filterIsInstance<Number>()
                if (numbers.isNotEmpty()) numbers.minOf { it.toDouble() } else null
            }
            PropertyAggregation.MAX -> {
                val numbers = allValues.filterIsInstance<Number>()
                if (numbers.isNotEmpty()) numbers.maxOf { it.toDouble() } else null
            }
            PropertyAggregation.SUM -> {
                val numbers = allValues.filterIsInstance<Number>()
                numbers.sumOf { it.toDouble() }
            }
            PropertyAggregation.AVERAGE -> {
                val numbers = allValues.filterIsInstance<Number>()
                if (numbers.isNotEmpty()) numbers.sumOf { it.toDouble() } / numbers.size else null
            }
        }
    }

    /**
     * Get property statistics across the entire graph.
     */
    fun getGraphPropertyStatistics(): Map<String, GraphPropertyStats> {
        val stats = mutableMapOf<String, GraphPropertyStats>()
        val allVertices = graph.vertices().asSequence().map { it as TinkerVertex }.toList()

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

    /**
     * Evaluates a property criterion against a vertex to determine if it matches.
     * This method handles all types of criteria including exact matches, ranges,
     * exists checks, and compound expressions.
     *
     * @param vertex the vertex to evaluate the criterion against
     * @param criterion the property criterion to evaluate
     * @return true if the vertex satisfies the criterion, false otherwise
     */
    private fun evaluateCriterion(vertex: TinkerVertex, criterion: PropertyCriterion): Boolean {
        return when (criterion) {
            is ExactCriterion -> {
                val properties = vertex.getVertexProperties<Any>(criterion.key)
                properties.any { it.value() == criterion.value }
            }
            is RangeCriterion -> {
                val properties = vertex.getVertexProperties<Any>(criterion.key)
                properties.any { property ->
                    val value = property.value()
                    if (value !is Number) false
                    else {
                        val numValue = value.toDouble()
                        val minCheck = criterion.minValue?.let { min ->
                            if (criterion.includeMin) numValue >= min.toDouble()
                            else numValue > min.toDouble()
                        } ?: true

                        val maxCheck = criterion.maxValue?.let { max ->
                            if (criterion.includeMax) numValue <= max.toDouble()
                            else numValue < max.toDouble()
                        } ?: true

                        minCheck && maxCheck
                    }
                }
            }
            is ExistsCriterion -> {
                vertex.hasProperty(criterion.key)
            }
            is NotExistsCriterion -> {
                !vertex.hasProperty(criterion.key)
            }
            is ContainsCriterion -> {
                val properties = vertex.getVertexProperties<Any>(criterion.key)
                properties.any { property ->
                    val value = property.value()
                    when (value) {
                        is String -> criterion.substring?.let { value.contains(it, ignoreCase = criterion.ignoreCase) } ?: false
                        is Collection<*> -> criterion.element in value
                        else -> false
                    }
                }
            }
            is RegexCriterion -> {
                val properties = vertex.getVertexProperties<Any>(criterion.key)
                properties.any { property ->
                    val value = property.value()
                    if (value is String) {
                        criterion.pattern.matches(value)
                    } else false
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
        property: TinkerVertexProperty<*>,
        criterion: PropertyCriterion
    ): Boolean {
        return when (criterion) {
            is ExactCriterion -> {
                property.key() == criterion.key && property.value() == criterion.value
            }
            is ExistsCriterion -> {
                property.key() == criterion.key
            }
            is NotExistsCriterion -> {
                property.key() != criterion.key
            }
            else -> {
                // For other criteria, create a temporary vertex and evaluate
                // This is less efficient but handles complex cases
                val tempVertex = TinkerVertex("temp", "temp", graph)
                tempVertex.getVertexProperties<Any>(property.key()).isNotEmpty() &&
                evaluateCriterion(tempVertex, criterion)
            }
        }
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

    companion object {
        /**
         * Create an exact match criterion.
         */
        fun exact(key: String, value: Any?): ExactCriterion {
            return ExactCriterion(key, value)
        }

        /**
         * Create a range criterion following TinkerPop semantics [min, max).
         */
        fun range(key: String, min: Number? = null, max: Number? = null, includeMin: Boolean = true, includeMax: Boolean = false): RangeCriterion {
            return RangeCriterion(key, min, max, includeMin, includeMax)
        }

        /**
         * Create an exists criterion.
         */
        fun exists(key: String): ExistsCriterion {
            return ExistsCriterion(key)
        }

        /**
         * Create a not exists criterion.
         */
        fun notExists(key: String): NotExistsCriterion {
            return NotExistsCriterion(key)
        }

        /**
         * Create a contains criterion.
         */
        fun contains(key: String, substring: String, ignoreCase: Boolean = false): ContainsCriterion {
            return ContainsCriterion(key, substring = substring, ignoreCase = ignoreCase)
        }

        /**
         * Create a collection contains criterion.
         */
        fun containsElement(key: String, element: Any?): ContainsCriterion {
            return ContainsCriterion(key, element = element)
        }

        /**
         * Create a regex criterion.
         */
        fun regex(key: String, pattern: String): RegexCriterion {
            return RegexCriterion(key, Regex(pattern))
        }

        /**
         * Create an AND composite criterion.
         */
        fun and(vararg criteria: PropertyCriterion): CompositeCriterion {
            return CompositeCriterion(LogicalOperator.AND, criteria.toList())
        }

        /**
         * Create an OR composite criterion.
         */
        fun or(vararg criteria: PropertyCriterion): CompositeCriterion {
            return CompositeCriterion(LogicalOperator.OR, criteria.toList())
        }

        /**
         * Create a NOT composite criterion.
         */
        fun not(criterion: PropertyCriterion): CompositeCriterion {
            return CompositeCriterion(LogicalOperator.NOT, listOf(criterion))
        }
    }

    /**
     * Executes a query using a composite index strategy.
     * Uses the composite index to efficiently find vertices that match
     * multiple property criteria, then applies any remaining filters.
     *
     * @param strategy the composite index strategy to use
     * @param secondaryFilters additional criteria to apply after index lookup
     * @return sequence of vertices matching all criteria
     */
    private fun executeCompositeQuery(
        strategy: IndexOptimizer.CompositeIndexStrategy,
        secondaryFilters: List<PropertyCriterion>
    ): Set<TinkerVertex> {
        val keys = strategy.compositeKeys
        val values = strategy.applicableCriteria.map { it.value }

        val candidates = graph.vertexCompositeIndex.get(keys, values)

        return if (secondaryFilters.isEmpty()) {
            candidates
        } else {
            candidates.filter { vertex ->
                secondaryFilters.all { criterion ->
                    evaluateCriterion(vertex, criterion)
                }
            }.toSet()
        }
    }

    /**
     * Executes a query using a range index strategy.
     * Uses the range index to efficiently find vertices within a specified
     * value range, then applies any remaining filters.
     *
     * @param strategy the range index strategy to use
     * @param secondaryFilters additional criteria to apply after index lookup
     * @return sequence of vertices matching all criteria
     */
    private fun executeRangeQuery(
        strategy: IndexOptimizer.RangeIndexStrategy,
        secondaryFilters: List<PropertyCriterion>
    ): Set<TinkerVertex> {
        val criterion = strategy.criterion
        val minComparable = RangeIndex.safeComparable(criterion.minValue)
        val maxComparable = RangeIndex.safeComparable(criterion.maxValue)

        val candidates = graph.vertexRangeIndex.rangeQuery(
            criterion.key,
            minComparable,
            maxComparable,
            criterion.includeMin,
            criterion.includeMax
        )

        return if (secondaryFilters.isEmpty()) {
            candidates
        } else {
            candidates.filter { vertex ->
                secondaryFilters.all { filter ->
                    evaluateCriterion(vertex, filter)
                }
            }.toSet()
        }
    }

    /**
     * Execute query using single property index strategy.
     */
    private fun executeSingleQuery(
        strategy: IndexOptimizer.SingleIndexStrategy,
        criteria: List<PropertyCriterion>
    ): Set<TinkerVertex> {
        val exactCriterion = criteria.filterIsInstance<ExactCriterion>()
            .find { it.key == strategy.key }

        val candidates = if (exactCriterion != null) {
            graph.vertexIndex.get(strategy.key, exactCriterion.value)
        } else {
            graph.vertexIndex.getAllForKey(strategy.key)
        }

        return candidates.filter { vertex ->
            criteria.all { criterion ->
                evaluateCriterion(vertex, criterion)
            }
        }.toSet()
    }
}
