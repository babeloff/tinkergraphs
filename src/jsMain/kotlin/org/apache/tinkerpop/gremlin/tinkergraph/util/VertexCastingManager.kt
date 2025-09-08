package org.apache.tinkerpop.gremlin.tinkergraph.util

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerEdge

/**
 * JavaScript-optimized implementation of vertex casting manager.
 * Uses JavaScript-specific techniques like duck typing and dynamic property access
 * to safely handle type conversions without ClassCastExceptions.
 */
actual object VertexCastingManager {

    /**
     * Safely converts any vertex-like object to TinkerVertex using JavaScript-optimized checks.
     */
    actual fun tryGetTinkerVertex(vertex: Any?): TinkerVertex? {
        if (vertex == null) {
            CommonCastingUtils.CastingStats.incrementNullInput()
            return null
        }

        return try {
            when {
                // Direct type match - fastest path
                vertex is TinkerVertex -> {
                    CommonCastingUtils.CastingStats.incrementSuccess("vertex")
                    vertex
                }

                // Standard Vertex interface
                vertex is Vertex -> {
                    // Use JavaScript-specific type checking
                    val dynamic = vertex.asDynamic()
                    val constructorName = try {
                        js("vertex && vertex.constructor && vertex.constructor.name ? vertex.constructor.name : null") as? String
                    } catch (e: Exception) {
                        null
                    }

                    when (constructorName) {
                        "TinkerVertex" -> {
                            CommonCastingUtils.CastingStats.incrementSuccess("vertex")
                            dynamic.unsafeCast<TinkerVertex>()
                        }
                        else -> {
                            // Try duck typing validation
                            if (isValidVertexStructure(dynamic)) {
                                // Create a proper TinkerVertex from the interface
                                convertVertexInterface(vertex)
                            } else {
                                CommonCastingUtils.CastingStats.incrementFailure("vertex")
                                null
                            }
                        }
                    }
                }

                // Unknown object - try duck typing
                else -> {
                    val dynamic = vertex.asDynamic()
                    if (isValidVertexStructure(dynamic)) {
                        createTinkerVertexFromDynamic(dynamic)
                    } else {
                        CommonCastingUtils.CastingStats.incrementTypeMismatch()
                        null
                    }
                }
            }
        } catch (e: Exception) {
            console.warn("Vertex casting failed for object:", vertex, "Error:", e.message)
            CommonCastingUtils.CastingStats.incrementFailure("vertex")
            null
        }
    }

    /**
     * Safely converts any edge-like object to TinkerEdge using JavaScript-optimized checks.
     */
    actual fun tryGetTinkerEdge(edge: Any?): TinkerEdge? {
        if (edge == null) {
            CommonCastingUtils.CastingStats.incrementNullInput()
            return null
        }

        return try {
            when {
                // Direct type match - fastest path
                edge is TinkerEdge -> {
                    CommonCastingUtils.CastingStats.incrementSuccess("edge")
                    edge
                }

                // Standard Edge interface
                edge is Edge -> {
                    val dynamic = edge.asDynamic()
                    val constructorName = try {
                        js("edge && edge.constructor && edge.constructor.name ? edge.constructor.name : null") as? String
                    } catch (e: Exception) {
                        null
                    }

                    when (constructorName) {
                        "TinkerEdge" -> {
                            CommonCastingUtils.CastingStats.incrementSuccess("edge")
                            dynamic.unsafeCast<TinkerEdge>()
                        }
                        else -> {
                            if (isValidEdgeStructure(dynamic)) {
                                convertEdgeInterface(edge)
                            } else {
                                CommonCastingUtils.CastingStats.incrementFailure("edge")
                                null
                            }
                        }
                    }
                }

                // Unknown object - try duck typing
                else -> {
                    val dynamic = edge.asDynamic()
                    if (isValidEdgeStructure(dynamic)) {
                        createTinkerEdgeFromDynamic(dynamic)
                    } else {
                        CommonCastingUtils.CastingStats.incrementTypeMismatch()
                        null
                    }
                }
            }
        } catch (e: Exception) {
            console.warn("Edge casting failed for object:", edge, "Error:", e.message)
            CommonCastingUtils.CastingStats.incrementFailure("edge")
            null
        }
    }

    /**
     * Maps iterator of objects to TinkerVertex instances with JavaScript-safe iteration.
     */
    actual fun safelyMapVertices(vertices: Iterator<*>): Iterator<TinkerVertex> {
        return vertices.asSequence().mapNotNull { obj ->
            tryGetTinkerVertex(obj)
        }.iterator()
    }

    /**
     * Maps sequence of objects to TinkerVertex instances with JavaScript-safe processing.
     */
    actual fun safelyMapVertices(vertices: Sequence<*>): Sequence<TinkerVertex> {
        return vertices.mapNotNull { obj ->
            tryGetTinkerVertex(obj)
        }
    }

    /**
     * Maps iterator of objects to TinkerEdge instances with JavaScript-safe iteration.
     */
    actual fun safelyMapEdges(edges: Iterator<*>): Iterator<TinkerEdge> {
        return edges.asSequence().mapNotNull { obj ->
            tryGetTinkerEdge(obj)
        }.iterator()
    }

    /**
     * Maps sequence of objects to TinkerEdge instances with JavaScript-safe processing.
     */
    actual fun safelyMapEdges(edges: Sequence<*>): Sequence<TinkerEdge> {
        return edges.mapNotNull { obj ->
            tryGetTinkerEdge(obj)
        }
    }

    /**
     * Provides detailed JavaScript-specific type diagnosis.
     */
    actual fun diagnoseObjectType(obj: Any?): String {
        if (obj == null) return "null"

        return try {
            // First check if it's a primitive type
            val jsType = js("typeof obj") as? String ?: "unknown"
            if (jsType != "object" && jsType != "function") {
                return "Primitive type: $jsType, value: $obj"
            }

            val dynamic = obj.asDynamic()

            // Safe constructor name extraction
            val constructorName = try {
                js("obj && obj.constructor && obj.constructor.name ? obj.constructor.name : 'unknown'") as? String ?: "unknown"
            } catch (e: Exception) {
                "error: ${e.message}"
            }

            // Safe prototype extraction
            val prototype = try {
                js("obj && Object.getPrototypeOf(obj) && Object.getPrototypeOf(obj).constructor && Object.getPrototypeOf(obj).constructor.name ? Object.getPrototypeOf(obj).constructor.name : 'unknown'") as? String ?: "unknown"
            } catch (e: Exception) {
                "error: ${e.message}"
            }

            // Safe property checks
            val hasId = try {
                js("obj && typeof obj === 'object' && 'id' in obj && obj.id !== undefined") as? Boolean ?: false
            } catch (e: Exception) {
                false
            }

            val hasLabel = try {
                js("obj && typeof obj === 'object' && 'label' in obj && obj.label !== undefined") as? Boolean ?: false
            } catch (e: Exception) {
                false
            }

            val hasGraph = try {
                js("obj && typeof obj === 'object' && 'graph' in obj && obj.graph !== undefined") as? Boolean ?: false
            } catch (e: Exception) {
                false
            }

            buildString {
                appendLine("Object Type Diagnosis:")
                appendLine("  JavaScript Type: $jsType")
                appendLine("  Constructor: $constructorName")
                appendLine("  Prototype: $prototype")
                appendLine("  Has ID: $hasId")
                appendLine("  Has Label: $hasLabel")
                appendLine("  Has Graph: $hasGraph")
                appendLine("  Is Vertex Interface: ${obj is Vertex}")
                appendLine("  Is Edge Interface: ${obj is Edge}")
                appendLine("  Is TinkerVertex: ${obj is TinkerVertex}")
                appendLine("  Is TinkerEdge: ${obj is TinkerEdge}")

                // Try to extract actual values safely
                try {
                    if (hasId) {
                        val id = js("obj.id")
                        val idType = js("typeof obj.id") as? String ?: "unknown"
                        appendLine("  ID Value: $id ($idType)")
                    }
                    if (hasLabel) {
                        val label = js("obj.label")
                        val labelType = js("typeof obj.label") as? String ?: "unknown"
                        appendLine("  Label Value: $label ($labelType)")
                    }
                } catch (e: Exception) {
                    appendLine("  Value extraction failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            "Diagnosis failed: ${e.message} for object: $obj"
        }
    }

    actual fun getCastingStatistics(): Map<String, Any> {
        return CommonCastingUtils.CastingStats.getStats()
    }

    actual fun clearStatistics() {
        CommonCastingUtils.CastingStats.clear()
    }

    // JavaScript-specific helper functions

    /**
     * Validates vertex structure using JavaScript dynamic property access.
     */
    private fun isValidVertexStructure(dynamic: dynamic): Boolean {
        return try {
            val hasId = js("dynamic && typeof dynamic === 'object' && 'id' in dynamic && dynamic.id !== undefined") as? Boolean ?: false
            val hasLabel = js("dynamic && typeof dynamic === 'object' && 'label' in dynamic && dynamic.label !== undefined") as? Boolean ?: false
            val hasProperties = js("dynamic && typeof dynamic === 'object' && ('properties' in dynamic || 'property' in dynamic)") as? Boolean ?: false
            val hasGraph = js("dynamic && typeof dynamic === 'object' && 'graph' in dynamic") as? Boolean ?: false

            hasId && hasLabel && (hasProperties || hasGraph)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates edge structure using JavaScript dynamic property access.
     */
    private fun isValidEdgeStructure(dynamic: dynamic): Boolean {
        return try {
            val hasId = js("dynamic && typeof dynamic === 'object' && 'id' in dynamic && dynamic.id !== undefined") as? Boolean ?: false
            val hasLabel = js("dynamic && typeof dynamic === 'object' && 'label' in dynamic && dynamic.label !== undefined") as? Boolean ?: false
            val hasInVertex = js("dynamic && typeof dynamic === 'object' && 'inVertex' in dynamic && dynamic.inVertex !== undefined") as? Boolean ?: false
            val hasOutVertex = js("dynamic && typeof dynamic === 'object' && 'outVertex' in dynamic && dynamic.outVertex !== undefined") as? Boolean ?: false

            hasId && hasLabel && hasInVertex && hasOutVertex
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Converts a Vertex interface to TinkerVertex by accessing properties safely.
     */
    private fun convertVertexInterface(vertex: Vertex): TinkerVertex? {
        return try {
            // This would require access to TinkerVertex constructor or factory method
            // For now, return null and log the conversion attempt
            console.warn("Vertex interface conversion not yet implemented for:", vertex)
            null
        } catch (e: Exception) {
            console.warn("Vertex interface conversion failed:", e.message)
            null
        }
    }

    /**
     * Converts an Edge interface to TinkerEdge by accessing properties safely.
     */
    private fun convertEdgeInterface(edge: Edge): TinkerEdge? {
        return try {
            // This would require access to TinkerEdge constructor or factory method
            // For now, return null and log the conversion attempt
            console.warn("Edge interface conversion not yet implemented for:", edge)
            null
        } catch (e: Exception) {
            console.warn("Edge interface conversion failed:", e.message)
            null
        }
    }

    /**
     * Creates TinkerVertex from dynamic object with duck typing validation.
     */
    private fun createTinkerVertexFromDynamic(dynamic: dynamic): TinkerVertex? {
        return try {
            // This would require access to TinkerVertex constructor
            // For now, return null and log the creation attempt
            console.warn("Dynamic vertex creation not yet implemented for:", dynamic)
            null
        } catch (e: Exception) {
            console.warn("Dynamic vertex creation failed:", e.message)
            null
        }
    }

    /**
     * Creates TinkerEdge from dynamic object with duck typing validation.
     */
    private fun createTinkerEdgeFromDynamic(dynamic: dynamic): TinkerEdge? {
        return try {
            // This would require access to TinkerEdge constructor
            // For now, return null and log the creation attempt
            console.warn("Dynamic edge creation not yet implemented for:", dynamic)
            null
        } catch (e: Exception) {
            console.warn("Dynamic edge creation failed:", e.message)
            null
        }
    }
}
