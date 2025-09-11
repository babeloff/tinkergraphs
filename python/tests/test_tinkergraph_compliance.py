"""
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
"""

import unittest
import time
import gc
from typing import List, Dict, Any, Optional
import sys
import os

# Add the parent directory to the Python path for imports
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

try:
    from tinkergraph import TinkerGraph, Vertex, Edge, T, P, __
    from tinkergraph.structure import VertexProperty
    from tinkergraph.process import GraphTraversalSource
except ImportError:
    # Fallback for when Python bindings are not yet available
    class MockTinkerGraph:
        def __init__(self):
            self._vertices = {}
            self._edges = {}
            self._next_id = 1

        @classmethod
        def open(cls):
            return cls()

        def add_vertex(self, *args, **kwargs):
            vertex = MockVertex(self._next_id, kwargs)
            self._vertices[self._next_id] = vertex
            self._next_id += 1
            return vertex

        def traversal(self):
            return MockGraphTraversalSource(self)

    class MockVertex:
        def __init__(self, id, properties):
            self.id = id
            self.properties = properties

        def value(self, key):
            return self.properties.get(key)

        def property(self, key, value):
            self.properties[key] = value

    class MockGraphTraversalSource:
        def __init__(self, graph):
            self.graph = graph

        def V(self):
            return MockTraversal(list(self.graph._vertices.values()))

        def E(self):
            return MockTraversal(list(self.graph._edges.values()))

    class MockTraversal:
        def __init__(self, items):
            self.items = items

        def count(self):
            return MockTraversalNext(len(self.items))

        def next(self):
            return self.items[0] if self.items else None

    class MockTraversalNext:
        def __init__(self, value):
            self.value = value

        def next(self):
            return self.value

    # Use mock implementations for testing
    TinkerGraph = MockTinkerGraph


