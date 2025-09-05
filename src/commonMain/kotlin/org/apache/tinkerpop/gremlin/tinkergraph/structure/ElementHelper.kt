package org.apache.tinkerpop.gremlin.tinkergraph.structure

import org.apache.tinkerpop.gremlin.structure.*

/**
 * Utility class for common element operations and property validation.
 * This mirrors the ElementHelper from the Java TinkerPop implementation.
 */
object ElementHelper {

    /**
     * Reserved property keys that cannot be used as user properties.
     */
    private val RESERVED_KEYS = setOf("id", "label")

    /**
     * Convert varargs key-value pairs to a Map.
     * @param keyValues array of alternating keys and values
     * @return Map of key-value pairs
     * @throws IllegalArgumentException if array length is odd or contains invalid keys/values
     */
    fun asMap(keyValues: Array<out Any?>): Map<String, Any?> {
        if (keyValues.size % 2 != 0) {
            throw IllegalArgumentException("Key-value array must have even length")
        }

        val map = mutableMapOf<String, Any?>()
        var i = 0
        while (i < keyValues.size) {
            val key = keyValues[i]
            val value = keyValues[i + 1]

            if (key !is String) {
                throw IllegalArgumentException("Property key must be a String, got: ${key?.let { it::class.simpleName }}")
            }

            if (key.isBlank()) {
                throw Element.Exceptions.propertyKeyCanNotBeEmpty()
            }

            map[key] = value
            i += 2
        }

        return map
    }

    /**
     * Validate a property key-value array for legal property names and values.
     * @param keyValues array of alternating keys and values
     * @throws IllegalArgumentException if validation fails
     */
    fun validatePropertyKeyValueArray(keyValues: Array<out Any?>) {
        if (keyValues.size % 2 != 0) {
            throw IllegalArgumentException("Key-value array must have even length")
        }

        var i = 0
        while (i < keyValues.size) {
            val key = keyValues[i]
            val value = keyValues[i + 1]

            validatePropertyKey(key)
            validatePropertyValue(value)

            i += 2
        }
    }

    /**
     * Extract the ID value from a property array.
     * @param keyValues array of alternating keys and values
     * @return the ID value or null if not found
     */
    fun getIdValue(keyValues: Array<out Any?>): Any? {
        return getValue("id", keyValues)
    }

    /**
     * Extract the ID value from a property map.
     * @param properties map of properties
     * @return the ID value or null if not found
     */
    fun getIdValue(properties: Map<String, Any?>): Any? {
        return properties["id"]
    }

    /**
     * Extract the label value from a property array.
     * @param keyValues array of alternating keys and values
     * @return the label value or null if not found
     */
    fun getLabelValue(keyValues: Array<out Any?>): String? {
        return getValue("label", keyValues) as? String
    }

    /**
     * Extract the label value from a property map.
     * @param properties map of properties
     * @return the label value or null if not found
     */
    fun getLabelValue(properties: Map<String, Any?>): String? {
        return properties["label"] as? String
    }

    /**
     * Get a specific value from a key-value array.
     * @param searchKey the key to search for
     * @param keyValues array of alternating keys and values
     * @return the value associated with the key or null if not found
     */
    fun getValue(searchKey: String, keyValues: Array<out Any?>): Any? {
        var i = 0
        while (i < keyValues.size) {
            val key = keyValues[i]
            if (key == searchKey) {
                return if (i + 1 < keyValues.size) keyValues[i + 1] else null
            }
            i += 2
        }
        return null
    }

    /**
     * Attach properties from a key-value array to an element.
     * @param element the element to attach properties to
     * @param keyValues array of alternating keys and values
     */
    fun attachProperties(element: Element, keyValues: Array<out Any?>) {
        attachProperties(element, asMap(keyValues))
    }

    /**
     * Attach properties from a map to an element.
     * @param element the element to attach properties to
     * @param properties map of properties to attach
     */
    fun attachProperties(element: Element, properties: Map<String, Any?>) {
        properties.filterKeys { !isReservedKey(it) }
            .forEach { (key, value) ->
                element.property(key, value)
            }
    }

    /**
     * Attach properties from a key-value array to a vertex property.
     * @param vertexProperty the vertex property to attach properties to
     * @param keyValues array of alternating keys and values
     */
    fun attachProperties(vertexProperty: VertexProperty<*>, keyValues: Array<out Any?>) {
        attachProperties(vertexProperty, asMap(keyValues))
    }

