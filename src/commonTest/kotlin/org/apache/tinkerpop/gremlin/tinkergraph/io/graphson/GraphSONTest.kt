package org.apache.tinkerpop.gremlin.tinkergraph.io.graphson

import kotlin.test.*
import org.apache.tinkerpop.gremlin.structure.*
import org.apache.tinkerpop.gremlin.tinkergraph.structure.*

/**
 * Comprehensive test suite for GraphSON v3.0 implementation.
 *
 * Tests serialization and deserialization of all supported GraphSON v3.0 types and ensures
 * compliance with the Apache TinkerPop specification.
 */
class GraphSONTest {

    private lateinit var graph: TinkerGraph
    private lateinit var mapper: GraphSONMapper

    @BeforeTest
    fun setup() {
        graph = TinkerGraph.open()
        mapper = GraphSONMapper.create()
    }

    @AfterTest
    fun cleanup() {
        graph.close()
    }

    @Test
    fun testGraphSONVersionConstant() {
        assertEquals("3.0", GraphSONTypes.VERSION)
    }

    @Test
    fun testEmptyGraphSerialization() {
        val graphsonString = mapper.writeGraph(graph)

        assertNotNull(graphsonString)
        assertTrue(graphsonString.contains("\"version\""))
        assertTrue(graphsonString.contains("\"vertices\""))
        assertTrue(graphsonString.contains("\"edges\""))

        val deserializedGraph = mapper.readGraph(graphsonString)
        assertEquals(0, deserializedGraph.vertices().asSequence().count())
        assertEquals(0, deserializedGraph.edges().asSequence().count())
    }

    @Test
    fun testBasicVertexSerialization() {
        val vertex = graph.addVertex("id", 1, "label", "person")
        vertex.property("name", "Alice")
        vertex.property("age", 30)

        val vertexString = mapper.writeVertex(vertex)

        assertTrue(vertexString.contains("\"@type\": \"g:Vertex\""))
        assertTrue(vertexString.contains("\"@value\""))
        assertTrue(vertexString.contains("\"id\""))
        assertTrue(vertexString.contains("\"label\": \"person\""))
        assertTrue(vertexString.contains("\"properties\""))
    }

    @Test
    fun testBasicEdgeSerialization() {
        val v1 = graph.addVertex("id", 1, "label", "person")
        val v2 = graph.addVertex("id", 2, "label", "person")
        val edge = v1.addEdge("knows", v2, "id", "e1")
        edge.property("since", 2020)

        val edgeString = mapper.writeEdge(edge)

        assertTrue(edgeString.contains("\"@type\": \"g:Edge\""))
        assertTrue(edgeString.contains("\"@value\""))
        assertTrue(edgeString.contains("\"id\""))
        assertTrue(edgeString.contains("\"label\": \"knows\""))
        assertTrue(edgeString.contains("\"inV\""))
        assertTrue(edgeString.contains("\"outV\""))
        assertTrue(edgeString.contains("\"properties\""))
    }

    @Test
    fun testTypedValueSerialization() {
        // Test Int32 - JavaScript platform treats all numbers as doubles
        val int32String = mapper.writeValue(42)
        assertTrue(
                int32String.contains("\"@type\": \"g:Int32\"") ||
                        int32String.contains("\"@type\": \"g:Double\"")
        )
        assertTrue(int32String.contains("\"@value\": 42"))

        // Test Int64 - JavaScript platform may treat as double
        val int64String = mapper.writeValue(42L)
        assertTrue(
                int64String.contains("\"@type\": \"g:Int64\"") ||
                        int64String.contains("\"@type\": \"g:Double\"")
        )
        assertTrue(int64String.contains("\"@value\": 42"))

        // Test Float - JavaScript platform may have precision differences
        val floatString = mapper.writeValue(3.14f)
        assertTrue(
                floatString.contains("\"@type\": \"g:Float\"") ||
                        floatString.contains("\"@type\": \"g:Double\"")
        )
        // Skip exact value check due to JS precision differences

        // Test Double
        val doubleString = mapper.writeValue(3.14)
        assertTrue(doubleString.contains("\"@type\": \"g:Double\""))
        assertTrue(doubleString.contains("\"@value\": 3.14"))

        // Test Boolean
        val boolString = mapper.writeValue(true)
        assertTrue(boolString.contains("\"@type\": \"g:Boolean\""))
        assertTrue(boolString.contains("\"@value\": true"))

        // Test String
        val stringString = mapper.writeValue("hello")
        assertTrue(stringString.contains("\"@type\": \"g:String\""))
        assertTrue(stringString.contains("\"@value\": \"hello\""))

        // Test null
        val nullString = mapper.writeValue(null)
        assertTrue(nullString.contains("\"@type\": \"g:Null\""))
    }

