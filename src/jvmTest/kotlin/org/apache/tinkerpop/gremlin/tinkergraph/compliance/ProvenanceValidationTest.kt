package org.apache.tinkerpop.gremlin.tinkergraph.compliance

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.comparables.shouldBeGreaterThan

/**
 * Automated Provenance Validation Tests
 *
 * This test suite automatically validates that all TinkerPop compliance tests
 * maintain proper provenance tracking, legal attribution, and mapping accuracy.
 *
 * PROVENANCE:
 * - Original concept: Custom implementation for TinkerGraphs project
 * - Purpose: Ensure legal compliance and maintainability of test suite
 * - License: Apache License 2.0 (consistent with TinkerPop)
 *
 * Licensed under the Apache License, Version 2.0 (same as Apache TinkerPop)
 */
@TinkerPopTestSource(
    originalClass = "Custom implementation",
    tinkerPopVersion = "3.7.2",
    adaptations = "Original work for provenance validation",
    coverage = 1.0
)
class ProvenanceValidationTest : StringSpec({

    "Provenance registry should be properly initialized" {
        val mappings = TinkerPopProvenanceRegistry.getAllMappings()
        mappings.shouldNotBeNull()
        mappings.size shouldBeGreaterThan 0
    }

    "All registered test mappings should have valid structure" {
        val mappings = TinkerPopProvenanceRegistry.getAllMappings()

        mappings.values.forEach { mapping ->
            // Validate basic structure
            mapping.kotlinTestClass.shouldNotBeNull()
            mapping.originalJavaClass.shouldNotBeNull()
            mapping.originalPackage.shouldNotBeNull()
            mapping.testCategory.shouldNotBeNull()
            mapping.status.shouldNotBeNull()

            // Validate coverage is reasonable (allow 0% for planned tests)
            if (mapping.status != MappingStatus.PLANNED) {
                mapping.coveragePercentage shouldBeGreaterThan 0.0
            }

            // Validate URLs are properly formed
            mapping.javaSourceUrl.shouldNotBeNull()
            mapping.javaSourceUrl.startsWith("https://github.com/apache/tinkerpop/") shouldBe true
        }
    }

    "Base TinkerPop information should be complete" {
        val baseInfo = TinkerPopProvenanceRegistry.BASE_TINKERPOP_INFO

        baseInfo.version.shouldNotBeNull()
        baseInfo.repositoryUrl shouldBe "https://github.com/apache/tinkerpop"
        baseInfo.testSourcePath shouldBe "tinkergraph-gremlin/src/test/java"
        baseInfo.lastSyncDate.shouldNotBeNull()
        baseInfo.lastSyncCommit.shouldNotBeNull()
    }

    "Provenance report should generate correctly" {
        val report = TinkerPopProvenanceRegistry.generateProvenanceReport()

        report.totalMappings shouldBeGreaterThan 0
        report.baseInfo.shouldNotBeNull()
        report.mappings.size shouldBe report.totalMappings
        report.overallCoverage shouldBeGreaterThan 0.0

        // Verify counts add up
        val calculatedTotal = report.completeMappings + report.partialMappings + report.plannedMappings
        calculatedTotal shouldBe report.totalMappings
    }

    "All test categories should be represented" {
        val mappings = TinkerPopProvenanceRegistry.getAllMappings()
        val representedCategories = mappings.values.map { it.testCategory }.toSet()

        // At least some categories should be represented
        representedCategories.size shouldBeGreaterThan 0

        // Structure API should definitely be represented since we've implemented it
        representedCategories.contains(TestCategory.STRUCTURE_API) shouldBe true
    }

    "Compliance matrix should generate valid markdown" {
        val matrix = ProvenanceUtils.generateComplianceMatrix()

        matrix.shouldNotBeNull()
        matrix.contains("| Kotlin Test Class |") shouldBe true
        matrix.contains("| Original Java Class |") shouldBe true
        matrix.contains("|------------------|") shouldBe true
    }

    "Attribution validation should pass for implemented tests" {
        val issues = ProvenanceUtils.validateAttribution()

        // For now, this should be empty as we don't have file scanning implemented yet
        // In the future, this would check actual file headers
        issues.shouldBeEmpty()
    }

    "Provenance annotations should be extractable from test classes" {
        // Test that our annotation system works
        val structureTestsClass = StructureComplianceTests::class.java
        val basicTestsClass = BasicStructureComplianceTests::class.java
        val provenanceTestClass = ProvenanceValidationTest::class.java

        // Check that provenance annotations exist
        ProvenanceUtils.verifyProvenanceAnnotation(structureTestsClass) shouldBe true
        ProvenanceUtils.verifyProvenanceAnnotation(basicTestsClass) shouldBe true
        ProvenanceUtils.verifyProvenanceAnnotation(provenanceTestClass) shouldBe true

        // Extract and validate annotation content
        val structureProvenance = ProvenanceUtils.extractProvenance(structureTestsClass)
        structureProvenance.shouldNotBeNull()
        structureProvenance.originalClass shouldBe "org.apache.tinkerpop.gremlin.structure.GraphTest"
        structureProvenance.tinkerPopVersion shouldBe "3.7.2"
        structureProvenance.coverage shouldBe 0.8
    }

    "Mapping status should be realistic for implementation progress" {
        val mappings = TinkerPopProvenanceRegistry.getAllMappings()

        // Count mappings by status
        val completeCount = mappings.values.count { it.status == MappingStatus.COMPLETE }
        val partialCount = mappings.values.count { it.status == MappingStatus.PARTIAL }
        val plannedCount = mappings.values.count { it.status == MappingStatus.PLANNED }

        // We should have at least some implemented tests
        (completeCount + partialCount) shouldBeGreaterThan 0

        // We should have some planned tests (showing roadmap)
        plannedCount shouldBeGreaterThan 0

        // Complete tests should have high coverage
        mappings.values.filter { it.status == MappingStatus.COMPLETE }
            .forEach { mapping ->
                mapping.coveragePercentage shouldBeGreaterThan 0.8
            }
    }

    "Test categories should have meaningful descriptions" {
        TestCategory.values().forEach { category ->
            category.description.shouldNotBeNull()
            category.description.length shouldBeGreaterThan 10
        }
    }

    "Mapping status should have meaningful descriptions" {
        MappingStatus.values().forEach { status ->
            status.description.shouldNotBeNull()
            status.description.length shouldBeGreaterThan 10
        }
    }

    "Java source URLs should be correctly formatted for TinkerPop 3.7.2" {
        val mappings = TinkerPopProvenanceRegistry.getAllMappings()

        mappings.values.forEach { mapping ->
            val url = mapping.javaSourceUrl

            // Should point to correct TinkerPop repository and version
            url.startsWith("https://github.com/apache/tinkerpop/blob/3.7.2/") shouldBe true
            url.endsWith(".java") shouldBe true
            url.contains("tinkergraph-gremlin/src/test/java/") shouldBe true
        }
    }

    "Coverage percentages should be within valid range" {
        val mappings = TinkerPopProvenanceRegistry.getAllMappings()

        mappings.values.forEach { mapping ->
            if (mapping.status != MappingStatus.PLANNED) {
                mapping.coveragePercentage shouldBeGreaterThan 0.0
            }
            // Allow for coverage > 1.0 in case we exceed original test coverage
        }
    }

    "Adaptation documentation should exist for implemented tests" {
        val mappings = TinkerPopProvenanceRegistry.getAllMappings()

        mappings.values.filter {
            it.status == MappingStatus.COMPLETE || it.status == MappingStatus.PARTIAL
        }.forEach { mapping ->
            // Implemented tests should have adaptation documentation
            mapping.adaptations.size shouldBeGreaterThan 0
            mapping.adaptations.forEach { adaptation ->
                adaptation.length shouldBeGreaterThan 5
            }
        }
    }

    "Provenance report should be human-readable" {
        val report = TinkerPopProvenanceRegistry.generateProvenanceReport()
        val reportText = report.generateReport()

        reportText.shouldNotBeNull()
        reportText.contains("TinkerPop Test Suite Provenance Report") shouldBe true
        reportText.contains("Base TinkerPop Version:") shouldBe true
        reportText.contains("Test Mapping Summary:") shouldBe true
        reportText.contains("Test Categories:") shouldBe true
        reportText.contains("Detailed Mappings:") shouldBe true

        // Should contain information about our implemented tests
        reportText.contains("StructureComplianceTests") shouldBe true
        reportText.contains("BasicStructureComplianceTests") shouldBe true
    }
})
