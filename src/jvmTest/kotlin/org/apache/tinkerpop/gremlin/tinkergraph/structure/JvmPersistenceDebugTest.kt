package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Debug test for JVM persistence layer to isolate issues.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JvmPersistenceDebugTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var persistenceLayer: JvmPersistenceLayer

    @BeforeEach
    fun setUp() {
        val testDirectory = tempDir.resolve("debug-persistence").toString()
        persistenceLayer = JvmPersistenceLayer(
            baseDirectory = testDirectory,
            enableTransactionLog = false,
            enableCompression = false,
            maxBackups = 5
        )
    }

    @Test
    fun `debug simple graph save and load`() {
        println("Starting debug test...")

        // Create simple test graph
        val graph = TinkerGraph.open()
        val vertex = graph.addVertex("id", "1", "name", "test")

        println("Created graph with ${graph.vertices().asSequence().count()} vertices")

        try {
            // Save graph
            println("Attempting to save graph...")
            val metadata = persistenceLayer.saveGraph(graph, "debug-test", JvmPersistenceLayer.PersistenceFormat.JSON)
            println("Save successful! Metadata: $metadata")

            // Load graph
            println("Attempting to load graph...")
            val loadedGraph = persistenceLayer.loadGraph("debug-test", JvmPersistenceLayer.PersistenceFormat.JSON)
            println("Load successful! Loaded graph has ${loadedGraph.vertices().asSequence().count()} vertices")

            // Basic verification
            val loadedVertex = loadedGraph.vertices().next()
            println("Loaded vertex ID: ${loadedVertex.id()}")
            println("Loaded vertex name: ${loadedVertex.property<String>("name").value()}")

            assertEquals(1, loadedGraph.vertices().asSequence().count())

        } catch (e: Exception) {
            println("Error occurred: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun `debug JSON conversion`() {
        val graph = TinkerGraph.open()
        graph.addVertex("id", "1", "name", "Alice", "age", 30)

        println("Testing graph conversion...")

        // Test the conversion methods directly
        val graphData = convertGraphToMap(graph)
        println("Graph data: $graphData")

        val jsonString = convertMapToJsonString(graphData)
        println("JSON string: $jsonString")

        val parsedData = parseJsonString(jsonString)
        println("Parsed data: $parsedData")

        assertTrue(parsedData.containsKey("vertices"))
        assertTrue(parsedData.containsKey("edges"))
    }

    private fun convertGraphToMap(graph: TinkerGraph): Map<String, Any> {
        val vertices = graph.vertices().asSequence().map { vertex ->
            mapOf(
                "id" to (vertex.id() ?: ""),
                "label" to vertex.label(),
                "properties" to vertex.properties<Any>().asSequence().associate {
                    it.key() to (it.value() ?: "")
                }
            )
        }.toList()

        val edges = graph.edges().asSequence().map { edge ->
            mapOf(
                "id" to (edge.id() ?: ""),
                "label" to edge.label(),
                "outVertexId" to (edge.outVertex().id() ?: ""),
                "inVertexId" to (edge.inVertex().id() ?: ""),
                "properties" to edge.properties<Any>().asSequence().associate {
                    it.key() to (it.value() ?: "")
                }
            )
        }.toList()

        return mapOf(
            "vertices" to vertices,
            "edges" to edges,
            "metadata" to mapOf(
                "vertexCount" to vertices.size,
                "edgeCount" to edges.size
            )
        )
    }

    private fun convertMapToJsonString(data: Map<String, Any>): String {
        return buildString {
            append("{")
            data.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) append(",")
                append("\"$key\":")
                append(convertValueToJsonString(value))
            }
            append("}")
        }
    }

    private fun convertValueToJsonString(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"${value.replace("\"", "\\\"")}\""
            is Number -> value.toString()
            is Boolean -> value.toString()
            is List<*> -> {
                "[" + value.joinToString(",") { convertValueToJsonString(it) } + "]"
            }
            is Map<*, *> -> {
                "{" + value.entries.joinToString(",") { (k, v) ->
                    "\"$k\":" + convertValueToJsonString(v)
                } + "}"
            }
            else -> "\"$value\""
        }
    }

    private fun parseJsonString(json: String): Map<String, Any> {
        // Very basic JSON parser for testing
        val content = json.trim().removePrefix("{").removeSuffix("}")
        val result = mutableMapOf<String, Any>()

        if (content.isEmpty()) return result

        // Simple parsing for test data
        val parts = content.split(",\"")
        parts.forEach { part ->
            val cleaned = part.removePrefix("\"")
            val colonIndex = cleaned.indexOf("\":")
            if (colonIndex > 0) {
                val key = cleaned.substring(0, colonIndex)
                val valueStr = cleaned.substring(colonIndex + 2)

                result[key] = when {
                    valueStr == "null" -> ""
                    valueStr.startsWith("\"") && valueStr.endsWith("\"") ->
                        valueStr.removeSurrounding("\"")
                    valueStr.startsWith("[") && valueStr.endsWith("]") ->
                        emptyList<Any>() // Simplified for test
                    valueStr.startsWith("{") ->
                        emptyMap<String, Any>() // Simplified for test
                    valueStr.toIntOrNull() != null -> valueStr.toInt()
                    else -> valueStr
                } as Any
            }
        }

        return result
    }
}
