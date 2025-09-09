"""
High-level Python API for TinkerGraph.

This module provides a Pythonic interface to the native TinkerGraph library,
wrapping the low-level ctypes bindings with high-level Python classes that
follow Python conventions and best practices.
"""

from typing import Dict, Any, Optional, List, Iterator, Union
import weakref

from .bindings import NativeGraphHandle
from .exceptions import (
    TinkerGraphError,
    TinkerGraphNativeError,
    TinkerGraphVertexError,
    TinkerGraphEdgeError,
    TinkerGraphMemoryError,
    TinkerGraphValidationError,
    raise_validation_error
)


class TinkerGraph:
    """
    Python wrapper for TinkerGraph providing a Pythonic API.

    This class provides access to the native Kotlin TinkerGraph implementation
    through a high-level Python interface that follows Python conventions
    and integrates seamlessly with Python code.

    Example:
        >>> graph = TinkerGraph()
        >>> alice = graph.add_vertex("person", name="Alice", age=30)
        >>> bob = graph.add_vertex("person", name="Bob", age=25)
        >>> edge = graph.add_edge("knows", alice, bob, since=2020)
        >>> print(f"Graph has {graph.vertex_count} vertices")
    """

    def __init__(self):
        """Create a new TinkerGraph instance."""
        try:
            self._native = NativeGraphHandle()
            self._vertices: Dict[int, 'Vertex'] = {}
            self._edges: Dict[int, 'Edge'] = {}
            self._vertex_id_counter = 0
            self._closed = False
        except TinkerGraphNativeError as e:
            raise TinkerGraphError(f"Failed to create TinkerGraph: {e}") from e

    def __enter__(self):
        """Context manager entry."""
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit - cleanup resources."""
        self.close()

    def __del__(self):
        """Destructor - cleanup resources."""
        if hasattr(self, '_closed') and not self._closed:
            self.close()

    def close(self):
        """
        Close the graph and cleanup all resources.

        This method should be called when you're done with the graph
        to ensure proper cleanup of native resources.
        """
        if not self._closed:
            # Clean up all vertices and edges
            for vertex in self._vertices.values():
                vertex._cleanup()
            for edge in self._edges.values():
                edge._cleanup()

            self._vertices.clear()
            self._edges.clear()
            self._closed = True

    def _check_not_closed(self):
        """Check that the graph hasn't been closed."""
        if self._closed:
            raise TinkerGraphError("Graph has been closed")

    def add_vertex(self, label: Optional[str] = None, vertex_id: Optional[str] = None,
                   **properties) -> 'Vertex':
        """
        Add a vertex to the graph.

        Args:
            label: Vertex label (optional, defaults to "vertex")
            vertex_id: Explicit vertex ID (optional, auto-generated if not provided)
            **properties: Vertex properties as keyword arguments

        Returns:
            The created Vertex instance

        Raises:
            TinkerGraphError: If vertex creation fails
            TinkerGraphValidationError: If parameters are invalid
        """
        self._check_not_closed()

        # Validate parameters
        if label is not None and not isinstance(label, str):
            raise_validation_error("label", "str or None", label)

        if vertex_id is not None and not isinstance(vertex_id, str):
            raise_validation_error("vertex_id", "str or None", vertex_id)

        # Set default label
        if label is None:
            label = "vertex"

        # Add label to properties if provided
        all_properties = dict(properties)
        if label != "vertex":  # Only add if not default
            all_properties["label"] = label

        try:
            if all_properties:
                vertex_ptr = self._native.add_vertex_with_properties(vertex_id, all_properties)
            else:
                vertex_ptr = self._native.add_vertex(vertex_id)

            # Get the actual ID from the native vertex if not provided
            if vertex_id is None:
                try:
                    vertex_id = self._native.get_vertex_id(vertex_ptr)
                except:
                    # Fall back to internal counter if native ID retrieval fails
                    vertex_id = str(self._vertex_id_counter)
                    self._vertex_id_counter += 1

            vertex = Vertex(self, vertex_ptr, vertex_id, label, properties)
            self._vertices[vertex_ptr] = vertex
            return vertex

        except TinkerGraphNativeError as e:
            raise TinkerGraphVertexError(f"Failed to add vertex: {e}") from e

    def add_edge(self, label: str, out_vertex: 'Vertex', in_vertex: 'Vertex',
                 edge_id: Optional[str] = None, **properties) -> 'Edge':
        """
        Add an edge to the graph.

        Args:
            label: Edge label (required)
            out_vertex: Source vertex
            in_vertex: Target vertex
            edge_id: Explicit edge ID (optional, auto-generated if not provided)
            **properties: Edge properties as keyword arguments

        Returns:
            The created Edge instance

        Raises:
            TinkerGraphError: If edge creation fails
            TinkerGraphValidationError: If parameters are invalid
        """
        self._check_not_closed()

        # Validate parameters
        if not isinstance(label, str):
            raise_validation_error("label", "str", label)

        if not isinstance(out_vertex, Vertex):
            raise_validation_error("out_vertex", "Vertex", out_vertex)

        if not isinstance(in_vertex, Vertex):
            raise_validation_error("in_vertex", "Vertex", in_vertex)

        if out_vertex._graph() != self or in_vertex._graph() != self:
            raise TinkerGraphValidationError("Vertices must belong to the same graph")

        try:
            if properties:
                edge_ptr = self._native.add_edge_with_properties(
                    label, out_vertex._ptr, in_vertex._ptr, properties
                )
            else:
                edge_ptr = self._native.add_edge(label, out_vertex._ptr, in_vertex._ptr)

            # Generate edge ID if not provided
            if edge_id is None:
                edge_id = f"{out_vertex.id}-{label}->{in_vertex.id}"

            edge = Edge(self, edge_ptr, edge_id, label, out_vertex, in_vertex, properties)
            self._edges[edge_ptr] = edge
            return edge

        except TinkerGraphNativeError as e:
            raise TinkerGraphEdgeError(f"Failed to add edge: {e}") from e

    @property
    def vertex_count(self) -> int:
        """Get the number of vertices in the graph."""
        self._check_not_closed()
        return self._native.vertex_count()

    @property
    def edge_count(self) -> int:
        """Get the number of edges in the graph."""
        self._check_not_closed()
        return self._native.edge_count()

    def vertices(self, **properties) -> List['Vertex']:
        """
        Get all vertices in the graph, optionally filtered by properties.

        Args:
            **properties: Property filters (exact match)

        Returns:
            List of vertices matching the criteria
        """
        self._check_not_closed()
        vertices = list(self._vertices.values())

        if properties:
            vertices = [v for v in vertices if v._matches_properties(properties)]

        return vertices

    def edges(self, **properties) -> List['Edge']:
        """
        Get all edges in the graph, optionally filtered by properties.

        Args:
            **properties: Property filters (exact match)

        Returns:
            List of edges matching the criteria
        """
        self._check_not_closed()
        edges = list(self._edges.values())

        if properties:
            edges = [e for e in edges if e._matches_properties(properties)]

        return edges

    def get_vertex(self, vertex_id: str) -> Optional['Vertex']:
        """
        Get a vertex by ID.

        Args:
            vertex_id: The vertex ID to search for

        Returns:
            The vertex if found, None otherwise
        """
        self._check_not_closed()
        for vertex in self._vertices.values():
            if vertex.id == vertex_id:
                return vertex
        return None

    def remove_vertex(self, vertex: 'Vertex'):
        """
        Remove a vertex from the graph.

        Note: This also removes all edges connected to the vertex.

        Args:
            vertex: The vertex to remove
        """
        self._check_not_closed()
        if vertex._ptr in self._vertices:
            # Remove connected edges first
            edges_to_remove = [e for e in self._edges.values()
                             if e.out_vertex == vertex or e.in_vertex == vertex]
            for edge in edges_to_remove:
                self.remove_edge(edge)

            # Remove the vertex
            vertex._cleanup()
            del self._vertices[vertex._ptr]

    def remove_edge(self, edge: 'Edge'):
        """
        Remove an edge from the graph.

        Args:
            edge: The edge to remove
        """
        self._check_not_closed()
        if edge._ptr in self._edges:
            edge._cleanup()
            del self._edges[edge._ptr]

    def clear(self):
        """Remove all vertices and edges from the graph."""
        self._check_not_closed()
        # Clear edges first
        for edge in list(self._edges.values()):
            self.remove_edge(edge)
        # Then clear vertices
        for vertex in list(self._vertices.values()):
            self.remove_vertex(vertex)

    def __len__(self) -> int:
        """Return the number of vertices in the graph."""
        return self.vertex_count

    def __str__(self) -> str:
        """String representation of the graph."""
        return f"TinkerGraph(vertices={self.vertex_count}, edges={self.edge_count})"

    def __repr__(self) -> str:
        """Developer representation of the graph."""
        return f"<TinkerGraph at 0x{id(self):x}: {self.vertex_count}V, {self.edge_count}E>"


