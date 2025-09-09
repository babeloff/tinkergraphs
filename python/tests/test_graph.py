"""
Comprehensive test suite for TinkerGraph Python bindings.

This module contains unit tests and integration tests for the TinkerGraph
Python API, testing graph creation, vertex/edge operations, property handling,
and error conditions.
"""

import pytest
import time
from typing import List

from tinkergraphs import TinkerGraph, Vertex, Edge
from tinkergraphs.exceptions import (
    TinkerGraphError,
    TinkerGraphVertexError,
    TinkerGraphEdgeError,
    TinkerGraphValidationError,
    TinkerGraphLibraryError
)


class TestTinkerGraph:
    """Test the main TinkerGraph class."""

    def test_create_graph(self):
        """Test basic graph creation."""
        graph = TinkerGraph()
        assert graph.vertex_count == 0
        assert graph.edge_count == 0
        assert len(graph) == 0

    def test_graph_context_manager(self):
        """Test graph as context manager."""
        with TinkerGraph() as graph:
            assert graph.vertex_count == 0
            v = graph.add_vertex("test", name="Alice")
            assert graph.vertex_count == 1

    def test_graph_string_representations(self):
        """Test string representations of graph."""
        graph = TinkerGraph()
        str_repr = str(graph)
        assert "TinkerGraph" in str_repr
        assert "vertices=0" in str_repr
        assert "edges=0" in str_repr

        repr_str = repr(graph)
        assert "TinkerGraph" in repr_str
        assert "0V" in repr_str
        assert "0E" in repr_str


class TestVertex:
    """Test vertex operations."""

    def test_add_vertex_no_params(self):
        """Test adding vertex without parameters."""
        graph = TinkerGraph()
        vertex = graph.add_vertex()

        assert graph.vertex_count == 1
        assert vertex.label == "vertex"
        assert isinstance(vertex.id, str)
        assert len(vertex.properties) == 0

    def test_add_vertex_with_id(self):
        """Test adding vertex with explicit ID."""
        graph = TinkerGraph()
        vertex = graph.add_vertex(vertex_id="user123")

        assert graph.vertex_count == 1
        assert vertex.id == "user123"

    def test_add_vertex_with_label(self):
        """Test adding vertex with label."""
        graph = TinkerGraph()
        vertex = graph.add_vertex("person")

        assert graph.vertex_count == 1
        assert vertex.label == "person"

    def test_add_vertex_with_properties(self):
        """Test adding vertex with properties."""
        graph = TinkerGraph()
        vertex = graph.add_vertex("person", name="Alice", age=30, active=True)

        assert graph.vertex_count == 1
        assert vertex.properties["name"] == "Alice"
        assert vertex.properties["age"] == 30
        assert vertex.properties["active"] == True

    def test_vertex_property_operations(self):
        """Test vertex property get/set operations."""
        graph = TinkerGraph()
        vertex = graph.add_vertex("person", name="Alice", age=30)

        # Test get_property
        assert vertex.get_property("name") == "Alice"
        assert vertex.get_property("nonexistent") is None
        assert vertex.get_property("nonexistent", "default") == "default"

        # Test set_property
        vertex.set_property("city", "New York")
        assert vertex.get_property("city") == "New York"

        # Test remove_property
        removed_value = vertex.remove_property("age")
        assert removed_value == 30
        assert "age" not in vertex.properties

    def test_vertex_validation_errors(self):
        """Test vertex validation errors."""
        graph = TinkerGraph()

        with pytest.raises(TinkerGraphValidationError):
            graph.add_vertex(label=123)  # Invalid label type

        with pytest.raises(TinkerGraphValidationError):
            graph.add_vertex(vertex_id=456)  # Invalid ID type

    def test_vertex_equality_and_hashing(self):
        """Test vertex equality and hashing."""
        graph = TinkerGraph()
        v1 = graph.add_vertex("person", vertex_id="alice")
        v2 = graph.add_vertex("person", vertex_id="bob")
        v3 = graph.get_vertex("alice")

        assert v1 == v3  # Same vertex
        assert v1 != v2  # Different vertices
        assert hash(v1) == hash(v3)
        assert hash(v1) != hash(v2)

    def test_vertex_string_representations(self):
        """Test vertex string representations."""
        graph = TinkerGraph()
        vertex = graph.add_vertex("person", vertex_id="alice", name="Alice", age=30)

        str_repr = str(vertex)
        assert "Vertex" in str_repr
        assert "alice" in str_repr
        assert "person" in str_repr

        repr_str = repr(vertex)
        assert "Vertex alice" in repr_str


