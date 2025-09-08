package org.apache.tinkerpop.gremlin.tinkergraph.io.graphson

/**
 * GraphSON v3.0 type constants and core data structures.
 *
 * This implements the Apache TinkerPop GraphSON v3.0 specification for
 * serializing graph data with proper type preservation.
 *
 * Reference: https://tinkerpop.apache.org/docs/current/dev/io/#graphson-3d0
 */
object GraphSONTypes {

    // GraphSON format version
    const val VERSION = "3.0"

    // Type markers
    const val TYPE_KEY = "@type"
    const val VALUE_KEY = "@value"

    // Core data types
    const val TYPE_INT32 = "g:Int32"
    const val TYPE_INT64 = "g:Int64"
    const val TYPE_FLOAT = "g:Float"
    const val TYPE_DOUBLE = "g:Double"
    const val TYPE_BOOLEAN = "g:Boolean"
    const val TYPE_STRING = "g:String"
    const val TYPE_UUID = "g:UUID"
    const val TYPE_DATE = "g:Date"
    const val TYPE_TIMESTAMP = "g:Timestamp"

    // Collection types
    const val TYPE_LIST = "g:List"
    const val TYPE_SET = "g:Set"
    const val TYPE_MAP = "g:Map"

    // Graph structure types
    const val TYPE_VERTEX = "g:Vertex"
    const val TYPE_EDGE = "g:Edge"
    const val TYPE_VERTEX_PROPERTY = "g:VertexProperty"
    const val TYPE_PROPERTY = "g:Property"
    const val TYPE_PATH = "g:Path"
    const val TYPE_TRAVERSER = "g:Traverser"

    // Graph metadata types
    const val TYPE_GRAPH = "g:Graph"
    const val TYPE_DIRECTION = "g:Direction"
    const val TYPE_CARDINALITY = "g:Cardinality"
    const val TYPE_T = "g:T"

    // Special value types
    const val TYPE_NULL = "g:Null"
    const val TYPE_CLASS = "g:Class"

    // Binding types for traversal
    const val TYPE_BINDING = "g:Binding"
    const val TYPE_BYTECODE = "g:Bytecode"
    const val TYPE_LAMBDA = "g:Lambda"

    // Numeric types
    const val TYPE_BIG_DECIMAL = "g:BigDecimal"
    const val TYPE_BIG_INTEGER = "g:BigInteger"
    const val TYPE_BYTE = "g:Byte"
    const val TYPE_SHORT = "g:Short"

    // Binary types
    const val TYPE_BLOB = "g:Blob"

    // Direction enum values
    const val DIRECTION_OUT = "OUT"
    const val DIRECTION_IN = "IN"
    const val DIRECTION_BOTH = "BOTH"

    // Cardinality enum values
    const val CARDINALITY_SINGLE = "single"
    const val CARDINALITY_LIST = "list"
    const val CARDINALITY_SET = "set"

    // T enum values (for property access)
    const val T_ID = "id"
    const val T_LABEL = "label"
    const val T_KEY = "key"
    const val T_VALUE = "value"
}

/**
 * Represents a typed GraphSON value with type information.
 */
data class GraphSONTypedValue(
    val type: String,
    val value: Any?
)

/**
 * Represents a GraphSON vertex structure.
 */
data class GraphSONVertex(
    val id: GraphSONTypedValue,
    val label: String,
    val properties: Map<String, List<GraphSONVertexProperty>>? = null
)

/**
 * Represents a GraphSON edge structure.
 */
data class GraphSONEdge(
    val id: GraphSONTypedValue,
    val label: String,
    val inV: GraphSONTypedValue,
    val outV: GraphSONTypedValue,
    val inVLabel: String,
    val outVLabel: String,
    val properties: Map<String, GraphSONProperty>? = null
)

/**
 * Represents a GraphSON vertex property.
 */
data class GraphSONVertexProperty(
    val id: GraphSONTypedValue,
    val value: GraphSONTypedValue,
    val label: String,
    val properties: Map<String, GraphSONProperty>? = null
)

/**
 * Represents a GraphSON property.
 */
data class GraphSONProperty(
    val key: String,
    val value: GraphSONTypedValue
)

/**
 * Represents a GraphSON list structure.
 */
data class GraphSONList(
    val list: List<GraphSONTypedValue>
)

/**
 * Represents a GraphSON set structure.
 */
data class GraphSONSet(
    val set: Set<GraphSONTypedValue>
)

/**
 * Represents a GraphSON map structure.
 */
data class GraphSONMap(
    val map: List<Pair<GraphSONTypedValue, GraphSONTypedValue>>
)

/**
 * Exception thrown when GraphSON parsing fails.
 */
open class GraphSONException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when an unsupported GraphSON type is encountered.
 */
class UnsupportedGraphSONTypeException(type: String) : GraphSONException("Unsupported GraphSON type: $type")

/**
 * Exception thrown when GraphSON format is malformed.
 */
class MalformedGraphSONException(message: String, cause: Throwable? = null) : GraphSONException(message, cause)