class Vertex:
    """Represents a vertex in the TinkerGraph."""

    def __init__(self, graph: TinkerGraph, ptr: int, vertex_id: str,
                 label: str, properties: Dict[str, Any]):
        """
        Initialize a vertex.

        Args:
            graph: The parent graph
            ptr: Native pointer to the vertex
            vertex_id: The vertex ID
            label: The vertex label
            properties: Initial properties
        """
        self._graph_ref = weakref.ref(graph)
        self._ptr = ptr
        self.id = vertex_id
        self.label = label
        self.properties = dict(properties)
        self._disposed = False

    def _graph(self) -> Optional[TinkerGraph]:
        """Get the parent graph (may be None if graph was garbage collected)."""
        return self._graph_ref()

    def _cleanup(self):
        """Clean up native resources."""
        if not self._disposed and self._ptr:
            graph = self._graph()
            if graph and hasattr(graph, '_native'):
                try:
                    graph._native.destroy_vertex(self._ptr)
                except:
                    pass
            self._disposed = True

    def _matches_properties(self, filters: Dict[str, Any]) -> bool:
        """Check if vertex matches property filters."""
        for key, value in filters.items():
            if key not in self.properties or self.properties[key] != value:
                return False
        return True

    def get_property(self, key: str, default=None):
        """Get a property value by key."""
        return self.properties.get(key, default)

    def set_property(self, key: str, value: Any):
        """Set a property value (local only - not synchronized with native)."""
        if not isinstance(key, str):
            raise_validation_error("key", "str", key)
        self.properties[key] = value

    def remove_property(self, key: str) -> Any:
        """Remove a property and return its value."""
        return self.properties.pop(key, None)

    def out_edges(self, *labels) -> List['Edge']:
        """Get outgoing edges, optionally filtered by label."""
        graph = self._graph()
        if not graph:
            return []

        edges = [e for e in graph._edges.values() if e.out_vertex == self]
        if labels:
            edges = [e for e in edges if e.label in labels]
        return edges

    def in_edges(self, *labels) -> List['Edge']:
        """Get incoming edges, optionally filtered by label."""
        graph = self._graph()
        if not graph:
            return []

        edges = [e for e in graph._edges.values() if e.in_vertex == self]
        if labels:
            edges = [e for e in edges if e.label in labels]
        return edges

    def both_edges(self, *labels) -> List['Edge']:
        """Get all connected edges, optionally filtered by label."""
        return self.out_edges(*labels) + self.in_edges(*labels)

    def out_vertices(self, *labels) -> List['Vertex']:
        """Get vertices connected by outgoing edges."""
        return [e.in_vertex for e in self.out_edges(*labels)]

    def in_vertices(self, *labels) -> List['Vertex']:
        """Get vertices connected by incoming edges."""
        return [e.out_vertex for e in self.in_edges(*labels)]

    def both_vertices(self, *labels) -> List['Vertex']:
        """Get all connected vertices."""
        return self.out_vertices(*labels) + self.in_vertices(*labels)

    def __str__(self) -> str:
        """String representation of the vertex."""
        props_str = ", ".join(f"{k}={v}" for k, v in self.properties.items())
        return f"Vertex(id={self.id}, label={self.label}, properties={{{props_str}}})"

    def __repr__(self) -> str:
        """Developer representation of the vertex."""
        return f"<Vertex {self.id} at 0x{id(self):x}>"

    def __eq__(self, other) -> bool:
        """Check equality based on ID and graph."""
        if not isinstance(other, Vertex):
            return False
        return self.id == other.id and self._graph() == other._graph()

    def __hash__(self) -> int:
        """Hash based on ID."""
        return hash(self.id)


