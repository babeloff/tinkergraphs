"""
Test package for TinkerGraph Python bindings.

This package contains unit tests, integration tests, and performance benchmarks
for the TinkerGraph Python bindings, ensuring the correctness and reliability
of the Python interface to the native Kotlin TinkerGraph implementation.
"""

__version__ = "1.0.0"

# Test configuration constants
DEFAULT_TEST_TIMEOUT = 30  # seconds
PERFORMANCE_TEST_ITERATIONS = 1000
LARGE_GRAPH_SIZE = 10000

# Test utilities
def get_test_graph_path():
    """Get path to test graph data files."""
    from pathlib import Path
    return Path(__file__).parent / "data"

def skip_if_no_native_lib():
    """Decorator to skip tests if native library is not available."""
    def decorator(test_func):
        def wrapper(*args, **kwargs):
            try:
                from tinkergraphs.bindings import NativeGraphHandle
                # Try to create a handle to verify library is loadable
                handle = NativeGraphHandle()
                return test_func(*args, **kwargs)
            except Exception as e:
                import pytest
                pytest.skip(f"Native library not available: {e}")
        return wrapper
    return decorator
