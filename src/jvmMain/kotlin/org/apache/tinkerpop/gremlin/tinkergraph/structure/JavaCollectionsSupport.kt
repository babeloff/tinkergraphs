package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * JVM-specific utilities for Java Collections interoperability with TinkerGraph.
 * Provides seamless integration between TinkerGraph elements and standard Java Collections.
 */
object JavaCollectionsSupport {

    /**
     * Converts a TinkerGraph vertex iterator to a Java Stream.
     */
    fun vertexStream(vertices: Iterator<Vertex>): Stream<Vertex> {
        return vertices.asSequence().asStream()
    }

    /**
     * Converts a TinkerGraph edge iterator to a Java Stream.
     */
    fun edgeStream(edges: Iterator<Edge>): Stream<Edge> {
        return edges.asSequence().asStream()
    }

    /**
     * Converts TinkerGraph vertices to a Java List.
     */
    fun verticesToList(vertices: Iterator<Vertex>): List<Vertex> {
        return vertices.asSequence().toList()
    }

    /**
     * Converts TinkerGraph edges to a Java List.
     */
    fun edgesToList(edges: Iterator<Edge>): List<Edge> {
        return edges.asSequence().toList()
    }

    /**
     * Converts TinkerGraph vertices to a Java Set.
     */
    fun verticesToSet(vertices: Iterator<Vertex>): Set<Vertex> {
        return vertices.asSequence().toSet()
    }

    /**
     * Converts TinkerGraph edges to a Java Set.
     */
    fun edgesToSet(edges: Iterator<Edge>): Set<Edge> {
        return edges.asSequence().toSet()
    }

    /**
     * Creates a concurrent Map from vertex IDs to vertices.
     */
    fun createVertexMap(vertices: Iterator<Vertex>): ConcurrentHashMap<Any, Vertex> {
        val map = ConcurrentHashMap<Any, Vertex>()
        vertices.forEach { vertex ->
            val id = vertex.id()
            if (id != null) {
                map[id] = vertex
            }
        }
        return map
    }

    /**
     * Creates a concurrent Map from edge IDs to edges.
     */
    fun createEdgeMap(edges: Iterator<Edge>): ConcurrentHashMap<Any, Edge> {
        val map = ConcurrentHashMap<Any, Edge>()
        edges.forEach { edge ->
            val id = edge.id()
            if (id != null) {
                map[id] = edge
            }
        }
        return map
    }

    /**
     * Groups vertices by a property value.
     */
    fun groupVerticesByProperty(
        vertices: Iterator<Vertex>,
        propertyKey: String
    ): Map<Any?, List<Vertex>> {
        return vertices.asSequence()
            .groupBy { vertex ->
                if (vertex.properties<Any>(propertyKey).hasNext()) {
                    vertex.properties<Any>(propertyKey).next().value()
                } else {
                    null
                }
            }
    }

    /**
     * Groups edges by a property value.
     */
    fun groupEdgesByProperty(
        edges: Iterator<Edge>,
        propertyKey: String
    ): Map<Any?, List<Edge>> {
        return edges.asSequence()
            .groupBy { edge ->
                if (edge.properties<Any>(propertyKey).hasNext()) {
                    edge.properties<Any>(propertyKey).next().value()
                } else {
                    null
                }
            }
    }

    /**
     * Creates a thread-safe index mapping property values to vertices.
     */
    fun createVertexPropertyIndex(
        vertices: Iterator<Vertex>,
        propertyKey: String
    ): ConcurrentHashMap<Any, MutableSet<Vertex>> {
        val index = ConcurrentHashMap<Any, MutableSet<Vertex>>()

        vertices.forEach { vertex ->
            vertex.properties<Any>(propertyKey).forEach { property ->
                val value = property.value()
                index.computeIfAbsent(value) {
                    Collections.newSetFromMap(ConcurrentHashMap<Vertex, Boolean>())
                }.add(vertex)
            }
        }

        return index
    }

    /**
     * Creates a thread-safe index mapping property values to edges.
     */
    fun createEdgePropertyIndex(
        edges: Iterator<Edge>,
        propertyKey: String
    ): ConcurrentHashMap<Any, MutableSet<Edge>> {
        val index = ConcurrentHashMap<Any, MutableSet<Edge>>()

        edges.forEach { edge ->
            edge.properties<Any>(propertyKey).forEach { property ->
                val value = property.value()
                index.computeIfAbsent(value) {
                    Collections.newSetFromMap(ConcurrentHashMap<Edge, Boolean>())
                }.add(edge)
            }
        }

        return index
    }

    /**
     * Converts element properties to a Java Properties object.
     */
    fun elementToProperties(element: Element): Properties {
        val properties = Properties()
        element.properties<Any>().forEach { property ->
            properties.setProperty(property.key(), property.value().toString())
        }
        return properties
    }

    /**
     * Applies properties from a Java Properties object to an element.
     */
    fun applyPropertiesToElement(element: Element, properties: Properties) {
        properties.forEach { key, value ->
            element.property(key.toString(), value)
        }
    }

