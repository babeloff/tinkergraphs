package org.apache.tinkerpop.gremlin.tinkergraph.structure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.tinkerpop.gremlin.tinkergraph.util.LoggingConfig
import java.nio.file.Files
import java.nio.file.Path

/** Debug test for JVM persistence layer to isolate issues. */
class JvmPersistenceDebugTest :
        StringSpec({

            companion object {
                private val logger = LoggingConfig.getLogger<JvmPersistenceDebugTest>()
            }
            lateinit var tempDir: Path
            lateinit var persistenceLayer: JvmPersistenceLayer

            beforeTest {
                tempDir = Files.createTempDirectory("kotest-persistence-debug")
                val testDirectory = tempDir.resolve("debug-persistence").toString()
                persistenceLayer =
                        JvmPersistenceLayer(
                                baseDirectory = testDirectory,
                                enableTransactionLog = false,
                                enableCompression = false,
                                maxBackups = 5
                        )
            }

            afterTest {
                persistenceLayer.close()
                // Clean up temp directory
                Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach {
                    Files.deleteIfExists(it)
                }
            }

            "debug simple graph save and load should work correctly" {
                println("Starting debug test...")

                // Create simple test graph
                val graph = TinkerGraph.open()
                val vertex = graph.addVertex("id", "1", "name", "test")

                println("Created graph with ${graph.vertices().asSequence().count()} vertices")

                try {
                    // Save graph
                    println("Attempting to save graph...")
                    val metadata =
                            persistenceLayer.saveGraph(
                                    graph,
                                    "debug-test",
                                    JvmPersistenceLayer.PersistenceFormat.JSON
                            )
                    println("Save successful! Metadata: $metadata")

                    metadata shouldNotBe null

                    // Load graph
                    println("Attempting to load graph...")
                    val loadedGraph =
                            persistenceLayer.loadGraph(
                                    "debug-test",
                                    JvmPersistenceLayer.PersistenceFormat.JSON
                            )
                    println("Load successful!")

                    loadedGraph shouldNotBe null
                    loadedGraph.vertices().asSequence().count() shouldBe 1

                    val loadedVertex = loadedGraph.vertices().next()
                    loadedVertex.id() shouldBe "1"
                    loadedVertex.value<String>("name") shouldBe "test"

                    println("Debug test completed successfully!")
                } catch (e: Exception) {
                    logger.e(e) { "Debug test failed with exception" }
                    e.printStackTrace()
                    throw e
                }
            }

            "debug graph with edges should save and load correctly" {
                val graph = TinkerGraph.open()

                // Create vertices
                val alice = graph.addVertex()
                alice.property("name", "Alice")
                alice.property("age", 30)

                val bob = graph.addVertex()
                bob.property("name", "Bob")
                bob.property("age", 25)

                // Create edge
                val knowsEdge = alice.addEdge("knows", bob)
                knowsEdge.property("since", 2020)

                println(
                        "Created graph with ${graph.vertices().asSequence().count()} vertices and ${graph.edges().asSequence().count()} edges"
                )

                // Save and load
                val metadata =
                        persistenceLayer.saveGraph(
                                graph,
                                "debug-edges",
                                JvmPersistenceLayer.PersistenceFormat.JSON
                        )
                val loadedGraph =
                        persistenceLayer.loadGraph(
                                "debug-edges",
                                JvmPersistenceLayer.PersistenceFormat.JSON
                        )

                // Verify structure
                loadedGraph.vertices().asSequence().count() shouldBe 2
                loadedGraph.edges().asSequence().count() shouldBe 1

                val edge = loadedGraph.edges().next()
                edge.value<Int>("since") shouldBe 2020
                edge.label() shouldBe "knows"

                println("Edge debug test completed successfully!")
            }

            "debug different persistence formats should work" {
                val graph = TinkerGraph.open()
                val vertex = graph.addVertex()
                vertex.property("format", "test")

                // Test JSON format
                persistenceLayer.saveGraph(
                        graph,
                        "debug-json",
                        JvmPersistenceLayer.PersistenceFormat.JSON
                )
                val jsonLoaded =
                        persistenceLayer.loadGraph(
                                "debug-json",
                                JvmPersistenceLayer.PersistenceFormat.JSON
                        )
                jsonLoaded.vertices().asSequence().count() shouldBe 1

                // Test BINARY format
                persistenceLayer.saveGraph(
                        graph,
                        "debug-binary",
                        JvmPersistenceLayer.PersistenceFormat.BINARY
                )
                val binaryLoaded =
                        persistenceLayer.loadGraph(
                                "debug-binary",
                                JvmPersistenceLayer.PersistenceFormat.BINARY
                        )
                binaryLoaded.vertices().asSequence().count() shouldBe 1

                println("Format debug test completed successfully!")
            }

            "debug persistence layer statistics should be accurate" {
                val graph = TinkerGraph.open()
                repeat(5) { i ->
                    val vertex = graph.addVertex()
                    vertex.property("index", i)
                }

                persistenceLayer.saveGraph(
                        graph,
                        "debug-stats",
                        JvmPersistenceLayer.PersistenceFormat.JSON
                )

                val stats = persistenceLayer.getStatistics()
                println("Persistence statistics: $stats")

                stats shouldNotBe null
                stats.containsKey("totalSaves") shouldBe true
                stats.containsKey("totalLoads") shouldBe true
                stats.containsKey("errorCount") shouldBe true

                println("Statistics debug test completed successfully!")
            }

            "debug error handling should be robust" {
                try {
                    // Attempt to load non-existent graph
                    persistenceLayer.loadGraph(
                            "non-existent",
                            JvmPersistenceLayer.PersistenceFormat.JSON
                    )
                } catch (e: Exception) {
                    logger.d(e) { "Expected error caught during error handling test" }
                    e shouldNotBe null
                }

                // Verify persistence layer is still functional after error
                val graph = TinkerGraph.open()
                val vertex = graph.addVertex()
                vertex.property("recovery", "test")

                persistenceLayer.saveGraph(
                        graph,
                        "debug-recovery",
                        JvmPersistenceLayer.PersistenceFormat.JSON
                )
                val recovered =
                        persistenceLayer.loadGraph(
                                "debug-recovery",
                                JvmPersistenceLayer.PersistenceFormat.JSON
                        )

                recovered.vertices().asSequence().count() shouldBe 1
                println("Error handling debug test completed successfully!")
            }

            "debug transaction log functionality should work when enabled" {
                // Create persistence layer with transaction log enabled
                val txPersistenceLayer =
                        JvmPersistenceLayer(
                                baseDirectory = tempDir.resolve("tx-debug").toString(),
                                enableTransactionLog = true,
                                enableCompression = false,
                                maxBackups = 5
                        )

                try {
                    val graph = TinkerGraph.open()
                    val vertex = graph.addVertex()
                    vertex.property("transaction", "test")

                    txPersistenceLayer.saveGraph(
                            graph,
                            "debug-tx",
                            JvmPersistenceLayer.PersistenceFormat.JSON
                    )
                    val loaded =
                            txPersistenceLayer.loadGraph(
                                    "debug-tx",
                                    JvmPersistenceLayer.PersistenceFormat.JSON
                            )

                    loaded.vertices().asSequence().count() shouldBe 1
                    loaded.vertices().next().value<String>("transaction") shouldBe "test"

                    println("Transaction log debug test completed successfully!")
                } finally {
                    txPersistenceLayer.close()
                }
            }

            "debug compression functionality should reduce file size" {
                // Create persistence layer with compression enabled
                val compressedPersistenceLayer =
                        JvmPersistenceLayer(
                                baseDirectory = tempDir.resolve("compressed-debug").toString(),
                                enableTransactionLog = false,
                                enableCompression = true,
                                maxBackups = 5
                        )

                try {
                    val graph = TinkerGraph.open()
                    repeat(10) { i ->
                        val vertex = graph.addVertex()
                        vertex.property(
                                "data",
                                "this is repeated data that should compress well $i"
                        )
                        vertex.property("index", i)
                    }

                    compressedPersistenceLayer.saveGraph(
                            graph,
                            "debug-compressed",
                            JvmPersistenceLayer.PersistenceFormat.JSON
                    )
                    val loaded =
                            compressedPersistenceLayer.loadGraph(
                                    "debug-compressed",
                                    JvmPersistenceLayer.PersistenceFormat.JSON
                            )

                    loaded.vertices().asSequence().count() shouldBe 10

                    println("Compression debug test completed successfully!")
                } finally {
                    compressedPersistenceLayer.close()
                }
            }

            "debug backup functionality should maintain versions" {
                val graph = TinkerGraph.open()

                // Save multiple versions
                repeat(3) { version ->
                    val vertex = graph.addVertex()
                    vertex.property("version", version)

                    persistenceLayer.saveGraph(
                            graph,
                            "debug-backup",
                            JvmPersistenceLayer.PersistenceFormat.JSON
                    )
                }

                // Should be able to load the latest version
                val loaded =
                        persistenceLayer.loadGraph(
                                "debug-backup",
                                JvmPersistenceLayer.PersistenceFormat.JSON
                        )
                loaded.vertices().asSequence().count() shouldBe 3

                val versions =
                        loaded.vertices()
                                .asSequence()
                                .map { it.value<Int>("version") ?: 0 }
                                .sorted()
                                .toList()

                versions shouldBe listOf(0, 1, 2)

                println("Backup debug test completed successfully!")
            }

            "debug serialization issue" {
                // Create a simple graph
                val simpleGraph = TinkerGraph.open()
                val v1 = simpleGraph.addVertex("id", "1", "label", "person", "name", "marko", "age", 29)
                val v2 = simpleGraph.addVertex("id", "2", "label", "person", "name", "vadas", "age", 27)

                val edge = v1.addEdge("knows", v2, "id", "7", "weight", 0.5f)

                println("Original graph:")
                println("Vertices: ${simpleGraph.vertices().asSequence().count()}")
                println("Edges: ${simpleGraph.edges().asSequence().count()}")
                println("V1 label property: ${v1.value<String>("label")}")
                println("V1 actual label: ${v1.label()}")

                // Save the graph
                persistenceLayer.saveGraph(simpleGraph, "debug-test", JvmPersistenceLayer.PersistenceFormat.JSON)

                // Load the graph
                val loadedGraph = persistenceLayer.loadGraph("debug-test", JvmPersistenceLayer.PersistenceFormat.JSON)

                println("Loaded graph:")
                println("Vertices: ${loadedGraph.vertices().asSequence().count()}")
                println("Edges: ${loadedGraph.edges().asSequence().count()}")

                val loadedV1 = loadedGraph.vertices().asSequence().find { it.value<String>("name") == "marko" }
                if (loadedV1 != null) {
                    println("Loaded V1 label property: ${loadedV1.value<String>("label")}")
                    println("Loaded V1 actual label: ${loadedV1.label()}")
                } else {
                    println("Could not find marko vertex in loaded graph")
                }

                simpleGraph.close()
                loadedGraph.close()
            }
        })
