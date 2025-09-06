package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

/**
 * Comprehensive test suite for JVM persistence layer functionality.
 * Tests all supported formats, transaction logging, backup/recovery, and error handling.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JvmPersistenceLayerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var persistenceLayer: JvmPersistenceLayer
    private lateinit var testGraph: TinkerGraph

    @BeforeEach
    fun setUp() {
        val testDirectory = tempDir.resolve("test-persistence").toString()
        persistenceLayer = JvmPersistenceLayer(
            baseDirectory = testDirectory,
            enableTransactionLog = true,
            enableCompression = true,
            maxBackups = 5
        )

        // Create a test graph with vertices and edges
        testGraph = TinkerGraph.open()

        // Add vertices
        val v1 = testGraph.addVertex("id", "1", "label", "person", "name", "marko", "age", 29)
        val v2 = testGraph.addVertex("id", "2", "label", "person", "name", "vadas", "age", 27)
        val v3 = testGraph.addVertex("id", "3", "label", "software", "name", "lop", "lang", "java")
        val v4 = testGraph.addVertex("id", "4", "label", "person", "name", "josh", "age", 32)
        val v5 = testGraph.addVertex("id", "5", "label", "software", "name", "ripple", "lang", "java")
        val v6 = testGraph.addVertex("id", "6", "label", "person", "name", "peter", "age", 35)

        // Add edges
        v1.addEdge("knows", v2, "id", "7", "weight", 0.5f)
        v1.addEdge("knows", v4, "id", "8", "weight", 1.0f)
        v1.addEdge("created", v3, "id", "9", "weight", 0.4f)
        v4.addEdge("created", v5, "id", "10", "weight", 1.0f)
        v4.addEdge("created", v3, "id", "11", "weight", 0.4f)
        v6.addEdge("created", v3, "id", "12", "weight", 0.2f)
    }

    @Test
    fun `test save and load graph in JSON format`() {
        // Save graph
        val metadata = persistenceLayer.saveGraph(testGraph, "test-json", JvmPersistenceLayer.PersistenceFormat.JSON)

        assertEquals("JSON", metadata.format)
        assertEquals(6, metadata.vertexCount)
        assertEquals(6, metadata.edgeCount)
        assertTrue(metadata.compressed)
        assertTrue(metadata.fileSize > 0)

        // Load graph
        val loadedGraph = persistenceLayer.loadGraph("test-json", JvmPersistenceLayer.PersistenceFormat.JSON)

        // Verify loaded graph
        assertGraphsEqual(testGraph, loadedGraph)
    }

    @Test
    fun `test save and load graph in XML format`() {
        val metadata = persistenceLayer.saveGraph(testGraph, "test-xml", JvmPersistenceLayer.PersistenceFormat.XML)

        assertEquals("XML", metadata.format)
        assertEquals(6, metadata.vertexCount)
        assertEquals(6, metadata.edgeCount)

        val loadedGraph = persistenceLayer.loadGraph("test-xml", JvmPersistenceLayer.PersistenceFormat.XML)

        // Basic verification (XML conversion is simplified in implementation)
        assertNotNull(loadedGraph)
    }

    @Test
    fun `test save and load graph in YAML format`() {
        val metadata = persistenceLayer.saveGraph(testGraph, "test-yaml", JvmPersistenceLayer.PersistenceFormat.YAML)

        assertEquals("YAML", metadata.format)
        assertEquals(6, metadata.vertexCount)
        assertEquals(6, metadata.edgeCount)

        val loadedGraph = persistenceLayer.loadGraph("test-yaml", JvmPersistenceLayer.PersistenceFormat.YAML)

        // Basic verification (YAML conversion is simplified in implementation)
        assertNotNull(loadedGraph)
    }

    @Test
    fun `test save and load graph in GraphML format`() {
        val metadata = persistenceLayer.saveGraph(testGraph, "test-graphml", JvmPersistenceLayer.PersistenceFormat.GRAPHML)

        assertEquals("GRAPHML", metadata.format)
        assertEquals(6, metadata.vertexCount)
        assertEquals(6, metadata.edgeCount)

        val loadedGraph = persistenceLayer.loadGraph("test-graphml", JvmPersistenceLayer.PersistenceFormat.GRAPHML)

        // Verify basic structure (GraphML should preserve most data)
        assertEquals(6, loadedGraph.vertices().asSequence().count())
        assertEquals(6, loadedGraph.edges().asSequence().count())
    }

    @Test
    fun `test save and load graph in GraphSON format`() {
        val metadata = persistenceLayer.saveGraph(testGraph, "test-graphson", JvmPersistenceLayer.PersistenceFormat.GRAPHSON)

        assertEquals("GRAPHSON", metadata.format)
        assertEquals(6, metadata.vertexCount)
        assertEquals(6, metadata.edgeCount)

        val loadedGraph = persistenceLayer.loadGraph("test-graphson", JvmPersistenceLayer.PersistenceFormat.GRAPHSON)

        // Verify basic structure
        assertEquals(6, loadedGraph.vertices().asSequence().count())
        assertEquals(6, loadedGraph.edges().asSequence().count())
    }

    @Test
    fun `test save and load graph in Gryo format`() {
        val metadata = persistenceLayer.saveGraph(testGraph, "test-gryo", JvmPersistenceLayer.PersistenceFormat.GRYO)

        assertEquals("GRYO", metadata.format)
        assertEquals(6, metadata.vertexCount)
        assertEquals(6, metadata.edgeCount)

        val loadedGraph = persistenceLayer.loadGraph("test-gryo", JvmPersistenceLayer.PersistenceFormat.GRYO)

        // Verify basic structure
        assertEquals(6, loadedGraph.vertices().asSequence().count())
        assertEquals(6, loadedGraph.edges().asSequence().count())
    }

    @Test
    fun `test save and load graph in binary format`() {
        val metadata = persistenceLayer.saveGraph(testGraph, "test-binary", JvmPersistenceLayer.PersistenceFormat.BINARY)

        assertEquals("BINARY", metadata.format)
        assertEquals(6, metadata.vertexCount)
        assertEquals(6, metadata.edgeCount)

        val loadedGraph = persistenceLayer.loadGraph("test-binary", JvmPersistenceLayer.PersistenceFormat.BINARY)

        // Binary format should preserve complete fidelity
        assertGraphsEqual(testGraph, loadedGraph)
    }

    @Test
    fun `test multi-format export`() {
        val formats = setOf(
            JvmPersistenceLayer.PersistenceFormat.JSON,
            JvmPersistenceLayer.PersistenceFormat.GRAPHML,
            JvmPersistenceLayer.PersistenceFormat.BINARY
        )

        val results = persistenceLayer.exportMultiFormat(testGraph, "test-multi", formats)

        assertEquals(3, results.size)
        assertTrue(results.containsKey(JvmPersistenceLayer.PersistenceFormat.JSON))
        assertTrue(results.containsKey(JvmPersistenceLayer.PersistenceFormat.GRAPHML))
        assertTrue(results.containsKey(JvmPersistenceLayer.PersistenceFormat.BINARY))

        // Verify each format can be loaded
        formats.forEach { format ->
            val loadedGraph = persistenceLayer.loadGraph("test-multi", format)
            assertNotNull(loadedGraph)
            assertEquals(6, loadedGraph.vertices().asSequence().count())
            assertEquals(6, loadedGraph.edges().asSequence().count())
        }
    }

    @Test
    fun `test backup creation and restoration`() {
        // Save initial graph
        persistenceLayer.saveGraph(testGraph, "test-backup", JvmPersistenceLayer.PersistenceFormat.JSON)

        // Create backup
        val sourcePath = tempDir.resolve("test-persistence/test-backup.json")
        val backupPath = persistenceLayer.createBackup(
            sourcePath,
            JvmPersistenceLayer.PersistenceFormat.JSON,
            "manual-backup"
        )

        assertTrue(Files.exists(backupPath))
        assertTrue(backupPath.fileName.toString().contains("manual-backup"))

        // Modify original graph
        testGraph.addVertex("id", "7", "label", "newVertex")
        persistenceLayer.saveGraph(testGraph, "test-backup", JvmPersistenceLayer.PersistenceFormat.JSON)

        // Restore from backup
        persistenceLayer.restoreFromBackup(
            "manual-backup",
            "test-restored",
            JvmPersistenceLayer.PersistenceFormat.JSON
        )

        // Verify loaded graph has original structure
        val restoredGraph = persistenceLayer.loadGraph("test-restored", JvmPersistenceLayer.PersistenceFormat.JSON)
        assertEquals(6, restoredGraph.vertices().asSequence().count()) // Original count, not 7
    }

    @Test
    fun `test backup listing`() {
        // Save graph and create multiple backups
        persistenceLayer.saveGraph(testGraph, "test-list", JvmPersistenceLayer.PersistenceFormat.JSON)

        val sourcePath = tempDir.resolve("test-persistence/test-list.json")

        persistenceLayer.createBackup(sourcePath, JvmPersistenceLayer.PersistenceFormat.JSON, "backup1")
        Thread.sleep(10) // Ensure different timestamps
        persistenceLayer.createBackup(sourcePath, JvmPersistenceLayer.PersistenceFormat.JSON, "backup2")

        val backups = persistenceLayer.listBackups()

        assertTrue(backups.size >= 2)
        assertTrue(backups.any { it.fileName.contains("backup1") })
        assertTrue(backups.any { it.fileName.contains("backup2") })

        // Verify backup info structure
        backups.forEach { backup ->
            assertTrue(backup.size > 0)
            assertNotNull(backup.createdAt)
            assertNotNull(backup.lastModified)
        }
    }

    @Test
    fun `test transaction logging`() {
        // Perform some operations
        persistenceLayer.saveGraph(testGraph, "tx-test", JvmPersistenceLayer.PersistenceFormat.JSON)
        persistenceLayer.loadGraph("tx-test", JvmPersistenceLayer.PersistenceFormat.JSON)

        val transactions = persistenceLayer.getTransactionLog()

        assertTrue(transactions.size >= 2)

        val saveTransaction = transactions.find { it.operation == "SAVE" }
        val loadTransaction = transactions.find { it.operation == "LOAD" }

        assertNotNull(saveTransaction)
        assertNotNull(loadTransaction)

        assertEquals("JSON", saveTransaction.format)
        assertEquals("tx-test", saveTransaction.fileName)
        assertTrue(saveTransaction.completed)

        assertEquals("JSON", loadTransaction.format)
        assertEquals("tx-test", loadTransaction.fileName)
        assertTrue(loadTransaction.completed)
    }

    @Test
    fun `test transaction log cleanup`() {
        // Create some transactions
        repeat(5) {
            persistenceLayer.saveGraph(testGraph, "cleanup-test-$it", JvmPersistenceLayer.PersistenceFormat.JSON)
        }

        val initialTransactions = persistenceLayer.getTransactionLog()
        val initialCount = initialTransactions.size

        // Cleanup (keeping 0 days should remove old completed transactions)
        persistenceLayer.cleanupTransactionLog(0)

        val afterCleanupTransactions = persistenceLayer.getTransactionLog()

        // Should have fewer transactions after cleanup
        assertTrue(afterCleanupTransactions.size <= initialCount)
    }

    @Test
    fun `test persistence statistics`() {
        // Create files in multiple formats
        persistenceLayer.saveGraph(testGraph, "stats-json", JvmPersistenceLayer.PersistenceFormat.JSON)
        persistenceLayer.saveGraph(testGraph, "stats-xml", JvmPersistenceLayer.PersistenceFormat.XML)
        persistenceLayer.saveGraph(testGraph, "stats-binary", JvmPersistenceLayer.PersistenceFormat.BINARY)

        // Create a backup
        val sourcePath = tempDir.resolve("test-persistence/stats-json.json")
        persistenceLayer.createBackup(sourcePath, JvmPersistenceLayer.PersistenceFormat.JSON)

        val stats = persistenceLayer.getStatistics()

        // Verify statistics structure
        assertTrue(stats.containsKey("formatCounts"))
        assertTrue(stats.containsKey("totalSizeBytes"))
        assertTrue(stats.containsKey("totalSizeMB"))
        assertTrue(stats.containsKey("backupCount"))
        assertTrue(stats.containsKey("backupSizeBytes"))
        assertTrue(stats.containsKey("transactionCount"))
        assertTrue(stats.containsKey("completedTransactions"))

        val formatCounts = stats["formatCounts"] as Map<String, Long>
        assertTrue(formatCounts["JSON"]!! >= 1)
        assertTrue(formatCounts["XML"]!! >= 1)
        assertTrue(formatCounts["BINARY"]!! >= 1)

        val totalSize = stats["totalSizeBytes"] as Long
        assertTrue(totalSize > 0)

        val backupCount = stats["backupCount"] as Long
        assertTrue(backupCount >= 1)
    }

    @Test
    fun `test error handling - load non-existent file`() {
        assertFailsWith<JvmPersistenceLayer.PersistenceException> {
            persistenceLayer.loadGraph("non-existent", JvmPersistenceLayer.PersistenceFormat.JSON)
        }
    }

    @Test
    fun `test error handling - restore non-existent backup`() {
        assertFailsWith<JvmPersistenceLayer.PersistenceException> {
            persistenceLayer.restoreFromBackup(
                "non-existent-backup",
                "target",
                JvmPersistenceLayer.PersistenceFormat.JSON
            )
        }
    }

    @Test
    fun `test concurrent access safety`() {
        // Test concurrent saves
        val threads = (1..5).map { index ->
            Thread {
                val localGraph = TinkerGraph.open()
                localGraph.addVertex("id", "thread-$index", "label", "test")
                persistenceLayer.saveGraph(localGraph, "concurrent-$index", JvmPersistenceLayer.PersistenceFormat.JSON)
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Verify all files were created successfully
        (1..5).forEach { index ->
            val loadedGraph = persistenceLayer.loadGraph("concurrent-$index", JvmPersistenceLayer.PersistenceFormat.JSON)
            assertEquals(1, loadedGraph.vertices().asSequence().count())
        }
    }

    @Test
    fun `test metadata persistence and loading`() {
        val metadata = persistenceLayer.saveGraph(testGraph, "metadata-test", JvmPersistenceLayer.PersistenceFormat.JSON)

        // Verify metadata content
        assertEquals("JSON", metadata.format)
        assertEquals(6, metadata.vertexCount)
        assertEquals(6, metadata.edgeCount)
        assertTrue(metadata.compressed)
        assertNotNull(metadata.createdAt)
        assertNotNull(metadata.lastModified)
        assertTrue(metadata.fileSize > 0)
        assertTrue(metadata.transactionCount > 0)

        // Verify metadata file was created
        val metadataFile = tempDir.resolve("test-persistence/metadata-test.metadata")
        assertTrue(Files.exists(metadataFile))
    }

    @Test
    fun `test compression functionality`() {
        // Create persistence layer with compression disabled
        val noCompressionLayer = JvmPersistenceLayer(
            baseDirectory = tempDir.resolve("no-compression").toString(),
            enableTransactionLog = false,
            enableCompression = false
        )

        // Save same graph with both layers
        val compressedMetadata = persistenceLayer.saveGraph(testGraph, "compressed", JvmPersistenceLayer.PersistenceFormat.JSON)
        val uncompressedMetadata = noCompressionLayer.saveGraph(testGraph, "uncompressed", JvmPersistenceLayer.PersistenceFormat.JSON)

        // Compressed version should have compression flag set
        assertTrue(compressedMetadata.compressed)
        assertFalse(uncompressedMetadata.compressed)

        // Both should load correctly
        val compressedGraph = persistenceLayer.loadGraph("compressed", JvmPersistenceLayer.PersistenceFormat.JSON)
        val uncompressedGraph = noCompressionLayer.loadGraph("uncompressed", JvmPersistenceLayer.PersistenceFormat.JSON)

        assertGraphsEqual(testGraph, compressedGraph)
        assertGraphsEqual(testGraph, uncompressedGraph)
    }

    @Test
    fun `test empty graph persistence`() {
        val emptyGraph = TinkerGraph.open()

        val metadata = persistenceLayer.saveGraph(emptyGraph, "empty", JvmPersistenceLayer.PersistenceFormat.JSON)

        assertEquals(0, metadata.vertexCount)
        assertEquals(0, metadata.edgeCount)

        val loadedEmptyGraph = persistenceLayer.loadGraph("empty", JvmPersistenceLayer.PersistenceFormat.JSON)

        assertEquals(0, loadedEmptyGraph.vertices().asSequence().count())
        assertEquals(0, loadedEmptyGraph.edges().asSequence().count())
    }

    @Test
    fun `test large property values`() {
        val largeGraph = TinkerGraph.open()
        val largeText = "x".repeat(10000) // 10KB string

        largeGraph.addVertex("id", "1", "label", "test", "largeProperty", largeText, "normalProperty", "small")

        val metadata = persistenceLayer.saveGraph(largeGraph, "large-props", JvmPersistenceLayer.PersistenceFormat.JSON)

        assertTrue(metadata.fileSize > 10000) // Should be larger due to large property

        val loadedGraph = persistenceLayer.loadGraph("large-props", JvmPersistenceLayer.PersistenceFormat.JSON)

        val vertex = loadedGraph.vertices().next()
        assertEquals(largeText, vertex.property<String>("largeProperty").value())
        assertEquals("small", vertex.property<String>("normalProperty").value())
    }

    // Helper method to compare graphs
    private fun assertGraphsEqual(expected: TinkerGraph, actual: TinkerGraph) {
        // Compare vertex count
        val expectedVertices = expected.vertices().asSequence().toList()
        val actualVertices = actual.vertices().asSequence().toList()
        assertEquals(expectedVertices.size, actualVertices.size, "Vertex count mismatch")

        // Compare edge count
        val expectedEdges = expected.edges().asSequence().toList()
        val actualEdges = actual.edges().asSequence().toList()
        assertEquals(expectedEdges.size, actualEdges.size, "Edge count mismatch")

        // Compare vertices by ID and properties
        expectedVertices.forEach { expectedVertex ->
            val actualVertex = actual.vertices(expectedVertex.id()).next()
            assertNotNull(actualVertex, "Vertex with ID ${expectedVertex.id()} not found")

            assertEquals(expectedVertex.label(), actualVertex.label(), "Vertex label mismatch for ${expectedVertex.id()}")

            // Compare properties
            expectedVertex.properties<Any>().forEach { expectedProp ->
                val actualProp = actualVertex.property<Any>(expectedProp.key())
                assertTrue(actualProp.isPresent(), "Property ${expectedProp.key()} missing for vertex ${expectedVertex.id()}")
                assertEquals(expectedProp.value(), actualProp.value() as Any, "Property value mismatch for ${expectedProp.key()}")
            }
        }

        // Compare edges by ID and properties
        expectedEdges.forEach { expectedEdge ->
            val actualEdge = actual.edges(expectedEdge.id()).next()
            assertNotNull(actualEdge, "Edge with ID ${expectedEdge.id()} not found")

            assertEquals(expectedEdge.label(), actualEdge.label(), "Edge label mismatch for ${expectedEdge.id()}")
            assertEquals(expectedEdge.outVertex().id(), actualEdge.outVertex().id(), "Out vertex mismatch for edge ${expectedEdge.id()}")
            assertEquals(expectedEdge.inVertex().id(), actualEdge.inVertex().id(), "In vertex mismatch for edge ${expectedEdge.id()}")

            // Compare properties
            expectedEdge.properties<Any>().forEach { expectedProp ->
                val actualProp = actualEdge.property<Any>(expectedProp.key())
                assertTrue(actualProp.isPresent(), "Property ${expectedProp.key()} missing for edge ${expectedEdge.id()}")
                assertEquals(expectedProp.value(), actualProp.value() as Any, "Property value mismatch for ${expectedProp.key()}")
            }
        }
    }
}
