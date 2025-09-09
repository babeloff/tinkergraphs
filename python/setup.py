#!/usr/bin/env python3

from setuptools import setup, find_packages
import os
from pathlib import Path

# Read the README file for long description
def read_readme():
    readme_path = Path(__file__).parent / "README.md"
    if readme_path.exists():
        with open(readme_path, "r", encoding="utf-8") as f:
            return f.read()
    return "Python bindings for TinkerGraph Kotlin multiplatform implementation"

# Get version from package
def get_version():
    version_file = Path(__file__).parent / "tinkergraphs" / "__init__.py"
    if version_file.exists():
        with open(version_file, "r") as f:
            for line in f:
                if line.startswith("__version__"):
                    return line.split("=")[1].strip().strip('"').strip("'")
    return "1.0.0"

setup(
    name="tinkergraphs",
    version=get_version(),
    author="Apache TinkerPop",
    author_email="dev@tinkerpop.apache.org",
    description="Python bindings for TinkerGraph Kotlin multiplatform implementation",
    long_description=read_readme(),
    long_description_content_type="text/markdown",
    url="https://github.com/apache/tinkerpop",
    project_urls={
        "Bug Tracker": "https://github.com/apache/tinkerpop/issues",
        "Documentation": "https://tinkerpop.apache.org/docs/",
        "Source Code": "https://github.com/apache/tinkerpop",
    },
    packages=find_packages(),
    classifiers=[
        "Development Status :: 4 - Beta",
        "Intended Audience :: Developers",
        "License :: OSI Approved :: Apache Software License",
        "Operating System :: OS Independent",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.8",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
        "Programming Language :: Python :: 3.11",
        "Programming Language :: Python :: 3.12",
        "Topic :: Database",
        "Topic :: Software Development :: Libraries :: Python Modules",
    ],
    python_requires=">=3.8",
    install_requires=[
        # No external dependencies - uses only standard library ctypes
    ],
    extras_require={
        "dev": [
            "pytest>=6.0",
            "pytest-cov>=2.0",
            "black>=21.0",
            "isort>=5.0",
            "mypy>=0.800",
        ],
        "test": [
            "pytest>=6.0",
            "pytest-cov>=2.0",
        ],
    },
    package_data={
        "tinkergraphs": [
            # Include any data files if needed
        ],
    },
    include_package_data=True,
    zip_safe=False,
    keywords="graph database tinkerpop gremlin kotlin native",
    license="Apache License 2.0",
)
