"""
Custom exceptions for TinkerGraph Python bindings.

This module defines exception classes used throughout the TinkerGraph Python
bindings to provide meaningful error messages and proper error handling.
"""


class TinkerGraphError(Exception):
    """
    Base exception class for all TinkerGraph-related errors.

    This is the base class for all exceptions raised by the TinkerGraph
    Python bindings. It provides a common interface for error handling
    and allows users to catch all TinkerGraph-related exceptions with
    a single except clause.
    """

    def __init__(self, message: str, cause: Exception = None):
        """
        Initialize a TinkerGraphError.

        Args:
            message: A descriptive error message
            cause: The underlying exception that caused this error (optional)
        """
        super().__init__(message)
        self.message = message
        self.cause = cause

    def __str__(self) -> str:
        if self.cause:
            return f"{self.message} (caused by: {self.cause})"
        return self.message


class TinkerGraphNativeError(TinkerGraphError):
    """
    Exception raised when the native TinkerGraph library encounters an error.

    This exception is raised when operations on the native Kotlin/Native
    TinkerGraph library fail, such as when creating a graph, adding vertices
    or edges, or performing queries.
    """
    pass


class TinkerGraphLibraryError(TinkerGraphError):
    """
    Exception raised when the native library cannot be loaded or found.

    This exception is raised during initialization when the Python bindings
    cannot locate or load the native TinkerGraph shared library.
    """
    pass


class TinkerGraphVertexError(TinkerGraphError):
    """
    Exception raised for vertex-related operations.

    This exception is raised when vertex operations fail, such as when
    trying to access a vertex that doesn't exist or when vertex creation
    fails due to invalid parameters.
    """
    pass


class TinkerGraphEdgeError(TinkerGraphError):
    """
    Exception raised for edge-related operations.

    This exception is raised when edge operations fail, such as when
    trying to create an edge between non-existent vertices or when
    edge creation fails due to invalid parameters.
    """
    pass


class TinkerGraphMemoryError(TinkerGraphError):
    """
    Exception raised for memory management issues.

    This exception is raised when there are problems with memory management
    between Python and the native library, such as when trying to access
    a disposed object or when memory allocation fails.
    """
    pass


class TinkerGraphValidationError(TinkerGraphError):
    """
    Exception raised for validation errors.

    This exception is raised when input validation fails, such as when
    invalid property types are provided or when required parameters
    are missing.
    """
    pass


class TinkerGraphConfigurationError(TinkerGraphError):
    """
    Exception raised for configuration-related errors.

    This exception is raised when there are problems with graph configuration,
    such as invalid configuration parameters or conflicting settings.
    """
    pass


# Convenience functions for raising common exceptions

def raise_native_error(operation: str, details: str = None) -> None:
    """Raise a TinkerGraphNativeError with a standardized message."""
    message = f"Native operation '{operation}' failed"
    if details:
        message += f": {details}"
    raise TinkerGraphNativeError(message)


def raise_library_error(library_path: str, details: str = None) -> None:
    """Raise a TinkerGraphLibraryError for library loading issues."""
    message = f"Failed to load native library at '{library_path}'"
    if details:
        message += f": {details}"
    raise TinkerGraphLibraryError(message)


def raise_vertex_error(operation: str, vertex_id=None, details: str = None) -> None:
    """Raise a TinkerGraphVertexError with vertex context."""
    message = f"Vertex operation '{operation}' failed"
    if vertex_id is not None:
        message += f" for vertex '{vertex_id}'"
    if details:
        message += f": {details}"
    raise TinkerGraphVertexError(message)


def raise_edge_error(operation: str, edge_details: str = None, details: str = None) -> None:
    """Raise a TinkerGraphEdgeError with edge context."""
    message = f"Edge operation '{operation}' failed"
    if edge_details:
        message += f" for edge {edge_details}"
    if details:
        message += f": {details}"
    raise TinkerGraphEdgeError(message)


def raise_validation_error(parameter: str, expected_type: str, actual_value=None) -> None:
    """Raise a TinkerGraphValidationError for parameter validation."""
    message = f"Invalid parameter '{parameter}': expected {expected_type}"
    if actual_value is not None:
        message += f", got {type(actual_value).__name__} '{actual_value}'"
    raise TinkerGraphValidationError(message)