class TestEdge:
    """Test edge operations."""

    def test_add_edge_basic(self):
        """Test basic edge creation."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", vertex_id="alice", name="Alice")
        bob = graph.add_vertex("person", vertex_id="bob", name="Bob")

        edge = graph.add_edge("knows", alice, bob)

        assert graph.edge_count == 1
        assert edge.label == "knows"
        assert edge.out_vertex == alice
        assert edge.in_vertex == bob

    def test_add_edge_with_properties(self):
        """Test edge creation with properties."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", name="Alice")
        bob = graph.add_vertex("person", name="Bob")

        edge = graph.add_edge("knows", alice, bob, since=2018, weight=0.8)

        assert graph.edge_count == 1
        assert edge.properties["since"] == 2018
        assert edge.properties["weight"] == 0.8

    def test_edge_property_operations(self):
        """Test edge property operations."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", name="Alice")
        bob = graph.add_vertex("person", name="Bob")
        edge = graph.add_edge("knows", alice, bob, since=2018)

        # Test property operations
        assert edge.get_property("since") == 2018
        edge.set_property("strength", "strong")
        assert edge.get_property("strength") == "strong"

        removed = edge.remove_property("since")
        assert removed == 2018
        assert "since" not in edge.properties

    def test_edge_validation_errors(self):
        """Test edge validation errors."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", name="Alice")
        bob = graph.add_vertex("person", name="Bob")

        with pytest.raises(TinkerGraphValidationError):
            graph.add_edge(123, alice, bob)  # Invalid label type

        with pytest.raises(TinkerGraphValidationError):
            graph.add_edge("knows", "not_vertex", bob)  # Invalid vertex type

        with pytest.raises(TinkerGraphValidationError):
            graph.add_edge("knows", alice, "not_vertex")  # Invalid vertex type

    def test_edge_other_vertex(self):
        """Test getting the other vertex of an edge."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", name="Alice")
        bob = graph.add_vertex("person", name="Bob")
        edge = graph.add_edge("knows", alice, bob)

        assert edge.other_vertex(alice) == bob
        assert edge.other_vertex(bob) == alice

        charlie = graph.add_vertex("person", name="Charlie")
        with pytest.raises(TinkerGraphValidationError):
            edge.other_vertex(charlie)

    def test_edge_string_representations(self):
        """Test edge string representations."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", vertex_id="alice", name="Alice")
        bob = graph.add_vertex("person", vertex_id="bob", name="Bob")
        edge = graph.add_edge("knows", alice, bob, since=2018)

        str_repr = str(edge)
        assert "Edge" in str_repr
        assert "knows" in str_repr
        assert "alice" in str_repr
        assert "bob" in str_repr