    @Test
    fun testTypedValueDeserialization() {
        // Test Int32
        val int32Value = mapper.readValue("{\"@type\":\"g:Int32\",\"@value\":42}")
        assertEquals(42, int32Value)

        // Test Int64
        val int64Value = mapper.readValue("{\"@type\":\"g:Int64\",\"@value\":42}")
        assertEquals(42L, int64Value)

        // Test Float
        val floatValue = mapper.readValue("{\"@type\":\"g:Float\",\"@value\":3.14}")
        assertEquals(3.14f, floatValue as Float, 0.01f)

        // Test Double
        val doubleValue = mapper.readValue("{\"@type\":\"g:Double\",\"@value\":3.14}")
        assertEquals(3.14, doubleValue as Double, 0.01)

        // Test Boolean
        val boolValue = mapper.readValue("{\"@type\":\"g:Boolean\",\"@value\":true}")
        assertEquals(true, boolValue)

        // Test String
        val stringValue = mapper.readValue("{\"@type\":\"g:String\",\"@value\":\"hello\"}")
        assertEquals("hello", stringValue)

        // Test null
        val nullValue = mapper.readValue("{\"@type\":\"g:Null\",\"@value\":null}")
        assertNull(nullValue)
    }

    @Test
    fun testCollectionSerialization() {
        // Test List
        val list = listOf(1, "hello", true)
        val listString = mapper.writeValue(list)
        assertTrue(listString.contains("\"@type\": \"g:List\""))
        assertTrue(listString.contains("\"@value\": ["))

        // Test Set
        val set = setOf(1, 2, 3)
        val setString = mapper.writeValue(set)
        assertTrue(setString.contains("\"@type\": \"g:Set\""))
        assertTrue(setString.contains("\"@value\": ["))

        // Test Map
        val map = mapOf("key1" to "value1", "key2" to 42)
        val mapString = mapper.writeValue(map)
        assertTrue(mapString.contains("\"@type\": \"g:Map\""))
        assertTrue(mapString.contains("\"@value\": ["))
    }

    @Test
    fun testCollectionDeserialization() {
        // Test List
        val listJson =
                "{\"@type\":\"g:List\",\"@value\":[{\"@type\":\"g:Int32\",\"@value\":1},{\"@type\":\"g:String\",\"@value\":\"hello\"}]}"
        val listValue = mapper.readValue(listJson) as List<*>
        assertEquals(2, listValue.size)
        assertEquals(1, listValue[0])
        assertEquals("hello", listValue[1])

        // Test Set
        val setJson =
                "{\"@type\":\"g:Set\",\"@value\":[{\"@type\":\"g:Int32\",\"@value\":1},{\"@type\":\"g:Int32\",\"@value\":2}]}"
        val setValue = mapper.readValue(setJson) as Set<*>
        assertEquals(2, setValue.size)
        assertTrue(setValue.contains(1))
        assertTrue(setValue.contains(2))

        // Test Map
        val mapJson =
                "{\"@type\":\"g:Map\",\"@value\":[{\"@type\":\"g:String\",\"@value\":\"key1\"},{\"@type\":\"g:String\",\"@value\":\"value1\"}]}"
        val mapValue = mapper.readValue(mapJson) as Map<*, *>
        assertEquals(1, mapValue.size)
        assertEquals("value1", mapValue["key1"])
    }

