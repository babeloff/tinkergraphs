package org.apache.tinkerpop.gremlin.tinkergraph.util

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerEdge
import org.apache.tinkerpop.gremlin.tinkergraph.util.LoggingConfig

/**
 * JVM-optimized implementation of vertex casting manager.
 * Uses reflection and instanceof checks for efficient and safe type conversions.
 */
actual object VertexCastingManager {

    private val logger = LoggingConfig.getLogger("JVMVertexCastingManager")

    /**
     * Safely converts any vertex-like object to TinkerVertex using JVM reflection.
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
                    // Check class hierarchy using reflection
                    val vertexClass = vertex.javaClass
                    if (TinkerVertex::class.java.isAssignableFrom(vertexClass)) {
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
                        // Try reflection-based conversion
                        createTinkerVertexFromObject(vertex)
                    } else {
                        CommonCastingUtils.CastingStats.incrementTypeMismatch()
                        null
                    }
                }
            }
        } catch (e: ClassCastException) {
            CommonCastingUtils.CastingStats.incrementFailure("vertex")
            logger.w(e) { "JVM vertex casting failed for ${vertex.javaClass.simpleName}" }
            null
        } catch (e: Exception) {
            CommonCastingUtils.CastingStats.incrementFailure("vertex")
            logger.w(e) { "JVM vertex casting error for ${vertex.javaClass.simpleName}" }
            null
        }
    }

    /**
     * Safely converts any edge-like object to TinkerEdge using JVM reflection.
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
                    val edgeClass = edge.javaClass
                    if (TinkerEdge::class.java.isAssignableFrom(edgeClass)) {
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
        } catch (e: ClassCastException) {
            CommonCastingUtils.CastingStats.incrementFailure("edge")
            logger.w(e) { "JVM edge casting failed for ${edge.javaClass.simpleName}" }
            null
        } catch (e: Exception) {
            CommonCastingUtils.CastingStats.incrementFailure("edge")
            logger.w(e) { "JVM edge casting error for ${edge.javaClass.simpleName}" }
            null
        }
    }

    /**
     * Maps iterator of objects to TinkerVertex instances using JVM-optimized processing.
     */
    actual fun safelyMapVertices(vertices: Iterator<*>): Iterator<TinkerVertex> {
        return vertices.asSequence()
            .mapNotNull { obj -> tryGetTinkerVertex(obj) }
            .iterator()
    }

    /**
     * Maps sequence of objects to TinkerVertex instances using JVM-optimized processing.
     */
    actual fun safelyMapVertices(vertices: Sequence<*>): Sequence<TinkerVertex> {
        return vertices.mapNotNull { obj -> tryGetTinkerVertex(obj) }
    }

    /**
     * Maps iterator of objects to TinkerEdge instances using JVM-optimized processing.
     */
    actual fun safelyMapEdges(edges: Iterator<*>): Iterator<TinkerEdge> {
        return edges.asSequence()
            .mapNotNull { obj -> tryGetTinkerEdge(obj) }
            .iterator()
    }

    /**
     * Maps sequence of objects to TinkerEdge instances using JVM-optimized processing.
     */
    actual fun safelyMapEdges(edges: Sequence<*>): Sequence<TinkerEdge> {
        return edges.mapNotNull { obj -> tryGetTinkerEdge(obj) }
    }

    /**
     * Provides detailed JVM-specific type diagnosis using reflection.
     */
    actual fun diagnoseObjectType(obj: Any?): String {
        if (obj == null) return "null"

        return try {
            val objClass = obj.javaClass
            buildString {
                appendLine("JVM Object Type Diagnosis:")
                appendLine("  Class: ${objClass.name}")
                appendLine("  Simple Name: ${objClass.simpleName}")
                appendLine("  Package: ${objClass.packageName}")

                // Inheritance hierarchy
                appendLine("  Superclass: ${objClass.superclass?.simpleName ?: "none"}")
                appendLine("  Interfaces: ${objClass.interfaces.joinToString { it.simpleName }}")

                // TinkerPop interface checks
                appendLine("  Is Vertex: ${obj is Vertex}")
                appendLine("  Is Edge: ${obj is Edge}")
                appendLine("  Is TinkerVertex: ${obj is TinkerVertex}")
                appendLine("  Is TinkerEdge: ${obj is TinkerEdge}")

                // Assignability checks
                appendLine("  Assignable to TinkerVertex: ${TinkerVertex::class.java.isAssignableFrom(objClass)}")
                appendLine("  Assignable to TinkerEdge: ${TinkerEdge::class.java.isAssignableFrom(objClass)}")

                // Try to extract vertex/edge information
                when (obj) {
                    is Vertex -> {
                        try {
                            appendLine("  Vertex ID: ${obj.id()}")
                            appendLine("  Vertex Label: ${obj.label()}")
                            appendLine("  Has Properties: ${obj.keys().isNotEmpty()}")
                        } catch (e: Exception) {
                            appendLine("  Vertex info extraction failed: ${e.message}")
                        }
                    }
                    is Edge -> {
                        try {
                            appendLine("  Edge ID: ${obj.id()}")
                            appendLine("  Edge Label: ${obj.label()}")
                            appendLine("  OutVertex: ${obj.outVertex().javaClass.simpleName}")
                            appendLine("  InVertex: ${obj.inVertex().javaClass.simpleName}")
                        } catch (e: Exception) {
                            appendLine("  Edge info extraction failed: ${e.message}")
                        }
                    }
                }

                // Structure validation
                appendLine("  Has Vertex Structure: ${CommonCastingUtils.hasVertexStructure(obj)}")
                appendLine("  Has Edge Structure: ${CommonCastingUtils.hasEdgeStructure(obj)}")
            }
        } catch (e: Exception) {
            "JVM diagnosis failed: ${e.message}"
        }
    }

    actual fun getCastingStatistics(): Map<String, Any> {
        return CommonCastingUtils.CastingStats.getStats()
    }

    actual fun clearStatistics() {
        CommonCastingUtils.CastingStats.clear()
    }

    // JVM-specific helper functions

    /**
     * Converts a Vertex interface to TinkerVertex using reflection.
     */
    private fun convertVertexInterface(vertex: Vertex): TinkerVertex? {
        return try {
            // For now, we can't convert between different vertex implementations
            // This would require deep graph copying which is beyond scope
            println("JVM vertex interface conversion not implemented for: ${vertex.javaClass.simpleName}")
            CommonCastingUtils.CastingStats.incrementFailure("vertex")
            null
        } catch (e: Exception) {
            println("JVM vertex interface conversion failed: ${e.message}")
            CommonCastingUtils.CastingStats.incrementFailure("vertex")
            null
        }
    }

    /**
     * Converts an Edge interface to TinkerEdge using reflection.
     */
    private fun convertEdgeInterface(edge: Edge): TinkerEdge? {
        return try {
            // For now, we can't convert between different edge implementations
            println("JVM edge interface conversion not implemented for: ${edge.javaClass.simpleName}")
            CommonCastingUtils.CastingStats.incrementFailure("edge")
            null
        } catch (e: Exception) {
            println("JVM edge interface conversion failed: ${e.message}")
            CommonCastingUtils.CastingStats.incrementFailure("edge")
            null
        }
    }

    /**
     * Creates TinkerVertex from arbitrary object using reflection.
     */
    private fun createTinkerVertexFromObject(obj: Any): TinkerVertex? {
        return try {
            // This would require complex reflection-based construction
            println("JVM object-to-vertex conversion not implemented for: ${obj.javaClass.simpleName}")
            CommonCastingUtils.CastingStats.incrementFailure("vertex")
            null
        } catch (e: Exception) {
            println("JVM object-to-vertex conversion failed: ${e.message}")
            CommonCastingUtils.CastingStats.incrementFailure("vertex")
            null
        }
    }

    /**
     * Creates TinkerEdge from arbitrary object using reflection.
     */
    private fun createTinkerEdgeFromObject(obj: Any): TinkerEdge? {
        return try {
            // This would require complex reflection-based construction
            println("JVM object-to-edge conversion not implemented for: ${obj.javaClass.simpleName}")
            CommonCastingUtils.CastingStats.incrementFailure("edge")
            null
        } catch (e: Exception) {
            println("JVM object-to-edge conversion failed: ${e.message}")
            CommonCastingUtils.CastingStats.incrementFailure("edge")
            null
        }
    }
}
