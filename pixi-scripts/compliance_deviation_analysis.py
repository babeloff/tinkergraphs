#!/usr/bin/env python3
"""
TinkerPop Compliance Test Deviation Analysis

This script compares upstream Apache TinkerPop compliance tests with local
TinkerGraphs compliance test implementations and generates a comprehensive
deviation report.

Usage:
    python compliance_deviation_analysis.py [--output report.adoc] [--format adoc|json|html]

Author: TinkerGraphs Compliance Framework
License: Apache 2.0
"""

import os
import re
import json
import argparse
from pathlib import Path
from typing import Dict, List, Set, Optional, Tuple
from dataclasses import dataclass, field
from datetime import datetime
import subprocess
import urllib.request
import tempfile
import zipfile


@dataclass
class TestMethod:
    """Represents a test method with its metadata."""
    name: str
    class_name: str
    file_path: str
    line_number: int
    annotations: List[str] = field(default_factory=list)
    parameters: List[str] = field(default_factory=list)
    description: Optional[str] = None
    category: Optional[str] = None


@dataclass
class TestClass:
    """Represents a test class with its methods."""
    name: str
    file_path: str
    package: str
    methods: List[TestMethod] = field(default_factory=list)
    annotations: List[str] = field(default_factory=list)
    extends: Optional[str] = None
    imports: List[str] = field(default_factory=list)


@dataclass
class DeviationReport:
    """Contains the complete deviation analysis."""
    timestamp: str
    upstream_tests: Dict[str, TestClass]
    local_tests: Dict[str, TestClass]
    missing_tests: List[str]
    extra_tests: List[str]
    modified_tests: List[Tuple[str, str, str]]  # test_name, upstream_sig, local_sig
    coverage_analysis: Dict[str, float]
    recommendations: List[str]
    test_categories: Dict[str, Dict] = None


