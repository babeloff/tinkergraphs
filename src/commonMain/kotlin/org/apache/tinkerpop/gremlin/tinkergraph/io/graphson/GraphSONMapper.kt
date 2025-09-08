package org.apache.tinkerpop.gremlin.tinkergraph.io.graphson

import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.*

/**
 * Main facade for GraphSON v3.0 serialization and deserialization.
 *
 * This provides a unified interface for working with GraphSON v3.0 format,
 * combining the functionality of GraphSONWriter and GraphSONReader with
 * additional convenience methods and configuration options.
 *
 * Reference: https://tinkerpop.apache.org/docs/current/dev/io/#graphson-3d0
 */
class GraphSONMapper private constructor(
    private val writer: GraphSONWriter,
    private val reader: GraphSONReader
) {

    companion object {
        /**
         * Creates a new GraphSONMapper with default configuration.
         */
        fun create(): GraphSONMapper {
            return GraphSONMapper(
                writer = GraphSONWriter(),
                reader = GraphSONReader()
            )
        }

        /**
         * Creates a builder for configuring GraphSONMapper.
         */
        fun build(): Builder {
            return Builder()
        }
    }

    /**
     * Serializes a TinkerGraph to GraphSON v3.0 format.
     */
    fun writeGraph(graph: TinkerGraph): String {
        return writer.writeGraph(graph)
    }

    /**
     * Deserializes a TinkerGraph from GraphSON v3.0 format.
     */
    fun readGraph(graphsonString: String): TinkerGraph {
        return reader.readGraph(graphsonString)
    }

    /**
     * Serializes a vertex to GraphSON v3.0 format.
     */
    fun writeVertex(vertex: Vertex): String {
        return writer.writeVertex(vertex)
    }

    /**
     * Deserializes a vertex from GraphSON v3.0 format.
     */
    fun readVertex(graphsonString: String): GraphSONVertex {
        return reader.readVertex(graphsonString)
    }

    /**
     * Serializes an edge to GraphSON v3.0 format.
     */
    fun writeEdge(edge: Edge): String {
        return writer.writeEdge(edge)
    }

    /**
     * Deserializes an edge from GraphSON v3.0 format.
     */
    fun readEdge(graphsonString: String): GraphSONEdge {
        return reader.readEdge(graphsonString)
    }

    /**
     * Serializes any value to GraphSON v3.0 typed format.
     */
    fun writeValue(value: Any?): String {
        return writer.writeValue(value)
    }

    /**
     * Deserializes any typed value from GraphSON v3.0 format.
     */
    fun readValue(graphsonString: String): Any? {
        return reader.readValue(graphsonString)
    }

    /**
     * Builder for creating configured GraphSONMapper instances.
     */
    class Builder {
        private var prettyPrint: Boolean = true
        private var typeInfo: Boolean = true
        private var embedTypes: Boolean = true

        /**
         * Configure whether to pretty-print the JSON output.
         * Default is true.
         */
        fun prettyPrint(enabled: Boolean): Builder {
            this.prettyPrint = enabled
            return this
        }

        /**
         * Configure whether to include type information.
         * Default is true (required for GraphSON v3.0 compliance).
         */
        fun typeInfo(enabled: Boolean): Builder {
            this.typeInfo = enabled
            return this
        }

        /**
         * Configure whether to embed type information in all values.
         * Default is true (required for GraphSON v3.0 compliance).
         */
        fun embedTypes(enabled: Boolean): Builder {
            this.embedTypes = enabled
            return this
        }

        /**
         * Creates the configured GraphSONMapper.
         */
        fun create(): GraphSONMapper {
            // For now, we don't use the configuration options as the base
            // implementation follows GraphSON v3.0 spec requirements.
            // In the future, these could be used to customize behavior.
            return GraphSONMapper(
                writer = GraphSONWriter(),
                reader = GraphSONReader()
            )
        }
    }
}

/**
 * Utility object for quick GraphSON v3.0 operations without creating a mapper.
 */
object GraphSON {
    private val defaultMapper = GraphSONMapper.create()

    /**
     * Quick method to serialize a graph to GraphSON v3.0.
     */
    fun toGraphSON(graph: TinkerGraph): String {
        return defaultMapper.writeGraph(graph)
    }

    /**
     * Quick method to deserialize a graph from GraphSON v3.0.
     */
    fun graphFromGraphSON(graphsonString: String): TinkerGraph {
        return defaultMapper.readGraph(graphsonString)
    }

    /**
     * Quick method to serialize a vertex to GraphSON v3.0.
     */
    fun toGraphSON(vertex: Vertex): String {
        return defaultMapper.writeVertex(vertex)
    }

    /**
     * Quick method to serialize an edge to GraphSON v3.0.
     */
    fun toGraphSON(edge: Edge): String {
        return defaultMapper.writeEdge(edge)
    }

    /**
     * Quick method to serialize any value to GraphSON v3.0.
     */
    fun toGraphSON(value: Any?): String {
        return defaultMapper.writeValue(value)
    }

    /**
     * Quick method to deserialize any value from GraphSON v3.0.
     */
    fun valueFromGraphSON(graphsonString: String): Any? {
        return defaultMapper.readValue(graphsonString)
    }
}