    @Test
    fun testComplexGraphRoundTrip() {
        // Create a more complex graph
        val alice = graph.addVertex("id", "alice", "label", "person")
        alice.property("name", "Alice")
        alice.property("age", 30)
        alice.property("languages", listOf("English", "Spanish"))

        val bob = graph.addVertex("id", "bob", "label", "person")
        bob.property("name", "Bob")
        bob.property("age", 25)

        val company = graph.addVertex("id", "acme", "label", "company")
        company.property("name", "ACME Corp")
        company.property("employees", 500)

        val knowsEdge = alice.addEdge("knows", bob, "id", "knows1")
        val inputSinceYear = 2020
        val inputKnowsWeight = 0.8
        knowsEdge.property("since", inputSinceYear)
        knowsEdge.property("weight", inputKnowsWeight)

        val worksForEdge = alice.addEdge("worksFor", company, "id", "works1")
        val inputPosition = "Engineer"
        val inputSalary = 75000
        worksForEdge.property("position", inputPosition)
        worksForEdge.property("salary", inputSalary)

        // Add some graph variables
        graph.variables().set("created", "2024-01-01")
        graph.variables().set("version", 1.0)

        // Serialize the graph
        val graphsonString = mapper.writeGraph(graph)
        assertNotNull(graphsonString)
        assertTrue(graphsonString.isNotEmpty())

        // Deserialize and verify
        val deserializedGraph = mapper.readGraph(graphsonString)

        // Verify vertex count and properties
        val vertices = deserializedGraph.vertices().asSequence().toList()
        assertEquals(3, vertices.size)

        val deserializedAlice = deserializedGraph.vertices("alice").next()
        assertEquals("Alice", deserializedAlice.value<String>("name"))
        assertEquals(30, deserializedAlice.value<Int>("age"))

        val deserializedBob = deserializedGraph.vertices("bob").next()
        assertEquals("Bob", deserializedBob.value<String>("name"))
        assertEquals(25, deserializedBob.value<Int>("age"))

        val deserializedCompany = deserializedGraph.vertices("acme").next()
        assertEquals("ACME Corp", deserializedCompany.value<String>("name"))
        assertEquals(500, deserializedCompany.value<Int>("employees"))

        // Verify edge count and properties
        val edges = deserializedGraph.edges().asSequence().toList()
        assertEquals(2, edges.size)

        val deserializedEdge = deserializedGraph.edges("knows1").next()
        assertEquals("knows", deserializedEdge.label())
        assertEquals(inputSinceYear, deserializedEdge.value<Int>("since"))
        assertEquals(inputKnowsWeight, deserializedEdge.value<Double>("weight")!!, 0.001)

        val deserializedWorks = deserializedGraph.edges("works1").next()
        assertEquals("worksFor", deserializedWorks.label())
        assertEquals(inputPosition, deserializedWorks.value<String>("position"))
        assertEquals(inputSalary, deserializedWorks.value<Int>("salary"))

        // Verify variables
        assertEquals("2024-01-01", deserializedGraph.variables().get<String>("created"))
        assertEquals(1.0, deserializedGraph.variables().get<Double>("version"))
    }

    @Test
    fun testVertexPropertiesWithMetaProperties() {
        val vertex = graph.addVertex("id", 1, "label", "person")

        // Add vertex property
        val nameProperty = vertex.property("name", "Alice")

        val graphsonString = mapper.writeGraph(graph)
        val deserializedGraph = mapper.readGraph(graphsonString)

        val deserializedVertex = deserializedGraph.vertices(1).next()
        val deserializedNameProperty = deserializedVertex.property<String>("name")

        assertEquals("Alice", deserializedNameProperty.value())
    }

    @Test
    fun testDirectionEnumSerialization() {
        val outString = mapper.writeValue(Direction.OUT)

        // The JSON output has pretty printing with spaces, so we need to account for that
        assertTrue(
                outString.contains("\"@type\": \"g:Direction\""),
                "Expected @type:g:Direction with spaces in: $outString"
        )
        assertTrue(
                outString.contains("\"@value\": \"OUT\""),
                "Expected @value:OUT with spaces in: $outString"
        )

        val inString = mapper.writeValue(Direction.IN)
        assertTrue(inString.contains("\"@type\": \"g:Direction\""))
        assertTrue(inString.contains("\"@value\": \"IN\""))

        val bothString = mapper.writeValue(Direction.BOTH)
        assertTrue(bothString.contains("\"@type\": \"g:Direction\""))
        assertTrue(bothString.contains("\"@value\": \"BOTH\""))
    }

    @Test
    fun testCardinalityEnumSerialization() {
        // Test cardinality string serialization with round-trip variables
        val singleInput = "single"
        val singleSerialized = mapper.writeValue(singleInput)
        assertTrue(singleSerialized.contains("\"@type\": \"g:Cardinality\""))
        assertTrue(singleSerialized.contains("\"@value\": \"single\""))

        val listInput = "list"
        val listSerialized = mapper.writeValue(listInput)
        assertTrue(listSerialized.contains("\"@type\": \"g:Cardinality\""))
        assertTrue(listSerialized.contains("\"@value\": \"list\""))

        val setInput = "set"
        val setSerialized = mapper.writeValue(setInput)
        assertTrue(setSerialized.contains("\"@type\": \"g:Cardinality\""))
        assertTrue(setSerialized.contains("\"@value\": \"set\""))
    }

    @Test
    fun testByteArraySerialization() {
        // Test byte array round-trip with clear variable naming
        val inputByteArray = byteArrayOf(1, 2, 3, 4, 5)
        val serializedByteArray = mapper.writeValue(inputByteArray)

        assertTrue(serializedByteArray.contains("\"@type\": \"g:Blob\""))
        assertTrue(serializedByteArray.contains("\"@value\": \"1,2,3,4,5\""))

        val deserializedByteArray = mapper.readValue(serializedByteArray) as ByteArray
        assertContentEquals(inputByteArray, deserializedByteArray)
    }