class TestGraphTraversal:
    """Test graph traversal operations."""

    def test_vertex_edges(self):
        """Test vertex edge traversal methods."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", name="Alice")
        bob = graph.add_vertex("person", name="Bob")
        charlie = graph.add_vertex("person", name="Charlie")

        knows_edge = graph.add_edge("knows", alice, bob)
        works_with_edge = graph.add_edge("works_with", alice, charlie)
        friends_edge = graph.add_edge("friends", bob, alice)

        # Test outgoing edges
        out_edges = alice.out_edges()
        assert len(out_edges) == 2
        assert knows_edge in out_edges
        assert works_with_edge in out_edges

        # Test incoming edges
        in_edges = alice.in_edges()
        assert len(in_edges) == 1
        assert friends_edge in in_edges

        # Test both edges
        both_edges = alice.both_edges()
        assert len(both_edges) == 3

        # Test filtered edges
        knows_edges = alice.out_edges("knows")
        assert len(knows_edges) == 1
        assert knows_edges[0] == knows_edge

    def test_vertex_vertices(self):
        """Test vertex-to-vertex traversal methods."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", name="Alice")
        bob = graph.add_vertex("person", name="Bob")
        charlie = graph.add_vertex("person", name="Charlie")

        graph.add_edge("knows", alice, bob)
        graph.add_edge("works_with", alice, charlie)
        graph.add_edge("friends", bob, alice)

        # Test outgoing vertices
        out_vertices = alice.out_vertices()
        assert len(out_vertices) == 2
        assert bob in out_vertices
        assert charlie in out_vertices

        # Test incoming vertices
        in_vertices = alice.in_vertices()
        assert len(in_vertices) == 1
        assert bob in in_vertices

        # Test both vertices
        both_vertices = alice.both_vertices()
        assert len(both_vertices) == 3  # Bob appears twice


class TestGraphQueries:
    """Test graph querying operations."""

    def test_get_vertices(self):
        """Test getting vertices with and without filters."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", name="Alice", age=30)
        bob = graph.add_vertex("person", name="Bob", age=25)
        company = graph.add_vertex("organization", name="TechCorp")

        # Test get all vertices
        all_vertices = graph.vertices()
        assert len(all_vertices) == 3

        # Test filtered by properties
        persons = graph.vertices(age=30)
        assert len(persons) == 1
        assert alice in persons

        people = graph.vertices(name="Bob")
        assert len(people) == 1
        assert bob in people

    def test_get_edges(self):
        """Test getting edges with and without filters."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", name="Alice")
        bob = graph.add_vertex("person", name="Bob")
        charlie = graph.add_vertex("person", name="Charlie")

        knows_edge = graph.add_edge("knows", alice, bob, since=2018)
        works_with_edge = graph.add_edge("works_with", alice, charlie, project="X")

        # Test get all edges
        all_edges = graph.edges()
        assert len(all_edges) == 2

        # Test filtered edges
        recent_edges = graph.edges(since=2018)
        assert len(recent_edges) == 1
        assert knows_edge in recent_edges

    def test_get_vertex_by_id(self):
        """Test getting vertex by ID."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", vertex_id="alice", name="Alice")

        found_vertex = graph.get_vertex("alice")
        assert found_vertex == alice

        not_found = graph.get_vertex("nonexistent")
        assert not_found is None


class TestGraphModification:
    """Test graph modification operations."""

    def test_remove_vertex(self):
        """Test vertex removal."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", name="Alice")
        bob = graph.add_vertex("person", name="Bob")
        edge = graph.add_edge("knows", alice, bob)

        assert graph.vertex_count == 2
        assert graph.edge_count == 1

        graph.remove_vertex(alice)

        assert graph.vertex_count == 1
        assert graph.edge_count == 0  # Connected edges should be removed

    def test_remove_edge(self):
        """Test edge removal."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", name="Alice")
        bob = graph.add_vertex("person", name="Bob")
        edge = graph.add_edge("knows", alice, bob)

        assert graph.edge_count == 1

        graph.remove_edge(edge)

        assert graph.edge_count == 0
        assert graph.vertex_count == 2  # Vertices should remain

    def test_clear_graph(self):
        """Test clearing the entire graph."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", name="Alice")
        bob = graph.add_vertex("person", name="Bob")
        graph.add_edge("knows", alice, bob)

        assert graph.vertex_count == 2
        assert graph.edge_count == 1

        graph.clear()

        assert graph.vertex_count == 0
        assert graph.edge_count == 0


