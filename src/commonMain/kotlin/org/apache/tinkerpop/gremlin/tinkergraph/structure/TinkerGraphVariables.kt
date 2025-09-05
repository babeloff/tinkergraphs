package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.Graph

/**
 * TinkerGraphVariables provides a key-value store for graph-level metadata.
 * Variables are persistent across graph operations and can store arbitrary data
 * associated with the graph instance.
 */
class TinkerGraphVariables : Graph.Variables {

    /**
     * Internal storage for graph variables.
     */
    private val variables: MutableMap<String, Any?> = mutableMapOf()

    override fun keys(): Set<String> {
        return variables.keys.toSet()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> get(key: String): R? {
        return variables[key] as? R
    }

    override fun set(key: String, value: Any?) {
        validateKey(key)
        validateValue(value)
        variables[key] = value
    }

    override fun remove(key: String) {
        variables.remove(key)
    }

    override fun asMap(): Map<String, Any?> {
        return variables.toMap()
    }

    /**
     * Check if a variable exists.
     *
     * @param key the variable key
     * @return true if the variable exists
     */
    fun containsKey(key: String): Boolean {
        return variables.containsKey(key)
    }

    /**
     * Get a variable with a default value if it doesn't exist.
     *
     * @param key the variable key
     * @param defaultValue the default value to return if key doesn't exist
     * @return the variable value or default value
     */
    @Suppress("UNCHECKED_CAST")
    fun <R> getOrDefault(key: String, defaultValue: R): R {
        return (variables[key] as? R) ?: defaultValue
    }

    /**
     * Set a variable only if it doesn't already exist.
     *
     * @param key the variable key
     * @param value the variable value
     * @return true if the variable was set, false if it already existed
     */
    fun setIfAbsent(key: String, value: Any?): Boolean {
        if (containsKey(key)) {
            return false
        }
        set(key, value)
        return true
    }

    /**
     * Replace a variable value only if it currently exists.
     *
     * @param key the variable key
     * @param value the new variable value
     * @return the previous value, or null if the key didn't exist
     */
    fun replace(key: String, value: Any?): Any? {
        return if (containsKey(key)) {
            val oldValue = variables[key]
            set(key, value)
            oldValue
        } else {
            null
        }
    }

    /**
     * Replace a variable value only if it currently has the expected value.
     *
     * @param key the variable key
     * @param expectedValue the expected current value
     * @param newValue the new variable value
     * @return true if the replacement occurred
     */
    fun replace(key: String, expectedValue: Any?, newValue: Any?): Boolean {
        val currentValue = variables[key]
        return if (currentValue == expectedValue) {
            set(key, newValue)
            true
        } else {
            false
        }
    }

    /**
     * Get the number of variables.
     *
     * @return the number of stored variables
     */
    fun size(): Int {
        return variables.size
    }

    /**
     * Check if there are no variables.
     *
     * @return true if no variables are stored
     */
    fun isEmpty(): Boolean {
        return variables.isEmpty()
    }

    /**
     * Clear all variables.
     */
    fun clear() {
        variables.clear()
    }

    /**
     * Get all variable values.
     *
     * @return collection of all values
     */
    fun values(): Collection<Any?> {
        return variables.values
    }

    /**
     * Merge another variables map into this one.
     *
     * @param other the other variables to merge
     * @param overwrite whether to overwrite existing keys
     */
    fun merge(other: Map<String, Any?>, overwrite: Boolean = true) {
        other.forEach { (key, value) ->
            if (overwrite || !containsKey(key)) {
                set(key, value)
            }
        }
    }

    /**
     * Get variables with keys matching a prefix.
     *
     * @param prefix the key prefix to match
     * @return map of matching variables
     */
    fun getByPrefix(prefix: String): Map<String, Any?> {
        return variables.filterKeys { it.startsWith(prefix) }
    }

    /**
     * Remove variables with keys matching a prefix.
     *
     * @param prefix the key prefix to match
     * @return number of variables removed
     */
    fun removeByPrefix(prefix: String): Int {
        val keysToRemove = variables.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { variables.remove(it) }
        return keysToRemove.size
    }

    /**
     * Create a copy of these variables.
     *
     * @return a new TinkerGraphVariables instance with the same data
     */
    fun copy(): TinkerGraphVariables {
        val copy = TinkerGraphVariables()
        copy.variables.putAll(this.variables)
        return copy
    }

    /**
     * Validate a variable key.
     *
     * @param key the key to validate
     * @throws IllegalArgumentException if the key is invalid
     */
    private fun validateKey(key: String) {
        if (key.isBlank()) {
            throw IllegalArgumentException("Variable key cannot be blank")
        }
    }

    /**
     * Validate a variable value.
     * This implementation allows any value including null.
     *
     * @param value the value to validate
     */
    private fun validateValue(value: Any?) {
        // TinkerGraph allows any serializable value
        // Additional validation could be added here based on requirements
    }

    /**
     * Get a string representation of the variables.
     */
    override fun toString(): String {
        return "TinkerGraphVariables[size=${variables.size}]"
    }

    /**
     * Check equality with another variables instance.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TinkerGraphVariables) return false
        return variables == other.variables
    }

    /**
     * Get hash code based on the stored variables.
     */
    override fun hashCode(): Int {
        return variables.hashCode()
    }
}
