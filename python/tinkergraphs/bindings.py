"""
Low-level ctypes bindings for TinkerGraph native library.

This module provides the ctypes interface to the native TinkerGraph shared library
compiled from Kotlin/Native. It handles library loading, function signature definitions,
and basic memory management for interfacing with the native code.
"""

import ctypes
import os
import sys
from pathlib import Path
from typing import Optional, List

from .exceptions import (
    TinkerGraphNativeError,
    TinkerGraphLibraryError,
    raise_library_error,
    raise_native_error
)


class TinkerGraphNativeLibrary:
    """Wrapper for the native TinkerGraph shared library."""

    def __init__(self):
        """Initialize the native library wrapper."""
        self._lib = None
        self._load_library()
        self._configure_functions()

    def _find_native_library(self) -> Path:
        """
        Find the TinkerGraph native shared library.

        Returns:
            Path to the shared library

        Raises:
            TinkerGraphLibraryError: If the library cannot be found
        """
        current_dir = Path(__file__).parent

        # Determine the shared library extension based on platform
        if sys.platform.startswith('linux'):
            lib_extension = 'so'
        elif sys.platform.startswith('darwin'):
            lib_extension = 'dylib'
        elif sys.platform.startswith('win'):
            lib_extension = 'dll'
        else:
            raise TinkerGraphLibraryError(f"Unsupported platform: {sys.platform}")

        lib_filename = f"libtinkergraphs.{lib_extension}"

        # Search locations for the native library
        candidates = [
            # Relative to the Python package (development/build)
            current_dir / f"../../build/bin/native/releaseShared/{lib_filename}",
            current_dir / f"../../build/bin/native/debugShared/{lib_filename}",
            # Relative to the Python package (installed)
            current_dir / lib_filename,
            # System-wide installation paths
            Path(f"/usr/local/lib/{lib_filename}"),
            Path(f"/usr/lib/{lib_filename}"),
            # Windows paths
            Path(f"C:/Program Files/TinkerGraphs/{lib_filename}"),
            # macOS paths
            Path(f"/usr/local/lib/{lib_filename}"),
            Path(f"/opt/homebrew/lib/{lib_filename}"),
        ]

        # Also check environment variable
        if 'TINKERGRAPH_NATIVE_LIB' in os.environ:
            env_path = Path(os.environ['TINKERGRAPH_NATIVE_LIB'])
            candidates.insert(0, env_path)

        for candidate in candidates:
            if candidate.exists():
                return candidate.resolve()

        raise_library_error(
            "native library",
            f"Could not find {lib_filename}. Tried: {[str(c) for c in candidates]}"
        )

    def _load_library(self):
        """Load the native shared library."""
        try:
            lib_path = self._find_native_library()
            self._lib = ctypes.CDLL(str(lib_path))
        except Exception as e:
            raise_library_error(str(lib_path) if 'lib_path' in locals() else "unknown", str(e))

    def _configure_functions(self):
        """Configure function signatures for all exported functions."""

        # Graph management functions
        self._lib.tinkergraph_create.restype = ctypes.c_void_p
        self._lib.tinkergraph_create.argtypes = []

        self._lib.tinkergraph_destroy.restype = None
        self._lib.tinkergraph_destroy.argtypes = [ctypes.c_void_p]

        # Vertex operations
        self._lib.tinkergraph_add_vertex.restype = ctypes.c_void_p
        self._lib.tinkergraph_add_vertex.argtypes = [ctypes.c_void_p, ctypes.c_char_p]

        self._lib.tinkergraph_add_vertex_with_properties.restype = ctypes.c_void_p
        self._lib.tinkergraph_add_vertex_with_properties.argtypes = [
            ctypes.c_void_p,  # graph
            ctypes.c_char_p,  # id
            ctypes.POINTER(ctypes.c_char_p),  # property keys
            ctypes.POINTER(ctypes.c_char_p),  # property values
            ctypes.c_int,  # property count
        ]

        # Edge operations
        self._lib.tinkergraph_add_edge.restype = ctypes.c_void_p
        self._lib.tinkergraph_add_edge.argtypes = [
            ctypes.c_void_p,  # graph
            ctypes.c_char_p,  # label
            ctypes.c_void_p,  # out vertex
            ctypes.c_void_p,  # in vertex
        ]

        self._lib.tinkergraph_add_edge_with_properties.restype = ctypes.c_void_p
        self._lib.tinkergraph_add_edge_with_properties.argtypes = [
            ctypes.c_void_p,  # graph
            ctypes.c_char_p,  # label
            ctypes.c_void_p,  # out vertex
            ctypes.c_void_p,  # in vertex
            ctypes.POINTER(ctypes.c_char_p),  # property keys
            ctypes.POINTER(ctypes.c_char_p),  # property values
            ctypes.c_int,  # property count
        ]

        # Query functions
        self._lib.tinkergraph_vertex_count.restype = ctypes.c_long
        self._lib.tinkergraph_vertex_count.argtypes = [ctypes.c_void_p]

        self._lib.tinkergraph_edge_count.restype = ctypes.c_long
        self._lib.tinkergraph_edge_count.argtypes = [ctypes.c_void_p]

        # Element property access functions
        self._lib.tinkergraph_vertex_id.restype = ctypes.c_int
        self._lib.tinkergraph_vertex_id.argtypes = [
            ctypes.c_void_p,  # vertex
            ctypes.c_char_p,  # buffer
            ctypes.c_int,     # buffer size
        ]

        self._lib.tinkergraph_edge_label.restype = ctypes.c_int
        self._lib.tinkergraph_edge_label.argtypes = [
            ctypes.c_void_p,  # edge
            ctypes.c_char_p,  # buffer
            ctypes.c_int,     # buffer size
        ]

        # Element cleanup functions
        self._lib.tinkergraph_destroy_vertex.restype = None
        self._lib.tinkergraph_destroy_vertex.argtypes = [ctypes.c_void_p]

        self._lib.tinkergraph_destroy_edge.restype = None
        self._lib.tinkergraph_destroy_edge.argtypes = [ctypes.c_void_p]

        # Error handling function
        self._lib.tinkergraph_get_error_message.restype = ctypes.c_int
        self._lib.tinkergraph_get_error_message.argtypes = [
            ctypes.c_char_p,  # buffer
            ctypes.c_int,     # buffer size
        ]