class Edge:
    """Represents an edge in the TinkerGraph."""

    def __init__(self, graph: TinkerGraph, ptr: int, edge_id: str, label: str,
                 out_vertex: Vertex, in_vertex: Vertex, properties: Dict[str, Any]):
        """
        Initialize an edge.

        Args:
            graph: The parent graph
            ptr: Native pointer to the edge
            edge_id: The edge ID
            label: The edge label
            out_vertex: Source vertex
            in_vertex: Target vertex
            properties: Initial properties
        """
        self._graph_ref = weakref.ref(graph)
        self._ptr = ptr
        self.id = edge_id
        self.label = label
        self.out_vertex = out_vertex
        self.in_vertex = in_vertex
        self.properties = dict(properties)
        self._disposed = False

    def _graph(self) -> Optional[TinkerGraph]:
        """Get the parent graph (may be None if graph was garbage collected)."""
        return self._graph_ref()

    def _cleanup(self):
        """Clean up native resources."""
        if not self._disposed and self._ptr:
            graph = self._graph()
            if graph and hasattr(graph, '_native'):
                try:
                    graph._native.destroy_edge(self._ptr)
                except:
                    pass
            self._disposed = True

    def _matches_properties(self, filters: Dict[str, Any]) -> bool:
        """Check if edge matches property filters."""
        for key, value in filters.items():
            if key not in self.properties or self.properties[key] != value:
                return False
        return True

    def get_property(self, key: str, default=None):
        """Get a property value by key."""
        return self.properties.get(key, default)

    def set_property(self, key: str, value: Any):
        """Set a property value (local only - not synchronized with native)."""
        if not isinstance(key, str):
            raise_validation_error("key", "str", key)
        self.properties[key] = value

    def remove_property(self, key: str) -> Any:
        """Remove a property and return its value."""
        return self.properties.pop(key, None)

    def other_vertex(self, vertex: Vertex) -> Vertex:
        """Get the other vertex of this edge."""
        if vertex == self.out_vertex:
            return self.in_vertex
        elif vertex == self.in_vertex:
            return self.out_vertex
        else:
            raise TinkerGraphValidationError("Vertex is not connected to this edge")

    def __str__(self) -> str:
        """String representation of the edge."""
        props_str = ", ".join(f"{k}={v}" for k, v in self.properties.items())
        return (f"Edge(id={self.id}, label={self.label}, "
                f"out={self.out_vertex.id}, in={self.in_vertex.id}, "
                f"properties={{{props_str}}})")

    def __repr__(self) -> str:
        """Developer representation of the edge."""
        return f"<Edge {self.id} at 0x{id(self):x}>"

    def __eq__(self, other) -> bool:
        """Check equality based on ID and graph."""
        if not isinstance(other, Edge):
            return False
        return self.id == other.id and self._graph() == other._graph()

    def __hash__(self) -> int:
        """Hash based on ID."""
        return hash(self.id)