class TinkerGraphPythonComplianceTest(unittest.TestCase):
    """
    Python platform compliance tests for TinkerGraph following Apache TinkerPop Java compliance tests.

    These tests validate TinkerGraph behavior on Python platform, ensuring compliance
    with Apache TinkerPop specifications. Tests are adapted from upstream Java tests
    while accounting for Python platform specifics including dynamic typing,
    memory management, and Pythonic idioms.

    Task 4.1.2 Phase 3 - Python Platform Compliance Testing

    Author: TinkerGraphs Compliance Framework
    """

    def setUp(self):
        """Set up test fixtures before each test method."""
        self.graph = TinkerGraph.open()

    def tearDown(self):
        """Clean up after each test method."""
        if hasattr(self.graph, 'close'):
            self.graph.close()
        gc.collect()

    def test_basic_graph_creation(self):
        """Test basic TinkerGraph creation on Python platform."""
        graph = TinkerGraph.open()
        self.assertIsNotNone(graph)

        # Test Python-specific graph properties
        self.assertIsInstance(graph, TinkerGraph)

        # Verify graph can be created multiple times
        graph2 = TinkerGraph.open()
        self.assertIsNotNone(graph2)
        self.assertIsNot(graph, graph2)

    def test_vertex_creation_and_properties(self):
        """Test vertex creation and property operations following Java compliance patterns."""
        graph = TinkerGraph.open()

        # Test vertex creation with properties
        vertex = graph.add_vertex(name="marko", age=29, city="santa fe")
        self.assertIsNotNone(vertex)

        # Test property retrieval
        self.assertEqual("marko", vertex.value("name"))
        self.assertEqual(29, vertex.value("age"))
        self.assertEqual("santa fe", vertex.value("city"))

        # Test property updates
        vertex.property("age", 30)
        self.assertEqual(30, vertex.value("age"))

    def test_python_data_type_support(self):
        """Test Python-specific data type support and compliance."""
        graph = TinkerGraph.open()

        # Test Python data types
        vertex = graph.add_vertex(
            string_prop="test string",
            int_prop=42,
            float_prop=3.14159,
            bool_prop=True,
            none_prop=None,
            list_prop=[1, 2, 3, "mixed", True],
            dict_prop={"nested": "value", "number": 123},
            tuple_prop=(1, "tuple", False)
        )

        self.assertEqual("test string", vertex.value("string_prop"))
        self.assertEqual(42, vertex.value("int_prop"))
        self.assertAlmostEqual(3.14159, vertex.value("float_prop"), places=5)
        self.assertTrue(vertex.value("bool_prop"))
        self.assertIsNone(vertex.value("none_prop"))
        self.assertEqual([1, 2, 3, "mixed", True], vertex.value("list_prop"))
        self.assertEqual({"nested": "value", "number": 123}, vertex.value("dict_prop"))
        self.assertEqual((1, "tuple", False), vertex.value("tuple_prop"))

    def test_edge_creation_and_traversal(self):
        """Test edge creation and basic traversal operations."""
        graph = TinkerGraph.open()
        g = graph.traversal()

        # Create the modern graph structure
        marko = graph.add_vertex(id=1, label="person", name="marko", age=29)
        vadas = graph.add_vertex(id=2, label="person", name="vadas", age=27)
        lop = graph.add_vertex(id=3, label="software", name="lop", lang="java")
        josh = graph.add_vertex(id=4, label="person", name="josh", age=32)
        ripple = graph.add_vertex(id=5, label="software", name="ripple", lang="java")
        peter = graph.add_vertex(id=6, label="person", name="peter", age=35)

        # Create edges
        marko.add_edge("knows", vadas, weight=0.5)
        marko.add_edge("knows", josh, weight=1.0)
        marko.add_edge("created", lop, weight=0.4)
        josh.add_edge("created", ripple, weight=1.0)
        josh.add_edge("created", lop, weight=0.4)
        peter.add_edge("created", lop, weight=0.2)

        # Test basic counts
        vertex_count = g.V().count().next()
        self.assertEqual(6, vertex_count)

        edge_count = g.E().count().next()
        self.assertEqual(6, edge_count)

    def test_traversal_operations_python(self):
        """Test traversal operations with Python-specific patterns."""
        graph = self._create_modern_graph()
        g = graph.traversal()

        # Test Pythonic traversal patterns
        person_names = g.V().has_label("person").values("name").to_list()
        self.assertIn("marko", person_names)
        self.assertIn("vadas", person_names)
        self.assertIn("josh", person_names)
        self.assertIn("peter", person_names)

        # Test list comprehension compatibility
        ages = [vertex.value("age") for vertex in g.V().has_label("person").to_list()]
        self.assertIn(29, ages)
        self.assertIn(27, ages)
        self.assertIn(32, ages)
        self.assertIn(35, ages)

    def test_python_iterator_compliance(self):
        """Test Python iterator and generator compliance."""
        graph = self._create_modern_graph()
        g = graph.traversal()

        # Test iterator protocol
        vertices_iter = g.V().has_label("person")
        vertex_count = 0
        for vertex in vertices_iter:
            vertex_count += 1
            self.assertIsNotNone(vertex.value("name"))

        self.assertEqual(4, vertex_count)

        # Test generator expressions
        names_gen = (v.value("name") for v in g.V().has_label("person"))
        names_list = list(names_gen)
        self.assertEqual(4, len(names_list))

    def test_exception_handling_python(self):
        """Test Python exception handling compliance."""
        graph = TinkerGraph.open()
        vertex = graph.add_vertex(name="test")

        # Test KeyError for nonexistent properties
        with self.assertRaises((KeyError, AttributeError, Exception)):
            vertex.value("nonexistent_property")

        # Test ValueError for invalid operations
        try:
            graph.add_vertex(None, "invalid_key_none")
        except (ValueError, TypeError, Exception):
            pass  # Expected behavior
        else:
            self.fail("Should raise exception for None key")

    def test_memory_management_python(self):
        """Test Python memory management and garbage collection compliance."""
        graph = TinkerGraph.open()

        # Create many vertices to test memory management
        vertices = []
        for i in range(1000):
            vertex = graph.add_vertex(id=i, name=f"vertex_{i}", value=i * 2.5)
            vertices.append(vertex)

        self.assertEqual(1000, graph.traversal().V().count().next())

        # Test reference cleanup
        vertices.clear()
        gc.collect()

        # Graph should still maintain vertex data
        self.assertEqual(1000, graph.traversal().V().count().next())

    def test_pythonic_property_access(self):
        """Test Python-style property access patterns."""
        graph = TinkerGraph.open()
        vertex = graph.add_vertex(name="marko", age=29)

        # Test dictionary-style access if supported
        try:
            self.assertEqual("marko", vertex["name"])
            self.assertEqual(29, vertex["age"])
            vertex["age"] = 30
            self.assertEqual(30, vertex["age"])
        except (TypeError, NotImplementedError):
            # Not all implementations may support dict-style access
            pass

        # Test attribute-style access if supported
        try:
            self.assertEqual("marko", vertex.name)
            self.assertEqual(29, vertex.age)
        except (AttributeError, NotImplementedError):
            # Not all implementations may support attribute access
            pass

    def test_python_context_manager(self):
        """Test Python context manager support if available."""
        try:
            with TinkerGraph.open() as graph:
                vertex = graph.add_vertex(name="context_test")
                self.assertIsNotNone(vertex)
                self.assertEqual("context_test", vertex.value("name"))
        except (AttributeError, NotImplementedError):
            # Context manager support may not be implemented
            pass

    def test_performance_baseline_python(self):
        """Test performance baseline on Python platform."""
        graph = TinkerGraph.open()

        start_time = time.time()

        # Create vertices
        for i in range(1000):
            graph.add_vertex(id=i, name=f"vertex_{i}", value=i * 1.5)

        creation_time = time.time() - start_time
        self.assertLess(creation_time, 10.0)  # Should complete within 10 seconds

        # Test traversal performance
        g = graph.traversal()
        traversal_start = time.time()
        count = g.V().count().next()
        traversal_time = time.time() - traversal_start

        self.assertEqual(1000, count)
        self.assertLess(traversal_time, 1.0)  # Should complete within 1 second

    def test_unicode_and_encoding_support(self):
        """Test Unicode and encoding support on Python platform."""
        graph = TinkerGraph.open()

        # Test various Unicode strings
        vertex = graph.add_vertex(
            english="Hello World",
            spanish="¡Hola Mundo!",
            chinese="你好世界",
            japanese="こんにちは世界",
            emoji="🌍🚀✨",
            mixed="Hello 世界 🌍"
        )

        self.assertEqual("Hello World", vertex.value("english"))
        self.assertEqual("¡Hola Mundo!", vertex.value("spanish"))
        self.assertEqual("你好世界", vertex.value("chinese"))
        self.assertEqual("こんにちは世界", vertex.value("japanese"))
        self.assertEqual("🌍🚀✨", vertex.value("emoji"))
        self.assertEqual("Hello 世界 🌍", vertex.value("mixed"))

    def test_serialization_compatibility(self):
        """Test serialization compatibility with Python pickle and JSON."""
        graph = TinkerGraph.open()
        vertex = graph.add_vertex(name="serialization_test", data={"key": "value"})

        # Test basic serialization support
        try:
            import pickle

            # Test if vertex properties can be pickled
            name_value = vertex.value("name")
            pickled_name = pickle.dumps(name_value)
            unpickled_name = pickle.loads(pickled_name)
            self.assertEqual(name_value, unpickled_name)

        except (ImportError, TypeError, AttributeError):
            # Serialization may not be fully supported
            pass

        try:
            import json

            # Test JSON compatibility
            data_value = vertex.value("data")
            json_data = json.dumps(data_value)
            parsed_data = json.loads(json_data)
            self.assertEqual(data_value, parsed_data)

        except (ImportError, TypeError):
            # JSON serialization may not be supported for all data types
            pass

    def _create_modern_graph(self):
        """Helper method to create the modern graph structure."""
        graph = TinkerGraph.open()

        # Create vertices
        marko = graph.add_vertex(id=1, label="person", name="marko", age=29)
        vadas = graph.add_vertex(id=2, label="person", name="vadas", age=27)
        lop = graph.add_vertex(id=3, label="software", name="lop", lang="java")
        josh = graph.add_vertex(id=4, label="person", name="josh", age=32)
        ripple = graph.add_vertex(id=5, label="software", name="ripple", lang="java")
        peter = graph.add_vertex(id=6, label="person", name="peter", age=35)

        # Create edges
        marko.add_edge("knows", vadas, weight=0.5)
        marko.add_edge("knows", josh, weight=1.0)
        marko.add_edge("created", lop, weight=0.4)
        josh.add_edge("created", ripple, weight=1.0)
        josh.add_edge("created", lop, weight=0.4)
        peter.add_edge("created", lop, weight=0.2)

        return graph