class NativeGraphHandle:
    """Handle to a native TinkerGraph instance."""

    _lib_instance = None

    @classmethod
    def _get_library(cls) -> TinkerGraphNativeLibrary:
        """Get the shared library instance (singleton)."""
        if cls._lib_instance is None:
            cls._lib_instance = TinkerGraphNativeLibrary()
        return cls._lib_instance

    def __init__(self):
        """Create a new TinkerGraph instance."""
        self._lib = self._get_library()
        self._ptr = self._lib._lib.tinkergraph_create()
        if not self._ptr:
            raise_native_error("create_graph", "Native graph creation returned null")

    def __del__(self):
        """Clean up the native graph instance."""
        if hasattr(self, '_ptr') and self._ptr and hasattr(self, '_lib'):
            try:
                self._lib._lib.tinkergraph_destroy(self._ptr)
            except:
                # Ignore errors during cleanup
                pass

    def add_vertex(self, vertex_id: Optional[str] = None) -> int:
        """
        Add a vertex to the graph.

        Args:
            vertex_id: Optional vertex ID

        Returns:
            Native pointer to the created vertex

        Raises:
            TinkerGraphNativeError: If vertex creation fails
        """
        id_bytes = vertex_id.encode('utf-8') if vertex_id else None
        vertex_ptr = self._lib._lib.tinkergraph_add_vertex(self._ptr, id_bytes)
        if not vertex_ptr:
            raise_native_error("add_vertex", f"Failed to add vertex with id '{vertex_id}'")
        return vertex_ptr

    def add_vertex_with_properties(self, vertex_id: Optional[str], properties: dict) -> int:
        """
        Add a vertex with properties to the graph.

        Args:
            vertex_id: Optional vertex ID
            properties: Dictionary of property key-value pairs

        Returns:
            Native pointer to the created vertex

        Raises:
            TinkerGraphNativeError: If vertex creation fails
        """
        id_bytes = vertex_id.encode('utf-8') if vertex_id else None

        # Convert properties to C arrays
        if properties:
            keys = [k.encode('utf-8') for k in properties.keys()]
            values = [str(v).encode('utf-8') for v in properties.values()]

            key_array = (ctypes.c_char_p * len(keys))(*keys)
            value_array = (ctypes.c_char_p * len(values))(*values)
            count = len(properties)
        else:
            key_array = None
            value_array = None
            count = 0

        vertex_ptr = self._lib._lib.tinkergraph_add_vertex_with_properties(
            self._ptr, id_bytes, key_array, value_array, count
        )

        if not vertex_ptr:
            raise_native_error(
                "add_vertex_with_properties",
                f"Failed to add vertex with id '{vertex_id}' and properties {properties}"
            )
        return vertex_ptr

    def add_edge(self, label: str, out_vertex_ptr: int, in_vertex_ptr: int) -> int:
        """
        Add an edge to the graph.

        Args:
            label: Edge label
            out_vertex_ptr: Native pointer to source vertex
            in_vertex_ptr: Native pointer to target vertex

        Returns:
            Native pointer to the created edge

        Raises:
            TinkerGraphNativeError: If edge creation fails
        """
        label_bytes = label.encode('utf-8')
        edge_ptr = self._lib._lib.tinkergraph_add_edge(
            self._ptr, label_bytes, out_vertex_ptr, in_vertex_ptr
        )
        if not edge_ptr:
            raise_native_error("add_edge", f"Failed to add edge with label '{label}'")
        return edge_ptr

    def add_edge_with_properties(self, label: str, out_vertex_ptr: int,
                                in_vertex_ptr: int, properties: dict) -> int:
        """
        Add an edge with properties to the graph.

        Args:
            label: Edge label
            out_vertex_ptr: Native pointer to source vertex
            in_vertex_ptr: Native pointer to target vertex
            properties: Dictionary of property key-value pairs

        Returns:
            Native pointer to the created edge

        Raises:
            TinkerGraphNativeError: If edge creation fails
        """
        label_bytes = label.encode('utf-8')

        # Convert properties to C arrays
        if properties:
            keys = [k.encode('utf-8') for k in properties.keys()]
            values = [str(v).encode('utf-8') for v in properties.values()]

            key_array = (ctypes.c_char_p * len(keys))(*keys)
            value_array = (ctypes.c_char_p * len(values))(*values)
            count = len(properties)
        else:
            key_array = None
            value_array = None
            count = 0

        edge_ptr = self._lib._lib.tinkergraph_add_edge_with_properties(
            self._ptr, label_bytes, out_vertex_ptr, in_vertex_ptr,
            key_array, value_array, count
        )

        if not edge_ptr:
            raise_native_error(
                "add_edge_with_properties",
                f"Failed to add edge with label '{label}' and properties {properties}"
            )
        return edge_ptr

    def vertex_count(self) -> int:
        """Get the number of vertices in the graph."""
        return self._lib._lib.tinkergraph_vertex_count(self._ptr)

    def edge_count(self) -> int:
        """Get the number of edges in the graph."""
        return self._lib._lib.tinkergraph_edge_count(self._ptr)

    def get_vertex_id(self, vertex_ptr: int, buffer_size: int = 256) -> str:
        """
        Get the ID of a vertex.

        Args:
            vertex_ptr: Native pointer to the vertex
            buffer_size: Size of the buffer for the ID string

        Returns:
            The vertex ID as a string
        """
        buffer = ctypes.create_string_buffer(buffer_size)
        result_size = self._lib._lib.tinkergraph_vertex_id(vertex_ptr, buffer, buffer_size)

        if result_size < 0:
            raise_native_error("get_vertex_id", "Failed to retrieve vertex ID")

        return buffer.value.decode('utf-8') if buffer.value else ""

    def get_edge_label(self, edge_ptr: int, buffer_size: int = 256) -> str:
        """
        Get the label of an edge.

        Args:
            edge_ptr: Native pointer to the edge
            buffer_size: Size of the buffer for the label string

        Returns:
            The edge label as a string
        """
        buffer = ctypes.create_string_buffer(buffer_size)
        result_size = self._lib._lib.tinkergraph_edge_label(edge_ptr, buffer, buffer_size)

        if result_size < 0:
            raise_native_error("get_edge_label", "Failed to retrieve edge label")

        return buffer.value.decode('utf-8') if buffer.value else ""

    def destroy_vertex(self, vertex_ptr: int):
        """Destroy a vertex handle (cleanup memory)."""
        if vertex_ptr:
            self._lib._lib.tinkergraph_destroy_vertex(vertex_ptr)

    def destroy_edge(self, edge_ptr: int):
        """Destroy an edge handle (cleanup memory)."""
        if edge_ptr:
            self._lib._lib.tinkergraph_destroy_edge(edge_ptr)

    def get_last_error(self, buffer_size: int = 512) -> str:
        """
        Get the last error message from the native library.

        Args:
            buffer_size: Size of the buffer for the error message

        Returns:
            The last error message as a string
        """
        buffer = ctypes.create_string_buffer(buffer_size)
        result_size = self._lib._lib.tinkergraph_get_error_message(buffer, buffer_size)

        if result_size < 0:
            return "Failed to retrieve error message"

        return buffer.value.decode('utf-8') if buffer.value else "No error message available"
