package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*

/**
 * PropertyQueryEngine provides advanced querying capabilities for properties in TinkerGraph.
 * It supports complex property filtering, range queries, and composite queries.
 */
class PropertyQueryEngine(private val graph: TinkerGraph) {

    /**
     * Query vertices by property criteria with support for multiple conditions.
     */
    fun queryVertices(criteria: List<PropertyCriterion>): Iterator<TinkerVertex> {
        val allVertices = graph.vertices().asSequence().map { it as TinkerVertex }

        val filteredVertices = allVertices.filter { vertex ->
            criteria.all { criterion ->
                evaluateCriterion(vertex, criterion)
            }
        }

        return filteredVertices.iterator()
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
     * Range query for numeric properties.
     */
    fun queryVerticesByRange(
        key: String,
        minValue: Number?,
        maxValue: Number?,
        inclusive: Boolean = true
    ): Iterator<TinkerVertex> {
        val criterion = RangeCriterion(key, minValue, maxValue, inclusive)
        return queryVertices(criterion)
    }

    /**
     * Query vertices that have properties with specific meta-properties.
     */
    fun queryVerticesByMetaProperty(
        propertyKey: String,
        metaPropertyKey: String,
        metaPropertyValue: Any?
    ): Iterator<TinkerVertex> {
        val allVertices = graph.vertices().asSequence().map { it as TinkerVertex }

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
        val allVertices = graph.vertices().asSequence().map { it as TinkerVertex }

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
     * Evaluate a property criterion against a vertex.
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
                            if (criterion.inclusive) numValue >= min.toDouble()
                            else numValue > min.toDouble()
                        } ?: true

                        val maxCheck = criterion.maxValue?.let { max ->
                            if (criterion.inclusive) numValue <= max.toDouble()
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
     * Evaluate a property criterion against a specific vertex property.
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
     */
    data class RangeCriterion(
        val key: String,
        val minValue: Number?,
        val maxValue: Number?,
        val inclusive: Boolean = true
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
         * Create a range criterion.
         */
        fun range(key: String, min: Number? = null, max: Number? = null, inclusive: Boolean = true): RangeCriterion {
            return RangeCriterion(key, min, max, inclusive)
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
}
