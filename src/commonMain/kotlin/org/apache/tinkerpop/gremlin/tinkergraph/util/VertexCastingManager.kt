package org.apache.tinkerpop.gremlin.tinkergraph.util

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerEdge
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.apache.tinkerpop.gremlin.tinkergraph.util.LoggingConfig

/**
 * Centralized vertex casting manager that provides safe type conversion
 * with platform-specific optimizations and comprehensive error handling.
 *
 * This manager eliminates the need for external SafeCasting calls by
 * providing liberal input parameters and handling type conversion internally.
 */
expect object VertexCastingManager {

    private val logger = LoggingConfig.getLogger("VertexCastingManager")

    /**
     * Safely converts any vertex-like object to TinkerVertex.
     * Returns null if conversion is not possible.
     *
     * @param vertex Any object that might be a vertex
     * @return TinkerVertex or null if conversion fails
     */
    fun tryGetTinkerVertex(vertex: Any?): TinkerVertex?

    /**
     * Safely converts any edge-like object to TinkerEdge.
     * Returns null if conversion is not possible.
     *
     * @param edge Any object that might be an edge
     * @return TinkerEdge or null if conversion fails
     */
    fun tryGetTinkerEdge(edge: Any?): TinkerEdge?

    /**
     * Maps an iterator of any objects to TinkerVertex instances.
     * Filters out objects that cannot be converted.
     *
     * @param vertices Iterator of potential vertex objects
     * @return Iterator of successfully converted TinkerVertex instances
     */
    fun safelyMapVertices(vertices: Iterator<*>): Iterator<TinkerVertex>

    /**
     * Maps a sequence of any objects to TinkerVertex instances.
     * Filters out objects that cannot be converted.
     *
     * @param vertices Sequence of potential vertex objects
     * @return Sequence of successfully converted TinkerVertex instances
     */
    fun safelyMapVertices(vertices: Sequence<*>): Sequence<TinkerVertex>

    /**
     * Maps an iterator of any objects to TinkerEdge instances.
     * Filters out objects that cannot be converted.
     *
     * @param edges Iterator of potential edge objects
     * @return Iterator of successfully converted TinkerEdge instances
     */
    fun safelyMapEdges(edges: Iterator<*>): Iterator<TinkerEdge>

    /**
     * Maps a sequence of any objects to TinkerEdge instances.
     * Filters out objects that cannot be converted.
     *
     * @param edges Sequence of potential edge objects
     * @return Sequence of successfully converted TinkerEdge instances
     */
    fun safelyMapEdges(edges: Sequence<*>): Sequence<TinkerEdge>

    /**
     * Diagnoses the type of an object for debugging purposes.
     * Provides detailed information about why casting might fail.
     *
     * @param obj Object to diagnose
     * @return Human-readable description of the object's type and casting potential
     */
    fun diagnoseObjectType(obj: Any?): String

    /**
     * Gets statistics about casting success/failure rates for monitoring.
     * Useful for detecting platform-specific issues.
     *
     * @return Map of casting statistics
     */
    fun getCastingStatistics(): Map<String, Any>

    /**
     * Clears casting statistics (useful for testing).
     */
    fun clearStatistics()
}

/**
 * Common casting utilities shared across platforms.
 */
object CommonCastingUtils {

    /**
     * Validates if an object has vertex-like structure.
     * Checks for presence of required vertex properties without casting.
     */
    fun hasVertexStructure(obj: Any?): Boolean {
        if (obj == null) return false

        return try {
            when (obj) {
                is Vertex -> true
                else -> {
                    // Check for duck-typing compatibility
                    val hasId = obj.toString().contains("id")
                    val hasLabel = obj.toString().contains("label")
                    hasId && hasLabel
                }
            }
        } catch (e: Exception) {
            logger.d(e) { "Exception during vertex-like object check, returning false" }
            false
        }
    }

    /**
     * Validates if an object has edge-like structure.
     * Checks for presence of required edge properties without casting.
     */
    fun hasEdgeStructure(obj: Any?): Boolean {
        if (obj == null) return false

        return try {
            when (obj) {
                is Edge -> true
                else -> {
                    // Check for duck-typing compatibility
                    val hasId = obj.toString().contains("id")
                    val hasLabel = obj.toString().contains("label")
                    val hasInVertex = obj.toString().contains("inVertex") || obj.toString().contains("in")
                    val hasOutVertex = obj.toString().contains("outVertex") || obj.toString().contains("out")
                    hasId && hasLabel && hasInVertex && hasOutVertex
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extracts basic vertex information safely without casting.
     */
    fun extractVertexInfo(obj: Any?): VertexInfo? {
        if (!hasVertexStructure(obj)) return null

        // obj is guaranteed to be non-null here since hasVertexStructure returned true
        val nonNullObj = obj!!

        return try {
            when (nonNullObj) {
                is Vertex -> VertexInfo(
                    id = nonNullObj.id()?.toString(),
                    label = nonNullObj.label(),
                    className = nonNullObj::class.simpleName
                )
                else -> VertexInfo(
                    id = null,
                    label = null,
                    className = nonNullObj::class.simpleName
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Information about a vertex-like object without requiring casting.
     */
    data class VertexInfo(
        val id: String?,
        val label: String?,
        val className: String?
    )

    /**
     * Statistics tracking for casting operations.
     */
    internal object CastingStats {
        private val stats = mutableMapOf<String, Long>(
            "vertex_cast_success" to 0L,
            "vertex_cast_failure" to 0L,
            "edge_cast_success" to 0L,
            "edge_cast_failure" to 0L,
            "null_inputs" to 0L,
            "type_mismatches" to 0L
        )

        fun incrementSuccess(type: String) {
            stats["${type}_cast_success"] = (stats["${type}_cast_success"] ?: 0L) + 1L
        }

        fun incrementFailure(type: String) {
            stats["${type}_cast_failure"] = (stats["${type}_cast_failure"] ?: 0L) + 1L
        }

        fun incrementNullInput() {
            stats["null_inputs"] = (stats["null_inputs"] ?: 0L) + 1L
        }

        fun incrementTypeMismatch() {
            stats["type_mismatches"] = (stats["type_mismatches"] ?: 0L) + 1L
        }

        fun getStats(): Map<String, Any> = stats.toMap()

        fun clear() {
            stats.keys.forEach { key -> stats[key] = 0L }
        }
    }
}