    /**
     * Creates a Java Comparator for vertices based on a property.
     */
    fun <T : Comparable<T>> createVertexComparator(propertyKey: String): Comparator<Vertex> {
        return Comparator { v1, v2 ->
            val prop1 = v1.properties<T>(propertyKey)
            val prop2 = v2.properties<T>(propertyKey)

            when {
                !prop1.hasNext() && !prop2.hasNext() -> 0
                !prop1.hasNext() -> -1
                !prop2.hasNext() -> 1
                else -> prop1.next().value().compareTo(prop2.next().value())
            }
        }
    }

    /**
     * Creates a Java Comparator for edges based on a property.
     */
    fun <T : Comparable<T>> createEdgeComparator(propertyKey: String): Comparator<Edge> {
        return Comparator { e1, e2 ->
            val prop1 = e1.properties<T>(propertyKey)
            val prop2 = e2.properties<T>(propertyKey)

            when {
                !prop1.hasNext() && !prop2.hasNext() -> 0
                !prop1.hasNext() -> -1
                !prop2.hasNext() -> 1
                else -> prop1.next().value().compareTo(prop2.next().value())
            }
        }
    }

    /**
     * Converts a Kotlin Sequence to a Java Stream.
     */
    private fun <T> Sequence<T>.asStream(): Stream<T> {
        return this.iterator().let { iterator ->
            val list = mutableListOf<T>()
            while (iterator.hasNext()) {
                list.add(iterator.next())
            }
            list.stream()
        }
    }

    /**
     * Thread-safe wrapper for TinkerGraph operations.
     */
    class ThreadSafeGraphWrapper(private val graph: TinkerGraph) {
        private val readWriteLock = java.util.concurrent.locks.ReentrantReadWriteLock()
        private val readLock = readWriteLock.readLock()
        private val writeLock = readWriteLock.writeLock()

        fun <T> read(operation: (TinkerGraph) -> T): T {
            readLock.lock()
            try {
                return operation(graph)
            } finally {
                readLock.unlock()
            }
        }

        fun <T> write(operation: (TinkerGraph) -> T): T {
            writeLock.lock()
            try {
                return operation(graph)
            } finally {
                writeLock.unlock()
            }
        }

        fun vertices(vararg ids: Any): Iterator<Vertex> {
            return read { it.vertices(*ids) }
        }

        fun edges(vararg ids: Any): Iterator<Edge> {
            return read { it.edges(*ids) }
        }

        fun addVertex(vararg keyValues: Any): Vertex {
            return write { it.addVertex(*keyValues) }
        }

        fun variables() = graph.variables()
    }

    /**
     * Utility class for batch operations on collections of elements.
     */
    class BatchProcessor {

        /**
         * Process vertices in batches with specified batch size.
         */
        fun processVerticesInBatches(
            vertices: Iterator<Vertex>,
            batchSize: Int,
            processor: (List<Vertex>) -> Unit
        ) {
            val batch = ArrayList<Vertex>(batchSize)

            while (vertices.hasNext()) {
                batch.add(vertices.next())

                if (batch.size >= batchSize) {
                    processor(batch)
                    batch.clear()
                }
            }

            if (batch.isNotEmpty()) {
                processor(batch)
            }
        }

        /**
         * Process edges in batches with specified batch size.
         */
        fun processEdgesInBatches(
            edges: Iterator<Edge>,
            batchSize: Int,
            processor: (List<Edge>) -> Unit
        ) {
            val batch = ArrayList<Edge>(batchSize)

            while (edges.hasNext()) {
                batch.add(edges.next())

                if (batch.size >= batchSize) {
                    processor(batch)
                    batch.clear()
                }
            }

            if (batch.isNotEmpty()) {
                processor(batch)
            }
        }
    }

    /**
     * Creates a Java Optional from a nullable TinkerGraph element.
     */
    fun <T : Any> toOptional(element: T?): Optional<T> {
        return Optional.ofNullable(element)
    }

    /**
     * Converts TinkerGraph property iterator to Java Stream.
     */
    fun <V> propertyStream(properties: Iterator<Property<V>>): Stream<Property<V>> {
        return properties.asSequence().asStream()
    }

    /**
     * Converts vertex properties to a Map with concurrent access support.
     */
    fun vertexPropertiesToMap(vertex: Vertex): ConcurrentHashMap<String, Any> {
        val map = ConcurrentHashMap<String, Any>()
        vertex.properties<Any>().forEach { property ->
            map[property.key()] = property.value()
        }
        return map
    }

    /**
     * Converts edge properties to a Map with concurrent access support.
     */
    fun edgePropertiesToMap(edge: Edge): ConcurrentHashMap<String, Any> {
        val map = ConcurrentHashMap<String, Any>()
        edge.properties<Any>().forEach { property ->
            map[property.key()] = property.value()
        }
        return map
    }
}
