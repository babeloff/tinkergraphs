package org.apache.tinkerpop.gremlin.tinkergraph.util

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerEdge

/**
 * Native-optimized implementation of vertex casting manager.
 * Uses Kotlin/Native-specific techniques for efficient and safe type conversions.
 */
actual object VertexCastingManager {

    /**
     * Safely converts any vertex-like object to TinkerVertex using Native-optimized checks.
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
                    // Native platform uses similar approach to JVM but with Native-specific optimizations
                    if (isNativeTinkerVertex(vertex)) {
                        CommonCastingUtils.CastingStats.incrementSuccess("vertex")
                        vertex as TinkerVertex
                    } else {
                        // Different vertex implementation - try interface conversion
                        convertVertexInterface(vertex)
                    }
                }

                // Unknown object - check if it has vertex-like structure
                else -> {
                    if (CommonCastingUtils.hasVertexStructure(vertex)) {
                        createTinkerVertexFromObject(vertex)
                    } else {
                        CommonCastingUtils.CastingStats.incrementTypeMismatch()
                        null
                    }
                }
            }
        } catch (e: Exception) {
            println("Native vertex casting failed for ${vertex::class.simpleName}: ${e.message}")
            CommonCastingUtils.CastingStats.incrementFailure("vertex")
            null
        }
    }

    /**
     * Safely converts any edge-like object to TinkerEdge using Native-optimized checks.
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
                    if (isNativeTinkerEdge(edge)) {
                        CommonCastingUtils.CastingStats.incrementSuccess("edge")
                        edge as TinkerEdge
                    } else {
                        convertEdgeInterface(edge)
                    }
                }

                // Unknown object - check if it has edge-like structure
                else -> {
                    if (CommonCastingUtils.hasEdgeStructure(edge)) {
                        createTinkerEdgeFromObject(edge)
                    } else {
                        CommonCastingUtils.CastingStats.incrementTypeMismatch()
                        null
                    }
                }
            }
        } catch (e: Exception) {
            println("Native edge casting failed for ${edge::class.simpleName}: ${e.message}")
            CommonCastingUtils.CastingStats.incrementFailure("edge")
            null
        }
    }

    /**
     * Maps iterator of objects to TinkerVertex instances using Native-optimized processing.
     */
    actual fun safelyMapVertices(vertices: Iterator<*>): Iterator<TinkerVertex> {
        return vertices.asSequence()
            .mapNotNull { obj -> tryGetTinkerVertex(obj) }
            .iterator()
    }

    /**
     * Maps sequence of objects to TinkerVertex instances using Native-optimized processing.
     */
    actual fun safelyMapVertices(vertices: Sequence<*>): Sequence<TinkerVertex> {
        return vertices.mapNotNull { obj -> tryGetTinkerVertex(obj) }
    }

    /**
     * Maps iterator of objects to TinkerEdge instances using Native-optimized processing.
     */
    actual fun safelyMapEdges(edges: Iterator<*>): Iterator<TinkerEdge> {
        return edges.asSequence()
            .mapNotNull { obj -> tryGetTinkerEdge(obj) }
            .iterator()
    }

    /**
     * Maps sequence of objects to TinkerEdge instances using Native-optimized processing.
     */
    actual fun safelyMapEdges(edges: Sequence<*>): Sequence<TinkerEdge> {
        return edges.mapNotNull { obj -> tryGetTinkerEdge(obj) }
    }

    /**
     * Provides detailed Native-specific type diagnosis.
     */
    actual fun diagnoseObjectType(obj: Any?): String {
        if (obj == null) return "null"

        return try {
            buildString {
                appendLine("Native Object Type Diagnosis:")
                appendLine("  Class Name: ${obj::class.simpleName}")
                appendLine("  Qualified Name: ${obj::class.qualifiedName}")

                // TinkerPop interface checks
                appendLine("  Is Vertex: ${obj is Vertex}")
                appendLine("  Is Edge: ${obj is Edge}")
                appendLine("  Is TinkerVertex: ${obj is TinkerVertex}")
                appendLine("  Is TinkerEdge: ${obj is TinkerEdge}")

                // Native-specific type checks
                appendLine("  Is Native TinkerVertex: ${isNativeTinkerVertex(obj)}")
                appendLine("  Is Native TinkerEdge: ${isNativeTinkerEdge(obj)}")

                // Try to extract vertex/edge information
                when (obj) {
                    is Vertex -> {
                        try {
                            appendLine("  Vertex ID: ${obj.id()}")
                            appendLine("  Vertex Label: ${obj.label()}")
                            appendLine("  Property Keys: ${obj.keys().joinToString(", ")}")
                        } catch (e: Exception) {
                            appendLine("  Vertex info extraction failed: ${e.message}")
                        }
                    }
                    is Edge -> {
                        try {
                            appendLine("  Edge ID: ${obj.id()}")
                            appendLine("  Edge Label: ${obj.label()}")
                            appendLine("  OutVertex Type: ${obj.outVertex()::class.simpleName}")
                            appendLine("  InVertex Type: ${obj.inVertex()::class.simpleName}")
                            appendLine("  Property Keys: ${obj.keys().joinToString(", ")}")
                        } catch (e: Exception) {
                            appendLine("  Edge info extraction failed: ${e.message}")
                        }
                    }
                }

                // Structure validation
                appendLine("  Has Vertex Structure: ${CommonCastingUtils.hasVertexStructure(obj)}")
                appendLine("  Has Edge Structure: ${CommonCastingUtils.hasEdgeStructure(obj)}")

                // Memory information (Native-specific)
                try {
                    appendLine("  String Representation Length: ${obj.toString().length}")
                } catch (e: Exception) {
                    appendLine("  String representation failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            "Native diagnosis failed: ${e.message}"
        }
    }

    actual fun getCastingStatistics(): Map<String, Any> {
        return CommonCastingUtils.CastingStats.getStats()
    }

    actual fun clearStatistics() {
        CommonCastingUtils.CastingStats.clear()
    }

    // Native-specific helper functions

    /**
     * Checks if a vertex is actually a TinkerVertex using Native-safe methods.
     */
    private fun isNativeTinkerVertex(vertex: Any): Boolean {
        return try {
            // Use safe casting check
            vertex is TinkerVertex
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if an edge is actually a TinkerEdge using Native-safe methods.
     */
    private fun isNativeTinkerEdge(edge: Any): Boolean {
        return try {
            // Use safe casting check
            edge is TinkerEdge
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Converts a Vertex interface to TinkerVertex in Native environment.
     */
    private fun convertVertexInterface(vertex: Vertex): TinkerVertex? {
        return try {
            // For now, we can't convert between different vertex implementations in Native
            println("Native vertex interface conversion not implemented for: ${vertex::class.simpleName}")
            CommonCastingUtils.CastingStats.incrementFailure("vertex")
            null
        } catch (e: Exception) {
            println("Native vertex interface conversion failed: ${e.message}")
            CommonCastingUtils.CastingStats.incrementFailure("vertex")
            null
        }
    }

    /**
     * Converts an Edge interface to TinkerEdge in Native environment.
     */
    private fun convertEdgeInterface(edge: Edge): TinkerEdge? {
        return try {
            // For now, we can't convert between different edge implementations in Native
            println("Native edge interface conversion not implemented for: ${edge::class.simpleName}")
            CommonCastingUtils.CastingStats.incrementFailure("edge")
            null
        } catch (e: Exception) {
            println("Native edge interface conversion failed: ${e.message}")
            CommonCastingUtils.CastingStats.incrementFailure("edge")
            null
        }
    }

    /**
     * Creates TinkerVertex from arbitrary object in Native environment.
     */
    private fun createTinkerVertexFromObject(obj: Any): TinkerVertex? {
        return try {
            // This would require complex object introspection in Native
            println("Native object-to-vertex conversion not implemented for: ${obj::class.simpleName}")
            CommonCastingUtils.CastingStats.incrementFailure("vertex")
            null
        } catch (e: Exception) {
            println("Native object-to-vertex conversion failed: ${e.message}")
            CommonCastingUtils.CastingStats.incrementFailure("vertex")
            null
        }
    }

    /**
     * Creates TinkerEdge from arbitrary object in Native environment.
     */
    private fun createTinkerEdgeFromObject(obj: Any): TinkerEdge? {
        return try {
            // This would require complex object introspection in Native
            println("Native object-to-edge conversion not implemented for: ${obj::class.simpleName}")
            CommonCastingUtils.CastingStats.incrementFailure("edge")
            null
        } catch (e: Exception) {
            println("Native object-to-edge conversion failed: ${e.message}")
            CommonCastingUtils.CastingStats.incrementFailure("edge")
            null
        }
    }
}
