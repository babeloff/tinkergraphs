package org.apache.tinkerpop.gremlin.structure

/**
 * A Graph object represents a graph data structure.
 * It maintains a collection of vertices and edges.
 */
interface Graph : AutoCloseable {

    /**
     * Add a vertex to the graph given an optional series of key/value pairs.
     * @param keyValues key/value pairs provided as vararg
     * @return the newly created vertex
     */
    fun addVertex(vararg keyValues: Any?): Vertex

    /**
     * Add a vertex to the graph given a map of properties
     * @param properties map of key/value pairs
     * @return the newly created vertex
     */
    fun addVertex(properties: Map<String, Any?>): Vertex

    /**
     * Get the vertex by its identifier.
     * @param id the vertex identifier
     * @return the vertex or null if not found
     */
    fun vertex(id: Any?): Vertex?

    /**
     * Get all vertices in the graph.
     * @param vertexIds optional vertex identifiers to filter
     * @return iterator of vertices
     */
    fun vertices(vararg vertexIds: Any?): Iterator<Vertex>

    /**
     * Get the edge by its identifier.
     * @param id the edge identifier
     * @return the edge or null if not found
     */
    fun edge(id: Any?): Edge?

    /**
     * Get all edges in the graph.
     * @param edgeIds optional edge identifiers to filter
     * @return iterator of edges
     */
    fun edges(vararg edgeIds: Any?): Iterator<Edge>

    /**
     * Get the Features exposed by the Graph.
     * @return the features of this graph
     */
    fun features(): Features

    /**
     * Get the Variables associated with the Graph.
     * @return the variables of this graph
     */
    fun variables(): Variables

    /**
     * Get the configuration that was used to construct this graph.
     * @return the configuration used during graph construction
     */
    fun configuration(): Map<String, Any?>

    /**
     * Graph configuration keys
     */
    object Configuration {
        const val GRAPH = "gremlin.graph"
    }

    /**
     * A collection of exceptions that are thrown by the Graph
     */
    object Exceptions {
        fun vertexWithIdAlreadyExists(id: Any?): IllegalArgumentException =
            IllegalArgumentException("Vertex with id already exists: $id")

        fun edgeWithIdAlreadyExists(id: Any?): IllegalArgumentException =
            IllegalArgumentException("Edge with id already exists: $id")

        fun elementNotFound(elementClass: String, id: Any?): IllegalArgumentException =
            IllegalArgumentException("$elementClass with id does not exist: $id")
    }

    /**
     * Graph features define the capabilities of a graph implementation
     */
    interface Features {
        /**
         * Features related to graph operations
         */
        fun graph(): GraphFeatures

        /**
         * Features related to vertex operations
         */
        fun vertex(): VertexFeatures

        /**
         * Features related to edge operations
         */
        fun edge(): EdgeFeatures

        interface GraphFeatures {
            fun supportsComputer(): Boolean = true
            fun supportsPersistence(): Boolean = true
            fun supportsConcurrentAccess(): Boolean = true
            fun supportsTransactions(): Boolean = false
            fun supportsThreadedTransactions(): Boolean = false
        }

        interface VertexFeatures : ElementFeatures {
            fun supportsMetaProperties(): Boolean = false
            fun supportsMultiProperties(): Boolean = false
            fun supportsUserSuppliedIds(): Boolean = true
            fun supportsNumericIds(): Boolean = true
            fun supportsStringIds(): Boolean = true
            fun supportsUuidIds(): Boolean = true
            fun supportsCustomIds(): Boolean = true
            fun supportsAnyIds(): Boolean = true
        }

        interface EdgeFeatures : ElementFeatures {
            fun supportsUserSuppliedIds(): Boolean = true
            fun supportsNumericIds(): Boolean = true
            fun supportsStringIds(): Boolean = true
            fun supportsUuidIds(): Boolean = true
            fun supportsCustomIds(): Boolean = true
            fun supportsAnyIds(): Boolean = true
        }

        interface ElementFeatures {
            fun supportsProperties(): Boolean = true
            fun supportsAddProperty(): Boolean = true
            fun supportsRemoveProperty(): Boolean = true
        }
    }

    /**
     * Graph variables provide a way to store metadata about the graph
     */
    interface Variables {
        fun keys(): Set<String>
        fun <R> get(key: String): R?
        fun set(key: String, value: Any?)
        fun remove(key: String)
        fun asMap(): Map<String, Any?>
    }

    companion object {
        /**
         * Common method to open a Graph implementation
         */
        fun open(configuration: Map<String, Any?>): Graph {
            val graphClass = configuration[Configuration.GRAPH]
                ?: throw IllegalArgumentException("Configuration must contain a value for ${Configuration.GRAPH}")

            // This would need platform-specific implementation for class loading
            throw UnsupportedOperationException("Graph.open() requires platform-specific implementation")
        }
    }
}
