package org.apache.tinkerpop.gremlin.tinkergraph.structure.iterators

import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerEdge
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex
import org.apache.tinkerpop.gremlin.tinkergraph.util.SafeCasting

/**
 * A memory-efficient iterator for TinkerGraph edges that supports lazy evaluation,
 * direction filtering, and label filtering. This iterator processes edges on-demand
 * without creating intermediate collections, making it suitable for large graphs.
 *
 * @param graph the TinkerGraph instance
 * @param edgeIds optional array of edge IDs to filter by (empty means all edges)
 * @param direction optional direction filter (null means all directions)
 * @param edgeLabels set of edge labels to include (empty means all labels)
 * @param propertyFilters list of property-based filter predicates
 * @param sourceVertex optional source vertex for vertex-centric edge iteration
 */
class TinkerEdgeIterator(
    private val graph: TinkerGraph,
    private val edgeIds: Array<out Any?> = emptyArray(),
    private val direction: Direction? = null,
    private val edgeLabels: Set<String> = emptySet(),
    private val propertyFilters: List<(Edge) -> Boolean> = emptyList(),
    private val sourceVertex: TinkerVertex? = null
) : Iterator<Edge> {

    private val baseSequence: Sequence<TinkerEdge> = createBaseSequence()
    private val iterator = baseSequence.iterator()

    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): Edge = iterator.next()

    /**
     * Creates the base sequence with all filters applied.
     * Uses lazy evaluation to process edges on-demand.
     */
    private fun createBaseSequence(): Sequence<TinkerEdge> {
        val sourceSequence = if (sourceVertex != null) {
            // Vertex-centric iteration
            createVertexCentricSequence(sourceVertex)
        } else if (edgeIds.isNotEmpty()) {
            // Iterate only over specified edge IDs
            edgeIds.asSequence()
                .mapNotNull { id -> graph.edges[id] }
        } else {
            // Iterate over all edges in the graph
            graph.edges.values.asSequence()
        }

        return sourceSequence
            .filter { edge -> !edge.isEdgeRemoved() }
            .filter { edge -> matchesDirectionFilter(edge) }
            .filter { edge -> matchesLabelFilter(edge) }
            .filter { edge -> matchesPropertyFilters(edge) }
    }

    /**
     * Creates a sequence for vertex-centric edge iteration based on direction.
     */
    private fun createVertexCentricSequence(vertex: TinkerVertex): Sequence<TinkerEdge> {
        return when (direction) {
            Direction.OUT -> vertex.getOutEdges().asSequence()
            Direction.IN -> vertex.getInEdges().asSequence()
            Direction.BOTH, null -> {
                (vertex.getOutEdges() + vertex.getInEdges()).asSequence()
            }
        }
    }

    /**
     * Checks if an edge matches the direction filter criteria.
     * Only applies when not doing vertex-centric iteration.
     */
    private fun matchesDirectionFilter(edge: TinkerEdge): Boolean {
        return sourceVertex != null || direction == null || when (direction) {
            Direction.OUT -> true // For global iteration, OUT means any outgoing edge
            Direction.IN -> true  // For global iteration, IN means any incoming edge
            Direction.BOTH -> true
        }
    }

    /**
     * Checks if an edge matches the label filter criteria.
     */
    private fun matchesLabelFilter(edge: TinkerEdge): Boolean {
        return edgeLabels.isEmpty() || edge.label() in edgeLabels
    }

    /**
     * Checks if an edge matches all property filter criteria.
     */
    private fun matchesPropertyFilters(edge: TinkerEdge): Boolean {
        return propertyFilters.all { filter -> filter(edge) }
    }

    companion object {
        /**
         * Creates an edge iterator for all edges in the graph.
         */
        fun all(graph: TinkerGraph): TinkerEdgeIterator {
            return TinkerEdgeIterator(graph)
        }

        /**
         * Creates an edge iterator for specific edge IDs.
         */
        fun byIds(graph: TinkerGraph, vararg edgeIds: Any?): TinkerEdgeIterator {
            return TinkerEdgeIterator(graph, edgeIds)
        }

        /**
         * Creates an edge iterator with label filtering.
         */
        fun byLabels(graph: TinkerGraph, vararg labels: String): TinkerEdgeIterator {
            return TinkerEdgeIterator(
                graph = graph,
                edgeLabels = labels.toSet()
            )
        }

        /**
         * Creates an edge iterator for a specific vertex with direction and label filtering.
         */
        fun fromVertex(
            vertex: TinkerVertex,
            direction: Direction,
            vararg edgeLabels: String
        ): TinkerEdgeIterator {
            return TinkerEdgeIterator(
                graph = SafeCasting.asTinkerGraph(vertex.graph()) ?: throw IllegalStateException("Expected TinkerGraph"),
                direction = direction,
                edgeLabels = edgeLabels.toSet(),
                sourceVertex = vertex
            )
        }

        /**
         * Creates an edge iterator with property filtering.
         */
        fun withPropertyFilters(
            graph: TinkerGraph,
            filters: List<(Edge) -> Boolean>
        ): TinkerEdgeIterator {
            return TinkerEdgeIterator(
                graph = graph,
                propertyFilters = filters
            )
        }

        /**
         * Creates an edge iterator by property key-value pair.
         * Uses the graph's index if available for efficient lookups.
         */
        fun byProperty(
            graph: TinkerGraph,
            key: String,
            value: Any?
        ): TinkerEdgeIterator {
            // Check if we have an index for this property key
            if (graph.edgeIndex.getIndexedKeys().contains(key)) {
                // Use index for efficient lookup
                val indexedEdges = graph.edgeIndex.get(key, value!!)
                return TinkerEdgeIterator(
                    graph = graph,
                    edgeIds = indexedEdges.map { it.id() }.toTypedArray()
                )
            } else {
                // Fall back to full scan with property filter
                val propertyFilter: (Edge) -> Boolean = { edge ->
                    edge.property<Any?>(key).let { prop ->
                        prop.isPresent() && prop.value() == value
                    }
                }
                return TinkerEdgeIterator(
                    graph = graph,
                    propertyFilters = listOf(propertyFilter)
                )
            }
        }

        /**
         * Creates an edge iterator with multiple property filters.
         * Optimizes by using indices where available.
         */
        fun byProperties(
            graph: TinkerGraph,
            properties: Map<String, Any?>
        ): TinkerEdgeIterator {
            if (properties.isEmpty()) {
                return all(graph)
            }

            // Try to find an indexed property for efficient lookup
            val indexedProperty = properties.entries.find { (key, _) ->
                graph.edgeIndex.getIndexedKeys().contains(key)
            }

            return if (indexedProperty != null) {
                // Use indexed property for base set, then filter by remaining properties
                val (indexKey, indexValue) = indexedProperty
                val remainingProperties = properties - indexKey

                val indexedEdges = graph.edgeIndex.get(indexKey, indexValue!!)
                val edgeIds = indexedEdges.map { it.id() }.toTypedArray()

                val remainingFilters = remainingProperties.map { (key, propValue) ->
                    { edge: Edge ->
                        edge.property<Any?>(key).let { prop ->
                            prop.isPresent() && prop.value() == propValue
                        }
                    }
                }

                TinkerEdgeIterator(
                    graph = graph,
                    edgeIds = edgeIds,
                    propertyFilters = remainingFilters
                )
            } else {
                // No indexed properties available, use full scan with all filters
                val allFilters = properties.map { (key, propValue) ->
                    { edge: Edge ->
                        edge.property<Any?>(key).let { prop ->
                            prop.isPresent() && prop.value() == propValue
                        }
                    }
                }

                TinkerEdgeIterator(
                    graph = graph,
                    propertyFilters = allFilters
                )
            }
        }

        /**
         * Creates an edge iterator for edges connecting specific vertices.
         */
        fun between(
            graph: TinkerGraph,
            outVertex: Vertex,
            inVertex: Vertex,
            vararg edgeLabels: String
        ): TinkerEdgeIterator {
            val outVertexId = outVertex.id()
            val inVertexId = inVertex.id()

            val connectionFilter: (Edge) -> Boolean = { edge ->
                edge.outVertex().id() == outVertexId && edge.inVertex().id() == inVertexId
            }

            return TinkerEdgeIterator(
                graph = graph,
                direction = Direction.OUT,
                edgeLabels = edgeLabels.toSet(),
                propertyFilters = listOf(connectionFilter)
            )
        }

        /**
         * Creates an edge iterator for edges with specific direction from any vertex.
         */
        fun withDirection(
            graph: TinkerGraph,
            direction: Direction,
            vararg edgeLabels: String
        ): TinkerEdgeIterator {
            return TinkerEdgeIterator(
                graph = graph,
                direction = direction,
                edgeLabels = edgeLabels.toSet()
            )
        }
    }
}
