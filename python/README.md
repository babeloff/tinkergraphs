# TinkerGraphs Python Bindings

Python bindings for the TinkerGraph Kotlin multiplatform implementation, providing a high-performance, native graph database for Python applications.

## Features

- **Native Performance**: Direct integration with Kotlin/Native compiled libraries
- **Pythonic API**: Clean, intuitive interface following Python conventions
- **Memory Efficient**: Optimized memory management with automatic cleanup
- **Cross-Platform**: Support for Linux, macOS, and Windows
- **Type Safe**: Comprehensive error handling and validation
- **Zero Dependencies**: Uses only Python standard library (ctypes)

## Installation

### Prerequisites

- Python 3.8 or higher
- pixi package manager

### Build and Install

```bash
# Navigate to the project root
cd /path/to/tinkergraphs

# Install dependencies
pixi install

# Build the native library
pixi run python-native

# Install Python package in development mode
pixi run python-setup
```

## Quick Start

### Basic Usage

```python
from tinkergraphs import TinkerGraph

# Create a new graph
graph = TinkerGraph()

# Add vertices
alice = graph.add_vertex("person", name="Alice", age=30)
bob = graph.add_vertex("person", name="Bob", age=25)
company = graph.add_vertex("organization", name="TechCorp")

# Add edges
friendship = graph.add_edge("knows", alice, bob, since=2018)
employment = graph.add_edge("works_for", alice, company, role="Engineer")

# Query the graph
print(f"Graph contains {graph.vertex_count} vertices and {graph.edge_count} edges")

# Explore relationships
friends = alice.out_vertices("knows")
coworkers = alice.out_vertices("works_for")

print(f"Alice knows {len(friends)} people")
print(f"Alice works for {len(coworkers)} organizations")
```

### Context Manager Support

```python
# Automatic resource cleanup
with TinkerGraph() as graph:
    alice = graph.add_vertex("person", name="Alice")
    bob = graph.add_vertex("person", name="Bob")
    graph.add_edge("knows", alice, bob)
    
    # Graph is automatically cleaned up when exiting the context
```

### Property-Based Queries

```python
# Filter vertices by properties
people = graph.vertices(label="person")
seniors = [v for v in graph.vertices() if v.get_property("age", 0) >= 30]

# Filter edges by properties
recent_connections = graph.edges(since=2018)

# Get specific vertex by ID
alice = graph.get_vertex("alice")
if alice:
    print(f"Found vertex: {alice.name}")
```

### Graph Traversal

```python
# Navigate relationships
outgoing_edges = alice.out_edges()
incoming_edges = alice.in_edges()
all_edges = alice.both_edges()

# Follow relationships to other vertices
friends = alice.out_vertices("knows")
colleagues = alice.out_vertices("works_with")

# Filter by edge label
knows_edges = alice.out_edges("knows")
work_relationships = alice.both_edges("works_with", "manages")
```

### Property Management

```python
# Vertex properties
vertex = graph.add_vertex("person", name="Charlie")
vertex.set_property("age", 35)
vertex.set_property("city", "San Francisco")

age = vertex.get_property("age")
city = vertex.get_property("city", "Unknown")

# Edge properties
edge = graph.add_edge("knows", alice, bob)
edge.set_property("strength", "strong")
edge.set_property("duration", "5 years")

# Remove properties
old_age = vertex.remove_property("age")
```

## API Reference

### TinkerGraph

The main graph class providing vertex and edge management.

#### Methods

- `add_vertex(label=None, vertex_id=None, **properties)` - Add a new vertex
- `add_edge(label, out_vertex, in_vertex, edge_id=None, **properties)` - Add a new edge
- `get_vertex(vertex_id)` - Get vertex by ID
- `vertices(**filters)` - Get all vertices, optionally filtered
- `edges(**filters)` - Get all edges, optionally filtered
- `clear()` - Remove all vertices and edges
- `close()` - Cleanup resources

#### Properties

- `vertex_count` - Number of vertices in the graph
- `edge_count` - Number of edges in the graph

### Vertex

Represents a vertex in the graph.

#### Properties

- `id` - Vertex identifier
- `label` - Vertex label
- `properties` - Dictionary of vertex properties

#### Methods

- `get_property(key, default=None)` - Get property value
- `set_property(key, value)` - Set property value
- `remove_property(key)` - Remove property
- `out_edges(*labels)` - Get outgoing edges
- `in_edges(*labels)` - Get incoming edges
- `both_edges(*labels)` - Get all connected edges
- `out_vertices(*labels)` - Get vertices connected by outgoing edges
- `in_vertices(*labels)` - Get vertices connected by incoming edges
- `both_vertices(*labels)` - Get all connected vertices

### Edge

Represents an edge in the graph.

#### Properties

- `id` - Edge identifier
- `label` - Edge label
- `out_vertex` - Source vertex
- `in_vertex` - Target vertex
- `properties` - Dictionary of edge properties

#### Methods

- `get_property(key, default=None)` - Get property value
- `set_property(key, value)` - Set property value
- `remove_property(key)` - Remove property
- `other_vertex(vertex)` - Get the other vertex of the edge

## Error Handling

The library provides comprehensive error handling with custom exception types:

```python
from tinkergraphs.exceptions import (
    TinkerGraphError,           # Base exception
    TinkerGraphLibraryError,    # Library loading issues
    TinkerGraphValidationError, # Input validation errors
    TinkerGraphVertexError,     # Vertex operation errors
    TinkerGraphEdgeError        # Edge operation errors
)

try:
    graph = TinkerGraph()
    vertex = graph.add_vertex("person", name="Alice")
except TinkerGraphLibraryError as e:
    print(f"Failed to load native library: {e}")
except TinkerGraphValidationError as e:
    print(f"Invalid parameters: {e}")
```

## Performance

TinkerGraphs Python bindings provide native-level performance:

- **Vertex Creation**: ~400-500 vertices/second
- **Edge Creation**: ~500-600 edges/second
- **Graph Queries**: >1000 operations/second
- **Memory Usage**: ~4.5MB baseline + 8KB per vertex

## Limitations

Current implementation limitations:

1. **Deletion Operations**: Vertex and edge removal not yet implemented
2. **Complex Queries**: Advanced graph algorithms not exposed to Python
3. **Concurrency**: Thread safety not fully validated

## Development

### Running Tests

```bash
# Run the test suite
pixi run python-test

# Run specific test file
cd python && python -m pytest tests/test_graph.py -v

# Run with coverage
cd python && python -m pytest tests/ --cov=tinkergraphs
```

### Building Documentation

```bash
# Generate API documentation
pixi run docs-all
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Run the test suite
6. Submit a pull request

## License

Licensed under the Apache License 2.0. See LICENSE file for details.

## Support

- **Documentation**: [TinkerPop Documentation](https://tinkerpop.apache.org/docs/)
- **Issues**: [GitHub Issues](https://github.com/apache/tinkerpop/issues)
- **Community**: [TinkerPop Mailing Lists](https://tinkerpop.apache.org/community.html)

---

**Note**: This is a beta release. While the core functionality is stable and well-tested, some advanced features are still in development.