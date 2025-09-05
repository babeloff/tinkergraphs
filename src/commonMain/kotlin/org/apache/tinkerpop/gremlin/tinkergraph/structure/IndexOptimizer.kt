package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.platform.Platform

/**
 * IndexOptimizer provides query plan optimization and index selection strategies
 * to improve query performance by choosing the most efficient indices.
 */
class IndexOptimizer<T : Element>(
    private val tinkerIndex: TinkerIndex<T>,
    private val compositeIndex: CompositeIndex<T>,
    private val rangeIndex: RangeIndex<T>
) {

    /**
     * Statistics about query patterns and index usage.
     */
    private val queryStats = mutableMapOf<String, QueryStats>()

    /**
     * Index selectivity cache for performance optimization.
     */
    private val selectivityCache = mutableMapOf<String, Double>()

    /**
     * Threshold for considering an index selective (0.0 to 1.0).
     * Lower values indicate more selective indices.
     */
    private var selectivityThreshold = 0.1

    /**
     * Optimize a query by selecting the best available index strategy.
     *
     * @param criteria list of property criteria to optimize
     * @return optimized query plan
     */
    fun optimizeQuery(criteria: List<PropertyQueryEngine.PropertyCriterion>): QueryPlan {
        val queryKeys = extractQueryKeys(criteria)
        val plan = QueryPlan()

        // Record query for statistics
        recordQuery(queryKeys)

        // 1. Try composite indices first (most selective for multi-key queries)
        val compositeStrategy = findBestCompositeStrategy(queryKeys, criteria)
        if (compositeStrategy != null) {
            plan.primaryStrategy = compositeStrategy
            plan.estimatedCost = estimateCompositeCost(compositeStrategy)
            return plan
        }

        // 2. Try range indices for range queries
        val rangeStrategy = findBestRangeStrategy(criteria)
        if (rangeStrategy != null) {
            plan.primaryStrategy = rangeStrategy
            plan.estimatedCost = estimateRangeCost(rangeStrategy)

            // Add remaining criteria as filters
            val remainingCriteria = criteria.filterNot { it == rangeStrategy.criterion }
            if (remainingCriteria.isNotEmpty()) {
                plan.secondaryFilters = remainingCriteria
            }
            return plan
        }

        // 3. Try single property indices
        val singleStrategy = findBestSingleStrategy(queryKeys, criteria)
        if (singleStrategy != null) {
            plan.primaryStrategy = singleStrategy
            plan.estimatedCost = estimateSingleCost(singleStrategy)

            // Add remaining criteria as filters
            val remainingCriteria = criteria.filterNot {
                it is PropertyQueryEngine.ExactCriterion && it.key == singleStrategy.key
            }
            if (remainingCriteria.isNotEmpty()) {
                plan.secondaryFilters = remainingCriteria
            }
            return plan
        }

        // 4. Fall back to full scan
        plan.primaryStrategy = FullScanStrategy(criteria)
        plan.estimatedCost = estimateFullScanCost()
        return plan
    }

    /**
     * Finds the best composite index strategy for the given query keys and criteria.
     * Evaluates available composite indices to find the one that covers the most query keys
     * and provides the best selectivity for the given criteria.
     *
     * @param queryKeys the set of property keys being queried
     * @param criteria the list of query criteria to evaluate against
     * @return CompositeIndexStrategy if a suitable composite index is found, null otherwise
     */
    private fun findBestCompositeStrategy(
        queryKeys: Set<String>,
        criteria: List<PropertyQueryEngine.PropertyCriterion>
    ): CompositeIndexStrategy? {
        val exactCriteria = criteria.filterIsInstance<PropertyQueryEngine.ExactCriterion>()
        if (exactCriteria.size < 2) return null

        val exactKeys = exactCriteria.map { it.key }
        val bestComposite = compositeIndex.findBestCompositeIndex(exactKeys.toSet())

        return if (bestComposite != null) {
            val applicableCriteria = exactCriteria.filter { it.key in bestComposite }
            CompositeIndexStrategy(bestComposite, applicableCriteria)
        } else null
    }

    /**
     * Finds the best range index strategy for range queries.
     * Searches through the criteria to find range-based queries and selects
     * the most selective range index available.
     *
     * @param criteria the list of query criteria to evaluate
     * @return RangeIndexStrategy if a suitable range index is found, null otherwise
     */
    private fun findBestRangeStrategy(criteria: List<PropertyQueryEngine.PropertyCriterion>): RangeIndexStrategy? {
        val rangeCriteria = criteria.filterIsInstance<PropertyQueryEngine.RangeCriterion>()
        if (rangeCriteria.isEmpty()) return null

        // Find the most selective range criterion
        val bestRange = rangeCriteria.minByOrNull { criterion ->
            calculateRangeSelectivity(criterion)
        }

        return if (bestRange != null && rangeIndex.isRangeIndexed(bestRange.key)) {
            RangeIndexStrategy(bestRange.key, bestRange)
        } else null
    }

    /**
     * Finds the best single property index strategy.
     * Evaluates each indexed property key to find the one with the best selectivity
     * (lowest estimated result count) for the given query criteria.
     *
     * @param queryKeys the set of property keys being queried
     * @param criteria the list of query criteria to evaluate against
     * @return SingleIndexStrategy if a suitable index is found, null otherwise
     */
    private fun findBestSingleStrategy(
        queryKeys: Set<String>,
        criteria: List<PropertyQueryEngine.PropertyCriterion>
    ): SingleIndexStrategy? {
        val exactCriteria = criteria.filterIsInstance<PropertyQueryEngine.ExactCriterion>()
        if (exactCriteria.isEmpty()) return null

        // Find the most selective single index
        val bestKey = exactCriteria
            .filter { tinkerIndex.isIndexed(it.key) }
            .minByOrNull { criterion ->
                getIndexSelectivity(criterion.key)
            }?.key

        return if (bestKey != null) {
            SingleIndexStrategy(bestKey)
        } else null
    }

    /**
     * Calculates selectivity for a range criterion (lower values indicate higher selectivity).
     * Uses range statistics to estimate what fraction of values fall within the specified range.
     *
     * @param criterion the range criterion to evaluate
     * @return selectivity estimate between 0.0 (highly selective) and 1.0 (not selective)
     */
    private fun calculateRangeSelectivity(criterion: PropertyQueryEngine.RangeCriterion): Double {
        if (!rangeIndex.isRangeIndexed(criterion.key)) return 1.0

        val minVal = rangeIndex.getMinValue(criterion.key)
        val maxVal = rangeIndex.getMaxValue(criterion.key)

        if (minVal == null || maxVal == null) return 1.0

        // Rough estimate based on range size vs total range
        val totalRange = when {
            minVal is Number && maxVal is Number -> {
                maxVal.toDouble() - minVal.toDouble()
            }
            else -> Double.MAX_VALUE
        }

        val queryRange = when {
            criterion.minValue is Number && criterion.maxValue is Number -> {
                criterion.maxValue.toDouble() - criterion.minValue.toDouble()
            }
            criterion.minValue is Number -> totalRange * 0.5 // Estimate half the range
            criterion.maxValue is Number -> totalRange * 0.5 // Estimate half the range
            else -> totalRange
        }

        return if (totalRange > 0) (queryRange / totalRange).coerceIn(0.0, 1.0) else 0.5
    }

    /**
     * Gets or calculates selectivity for a single property index.
     * Uses caching to avoid recalculating selectivity for the same property key.
     *
     * @param key the property key to get selectivity for
     * @return cached or calculated selectivity estimate
     */
    private fun getIndexSelectivity(key: String): Double {
        return selectivityCache.getOrPut(key) {
            calculateIndexSelectivity(key)
        }
    }

    /**
     * Calculates selectivity for a single property index.
     * Estimates selectivity based on the number of unique values in the index
     * compared to the total number of indexed values.
     *
     * @param key the property key to calculate selectivity for
     * @return selectivity estimate between 0.0 (highly selective) and 1.0 (not selective)
     */
    private fun calculateIndexSelectivity(key: String): Double {
        if (!tinkerIndex.isIndexed(key)) return 1.0

        val distinctValues = tinkerIndex.countValues(key)
        val totalElements = tinkerIndex.getAllForKey(key).size

        return if (totalElements > 0) {
            (distinctValues.toDouble() / totalElements).coerceIn(0.01, 1.0)
        } else 0.01
    }

    /**
     * Extracts unique property keys from query criteria.
     * Analyzes all criteria to build a set of property keys that are being queried,
     * which is used for index strategy selection.
     *
     * @param criteria the list of query criteria to analyze
     * @return set of unique property keys referenced in the criteria
     */
    private fun extractQueryKeys(criteria: List<PropertyQueryEngine.PropertyCriterion>): Set<String> {
        val keys = mutableSetOf<String>()
        criteria.forEach { criterion ->
            when (criterion) {
                is PropertyQueryEngine.ExactCriterion -> keys.add(criterion.key)
                is PropertyQueryEngine.RangeCriterion -> keys.add(criterion.key)
                is PropertyQueryEngine.ExistsCriterion -> keys.add(criterion.key)
                is PropertyQueryEngine.NotExistsCriterion -> keys.add(criterion.key)
                is PropertyQueryEngine.ContainsCriterion -> keys.add(criterion.key)
                is PropertyQueryEngine.RegexCriterion -> keys.add(criterion.key)
                is PropertyQueryEngine.CompositeCriterion -> {
                    keys.addAll(extractQueryKeys(criterion.criteria))
                }
            }
        }
        return keys
    }

    /**
     * Records a query pattern for statistics and optimization.
     * Tracks frequently used query patterns to help with index recommendations
     * and performance analysis.
     *
     * @param queryKeys the set of property keys used in this query
     */
    private fun recordQuery(queryKeys: Set<String>) {
        val keyPattern = queryKeys.sorted().joinToString(",")
        val stats = queryStats.getOrPut(keyPattern) { QueryStats() }
        stats.queryCount++
        stats.lastUsed = Platform.currentTimeMillis()
    }

    /**
     * Estimates the execution cost for a composite index strategy.
     * Cost is based on the number of applicable criteria and the selectivity
     * of the composite index.
     *
     * @param strategy the composite index strategy to evaluate
     * @return estimated cost between 0.01 (very efficient) and 1.0 (least efficient)
     */
    private fun estimateCompositeCost(strategy: CompositeIndexStrategy): Double {
        val compositeSelectivity = strategy.applicableCriteria.size * 0.1 // More criteria = better
        return (1.0 - compositeSelectivity).coerceIn(0.01, 1.0)
    }

    /**
     * Estimates the execution cost for a range index strategy.
     * Cost is directly based on the selectivity of the range criterion.
     *
     * @param strategy the range index strategy to evaluate
     * @return estimated cost between 0.01 (very efficient) and 1.0 (least efficient)
     */
    private fun estimateRangeCost(strategy: RangeIndexStrategy): Double {
        return calculateRangeSelectivity(strategy.criterion).coerceIn(0.01, 1.0)
    }

    /**
     * Estimates the execution cost for a single property index strategy.
     * Cost is based on the selectivity of the indexed property.
     *
     * @param strategy the single index strategy to evaluate
     * @return estimated cost based on index selectivity
     */
    private fun estimateSingleCost(strategy: SingleIndexStrategy): Double {
        return getIndexSelectivity(strategy.key)
    }

    /**
     * Estimates the cost for a full table scan.
     * This is always the highest cost option, used as a fallback when no
     * suitable indices are available.
     *
     * @return maximum cost value of 1.0
     */
    private fun estimateFullScanCost(): Double {
        return 1.0 // Highest cost
    }

    /**
     * Get query statistics for analysis and optimization.
     */
    fun getQueryStatistics(): Map<String, QueryStats> {
        return queryStats.toMap()
    }

    /**
     * Get index usage recommendations based on query patterns.
     */
    fun getIndexRecommendations(): List<IndexRecommendation> {
        val recommendations = mutableListOf<IndexRecommendation>()

        // Analyze frequent query patterns
        queryStats.entries
            .filter { it.value.queryCount > 5 } // Only consider frequently used patterns
            .sortedByDescending { it.value.queryCount }
            .forEach { (pattern, stats) ->
                val keys = pattern.split(",")

                when {
                    keys.size > 1 -> {
                        // Recommend composite index
                        if (!compositeIndex.isCompositeIndexed(keys)) {
                            recommendations.add(
                                IndexRecommendation(
                                    IndexType.COMPOSITE,
                                    keys,
                                    "Frequently queried together (${stats.queryCount} times)",
                                    stats.queryCount.toDouble()
                                )
                            )
                        }
                    }
                    keys.size == 1 -> {
                        val key = keys.first()
                        if (!tinkerIndex.isIndexed(key)) {
                            recommendations.add(
                                IndexRecommendation(
                                    IndexType.SINGLE,
                                    listOf(key),
                                    "Frequently queried single property (${stats.queryCount} times)",
                                    stats.queryCount.toDouble()
                                )
                            )
                        }
                    }
                }
            }

        return recommendations.sortedByDescending { it.priority }
    }

    /**
     * Clear selectivity cache (call when indices change significantly).
     */
    fun clearSelectivityCache() {
        selectivityCache.clear()
    }

    /**
     * Set the selectivity threshold for index recommendations.
     */
    fun setSelectivityThreshold(threshold: Double) {
        require(threshold in 0.0..1.0) { "Selectivity threshold must be between 0.0 and 1.0" }
        selectivityThreshold = threshold
    }

    /**
     * Get current optimizer statistics.
     */
    fun getOptimizerStatistics(): Map<String, Any> {
        return mapOf(
            "totalQueries" to queryStats.values.sumOf { it.queryCount },
            "uniqueQueryPatterns" to queryStats.size,
            "cachedSelectivities" to selectivityCache.size,
            "selectivityThreshold" to selectivityThreshold,
            "recommendations" to getIndexRecommendations().size
        )
    }

    /**
     * Data class representing an optimized query plan.
     */
    data class QueryPlan(
        var primaryStrategy: QueryStrategy? = null,
        var secondaryFilters: List<PropertyQueryEngine.PropertyCriterion> = emptyList(),
        var estimatedCost: Double = 1.0
    )

    /**
     * Base class for query strategies.
     */
    sealed class QueryStrategy

    /**
     * Strategy using composite indices.
     */
    data class CompositeIndexStrategy(
        val compositeKeys: List<String>,
        val applicableCriteria: List<PropertyQueryEngine.ExactCriterion>
    ) : QueryStrategy()

    /**
     * Strategy using range indices.
     */
    data class RangeIndexStrategy(
        val key: String,
        val criterion: PropertyQueryEngine.RangeCriterion
    ) : QueryStrategy()

    /**
     * Strategy using single property indices.
     */
    data class SingleIndexStrategy(
        val key: String
    ) : QueryStrategy()

    /**
     * Strategy using full table scan.
     */
    data class FullScanStrategy(
        val criteria: List<PropertyQueryEngine.PropertyCriterion>
    ) : QueryStrategy()

    /**
     * Statistics about query patterns.
     */
    data class QueryStats(
        var queryCount: Int = 0,
        var lastUsed: Long = Platform.currentTimeMillis()
    )

    /**
     * Index recommendation based on query patterns.
     */
    data class IndexRecommendation(
        val type: IndexType,
        val keys: List<String>,
        val reason: String,
        val priority: Double
    )

    /**
     * Types of indices that can be recommended.
     */
    enum class IndexType {
        SINGLE, COMPOSITE, RANGE
    }
}