class ComplianceTestAnalyzer:
    """Analyzes and compares TinkerPop compliance tests."""

    def __init__(self, project_root: Path):
        self.project_root = project_root
        self.upstream_cache_dir = project_root / "build" / "upstream_tests"
        self.reports_dir = project_root / "build" / "reports" / "compliance"

        # Patterns for parsing different test file types
        self.java_method_pattern = re.compile(
            r'@Test.*?\n.*?(?:public|private|protected)?\s+(?:static\s+)?void\s+(\w+)\s*\([^)]*\)',
            re.MULTILINE | re.DOTALL
        )
        self.kotlin_method_pattern = re.compile(
            r'@Test.*?\n.*?fun\s+(\w+)\s*\([^)]*\)',
            re.MULTILINE | re.DOTALL
        )
        self.python_method_pattern = re.compile(
            r'def\s+(test_\w+)\s*\([^)]*\):',
            re.MULTILINE
        )

    def download_upstream_tests(self) -> bool:
        """Download upstream Apache TinkerPop test sources."""
        print("📥 Downloading upstream Apache TinkerPop tests...")

        # TinkerPop repository URL
        upstream_url = "https://github.com/apache/tinkerpop/archive/refs/heads/master.zip"

        try:
            self.upstream_cache_dir.mkdir(parents=True, exist_ok=True)

            # Download the archive
            zip_path = self.upstream_cache_dir / "tinkerpop-master.zip"
            print(f"   Downloading from {upstream_url}...")
            urllib.request.urlretrieve(upstream_url, zip_path)

            # Extract test files
            extract_dir = self.upstream_cache_dir / "extracted"
            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                # Only extract test-related files
                for member in zip_ref.namelist():
                    if any(test_path in member for test_path in [
                        '/test/', '/tests/', 'Test.java', 'Test.kt', 'test_', 'compliance'
                    ]):
                        zip_ref.extract(member, extract_dir)

            print("✅ Upstream tests downloaded successfully")
            return True

        except Exception as e:
            print(f"❌ Failed to download upstream tests: {e}")
            return False

    def parse_java_tests(self, file_path: Path) -> TestClass:
        """Parse Java test files and extract test information."""
        content = file_path.read_text(encoding='utf-8', errors='ignore')

        # Extract package
        package_match = re.search(r'package\s+([\w.]+);', content)
        package = package_match.group(1) if package_match else ""

        # Extract class name and metadata
        class_match = re.search(r'public\s+class\s+(\w+)(?:\s+extends\s+(\w+))?', content)
        if not class_match:
            return TestClass(file_path.stem, str(file_path), package)

        class_name = class_match.group(1)
        extends = class_match.group(2)

        # Extract imports
        imports = re.findall(r'import\s+([\w.]+(?:\.\*)?);', content)

        # Extract class annotations
        class_annotations = re.findall(r'@(\w+)(?:\([^)]*\))?\s*\n.*?public\s+class', content)

        test_class = TestClass(
            name=class_name,
            file_path=str(file_path),
            package=package,
            extends=extends,
            imports=imports,
            annotations=class_annotations
        )

        # Extract test methods
        for match in self.java_method_pattern.finditer(content):
            method_name = match.group(1)
            line_number = content[:match.start()].count('\n') + 1

            # Extract method annotations
            method_start = match.start()
            method_text = content[max(0, method_start-200):match.end()]
            annotations = re.findall(r'@(\w+)(?:\([^)]*\))?', method_text)

            # Extract method description from comments
            desc_match = re.search(r'/\*\*(.*?)\*/', method_text, re.DOTALL)
            description = desc_match.group(1).strip() if desc_match else None

            test_method = TestMethod(
                name=method_name,
                class_name=class_name,
                file_path=str(file_path),
                line_number=line_number,
                annotations=annotations,
                description=description
            )
            test_class.methods.append(test_method)

        return test_class

    def parse_kotlin_tests(self, file_path: Path) -> TestClass:
        """Parse Kotlin test files and extract test information."""
        content = file_path.read_text(encoding='utf-8', errors='ignore')

        # Extract package
        package_match = re.search(r'package\s+([\w.]+)', content)
        package = package_match.group(1) if package_match else ""

        # Extract class name
        class_match = re.search(r'class\s+(\w+)(?:\s*:\s*(\w+))?', content)
        class_name = class_match.group(1) if class_match else file_path.stem
        extends = class_match.group(2) if class_match and class_match.group(2) else None

        # Extract imports
        imports = re.findall(r'import\s+([\w.]+)', content)

        test_class = TestClass(
            name=class_name,
            file_path=str(file_path),
            package=package,
            extends=extends,
            imports=imports
        )

        # Extract test methods
        for match in self.kotlin_method_pattern.finditer(content):
            method_name = match.group(1)
            line_number = content[:match.start()].count('\n') + 1

            # Extract method annotations
            method_start = match.start()
            method_text = content[max(0, method_start-200):match.end()]
            annotations = re.findall(r'@(\w+)(?:\([^)]*\))?', method_text)

            # Extract method description from comments
            desc_match = re.search(r'/\*\*(.*?)\*/', method_text, re.DOTALL)
            description = desc_match.group(1).strip() if desc_match else None

            test_method = TestMethod(
                name=method_name,
                class_name=class_name,
                file_path=str(file_path),
                line_number=line_number,
                annotations=annotations,
                description=description
            )
            test_class.methods.append(test_method)

        return test_class

    def parse_python_tests(self, file_path: Path) -> TestClass:
        """Parse Python test files and extract test information."""
        content = file_path.read_text(encoding='utf-8', errors='ignore')

        # Extract class name
        class_match = re.search(r'class\s+(\w+)(?:\([^)]*\))?:', content)
        class_name = class_match.group(1) if class_match else file_path.stem

        # Extract imports
        imports = re.findall(r'(?:from\s+[\w.]+\s+)?import\s+([\w.,\s*]+)', content)
        flat_imports = []
        for imp in imports:
            flat_imports.extend([i.strip() for i in imp.split(',')])

        test_class = TestClass(
            name=class_name,
            file_path=str(file_path),
            package="",  # Python doesn't have explicit packages like Java
            imports=flat_imports
        )

        # Extract test methods
        for match in self.python_method_pattern.finditer(content):
            method_name = match.group(1)
            line_number = content[:match.start()].count('\n') + 1

            # Extract method docstring as description
            method_end = content.find('\n    def ', match.end())
            if method_end == -1:
                method_end = len(content)
            method_text = content[match.start():method_end]

            desc_match = re.search(r'"""(.*?)"""', method_text, re.DOTALL)
            description = desc_match.group(1).strip() if desc_match else None

            test_method = TestMethod(
                name=method_name,
                class_name=class_name,
                file_path=str(file_path),
                line_number=line_number,
                description=description
            )
            test_class.methods.append(test_method)

        return test_class

    def analyze_upstream_tests(self) -> Dict[str, TestClass]:
        """Analyze upstream Apache TinkerPop tests."""
        print("🔍 Analyzing upstream Apache TinkerPop tests...")

        upstream_tests = {}
        extract_dir = self.upstream_cache_dir / "extracted"

        if not extract_dir.exists():
            print("❌ Upstream tests not found. Run download first.")
            return upstream_tests

        # Find all test files in the extracted directory
        test_patterns = [
            "**/tinkergraph-gremlin/src/test/**/*.java",
            "**/gremlin-test/src/main/**/*Test*.java",
            "**/gremlin-core/src/test/**/*.java",
            "**/*Structure*Test*.java",
            "**/*Process*Test*.java",
            "**/*Compliance*.java"
        ]

        for pattern in test_patterns:
            for test_file in extract_dir.rglob(pattern):
                if test_file.is_file():
                    try:
                        test_class = self.parse_java_tests(test_file)
                        if test_class.methods:  # Only include classes with test methods
                            key = f"{test_class.package}.{test_class.name}"
                            upstream_tests[key] = test_class
                            print(f"   Found upstream test class: {key} ({len(test_class.methods)} methods)")
                    except Exception as e:
                        print(f"   ⚠️  Error parsing {test_file}: {e}")

        print(f"✅ Analyzed {len(upstream_tests)} upstream test classes")
        return upstream_tests

    def analyze_local_tests(self) -> Dict[str, TestClass]:
        """Analyze local TinkerGraphs compliance tests."""
        print("🔍 Analyzing local TinkerGraphs compliance tests...")

        local_tests = {}

        # Analyze Java tests
        java_test_dirs = [
            self.project_root / "src" / "jvmTest" / "java",
            self.project_root / "src" / "jvmTest" / "reference",
            self.project_root / "src" / "test" / "java"
        ]

        for test_dir in java_test_dirs:
            if test_dir.exists():
                for test_file in test_dir.rglob("*.java"):
                    if "compliance" in test_file.name.lower() or "test" in test_file.name.lower():
                        try:
                            test_class = self.parse_java_tests(test_file)
                            if test_class.methods:
                                key = f"{test_class.package}.{test_class.name}"
                                local_tests[key] = test_class
                                print(f"   Found local Java test: {key} ({len(test_class.methods)} methods)")
                        except Exception as e:
                            print(f"   ⚠️  Error parsing {test_file}: {e}")

        # Analyze Kotlin tests
        kotlin_test_dirs = [
            self.project_root / "src" / "jvmTest" / "kotlin",
            self.project_root / "src" / "jsTest" / "kotlin",
            self.project_root / "src" / "nativeTest" / "kotlin",
            self.project_root / "src" / "commonTest" / "kotlin"
        ]

        for test_dir in kotlin_test_dirs:
            if test_dir.exists():
                for test_file in test_dir.rglob("*.kt"):
                    if "compliance" in test_file.name.lower() or "test" in test_file.name.lower():
                        try:
                            test_class = self.parse_kotlin_tests(test_file)
                            if test_class.methods:
                                key = f"{test_class.package}.{test_class.name}"
                                local_tests[key] = test_class
                                print(f"   Found local Kotlin test: {key} ({len(test_class.methods)} methods)")
                        except Exception as e:
                            print(f"   ⚠️  Error parsing {test_file}: {e}")

        # Analyze Python tests
        python_test_dir = self.project_root / "python" / "tests"
        if python_test_dir.exists():
            for test_file in python_test_dir.rglob("*.py"):
                if "compliance" in test_file.name.lower() or test_file.name.startswith("test_"):
                    try:
                        test_class = self.parse_python_tests(test_file)
                        if test_class.methods:
                            key = f"python.{test_class.name}"
                            local_tests[key] = test_class
                            print(f"   Found local Python test: {key} ({len(test_class.methods)} methods)")
                    except Exception as e:
                        print(f"   ⚠️  Error parsing {test_file}: {e}")

        print(f"✅ Analyzed {len(local_tests)} local test classes")
        return local_tests

    def categorize_tests(self, test_methods: List[str]) -> Dict[str, Dict]:
        """Categorize tests by TinkerPop API area and priority."""
        categories = {
            "Structure API": {
                "pattern": r"org\.apache\.tinkerpop\.gremlin\.structure\.",
                "priority": "CRITICAL",
                "description": "Core Graph Structure API compliance tests",
                "tests": [],
                "missing_count": 0
            },
            "Process API": {
                "pattern": r"org\.apache\.tinkerpop\.gremlin\.process\.",
                "priority": "CRITICAL",
                "description": "Graph traversal and processing API tests",
                "tests": [],
                "missing_count": 0
            },
            "TinkerGraph Specific": {
                "pattern": r"org\.apache\.tinkerpop\.gremlin\.tinkergraph\.",
                "priority": "HIGH",
                "description": "TinkerGraph implementation specific tests",
                "tests": [],
                "missing_count": 0
            },
            "Algorithm": {
                "pattern": r"org\.apache\.tinkerpop\.gremlin\.algorithm\.",
                "priority": "MEDIUM",
                "description": "Graph algorithm implementation tests",
                "tests": [],
                "missing_count": 0
            },
            "Scripting (JSR223)": {
                "pattern": r"org\.apache\.tinkerpop\.gremlin\.jsr223\.",
                "priority": "MEDIUM",
                "description": "Scripting engine and language binding tests",
                "tests": [],
                "missing_count": 0
            },
            "Utilities": {
                "pattern": r"org\.apache\.tinkerpop\.gremlin\.util\.",
                "priority": "LOW",
                "description": "Utility classes and helper function tests",
                "tests": [],
                "missing_count": 0
            }
        }

        # Categorize each test method
        for test_method in test_methods:
            categorized = False
            for category_name, category_info in categories.items():
                if re.match(category_info["pattern"], test_method):
                    category_info["tests"].append(test_method)
                    category_info["missing_count"] += 1
                    categorized = True
                    break

            if not categorized:
                # Handle uncategorized tests
                if "Other" not in categories:
                    categories["Other"] = {
                        "pattern": ".*",
                        "priority": "LOW",
                        "description": "Uncategorized tests",
                        "tests": [],
                        "missing_count": 0
                    }
                categories["Other"]["tests"].append(test_method)
                categories["Other"]["missing_count"] += 1

        return categories

    def compare_tests(self, upstream_tests: Dict[str, TestClass],
                     local_tests: Dict[str, TestClass]) -> DeviationReport:
        """Compare upstream and local tests to identify deviations."""
        print("📊 Comparing upstream vs local tests...")

        # Collect all test methods for comparison
        upstream_methods = {}
        local_methods = {}

        for class_key, test_class in upstream_tests.items():
            for method in test_class.methods:
                method_key = f"{class_key}.{method.name}"
                upstream_methods[method_key] = method

        for class_key, test_class in local_tests.items():
            for method in test_class.methods:
                method_key = f"{class_key}.{method.name}"
                local_methods[method_key] = method

        # Identify deviations
        upstream_method_names = set(upstream_methods.keys())
        local_method_names = set(local_methods.keys())

        missing_tests = list(upstream_method_names - local_method_names)
        extra_tests = list(local_method_names - upstream_method_names)

        # Find similar but modified tests
        modified_tests = []
        common_tests = upstream_method_names & local_method_names

        for test_name in common_tests:
            upstream_method = upstream_methods[test_name]
            local_method = local_methods[test_name]

            # Compare method signatures and descriptions
            upstream_sig = f"{upstream_method.name}({','.join(upstream_method.parameters)})"
            local_sig = f"{local_method.name}({','.join(local_method.parameters)})"

            if upstream_sig != local_sig or upstream_method.description != local_method.description:
                modified_tests.append((test_name, upstream_sig, local_sig))

        # Calculate coverage analysis
        coverage_analysis = {
            "total_upstream_tests": len(upstream_methods),
            "total_local_tests": len(local_methods),
            "matching_tests": len(common_tests),
            "coverage_percentage": (len(common_tests) / len(upstream_methods) * 100) if upstream_methods else 0,
            "missing_percentage": (len(missing_tests) / len(upstream_methods) * 100) if upstream_methods else 0,
            "extra_percentage": (len(extra_tests) / len(upstream_methods) * 100) if upstream_methods else 0
        }

        # Categorize missing tests
        test_categories = self.categorize_tests(missing_tests)

        # Generate recommendations
        recommendations = self.generate_recommendations(
            missing_tests, extra_tests, modified_tests, coverage_analysis, test_categories
        )

        return DeviationReport(
            timestamp=datetime.now().isoformat(),
            upstream_tests=upstream_tests,
            local_tests=local_tests,
            missing_tests=missing_tests,
            extra_tests=extra_tests,
            modified_tests=modified_tests,
            coverage_analysis=coverage_analysis,
            recommendations=recommendations,
            test_categories=test_categories
        )

    def generate_recommendations(self, missing_tests: List[str], extra_tests: List[str],
                                 modified_tests: List[Tuple[str, str, str]],
                                 coverage_analysis: Dict[str, float],
                                 test_categories: Dict[str, Dict] = None) -> List[str]:
        """Generate actionable recommendations based on deviation analysis."""
        recommendations = []

        # Coverage-based recommendations
        coverage = coverage_analysis["coverage_percentage"]
        if coverage < 10:
            recommendations.append(
                f"🚨 CRITICAL: Test coverage is only {coverage:.1f}%. "
                "TinkerPop compliance is severely lacking - immediate action required."
            )
        elif coverage < 50:
            recommendations.append(
                f"⚠️  WARNING: Test coverage is {coverage:.1f}%. "
                "Major compliance gaps exist - prioritize Structure and Process API tests."
            )
        elif coverage < 80:
            recommendations.append(
                f"📈 PROGRESS: Test coverage is {coverage:.1f}%. "
                "Good progress - continue implementing missing compliance tests."
            )
        else:
            recommendations.append(
                f"✅ EXCELLENT: Test coverage is {coverage:.1f}%. "
                "Strong compliance level - focus on remaining edge cases."
            )

        # Category-based recommendations
        if test_categories:
            critical_categories = [cat for cat, info in test_categories.items()
                                 if info["priority"] == "CRITICAL" and info["missing_count"] > 0]

            if critical_categories:
                recommendations.append(
                    f"🚨 CRITICAL MISSING: {', '.join(critical_categories)} tests missing. "
                    "These are foundational TinkerPop APIs - implement immediately."
                )

            # Prioritized implementation recommendations
            for category_name, category_info in sorted(test_categories.items(),
                                                     key=lambda x: {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}.get(x[1]["priority"], 4)):
                if category_info["missing_count"] > 0:
                    priority_emoji = {"CRITICAL": "🚨", "HIGH": "⚠️", "MEDIUM": "📝", "LOW": "ℹ️"}.get(category_info["priority"], "📋")
                    recommendations.append(
                        f"{priority_emoji} {category_info['priority']}: {category_name} - "
                        f"{category_info['missing_count']} missing tests. "
                        f"{category_info['description']}"
                    )

        # Missing tests recommendations
        if len(missing_tests) > 2000:
            recommendations.append(
                f"📋 MASSIVE GAP: {len(missing_tests)} missing tests detected. "
                "This represents a major compliance implementation project requiring 8-12 weeks."
            )
        elif len(missing_tests) > 500:
            recommendations.append(
                f"📋 LARGE GAP: {len(missing_tests)} missing tests detected. "
                "Significant implementation effort required - plan multi-phase approach."
            )
        elif len(missing_tests) > 50:
            recommendations.append(
                f"📝 MEDIUM GAP: {len(missing_tests)} missing tests detected. "
                "Plan implementation in next development cycle."
            )
        elif missing_tests:
            recommendations.append(
                f"✏️  SMALL GAP: {len(missing_tests)} missing tests detected. "
                "Consider implementing for complete coverage."
            )

        # Extra tests recommendations
        if extra_tests:
            recommendations.append(
                f"🎯 LOCAL EXTENSIONS: {len(extra_tests)} additional tests found beyond upstream. "
                "Review if these provide value or should be aligned with upstream."
            )

        # Modified tests recommendations
        if modified_tests:
            recommendations.append(
                f"🔄 REVIEW: {len(modified_tests)} tests have different signatures. "
                "Verify these modifications maintain TinkerPop compliance."
            )

        # Specific recommendations based on patterns
        structure_missing = [t for t in missing_tests if "structure" in t.lower()]
        process_missing = [t for t in missing_tests if "process" in t.lower()]

        if structure_missing:
            recommendations.append(
                f"🏗️  STRUCTURE API: {len(structure_missing)} missing Structure API tests. "
                "These are critical for graph database compliance."
            )

        if process_missing:
            recommendations.append(
                f"⚙️  PROCESS API: {len(process_missing)} missing Process API tests. "
                "These are important for traversal compliance."
            )

        return recommendations

    def generate_asciidoc_report(self, report: DeviationReport, output_path: Path):
        """Generate AsciiDoc format deviation report."""
        content = f"""= TinkerPop Compliance Test Deviation Analysis Report
:toc:
:toclevels: 3
:sectanchors:
:sectlinks:

== Executive Summary

**Analysis Date:** {report.timestamp} +
**Report Status:** {'🚨 CRITICAL DEVIATIONS' if report.coverage_analysis['coverage_percentage'] < 70 else '⚠️ MINOR DEVIATIONS' if report.coverage_analysis['coverage_percentage'] < 90 else '✅ GOOD ALIGNMENT'} +
**Coverage Level:** {report.coverage_analysis['coverage_percentage']:.1f}%

This report analyzes the deviation between upstream Apache TinkerPop compliance tests and local TinkerGraphs compliance test implementations.

== Coverage Analysis

[cols="2,1,3"]
|===
| Metric | Value | Status

| Total Upstream Tests
| {report.coverage_analysis['total_upstream_tests']}
| Baseline

| Total Local Tests
| {report.coverage_analysis['total_local_tests']}
| {'✅' if report.coverage_analysis['total_local_tests'] >= report.coverage_analysis['total_upstream_tests'] else '⚠️'}

| Matching Tests
| {report.coverage_analysis['matching_tests']}
| Implementation

| Coverage Percentage
| {report.coverage_analysis['coverage_percentage']:.1f}%
| {'✅ Good' if report.coverage_analysis['coverage_percentage'] >= 90 else '⚠️ Needs Improvement' if report.coverage_analysis['coverage_percentage'] >= 70 else '🚨 Critical'}

| Missing Tests
| {len(report.missing_tests)}
| {'✅ None' if not report.missing_tests else '⚠️ Review Required'}

| Extra Tests
| {len(report.extra_tests)}
| {'ℹ️ Additional Coverage' if report.extra_tests else '✅ Aligned'}

| Modified Tests
| {len(report.modified_tests)}
| {'ℹ️ Review Signatures' if report.modified_tests else '✅ Aligned'}
|===

== Missing Tests Analysis

{f'⚠️ **{len(report.missing_tests)} tests are missing from local implementation:**' if report.missing_tests else '✅ **No missing tests detected.**'}

"""

        if report.missing_tests:
            content += "=== Missing Tests by Category\n\n"

            # Use categorized analysis if available
            if hasattr(report, 'test_categories') and report.test_categories:
                priority_order = ["CRITICAL", "HIGH", "MEDIUM", "LOW"]
                for priority in priority_order:
                    priority_categories = {name: info for name, info in report.test_categories.items()
                                         if info["priority"] == priority and info["missing_count"] > 0}

                    if priority_categories:
                        priority_emoji = {"CRITICAL": "🚨", "HIGH": "⚠️", "MEDIUM": "📝", "LOW": "ℹ️"}
                        content += f"**{priority_emoji.get(priority, '📋')} {priority} Priority Tests:**\n\n"

                        for category_name, category_info in priority_categories.items():
                            content += f"* **{category_name}** ({category_info['missing_count']} tests)\n"
                            content += f"  - {category_info['description']}\n"

                            # Show sample tests
                            sample_tests = category_info['tests'][:5]
                            for test in sample_tests:
                                content += f"  - `{test}`\n"
                            if len(category_info['tests']) > 5:
                                content += f"  - ... and {len(category_info['tests']) - 5} more\n"
                            content += "\n"
            else:
                # Fallback to simple categorization
                structure_tests = [t for t in report.missing_tests if "structure" in t.lower()]
                process_tests = [t for t in report.missing_tests if "process" in t.lower()]
                other_tests = [t for t in report.missing_tests if t not in structure_tests + process_tests]

                if structure_tests:
                    content += "**🚨 Structure API Tests:**\n\n"
                    for test in sorted(structure_tests)[:10]:
                        content += f"* `{test}`\n"
                    if len(structure_tests) > 10:
                        content += f"* ... and {len(structure_tests) - 10} more\n"
                    content += "\n"

                if process_tests:
                    content += "**🚨 Process API Tests:**\n\n"
                    for test in sorted(process_tests)[:10]:
                        content += f"* `{test}`\n"
                    if len(process_tests) > 10:
                        content += f"* ... and {len(process_tests) - 10} more\n"
                    content += "\n"

                if other_tests:
                    content += "**📝 Other Tests:**\n\n"
                    for test in sorted(other_tests)[:5]:
                        content += f"* `{test}`\n"
                    if len(other_tests) > 5:
                        content += f"* ... and {len(other_tests) - 5} more\n"
                    content += "\n"

        # Add implementation recommendations section
        content += f"""
== Implementation Recommendations

"""
        if report.recommendations:
            for rec in report.recommendations:
                content += f"* {rec}\n"
            content += "\n"

        # Add detailed category summary if available
        if hasattr(report, 'test_categories') and report.test_categories:
            content += "=== Implementation Priority Matrix\n\n"
            content += "[cols=\"2,1,1,3\"]\n"
            content += "|===\n"
            content += "| Category | Priority | Missing Tests | Effort Estimate\n\n"

            priority_order = ["CRITICAL", "HIGH", "MEDIUM", "LOW"]
            for priority in priority_order:
                for name, info in report.test_categories.items():
                    if info["priority"] == priority and info["missing_count"] > 0:
                        effort = "🔥 Urgent" if priority == "CRITICAL" else \
                                "⚡ High" if priority == "HIGH" else \
                                "📅 Medium" if priority == "MEDIUM" else "🕐 Low"
                        content += f"| {name}\n"
                        content += f"| {priority}\n"
                        content += f"| {info['missing_count']}\n"
                        content += f"| {effort}\n\n"
            content += "|===\n\n"

        content += f"""
== Extra Tests Analysis

{f'ℹ️ **{len(report.extra_tests)} additional tests found in local implementation:**' if report.extra_tests else '✅ **No extra tests beyond upstream.**'}

"""

        if report.extra_tests:
            content += "=== Additional Local Tests\n\n"
            for test in sorted(report.extra_tests)[:20]:  # Show first 20
                content += f"* `{test}`\n"
            if len(report.extra_tests) > 20:
                content += f"* ... and {len(report.extra_tests) - 20} more\n"
            content += "\n"

        content += f"""
== Modified Tests Analysis

{f'🔄 **{len(report.modified_tests)} tests have different signatures:**' if report.modified_tests else '✅ **No modified test signatures detected.**'}

"""

        if report.modified_tests:
            content += "=== Signature Differences\n\n"
            content += "[cols=\"2,1,1\"]\n|===\n| Test Name | Upstream | Local\n\n"
            for test_name, upstream_sig, local_sig in sorted(report.modified_tests)[:10]:
                content += f"| `{test_name.split('.')[-1]}`\n| `{upstream_sig}`\n| `{local_sig}`\n\n"
            content += "|===\n\n"
            if len(report.modified_tests) > 10:
                content += f"_And {len(report.modified_tests) - 10} more differences..._\n\n"

        content += """
== Test Class Analysis

=== Upstream Test Classes

"""

        content += "[cols=\"3,1,4\"]\n|===\n| Class | Methods | Package\n\n"
        for class_key, test_class in sorted(report.upstream_tests.items())[:15]:
            content += f"| `{test_class.name}`\n| {len(test_class.methods)}\n| `{test_class.package}`\n\n"
        content += "|===\n\n"

        if len(report.upstream_tests) > 15:
            content += f"_And {len(report.upstream_tests) - 15} more upstream classes..._\n\n"

        content += """
=== Local Test Classes

"""

        content += "[cols=\"3,1,4\"]\n|===\n| Class | Methods | Package\n\n"
        for class_key, test_class in sorted(report.local_tests.items())[:15]:
            content += f"| `{test_class.name}`\n| {len(test_class.methods)}\n| `{test_class.package}`\n\n"
        content += "|===\n\n"

        if len(report.local_tests) > 15:
            content += f"_And {len(report.local_tests) - 15} more local classes..._\n\n"

        content += """
== Recommendations

"""

        for i, recommendation in enumerate(report.recommendations, 1):
            content += f"{i}. {recommendation}\n\n"

        content += f"""
== Implementation Priorities

=== High Priority (Immediate Action Required)

* Implement missing Structure API tests for core compliance
* Address critical coverage gaps below 70%
* Fix any broken test signatures that affect compliance

=== Medium Priority (Next Development Cycle)

* Implement missing Process API tests
* Review and align modified test signatures
* Improve coverage to >90%

=== Low Priority (Future Enhancement)

* Implement remaining edge case tests
* Add platform-specific optimizations
* Enhance test documentation and comments

== Conclusion

{'🚨 **CRITICAL:** Significant deviations detected requiring immediate attention.' if report.coverage_analysis['coverage_percentage'] < 70 else '⚠️ **WARNING:** Some deviations detected requiring review and planning.' if report.coverage_analysis['coverage_percentage'] < 90 else '✅ **SUCCESS:** Good alignment with upstream tests, minor improvements possible.'}

**Next Steps:**
1. Review missing critical tests and plan implementation
2. Validate modified tests maintain TinkerPop compliance
3. Consider implementing additional tests for enhanced coverage
4. Schedule regular deviation analysis to maintain alignment

---

**Generated:** {report.timestamp} +
**Tool:** TinkerGraphs Compliance Deviation Analyzer +
**Upstream Source:** Apache TinkerPop (github.com/apache/tinkerpop)
"""

        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(content, encoding='utf-8')
        print(f"📄 AsciiDoc report generated: {output_path}")

    def generate_json_report(self, report: DeviationReport, output_path: Path):
        """Generate JSON format deviation report."""
        # Convert dataclasses to dictionaries for JSON serialization
        def to_dict(obj):
            if hasattr(obj, '__dict__'):
                return {k: to_dict(v) for k, v in obj.__dict__.items()}
            elif isinstance(obj, dict):
                return {k: to_dict(v) for k, v in obj.items()}
            elif isinstance(obj, (list, tuple)):
                return [to_dict(item) for item in obj]
            else:
                return obj

        json_data = to_dict(report)

        output_path.parent.mkdir(parents=True, exist_ok=True)
        with output_path.open('w', encoding='utf-8') as f:
            json.dump(json_data, f, indent=2, ensure_ascii=False)

        print(f"📊 JSON report generated: {output_path}")

    def generate_html_report(self, report: DeviationReport, output_path: Path):
        """Generate HTML format deviation report."""
        status_color = "#dc3545" if report.coverage_analysis['coverage_percentage'] < 70 else "#ffc107" if report.coverage_analysis['coverage_percentage'] < 90 else "#28a745"

        html_content = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>TinkerPop Compliance Deviation Analysis</title>
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 20px; background: #f8f9fa; }}
        .container {{ max-width: 1200px; margin: 0 auto; }}
        .header {{ background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 20px; }}
        .status-badge {{ display: inline-block; padding: 8px 16px; border-radius: 20px; color: white; font-weight: bold; background: {status_color}; }}
        .metrics-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin: 20px 0; }}
        .metric-card {{ background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
        .metric-value {{ font-size: 2em; font-weight: bold; color: #333; }}
        .metric-label {{ color: #666; margin-bottom: 10px; }}
        .section {{ background: white; margin: 20px 0; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
        .test-list {{ max-height: 300px; overflow-y: auto; background: #f8f9fa; padding: 10px; border-radius: 4px; }}
        .test-item {{ padding: 5px 0; font-family: monospace; border-bottom: 1px solid #eee; }}
        .recommendations {{ background: #e7f3ff; border-left: 4px solid #2196f3; }}
        .critical {{ background: #ffe7e7; border-left: 4px solid #dc3545; }}
        .warning {{ background: #fff3cd; border-left: 4px solid #ffc107; }}
        .success {{ background: #d4edda; border-left: 4px solid #28a745; }}
        table {{ width: 100%; border-collapse: collapse; }}
        th, td {{ padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }}
        th {{ background: #f8f9fa; font-weight: 600; }}
        .progress-bar {{ background: #e9ecef; height: 20px; border-radius: 10px; overflow: hidden; }}
        .progress-fill {{ height: 100%; background: {status_color}; transition: width 0.3s ease; }}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🔍 TinkerPop Compliance Deviation Analysis</h1>
            <p><strong>Analysis Date:</strong> {report.timestamp}</p>
            <p><strong>Status:</strong> <span class="status-badge">{report.coverage_analysis['coverage_percentage']:.1f}% Coverage</span></p>
        </div>

        <div class="metrics-grid">
            <div class="metric-card">
                <div class="metric-label">Coverage Percentage</div>
                <div class="metric-value">{report.coverage_analysis['coverage_percentage']:.1f}%</div>
                <div class="progress-bar">
                    <div class="progress-fill" style="width: {report.coverage_analysis['coverage_percentage']}%"></div>
                </div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Upstream Tests</div>
                <div class="metric-value">{report.coverage_analysis['total_upstream_tests']}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Local Tests</div>
                <div class="metric-value">{report.coverage_analysis['total_local_tests']}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Missing Tests</div>
                <div class="metric-value" style="color: #dc3545;">{len(report.missing_tests)}</div>
            </div>
        </div>

        <div class="section recommendations">
            <h2>📋 Recommendations</h2>
            <ul>
"""

        for recommendation in report.recommendations:
            html_content += f"                <li>{recommendation}</li>\n"

        html_content += f"""            </ul>
        </div>

        <div class="section">
            <h2>❌ Missing Tests ({len(report.missing_tests)})</h2>
            <div class="test-list">
"""

        for test in sorted(report.missing_tests)[:50]:  # Show first 50
            html_content += f'                <div class="test-item">{test}</div>\n'

        if len(report.missing_tests) > 50:
            html_content += f'                <div class="test-item"><em>... and {len(report.missing_tests) - 50} more</em></div>\n'

        html_content += f"""            </div>
        </div>

        <div class="section">
            <h2>➕ Extra Tests ({len(report.extra_tests)})</h2>
            <div class="test-list">
"""

        for test in sorted(report.extra_tests)[:30]:  # Show first 30
            html_content += f'                <div class="test-item">{test}</div>\n'

        if len(report.extra_tests) > 30:
            html_content += f'                <div class="test-item"><em>... and {len(report.extra_tests) - 30} more</em></div>\n'

        html_content += f"""            </div>
        </div>

        <div class="section">
            <h2>🔄 Modified Tests ({len(report.modified_tests)})</h2>
            <table>
                <thead>
                    <tr>
                        <th>Test Name</th>
                        <th>Upstream Signature</th>
                        <th>Local Signature</th>
                    </tr>
                </thead>
                <tbody>
"""

        for test_name, upstream_sig, local_sig in sorted(report.modified_tests)[:20]:
            html_content += f"""                    <tr>
                        <td><code>{test_name.split('.')[-1]}</code></td>
                        <td><code>{upstream_sig}</code></td>
                        <td><code>{local_sig}</code></td>
                    </tr>
"""

        html_content += """                </tbody>
            </table>
        </div>

        <div class="section">
            <h2>📊 Test Class Summary</h2>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px;">
                <div>
                    <h3>Upstream Classes</h3>
                    <table>
                        <thead>
                            <tr><th>Class</th><th>Methods</th></tr>
                        </thead>
                        <tbody>
"""

        for class_key, test_class in sorted(report.upstream_tests.items())[:10]:
            html_content += f"""                            <tr>
                                <td><code>{test_class.name}</code></td>
                                <td>{len(test_class.methods)}</td>
                            </tr>
"""

        html_content += """                        </tbody>
                    </table>
                </div>
                <div>
                    <h3>Local Classes</h3>
                    <table>
                        <thead>
                            <tr><th>Class</th><th>Methods</th></tr>
                        </thead>
                        <tbody>
"""

        for class_key, test_class in sorted(report.local_tests.items())[:10]:
            html_content += f"""                            <tr>
                                <td><code>{test_class.name}</code></td>
                                <td>{len(test_class.methods)}</td>
                            </tr>
"""

        html_content += """                        </tbody>
                    </table>
                </div>
            </div>
        </div>

        <footer style="text-align: center; margin-top: 40px; color: #666; font-size: 0.9em;">
            <p>Generated by TinkerGraphs Compliance Deviation Analyzer</p>
            <p>Upstream Source: Apache TinkerPop (github.com/apache/tinkerpop)</p>
        </footer>
    </div>
</body>
</html>"""

        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(html_content, encoding='utf-8')
        print(f"🌐 HTML report generated: {output_path}")

    def run_analysis(self, download_fresh: bool = False, output_format: str = "adoc",
                    output_file: Optional[str] = None) -> bool:
        """Run complete compliance deviation analysis."""
        print("🚀 Starting TinkerPop Compliance Deviation Analysis...")

        # Ensure reports directory exists
        self.reports_dir.mkdir(parents=True, exist_ok=True)

        # Download upstream tests if needed
        if download_fresh or not (self.upstream_cache_dir / "extracted").exists():
            if not self.download_upstream_tests():
                return False

        # Analyze tests
        upstream_tests = self.analyze_upstream_tests()
        local_tests = self.analyze_local_tests()

        if not upstream_tests and not local_tests:
            print("❌ No tests found to analyze")
            return False

        # Compare and generate report
        report = self.compare_tests(upstream_tests, local_tests)

        # Generate output file
        if output_file:
            output_path = Path(output_file)
        else:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = f"compliance_deviation_report_{timestamp}.{output_format}"
            output_path = self.reports_dir / filename

        # Generate report in requested format
        if output_format == "adoc":
            self.generate_asciidoc_report(report, output_path)
        elif output_format == "json":
            self.generate_json_report(report, output_path)
        elif output_format == "html":
            self.generate_html_report(report, output_path)
        else:
            print(f"❌ Unsupported output format: {output_format}")
            return False

        # Print summary
        print(f"\n📊 Analysis Complete:")
        print(f"   Coverage: {report.coverage_analysis['coverage_percentage']:.1f}%")
        print(f"   Missing: {len(report.missing_tests)} tests")
        print(f"   Extra: {len(report.extra_tests)} tests")
        print(f"   Modified: {len(report.modified_tests)} tests")
        print(f"   Report: {output_path}")

        return True


def main():
    """Main entry point for the compliance deviation analysis tool."""
    parser = argparse.ArgumentParser(
        description="Analyze deviations between upstream TinkerPop and local compliance tests"
    )
    parser.add_argument(
        "--output", "-o",
        help="Output file path (default: auto-generated in build/reports/compliance/)"
    )
    parser.add_argument(
        "--format", "-f",
        choices=["adoc", "json", "html"],
        default="adoc",
        help="Output format (default: adoc)"
    )
    parser.add_argument(
        "--download", "-d",
        action="store_true",
        help="Download fresh upstream tests (default: use cached if available)"
    )
    parser.add_argument(
        "--project-root", "-p",
        type=Path,
        default=Path.cwd(),
        help="Project root directory (default: current directory)"
    )

    args = parser.parse_args()

    # Find project root if not explicitly provided
    project_root = args.project_root
    if not (project_root / "build.gradle.kts").exists():
        # Try to find project root by looking for build.gradle.kts
        current = Path.cwd()
        while current != current.parent:
            if (current / "build.gradle.kts").exists():
                project_root = current
                break
            current = current.parent

    print(f"📁 Using project root: {project_root}")

    # Run analysis
    analyzer = ComplianceTestAnalyzer(project_root)
    success = analyzer.run_analysis(
        download_fresh=args.download,
        output_format=args.format,
        output_file=args.output
    )

    exit(0 if success else 1)


if __name__ == "__main__":
    main()
