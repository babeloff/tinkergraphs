"""
TinkerGraphs Python Bindings

Python bindings for the TinkerGraph Kotlin multiplatform implementation.
This package provides a Pythonic interface to the native TinkerGraph library
compiled from Kotlin/Native.

Example usage:
    from tinkergraphs import TinkerGraph

    # Create a new graph
    graph = TinkerGraph()

    # Add vertices
    alice = graph.add_vertex("person", name="Alice", age=30)
    bob = graph.add_vertex("person", name="Bob", age=25)

    # Add an edge
    edge = graph.add_edge("knows", alice, bob, since=2020)

    # Query the graph
    print(f"Graph has {graph.vertex_count} vertices and {graph.edge_count} edges")
"""

__version__ = "1.0.0"
__author__ = "Apache TinkerPop"
__email__ = "dev@tinkerpop.apache.org"

# Import main classes for public API
from .graph import TinkerGraph, Vertex, Edge
from .exceptions import TinkerGraphError

# Define what gets imported with "from tinkergraphs import *"
__all__ = [
    "TinkerGraph",
    "Vertex",
    "Edge",
    "TinkerGraphError",
]

# Package metadata
__package_info__ = {
    "name": "tinkergraphs",
    "version": __version__,
    "description": "Python bindings for TinkerGraph Kotlin multiplatform implementation",
    "author": __author__,
    "email": __email__,
    "license": "Apache License 2.0",
    "url": "https://github.com/apache/tinkerpop",
}
