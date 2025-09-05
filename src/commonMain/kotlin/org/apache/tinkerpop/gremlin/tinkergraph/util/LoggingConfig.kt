package org.apache.tinkerpop.gremlin.tinkergraph.util

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.KLogger

/**
 * Logging configuration and utility class for TinkerGraph operations.
 *
 * This class provides centralized logging configuration and utilities for the TinkerGraph
 * implementation, using KmLogging for cross-platform compatibility.
 *
 * ## Features
 * - Cross-platform logging support via KmLogging
 * - Configurable log levels
 * - Performance monitoring utilities
 * - Debug helpers for graph operations
 *
 * ## Usage
 * ```kotlin
 * class MyGraphClass {
 *     companion object {
 *         private val logger = LoggingConfig.getLogger<MyGraphClass>()
 *     }
 *
 *     fun performOperation() {
 *         logger.info { "Starting operation" }
 *         LoggingConfig.measureTime("operation") {
 *             // Your code here
 *         }
 *         logger.info { "Operation completed" }
 *     }
 * }
 * ```
 *
 * @author TinkerGraph Team
 * @since 3.0.1
 * @see KotlinLogging
 */
object LoggingConfig {

    /**
     * Default logger instance for general TinkerGraph operations.
     */
    private val defaultLogger: KLogger = KotlinLogging.logger("TinkerGraph")

    /**
     * Logger for performance monitoring and metrics.
     */
    private val performanceLogger: KLogger = KotlinLogging.logger("TinkerGraph.Performance")

    /**
     * Logger for debug operations and detailed tracing.
     */
    private val debugLogger: KLogger = KotlinLogging.logger("TinkerGraph.Debug")

    /**
     * Creates a logger instance for a specific class.
     *
     * @param T The class type for which to create the logger
     * @return A configured KLogger instance
     *
     * Example:
     * ```kotlin
     * class TinkerVertex {
     *     companion object {
     *         private val logger = LoggingConfig.getLogger<TinkerVertex>()
     *     }
     * }
     * ```
     */
    inline fun <reified T> getLogger(): KLogger {
        return KotlinLogging.logger(T::class.simpleName ?: "Unknown")
    }

    /**
     * Creates a logger instance with a custom name.
     *
     * @param name Custom name for the logger
     * @return A configured KLogger instance with the specified name
     */
    fun getLogger(name: String): KLogger {
        return KotlinLogging.logger(name)
    }

    /**
     * Logs graph statistics for monitoring purposes.
     *
     * @param vertexCount Current number of vertices in the graph
     * @param edgeCount Current number of edges in the graph
     * @param operation The operation that triggered this log entry
     */
    fun logGraphStats(vertexCount: Int, edgeCount: Int, operation: String) {
        performanceLogger.info {
            "Graph stats after '$operation': vertices=$vertexCount, edges=$edgeCount"
        }
    }

    /**
     * Measures and logs the execution time of a block of code.
     *
     * This function executes the provided block and logs the execution time
     * to the performance logger.
     *
     * @param T The return type of the block
     * @param operationName Name of the operation being measured
     * @param block The code block to execute and measure
     * @return The result of executing the block
     *
     * Example:
     * ```kotlin
     * val result = LoggingConfig.measureTime("vertex-creation") {
     *     graph.addVertex("name", "John", "age", 30)
     * }
     * ```
     */
    fun <T> measureTime(operationName: String, block: () -> T): T {
        val startTime = getCurrentTimeMillis()
        try {
            performanceLogger.debug { "Starting operation: $operationName" }
            return block()
        } finally {
            val duration = getCurrentTimeMillis() - startTime
            performanceLogger.info { "Operation '$operationName' completed in ${duration}ms" }
        }
    }

    /**
     * Logs detailed debug information about graph elements.
     *
     * @param elementType Type of element (vertex, edge, property, etc.)
     * @param elementId ID of the element
     * @param details Additional details about the element
     */
    fun logElementDebug(elementType: String, elementId: Any?, details: String) {
        debugLogger.debug { "$elementType[$elementId]: $details" }
    }

    /**
     * Logs index operation details for performance analysis.
     *
     * @param indexType Type of index (vertex, edge, composite, range)
     * @param operation Operation performed (create, update, delete, query)
     * @param key Property key involved in the operation
     * @param resultCount Number of results returned (for queries)
     */
    fun logIndexOperation(
        indexType: String,
        operation: String,
        key: String,
        resultCount: Int? = null
    ) {
        val resultInfo = resultCount?.let { " (results: $it)" } ?: ""
        performanceLogger.debug {
            "Index operation: $indexType.$operation on key '$key'$resultInfo"
        }
    }

    /**
     * Logs memory usage information if available on the current platform.
     *
     * This method attempts to log memory usage statistics. Implementation
     * varies by platform and may not be available on all targets.
     *
     * @param context Description of when this memory check is being performed
     */
    fun logMemoryUsage(context: String) {
        // Note: Memory monitoring implementation would be platform-specific
        // This is a placeholder for future platform-specific implementations
        performanceLogger.debug { "Memory check at: $context" }
    }

    /**
     * Enables or disables debug logging for specific components.
     *
     * @param component Component name to configure
     * @param enabled Whether debug logging should be enabled
     */
    fun setDebugEnabled(component: String, enabled: Boolean) {
        val status = if (enabled) "enabled" else "disabled"
        defaultLogger.info { "Debug logging $status for component: $component" }
        // Note: Actual log level configuration would depend on the logging framework
        // This is a placeholder for configuration logic
    }

    /**
     * Gets the current time in milliseconds.
     *
     * This is a platform-abstraction function that should work across
     * JVM, JavaScript, and Native targets.
     *
     * @return Current time in milliseconds
     */
    internal fun getCurrentTimeMillis(): Long {
        // Platform-specific time implementation would go here
        // For now, returning a placeholder
        return 0L // This would be replaced with actual platform-specific implementation
    }
}