class TestGraphLifecycle:
    """Test graph lifecycle management."""

    def test_graph_close(self):
        """Test explicit graph closure."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", name="Alice")

        assert graph.vertex_count == 1

        graph.close()

        with pytest.raises(TinkerGraphError):
            graph.add_vertex("person", name="Bob")

        with pytest.raises(TinkerGraphError):
            _ = graph.vertex_count

    def test_graph_closed_operations(self):
        """Test that operations fail on closed graph."""
        graph = TinkerGraph()
        alice = graph.add_vertex("person", name="Alice")
        graph.close()

        with pytest.raises(TinkerGraphError):
            graph.add_vertex("person", name="Bob")

        with pytest.raises(TinkerGraphError):
            graph.add_edge("knows", alice, alice)

        with pytest.raises(TinkerGraphError):
            graph.vertices()


class TestPerformance:
    """Performance and stress tests."""

    def test_large_graph_creation(self):
        """Test creating a graph with many vertices and edges."""
        graph = TinkerGraph()
        vertex_count = 1000

        # Add vertices
        start_time = time.time()
        vertices = []
        for i in range(vertex_count):
            vertex = graph.add_vertex("person", vertex_id=f"user{i}", index=i)
            vertices.append(vertex)
        vertex_time = time.time() - start_time

        assert graph.vertex_count == vertex_count

        # Add edges (create a ring)
        start_time = time.time()
        for i in range(vertex_count):
            next_i = (i + 1) % vertex_count
            graph.add_edge("connects", vertices[i], vertices[next_i], weight=i)
        edge_time = time.time() - start_time

        assert graph.edge_count == vertex_count

        # Performance logging
        print(f"\nPerformance metrics:")
        print(f"Added {vertex_count} vertices in {vertex_time:.3f}s ({vertex_count/vertex_time:.1f} vertices/sec)")
        print(f"Added {vertex_count} edges in {edge_time:.3f}s ({vertex_count/edge_time:.1f} edges/sec)")

    def test_graph_traversal_performance(self):
        """Test performance of graph traversal operations."""
        graph = TinkerGraph()

        # Create a star topology (one central vertex connected to many others)
        central = graph.add_vertex("hub", name="Central")
        satellites = []

        for i in range(100):
            satellite = graph.add_vertex("node", name=f"Satellite{i}")
            satellites.append(satellite)
            graph.add_edge("connects", central, satellite)

        # Test traversal performance
        start_time = time.time()
        for _ in range(100):
            out_vertices = central.out_vertices()
            assert len(out_vertices) == 100
        traversal_time = time.time() - start_time

        print(f"100 traversals of 100-edge vertex: {traversal_time:.3f}s ({100/traversal_time:.1f} traversals/sec)")


class TestErrorHandling:
    """Test error handling and edge cases."""

    def test_cross_graph_edge_creation(self):
        """Test that edges cannot be created between vertices from different graphs."""
        graph1 = TinkerGraph()
        graph2 = TinkerGraph()

        alice = graph1.add_vertex("person", name="Alice")
        bob = graph2.add_vertex("person", name="Bob")

        with pytest.raises(TinkerGraphValidationError):
            graph1.add_edge("knows", alice, bob)

    def test_property_validation(self):
        """Test property validation."""
        graph = TinkerGraph()
        vertex = graph.add_vertex("person", name="Alice")

        with pytest.raises(TinkerGraphValidationError):
            vertex.set_property(123, "value")  # Invalid property key type

    def test_memory_cleanup(self):
        """Test that resources are cleaned up properly."""
        graph = TinkerGraph()

        # Create many vertices and edges
        vertices = []
        for i in range(100):
            vertex = graph.add_vertex("person", vertex_id=f"user{i}")
            vertices.append(vertex)

        for i in range(99):
            graph.add_edge("knows", vertices[i], vertices[i+1])

        # Close graph and verify cleanup
        graph.close()

        # Graph should be marked as closed
        with pytest.raises(TinkerGraphError):
            graph.vertex_count


if __name__ == "__main__":
    # Run a simple test if executed directly
    test = TestTinkerGraph()
    test.test_create_graph()
    print("Basic test passed!")
