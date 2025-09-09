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
    private val reader: GraphSONReader,
    private val idConflictStrategy: IdConflictStrategy = IdConflictStrategy.DEFAULT,
    private val allowNullProperties: Boolean = true
) {

    companion object {
        /**
         * Creates a new GraphSONMapper with default configuration.
         * Default configuration enables null property support for GraphSON v3.0 compatibility.
         */
        fun create(): GraphSONMapper {
            return GraphSONMapper(
                writer = GraphSONWriter(),
                reader = GraphSONReader(),
                idConflictStrategy = IdConflictStrategy.DEFAULT,
                allowNullProperties = true
            )
        }

        /**
         * Creates a builder for configuring GraphSONMapper.
         */
        fun build(): Builder {
            return Builder()
        }

        /**
         * Creates a new GraphSONMapper with the specified ID conflict strategy.
         */
        fun create(idConflictStrategy: IdConflictStrategy): GraphSONMapper {
            return GraphSONMapper(
                writer = GraphSONWriter(),
                reader = GraphSONReader(),
                idConflictStrategy = idConflictStrategy,
                allowNullProperties = true
            )
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
     * Creates a new TinkerGraph with proper configuration for GraphSON compatibility.
     */
    fun readGraph(graphsonString: String): TinkerGraph {
        val config = if (allowNullProperties) {
            mapOf(TinkerGraph.GREMLIN_TINKERGRAPH_ALLOW_NULL_PROPERTY_VALUES to true)
        } else {
            emptyMap()
        }
        val graph = TinkerGraph.open(config)
        return reader.readGraphInto(graphsonString, graph, idConflictStrategy)
    }

    /**
     * Deserializes a TinkerGraph from GraphSON v3.0 format into an existing graph.
     */
    fun readGraphInto(graphsonString: String, targetGraph: TinkerGraph): TinkerGraph {
        return reader.readGraphInto(graphsonString, targetGraph, idConflictStrategy)
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
        private var idConflictStrategy: IdConflictStrategy = IdConflictStrategy.DEFAULT
        private var allowNullProperties: Boolean = true

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
         * Configure the ID conflict resolution strategy.
         * Default is GENERATE_NEW_ID for user-friendly behavior.
         */
        fun idConflictStrategy(strategy: IdConflictStrategy): Builder {
            this.idConflictStrategy = strategy
            return this
        }

        /**
         * Configure whether to allow null properties in created TinkerGraph instances.
         * Default is true for GraphSON v3.0 compatibility.
         * Set to false for strict validation scenarios where null properties should be rejected.
         */
        fun allowNullProperties(enabled: Boolean): Builder {
            this.allowNullProperties = enabled
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
                reader = GraphSONReader(),
                idConflictStrategy = idConflictStrategy,
                allowNullProperties = allowNullProperties
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
     * Quick method to deserialize a graph from GraphSON v3.0 with specific ID conflict strategy.
     */
    fun graphFromGraphSON(graphsonString: String, idConflictStrategy: IdConflictStrategy): TinkerGraph {
        val mapper = GraphSONMapper.create(idConflictStrategy)
        return mapper.readGraph(graphsonString)
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