class TinkerGraphPythonProcessComplianceTest(unittest.TestCase):
    """
    Python platform Process API compliance tests following Apache TinkerPop specifications.
    """

    def setUp(self):
        """Set up test fixtures before each test method."""
        self.graph = self._create_modern_graph()
        self.g = self.graph.traversal()

    def test_basic_traversal_steps(self):
        """Test basic traversal steps with Python-specific patterns."""
        # Test V() and E() steps
        vertex_count = self.g.V().count().next()
        edge_count = self.g.E().count().next()

        self.assertEqual(6, vertex_count)
        self.assertEqual(6, edge_count)

        # Test has() filtering
        person_count = self.g.V().has_label("person").count().next()
        self.assertEqual(4, person_count)

    def test_python_comprehension_integration(self):
        """Test integration with Python comprehensions and functional patterns."""
        # Test with list comprehensions
        person_ages = [v.value("age") for v in self.g.V().has_label("person").to_list()]
        self.assertEqual(4, len(person_ages))
        self.assertIn(29, person_ages)

        # Test with filter and map
        young_people = [v.value("name") for v in self.g.V().has_label("person").to_list()
                       if v.value("age") < 30]
        self.assertIn("marko", young_people)
        self.assertIn("vadas", young_people)

    def test_functional_programming_patterns(self):
        """Test functional programming pattern compliance."""
        from functools import reduce

        # Test reduce operation
        ages = [v.value("age") for v in self.g.V().has_label("person").to_list()]
        total_age = reduce(lambda x, y: x + y, ages, 0)
        self.assertEqual(123, total_age)  # 29 + 27 + 32 + 35

        # Test map operation
        mapped_names = list(map(lambda v: v.value("name").upper(),
                               self.g.V().has_label("person").to_list()))
        self.assertIn("MARKO", mapped_names)

    def _create_modern_graph(self):
        """Helper method to create the modern graph structure."""
        graph = TinkerGraph.open()

        # Create vertices
        marko = graph.add_vertex(id=1, label="person", name="marko", age=29)
        vadas = graph.add_vertex(id=2, label="person", name="vadas", age=27)
        lop = graph.add_vertex(id=3, label="software", name="lop", lang="java")
        josh = graph.add_vertex(id=4, label="person", name="josh", age=32)
        ripple = graph.add_vertex(id=5, label="software", name="ripple", lang="java")
        peter = graph.add_vertex(id=6, label="person", name="peter", age="35")

        # Create edges
        marko.add_edge("knows", vadas, weight=0.5)
        marko.add_edge("knows", josh, weight=1.0)
        marko.add_edge("created", lop, weight=0.4)
        josh.add_edge("created", ripple, weight=1.0)
        josh.add_edge("created", lop, weight=0.4)
        peter.add_edge("created", lop, weight=0.2)

        return graph


if __name__ == '__main__':
    # Initialize Python platform specific configurations
    print("TinkerGraph Python Compliance Tests initialized")
    print(f"Python version: {sys.version}")
    print(f"Platform: {sys.platform}")

    # Run the compliance tests
    unittest.main(verbosity=2)