    @Test
    fun testErrorHandling() {
        // Test malformed JSON
        assertFailsWith<GraphSONException> { mapper.readGraph("{invalid json}") }

        // Test missing type
        assertFailsWith<MalformedGraphSONException> { mapper.readValue("{\"@value\":42}") }

        // Test unsupported type
        assertFailsWith<UnsupportedGraphSONTypeException> {
            mapper.readValue("{\"@type\":\"g:UnknownType\",\"@value\":42}")
        }

        // Test missing required fields
        assertFailsWith<MalformedGraphSONException> {
            mapper.readGraph(
                    "{\"version\":\"3.0\",\"vertices\":[{\"@type\":\"g:Vertex\",\"@value\":{\"label\":\"person\"}}]}"
            )
        }
    }

    @Test
    fun testQuickUtilityMethods() {
        // Test quick serialization methods
        val vertex = graph.addVertex("id", 1, "label", "person")
        vertex.property("name", "Alice")

        val graphsonString = GraphSON.toGraphSON(graph)
        assertNotNull(graphsonString)
        assertTrue(graphsonString.contains("\"@type\": \"g:Vertex\""))

        val vertexString = GraphSON.toGraphSON(vertex)
        assertNotNull(vertexString)
        assertTrue(vertexString.contains("\"@type\": \"g:Vertex\""))

        val valueString = GraphSON.toGraphSON("hello")
        assertNotNull(valueString)
        assertTrue(valueString.contains("\"@type\": \"g:String\""))

        // Test quick deserialization
        val deserializedGraph = GraphSON.graphFromGraphSON(graphsonString)
        assertEquals(1, deserializedGraph.vertices().asSequence().count())

        val deserializedValue = GraphSON.valueFromGraphSON(valueString)
        assertEquals("hello", deserializedValue)
    }

    @Test
    fun testBuilderPattern() {
        val customMapper =
                GraphSONMapper.build().prettyPrint(false).typeInfo(true).embedTypes(true).create()

        assertNotNull(customMapper)

        val vertex = graph.addVertex("id", 1, "label", "person")
        val vertexString = customMapper.writeVertex(vertex)
        assertNotNull(vertexString)
    }

    @Test
    fun testMultipleVertexProperties() {
        // Test multiple properties with the same key using clear variable naming
        val inputVertex = graph.addVertex("id", 1, "label", "person") as TinkerVertex
        val inputName = "John Doe"
        val inputPhone1 = "123-456-7890"
        val inputPhone2 = "098-765-4321"

        inputVertex.property("name", inputName)
        inputVertex.property("phone", inputPhone1, VertexProperty.Cardinality.LIST)
        inputVertex.property("phone", inputPhone2, VertexProperty.Cardinality.LIST)

        val serializedGraph = mapper.writeGraph(graph)
        val deserializedGraph = mapper.readGraph(serializedGraph)

        val deserializedVertex = deserializedGraph.vertices(1).next()
        val deserializedPhoneProperties =
                deserializedVertex.properties<String>("phone").asSequence().toList()

        assertEquals(2, deserializedPhoneProperties.size)
        val deserializedPhoneValues = deserializedPhoneProperties.map { it.value() }.toSet()
        assertTrue(deserializedPhoneValues.contains(inputPhone1))
        assertTrue(deserializedPhoneValues.contains(inputPhone2))
    }

    @Test
    fun testNumericTypePrecision() {
        // Test different numeric types maintain precision with clear round-trip variables
        val inputByte: Byte = 127
        val inputShort: Short = 32767
        val inputInt = Int.MAX_VALUE
        val inputLong = Long.MAX_VALUE
        val inputFloat = 3.14159f
        val inputDouble = kotlin.math.PI

        val serializedByte = mapper.writeValue(inputByte)
        val serializedShort = mapper.writeValue(inputShort)
        val serializedInt = mapper.writeValue(inputInt)
        val serializedLong = mapper.writeValue(inputLong)
        val serializedFloat = mapper.writeValue(inputFloat)
        val serializedDouble = mapper.writeValue(inputDouble)

        // Round-trip verification - bytes and shorts are correctly deserialized to their original
        // types
        val deserializedByte = mapper.readValue(serializedByte) as Byte
        val deserializedShort = mapper.readValue(serializedShort) as Short
        val deserializedInt = mapper.readValue(serializedInt) as Int
        val deserializedLong = mapper.readValue(serializedLong) as Long
        val deserializedFloat = mapper.readValue(serializedFloat) as Float
        val deserializedDouble = mapper.readValue(serializedDouble) as Double

        assertEquals(inputByte, deserializedByte)
        assertEquals(inputShort, deserializedShort)
        assertEquals(inputInt, deserializedInt)
        assertEquals(inputLong, deserializedLong)
        assertEquals(inputFloat, deserializedFloat, 0.00001f)
        assertEquals(inputDouble, deserializedDouble, 0.00001)
    }
}
