package org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators

import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex

/**
 * A memory-efficient iterator for TinkerGraph vertices that supports lazy evaluation
 * and filtering capabilities. This iterator processes vertices on-demand without
 * creating intermediate collections, making it suitable for large graphs.
 *
 * @param graph the TinkerGraph instance
 * @param vertexIds optional array of vertex IDs to filter by (empty means all vertices)
 * @param propertyFilters list of property-based filter predicates
 * @param labelFilters set of vertex labels to include (empty means all labels)
 */
class TinkerVertexIterator(
    private val graph: TinkerGraph,
    private val vertexIds: Array<out Any?> = emptyArray(),
    private val propertyFilters: List<(Vertex) -> Boolean> = emptyList(),
    private val labelFilters: Set<String> = emptySet()
) : Iterator<Vertex> {

    private val baseSequence: Sequence<TinkerVertex> = createBaseSequence()
    private val iterator = baseSequence.iterator()

    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): Vertex = iterator.next()

    /**
     * Creates the base sequence with all filters applied.
     * Uses lazy evaluation to process vertices on-demand.
     */
    private fun createBaseSequence(): Sequence<TinkerVertex> {
        val sourceSequence = if (vertexIds.isEmpty()) {
            // Iterate over all vertices in the graph
            graph.vertices.values.asSequence()
        } else {
            // Iterate only over specified vertex IDs
            vertexIds.asSequence()
                .mapNotNull { id -> graph.vertices[id] }
        }

        return sourceSequence
            .filter { vertex -> !vertex.isRemoved() }
            .filter { vertex -> matchesLabelFilter(vertex) }
            .filter { vertex -> matchesPropertyFilters(vertex) }
    }

    /**
     * Checks if a vertex matches the label filter criteria.
     */
    private fun matchesLabelFilter(vertex: TinkerVertex): Boolean {
        return labelFilters.isEmpty() || vertex.label() in labelFilters
    }

    /**
     * Checks if a vertex matches all property filter criteria.
     */
    private fun matchesPropertyFilters(vertex: TinkerVertex): Boolean {
        return propertyFilters.all { filter -> filter(vertex) }
    }

    companion object {
        /**
         * Creates a vertex iterator for all vertices in the graph.
         */
        fun all(graph: TinkerGraph): TinkerVertexIterator {
            return TinkerVertexIterator(graph)
        }

        /**
         * Creates a vertex iterator for specific vertex IDs.
         */
        fun byIds(graph: TinkerGraph, vararg vertexIds: Any?): TinkerVertexIterator {
            return TinkerVertexIterator(graph, vertexIds)
        }

        /**
         * Creates a vertex iterator with label filtering.
         */
        fun byLabels(graph: TinkerGraph, vararg labels: String): TinkerVertexIterator {
            return TinkerVertexIterator(
                graph = graph,
                labelFilters = labels.toSet()
            )
        }

        /**
         * Creates a vertex iterator with property filtering.
         */
        fun withPropertyFilters(
            graph: TinkerGraph,
            filters: List<(Vertex) -> Boolean>
        ): TinkerVertexIterator {
            return TinkerVertexIterator(
                graph = graph,
                propertyFilters = filters
            )
        }

        /**
         * Creates a vertex iterator by property key-value pair.
         * Uses the graph's index if available for efficient lookups.
         */
        fun byProperty(
            graph: TinkerGraph,
            key: String,
            value: Any?
        ): TinkerVertexIterator {
            // Check if we have an index for this property key
            if (graph.vertexIndex.getIndexedKeys().contains(key)) {
                // Use index for efficient lookup
                val indexedVertices = graph.vertexIndex.get(key, value!!)
                return TinkerVertexIterator(
                    graph = graph,
                    vertexIds = indexedVertices.map { it.id() }.toTypedArray()
                )
            } else {
                // Fall back to full scan with property filter
                val propertyFilter: (Vertex) -> Boolean = { vertex ->
                    vertex.value<Any?>(key) == value
                }
                return TinkerVertexIterator(
                    graph = graph,
                    propertyFilters = listOf(propertyFilter)
                )
            }
        }

        /**
         * Creates a vertex iterator with multiple property filters.
         * Optimizes by using indices where available.
         */
        fun byProperties(
            graph: TinkerGraph,
            properties: Map<String, Any?>
        ): TinkerVertexIterator {
            if (properties.isEmpty()) {
                return all(graph)
            }

            // Try to find an indexed property for efficient lookup
            val indexedProperty = properties.entries.find { (key, _) ->
                graph.vertexIndex.getIndexedKeys().contains(key)
            }

            return if (indexedProperty != null) {
                // Use indexed property for base set, then filter by remaining properties
                val (indexKey, indexValue) = indexedProperty
                val remainingProperties = properties - indexKey

                val indexedVertices = graph.vertexIndex.get(indexKey, indexValue!!)
                val vertexIds = indexedVertices.map { it.id() }.toTypedArray()

                val remainingFilters = remainingProperties.map { (key, propValue) ->
                    { vertex: Vertex ->
                        vertex.value<Any?>(key) == propValue
                    }
                }

                TinkerVertexIterator(
                    graph = graph,
                    vertexIds = vertexIds,
                    propertyFilters = remainingFilters
                )
            } else {
                // No indexed properties available, use full scan with all filters
                val allFilters = properties.map { (key, propValue) ->
                    { vertex: Vertex ->
                        vertex.value<Any?>(key) == propValue
                    }
                }

                TinkerVertexIterator(
                    graph = graph,
                    propertyFilters = allFilters
                )
            }
        }
    }
}