    /**
     * Attach properties from a map to a vertex property.
     * @param vertexProperty the vertex property to attach properties to
     * @param properties map of properties to attach
     */
    fun attachProperties(vertexProperty: VertexProperty<*>, properties: Map<String, Any?>) {
        properties.filterKeys { !isReservedKey(it) }
            .forEach { (key, value) ->
                vertexProperty.property(key, value)
            }
    }

    /**
     * Remove reserved keys (id, label) from a property array.
     * @param keyValues original key-value array
     * @return new array without reserved keys
     */
    fun removeReservedKeys(keyValues: Array<out Any?>): Array<Any?> {
        val filtered = mutableListOf<Any?>()
        var i = 0
        while (i < keyValues.size) {
            val key = keyValues[i]
            if (key is String && !isReservedKey(key)) {
                filtered.add(key)
                if (i + 1 < keyValues.size) {
                    filtered.add(keyValues[i + 1])
                }
            }
            i += 2
        }
        return filtered.toTypedArray()
    }

    /**
     * Remove reserved keys (id, label) from a property map.
     * @param properties original property map
     * @return new map without reserved keys
     */
    fun removeReservedKeys(properties: Map<String, Any?>): Map<String, Any?> {
        return properties.filterKeys { !isReservedKey(it) }
    }

    /**
     * Check if a key is reserved (id or label).
     * @param key the key to check
     * @return true if the key is reserved
     */
    fun isReservedKey(key: String): Boolean {
        return key in RESERVED_KEYS
    }

    /**
     * Validate a property key.
     * @param key the key to validate
     * @throws IllegalArgumentException if the key is invalid
     */
    fun validatePropertyKey(key: Any?) {
        if (key == null) {
            throw Element.Exceptions.propertyKeyCanNotBeNull()
        }

        if (key !is String) {
            throw IllegalArgumentException("Property key must be a String, got: ${key::class.simpleName}")
        }

        if (key.isBlank()) {
            throw Element.Exceptions.propertyKeyCanNotBeEmpty()
        }
    }

    /**
     * Validate a property value.
     * @param value the property value to validate
     * @throws IllegalArgumentException if the value is invalid
     */
    fun validatePropertyValue(value: Any?) {
        // Most graph databases don't allow null values by default
        // This can be overridden by graph configuration
        if (value == null) {
            throw IllegalArgumentException("Property value cannot be null")
        }
    }

    /**
     * Validate a property key-value pair.
     * @param key the property key
     * @param value the property value
     * @throws IllegalArgumentException if the key or value is invalid
     */
    fun validateProperty(key: String, value: Any?) {
        validatePropertyKey(key)
        validatePropertyValue(value)
    }

    /**
     * Validate a property key-value pair with graph context.
     * @param key the property key
     * @param value the property value
     * @param allowNullValues whether null values are allowed
     * @throws IllegalArgumentException if the key or value is invalid
     */
    fun validateProperty(key: String, value: Any?, allowNullValues: Boolean) {
        validatePropertyKey(key)
        if (value == null && !allowNullValues) {
            throw IllegalArgumentException("Property value cannot be null")
        }
    }

    /**
     * Check if two property arrays are equal.
     * @param keyValues1 first property array
     * @param keyValues2 second property array
     * @return true if the arrays represent the same properties
     */
    fun areEqual(keyValues1: Array<out Any?>, keyValues2: Array<out Any?>): Boolean {
        val map1 = asMap(keyValues1)
        val map2 = asMap(keyValues2)
        return map1 == map2
    }

    /**
     * Check if a property map contains all the specified properties.
     * @param properties the property map to check
     * @param requiredProperties the required properties as key-value array
     * @return true if all required properties are present with matching values
     */
    fun hasProperties(properties: Map<String, Any?>, requiredProperties: Array<out Any?>): Boolean {
        val requiredMap = asMap(requiredProperties)
        return requiredMap.all { (key, value) ->
            properties[key] == value
        }
    }

    /**
     * Merge two property maps, with the second map taking precedence.
     * @param base the base property map
     * @param override the override property map
     * @return merged property map
     */
    fun mergeProperties(base: Map<String, Any?>, override: Map<String, Any?>): Map<String, Any?> {
        return base + override
    }

    /**
     * Convert a property map to a key-value array.
     * @param properties the property map
     * @return array of alternating keys and values
     */
    fun mapToKeyValues(properties: Map<String, Any?>): Array<Any?> {
        val result = mutableListOf<Any?>()
        properties.forEach { (key, value) ->
            result.add(key)
            result.add(value)
        }
        return result.toTypedArray()
    }
}
