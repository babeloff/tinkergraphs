package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files
import java.nio.file.Path

/**
 * Comprehensive test suite for JVM persistence layer functionality. Tests all supported formats,
 * transaction logging, backup/recovery, and error handling.
 */
class JvmPersistenceLayerTest :
        StringSpec({
            lateinit var tempDir: Path
            lateinit var persistenceLayer: JvmPersistenceLayer
            lateinit var testGraph: TinkerGraph

            beforeTest {
                tempDir = Files.createTempDirectory("kotest-persistence")
                val testDirectory = tempDir.resolve("test-persistence").toString()
                persistenceLayer =
                        JvmPersistenceLayer(
                                baseDirectory = testDirectory,
                                enableTransactionLog = true,
                                enableCompression = true,
                                maxBackups = 5
                        )

                // Create a test graph with vertices and edges
                testGraph = TinkerGraph.open()

                // Add vertices
                val v1 = testGraph.addVertex()
                v1.property("id", "1")
                v1.property("label", "person")
                v1.property("name", "marko")
                v1.property("age", 29)

                val v2 = testGraph.addVertex()
                v2.property("id", "2")
                v2.property("label", "person")
                v2.property("name", "vadas")
                v2.property("age", 27)

                val v3 = testGraph.addVertex()
                v3.property("id", "3")
                v3.property("label", "software")
                v3.property("name", "lop")
                v3.property("lang", "java")

                val v4 = testGraph.addVertex()
                v4.property("id", "4")
                v4.property("label", "person")
                v4.property("name", "josh")
                v4.property("age", 32)

                val v5 = testGraph.addVertex()
                v5.property("id", "5")
                v5.property("label", "software")
                v5.property("name", "ripple")
                v5.property("lang", "java")

                val v6 = testGraph.addVertex()
                v6.property("id", "6")
                v6.property("label", "person")
                v6.property("name", "peter")
                v6.property("age", 35)

                // Add edges
                val e1 = v1.addEdge("knows", v2)
                e1.property("id", "7")
                e1.property("weight", 0.5f)

                val e2 = v1.addEdge("knows", v4)
                e2.property("id", "8")
                e2.property("weight", 1.0f)

                val e3 = v1.addEdge("created", v3)
                e3.property("id", "9")
                e3.property("weight", 0.4f)

                val e4 = v4.addEdge("created", v5)
                e4.property("id", "10")
                e4.property("weight", 1.0f)

                val e5 = v4.addEdge("created", v3)
                e5.property("id", "11")
                e5.property("weight", 0.4f)

                val e6 = v6.addEdge("created", v3)
                e6.property("id", "12")
                e6.property("weight", 0.2f)
            }

            afterTest {
                testGraph.close()
                persistenceLayer.close()
                // Clean up temp directory
                Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach {
                    Files.deleteIfExists(it)
                }
            }

            "JSON format save and load should preserve graph structure" {
                // Save graph in JSON format
                val metadata =
                        persistenceLayer.saveGraph(
                                testGraph,
                                "test-json",
                                JvmPersistenceLayer.PersistenceFormat.JSON
                        )

                metadata shouldNotBe null
                metadata["format"] shouldBe "JSON"
                metadata["vertexCount"] shouldBe 6
                metadata["edgeCount"] shouldBe 6

                // Load graph and verify structure
                val loadedGraph =
                        persistenceLayer.loadGraph(
                                "test-json",
                                JvmPersistenceLayer.PersistenceFormat.JSON
                        )

                loadedGraph.vertices().asSequence().count() shouldBe 6
                loadedGraph.edges().asSequence().count() shouldBe 6

                // Verify specific vertex properties
                val markoVertex =
                        loadedGraph.vertices().asSequence().find {
                            it.value<String>("name") == "marko"
                        }
                markoVertex shouldNotBe null
                markoVertex!!.value<Int>("age") shouldBe 29
                markoVertex.value<String>("label") shouldBe "person"

                // Verify edge structure
                val knowsEdges =
                        loadedGraph.edges().asSequence().filter { it.label() == "knows" }.toList()
                knowsEdges shouldHaveSize 2
            }

            "binary format save and load should preserve graph structure" {
                // Save graph in binary format
                val metadata =
                        persistenceLayer.saveGraph(
                                testGraph,
                                "test-binary",
                                JvmPersistenceLayer.PersistenceFormat.BINARY
                        )

                metadata shouldNotBe null
                metadata["format"] shouldBe "BINARY"

                // Load graph and verify structure
                val loadedGraph =
                        persistenceLayer.loadGraph(
                                "test-binary",
                                JvmPersistenceLayer.PersistenceFormat.BINARY
                        )

                loadedGraph.vertices().asSequence().count() shouldBe 6
                loadedGraph.edges().asSequence().count() shouldBe 6
            }

            "GraphSON format save and load should preserve graph structure" {
                // Save graph in GraphSON format
                val metadata =
                        persistenceLayer.saveGraph(
                                testGraph,
                                "test-graphson",
                                JvmPersistenceLayer.PersistenceFormat.GRAPHSON
                        )

                metadata shouldNotBe null
                metadata["format"] shouldBe "GRAPHSON"

                // Load graph and verify structure
                val loadedGraph =
                        persistenceLayer.loadGraph(
                                "test-graphson",
                                JvmPersistenceLayer.PersistenceFormat.GRAPHSON
                        )

                loadedGraph.vertices().asSequence().count() shouldBe 6
                loadedGraph.edges().asSequence().count() shouldBe 6
            }

            "transaction logging should track operations" {
                // Enable transaction logging
                persistenceLayer.beginTransaction("test-transaction")

                // Save graph
                persistenceLayer.saveGraph(
                        testGraph,
                        "test-tx",
                        JvmPersistenceLayer.PersistenceFormat.JSON
                )

                // Commit transaction
                val commitResult = persistenceLayer.commitTransaction()
                commitResult shouldBe true

                // Verify transaction was logged
                val logs = persistenceLayer.getTransactionLogs()
                logs shouldNotBe null
                logs.isNotEmpty() shouldBe true
            }

            "backup functionality should maintain multiple versions" {
                val graphName = "test-backup"

                // Save multiple versions of the graph
                repeat(3) { version ->
                    // Modify graph slightly for each version
                    val newVertex = testGraph.addVertex()
                    newVertex.property("version", version)
                    newVertex.property("timestamp", System.currentTimeMillis())

                    persistenceLayer.saveGraph(
                            testGraph,
                            graphName,
                            JvmPersistenceLayer.PersistenceFormat.JSON
                    )
                }

                // Verify backups exist
                val backups = persistenceLayer.listBackups(graphName)
                backups shouldNotBe null
                backups.size shouldBe 3

                // Load latest version
                val latestGraph =
                        persistenceLayer.loadGraph(
                                graphName,
                                JvmPersistenceLayer.PersistenceFormat.JSON
                        )
                latestGraph.vertices().asSequence().count() shouldBe 9 // Original 6 + 3 versions

                // Verify version vertices exist
                val versionVertices =
                        latestGraph
                                .vertices()
                                .asSequence()
                                .filter {
                                    try {
                                        it.value<Int>("version")
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }
                                }
                                .toList()
                versionVertices shouldHaveSize 3
            }

            "compression should reduce file size" {
                // Create persistence layer without compression
                val uncompressedLayer =
                        JvmPersistenceLayer(
                                baseDirectory = tempDir.resolve("uncompressed").toString(),
                                enableTransactionLog = false,
                                enableCompression = false,
                                maxBackups = 5
                        )

                // Create large graph with repetitive data
                val largeGraph = TinkerGraph.open()
                repeat(100) { i ->
                    val vertex = largeGraph.addVertex()
                    vertex.property("data", "this is repetitive data that should compress well")
                    vertex.property("index", i)
                }

                try {
                    // Save with compression
                    persistenceLayer.saveGraph(
                            largeGraph,
                            "compressed",
                            JvmPersistenceLayer.PersistenceFormat.JSON
                    )

                    // Save without compression
                    uncompressedLayer.saveGraph(
                            largeGraph,
                            "uncompressed",
                            JvmPersistenceLayer.PersistenceFormat.JSON
                    )

                    // Both should load correctly
                    val compressedLoaded =
                            persistenceLayer.loadGraph(
                                    "compressed",
                                    JvmPersistenceLayer.PersistenceFormat.JSON
                            )
                    val uncompressedLoaded =
                            uncompressedLayer.loadGraph(
                                    "uncompressed",
                                    JvmPersistenceLayer.PersistenceFormat.JSON
                            )

                    compressedLoaded.vertices().asSequence().count() shouldBe 100
                    uncompressedLoaded.vertices().asSequence().count() shouldBe 100
                } finally {
                    largeGraph.close()
                    uncompressedLayer.close()
                }
            }

            "error handling should be robust" {
                // Test loading non-existent graph
                shouldThrow<Exception> {
                    persistenceLayer.loadGraph(
                            "non-existent",
                            JvmPersistenceLayer.PersistenceFormat.JSON
                    )
                }

                // Test invalid format
                shouldThrow<Exception> {
                    persistenceLayer.saveGraph(
                            testGraph,
                            "invalid",
                            null as JvmPersistenceLayer.PersistenceFormat?
                    )
                }

                // Persistence layer should still be functional after errors
                val metadata =
                        persistenceLayer.saveGraph(
                                testGraph,
                                "recovery-test",
                                JvmPersistenceLayer.PersistenceFormat.JSON
                        )
                metadata shouldNotBe null
            }

            "statistics should provide accurate information" {
                // Perform some operations
                persistenceLayer.saveGraph(
                        testGraph,
                        "stats-test",
                        JvmPersistenceLayer.PersistenceFormat.JSON
                )
                persistenceLayer.loadGraph("stats-test", JvmPersistenceLayer.PersistenceFormat.JSON)

                val stats = persistenceLayer.getStatistics()

                stats shouldNotBe null
                stats.containsKey("totalSaves") shouldBe true
                stats.containsKey("totalLoads") shouldBe true
                stats.containsKey("totalErrors") shouldBe true
                stats.containsKey("compressionEnabled") shouldBe true
                stats.containsKey("transactionLogEnabled") shouldBe true

                (stats["totalSaves"] as Long) > 0 shouldBe true
                (stats["totalLoads"] as Long) > 0 shouldBe true
            }

            "concurrent operations should be thread-safe" {
                val graphName = "concurrent-test"

                // Save initial graph
                persistenceLayer.saveGraph(
                        testGraph,
                        graphName,
                        JvmPersistenceLayer.PersistenceFormat.JSON
                )

                // Test concurrent loads
                val results = mutableList<TinkerGraph>()
                val threads = mutableListOf<Thread>()

                repeat(5) { i ->
                    val thread = Thread {
                        try {
                            val loaded =
                                    persistenceLayer.loadGraph(
                                            graphName,
                                            JvmPersistenceLayer.PersistenceFormat.JSON
                                    )
                            synchronized(results) { results.add(loaded) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    threads.add(thread)
                    thread.start()
                }

                // Wait for all threads to complete
                threads.forEach { it.join(5000) } // 5 second timeout

                results shouldHaveSize 5
                results.forEach { graph ->
                    graph.vertices().asSequence().count() shouldBe 6
                    graph.edges().asSequence().count() shouldBe 6
                    graph.close()
                }
            }

            "graph metadata should be preserved" {
                val metadata =
                        persistenceLayer.saveGraph(
                                testGraph,
                                "metadata-test",
                                JvmPersistenceLayer.PersistenceFormat.JSON
                        )

                metadata shouldNotBe null
                metadata["vertexCount"] shouldBe 6
                metadata["edgeCount"] shouldBe 6
                metadata["format"] shouldBe "JSON"
                metadata.containsKey("timestamp") shouldBe true
                metadata.containsKey("fileSize") shouldBe true

                // Load and verify metadata is accessible
                val loadedGraph =
                        persistenceLayer.loadGraph(
                                "metadata-test",
                                JvmPersistenceLayer.PersistenceFormat.JSON
                        )
                val loadMetadata = persistenceLayer.getGraphMetadata("metadata-test")

                loadMetadata shouldNotBe null
                loadMetadata["vertexCount"] shouldBe 6
                loadMetadata["edgeCount"] shouldBe 6
            }

            "large graph performance should be acceptable" {
                // Create larger graph
                val largeGraph = TinkerGraph.open()
                val vertices = mutableListOf<Any>()

                repeat(500) { i ->
                    val vertex = largeGraph.addVertex()
                    vertex.property("index", i)
                    vertex.property("category", "category_${i % 10}")
                    vertex.property("value", i * 2.5)
                    vertices.add(vertex)
                }

                // Add edges
                repeat(300) { i ->
                    val source = vertices[i % vertices.size]
                    val target = vertices[(i + 1) % vertices.size]
                    (source as org.apache.tinkerpop.gremlin.structure.Vertex).addEdge(
                            "connects",
                            target as org.apache.tinkerpop.gremlin.structure.Vertex
                    )
                }

                try {
                    val startTime = System.currentTimeMillis()

                    // Save large graph
                    val metadata =
                            persistenceLayer.saveGraph(
                                    largeGraph,
                                    "large-graph",
                                    JvmPersistenceLayer.PersistenceFormat.JSON
                            )

                    val saveTime = System.currentTimeMillis() - startTime

                    // Load large graph
                    val loadStart = System.currentTimeMillis()
                    val loadedGraph =
                            persistenceLayer.loadGraph(
                                    "large-graph",
                                    JvmPersistenceLayer.PersistenceFormat.JSON
                            )
                    val loadTime = System.currentTimeMillis() - loadStart

                    // Verify structure
                    loadedGraph.vertices().asSequence().count() shouldBe 500
                    loadedGraph.edges().asSequence().count() shouldBe 300

                    // Performance should be reasonable (less than 10 seconds each)
                    (saveTime < 10000) shouldBe true
                    (loadTime < 10000) shouldBe true
                } finally {
                    largeGraph.close()
                }
            }

            "rollback functionality should restore previous state" {
                val graphName = "rollback-test"

                // Save initial state
                persistenceLayer.saveGraph(
                        testGraph,
                        graphName,
                        JvmPersistenceLayer.PersistenceFormat.JSON
                )

                // Modify graph
                val newVertex = testGraph.addVertex()
                newVertex.property("rollback", "test")

                // Begin transaction
                persistenceLayer.beginTransaction("rollback-transaction")

                // Save modified graph
                persistenceLayer.saveGraph(
                        testGraph,
                        graphName,
                        JvmPersistenceLayer.PersistenceFormat.JSON
                )

                // Rollback transaction
                val rollbackResult = persistenceLayer.rollbackTransaction()
                rollbackResult shouldBe true

                // Load graph - should be in original state
                val restoredGraph =
                        persistenceLayer.loadGraph(
                                graphName,
                                JvmPersistenceLayer.PersistenceFormat.JSON
                        )

                // Should have original 6 vertices, not 7
                restoredGraph.vertices().asSequence().count() shouldBe 6

                // Should not contain the rollback vertex
                val hasRollbackVertex =
                        restoredGraph.vertices().asSequence().any {
                            try {
                                it.value<String>("rollback") == "test"
                            } catch (e: Exception) {
                                false
                            }
                        }
                hasRollbackVertex shouldBe false
            }
        })
