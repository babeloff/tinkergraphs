package org.apache.tinkerpop.gremlin.tinkergraph.io.graphson

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize

import org.apache.tinkerpop.gremlin.structure.Graph
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

/**
 * Test suite for validating ID conflict resolution strategies in GraphSON deserialization.
 *
 * This test suite validates the implementation of configurable ID conflict resolution
 * strategies that enable standard GraphSON import workflows like merging data into
 * existing graphs, incremental loading, and round-trip serialization.
 *
 * Implementation addresses Task 3.6.3 requirements for production-ready GraphSON parser.
 */
class GraphSONIdConflictTest : StringSpec({

    lateinit var graph: TinkerGraph
    lateinit var mapper: GraphSONMapper

    beforeTest {
        graph = TinkerGraph.open()
        mapper = GraphSONMapper.create()
    }

    afterTest {
        graph.close()
    }



    "STRICT strategy should throw exception on vertex ID conflicts" {
        // Create a graph with some vertices
        val existingVertex = graph.addVertex("id", 1, "name", "Alice")

        // Create GraphSON string with conflicting vertex ID
        val graphsonString = """
        {
          "version" : "3.0",
          "vertices" : [ {
            "@type" : "g:Vertex",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 1
              },
              "label" : "person",
              "properties" : {
                "name" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 0
                    },
                    "value" : "Bob",
                    "label" : "name"
                  }
                } ]
              }
            }
          } ],
          "edges" : [ ]
        }
        """.trimIndent()

        // Create mapper with STRICT strategy (explicit)
        val strictMapper = GraphSONMapper.build()
            .idConflictStrategy(IdConflictStrategy.STRICT)
            .create()

        // Attempting to deserialize should throw an exception due to ID conflict
        shouldThrow<IllegalArgumentException> {
            strictMapper.readGraphInto(graphsonString, graph)
        }
    }

    "STRICT strategy should throw exception on edge ID conflicts" {
        // Create a graph with vertices and an edge
        val v1 = graph.addVertex("id", 1, "name", "Alice")
        val v2 = graph.addVertex("id", 2, "name", "Bob")
        val existingEdge = v1.addEdge("knows", v2, "id", 100)

        // Create GraphSON string with conflicting edge ID
        val graphsonString = """
        {
          "version" : "3.0",
          "vertices" : [ {
            "@type" : "g:Vertex",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 3
              },
              "label" : "person",
              "properties" : {
                "name" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 1
                    },
                    "value" : "Charlie",
                    "label" : "name"
                  }
                } ]
              }
            }
          }, {
            "@type" : "g:Vertex",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 4
              },
              "label" : "person",
              "properties" : {
                "name" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 2
                    },
                    "value" : "David",
                    "label" : "name"
                  }
                } ]
              }
            }
          } ],
          "edges" : [ {
            "@type" : "g:Edge",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 100
              },
              "label" : "friends",
              "inV" : {
                "@type" : "g:Int32",
                "@value" : 4
              },
              "outV" : {
                "@type" : "g:Int32",
                "@value" : 3
              },
              "inVLabel" : "person",
              "outVLabel" : "person"
            }
          } ]
        }
        """.trimIndent()

        // Create mapper with STRICT strategy
        val strictMapper = GraphSONMapper.build()
            .idConflictStrategy(IdConflictStrategy.STRICT)
            .create()

        // Attempting to deserialize should throw an exception due to edge ID conflict
        shouldThrow<IllegalArgumentException> {
            strictMapper.readGraphInto(graphsonString, graph)
        }
    }

    "deserializing GraphSON into empty graph should work correctly" {
        // This test verifies that the basic functionality works when there are no conflicts
        val graphsonString = """
        {
          "version" : "3.0",
          "vertices" : [ {
            "@type" : "g:Vertex",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 1
              },
              "label" : "person",
              "properties" : {
                "name" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 0
                    },
                    "value" : "Alice",
                    "label" : "name"
                  }
                } ]
              }
            }
          } ],
          "edges" : [ ]
        }
        """.trimIndent()

        val deserializedGraph = mapper.readGraph(graphsonString)
        val vertices = deserializedGraph.vertices().asSequence().toList()

        vertices.size shouldBe 1
        vertices[0].id() shouldBe 1
        vertices[0].value<String>("name") shouldBe "Alice"

        deserializedGraph.close()
    }

    "current behavior prevents merging GraphSON data into existing graphs" {
        // This test documents the limitation that prevents incremental graph loading
        // Step 1: Create initial graph
        val v1 = graph.addVertex("id", 1, "name", "Alice", "type", "original")
        val v2 = graph.addVertex("id", 2, "name", "Bob", "type", "original")
        v1.addEdge("knows", v2, "id", 100, "since", 2020)

        // Step 2: Try to add new data that references existing vertices
        val additionalDataGraphSON = """
        {
          "version" : "3.0",
          "vertices" : [ {
            "@type" : "g:Vertex",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 3
              },
              "label" : "person",
              "properties" : {
                "name" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 1
                    },
                    "value" : "Charlie",
                    "label" : "name"
                  }
                } ],
                "type" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 2
                    },
                    "value" : "additional",
                    "label" : "type"
                  }
                } ]
              }
            }
          } ],
          "edges" : [ {
            "@type" : "g:Edge",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 101
              },
              "label" : "knows",
              "inV" : {
                "@type" : "g:Int32",
                "@value" : 1
              },
              "outV" : {
                "@type" : "g:Int32",
                "@value" : 3
              },
              "inVLabel" : "person",
              "outVLabel" : "person",
              "properties" : {
                "since" : {
                  "@type" : "g:Property",
                  "@value" : {
                    "key" : "since",
                    "value" : {
                      "@type" : "g:Int32",
                      "@value" : 2021
                    }
                  }
                }
              }
            }
          } ]
        }
        """.trimIndent()

        // With new ID conflict resolution, this should work with default GENERATE_NEW_ID strategy
        val flexibleMapper = GraphSONMapper.create()  // Uses default GENERATE_NEW_ID
        flexibleMapper.readGraphInto(additionalDataGraphSON, graph)

        // Should now have 3 vertices (original 2 + Charlie)
        graph.vertices().asSequence().count() shouldBe 3
        graph.edges().asSequence().count() shouldBe 2  // original + new edge

        // Find Charlie vertex
        val charlieVertex = graph.vertices().asSequence().find {
            it.value<String>("name") == "Charlie"
        }
        charlieVertex shouldNotBe null
        charlieVertex!!.value<String>("type") shouldBe "additional"
    }

    "round trip serialization with same graph creates ID conflicts" {
        // This test shows that even round-trip operations can fail
        val originalVertex = graph.addVertex("id", 42, "name", "Test")

        // Serialize the graph
        val serialized = mapper.writeGraph(graph)

        // Try to deserialize back into the same graph with default strategy (should work)
        val flexibleMapper = GraphSONMapper.create()  // Uses default GENERATE_NEW_ID
        flexibleMapper.readGraphInto(serialized, graph)

        // Should now have 2 vertices (original + imported with new ID)
        graph.vertices().asSequence().count() shouldBe 2

        // But deserializing into a fresh graph works
        val freshGraph = TinkerGraph.open()
        val mapper2 = GraphSONMapper.create()

        // This should work fine
        val deserializedGraph = mapper2.readGraph(serialized)
        val vertices = deserializedGraph.vertices().asSequence().toList()

        vertices.size shouldBe 1
        vertices[0].id() shouldBe 42
        vertices[0].value<String>("name") shouldBe "Test"

        freshGraph.close()
        deserializedGraph.close()
    }

    "GENERATE_NEW_ID strategy should create new IDs for conflicting vertices" {
        val existingVertex = graph.addVertex("id", 1, "name", "Alice", "age", 30)

        val graphsonWithConflict = """
        {
          "version" : "3.0",
          "vertices" : [ {
            "@type" : "g:Vertex",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 1
              },
              "label" : "person",
              "properties" : {
                "name" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 0
                    },
                    "value" : "Bob",
                    "label" : "name"
                  }
                } ]
              }
            }
          } ],
          "edges" : [ ]
        }
        """.trimIndent()

        val flexibleMapper = GraphSONMapper.build()
            .idConflictStrategy(IdConflictStrategy.GENERATE_NEW_ID)
            .create()

        flexibleMapper.readGraphInto(graphsonWithConflict, graph)

        // Should have 2 vertices now (original + imported with new ID)
        val vertices = graph.vertices().asSequence().toList()
        vertices shouldHaveSize 2

        // Original vertex should be unchanged
        val aliceVertex = vertices.find { it.value<String>("name") == "Alice" }
        aliceVertex shouldNotBe null
        aliceVertex!!.id() shouldBe 1
        aliceVertex.value<Int>("age") shouldBe 30

        // New vertex should have different ID but same properties from GraphSON
        val bobVertex = vertices.find { it.value<String>("name") == "Bob" }
        bobVertex shouldNotBe null
        bobVertex!!.id() shouldNotBe 1  // Should have new auto-generated ID
    }

    "GENERATE_NEW_ID strategy should create new IDs for conflicting edges" {
        val v1 = graph.addVertex("id", 1, "name", "Alice")
        val v2 = graph.addVertex("id", 2, "name", "Bob")
        val existingEdge = v1.addEdge("knows", v2, "id", 100, "since", 2020)

        val graphsonWithConflict = """
        {
          "version" : "3.0",
          "vertices" : [ {
            "@type" : "g:Vertex",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 3
              },
              "label" : "person",
              "properties" : {
                "name" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 1
                    },
                    "value" : "Charlie",
                    "label" : "name"
                  }
                } ]
              }
            }
          }, {
            "@type" : "g:Vertex",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 4
              },
              "label" : "person",
              "properties" : {
                "name" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 2
                    },
                    "value" : "David",
                    "label" : "name"
                  }
                } ]
              }
            }
          } ],
          "edges" : [ {
            "@type" : "g:Edge",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 100
              },
              "label" : "friends",
              "inV" : {
                "@type" : "g:Int32",
                "@value" : 4
              },
              "outV" : {
                "@type" : "g:Int32",
                "@value" : 3
              },
              "inVLabel" : "person",
              "outVLabel" : "person"
            }
          } ]
        }
        """.trimIndent()

        val flexibleMapper = GraphSONMapper.build()
            .idConflictStrategy(IdConflictStrategy.GENERATE_NEW_ID)
            .create()

        flexibleMapper.readGraphInto(graphsonWithConflict, graph)

        // Should have 4 vertices now (2 original + 2 imported)
        graph.vertices().asSequence().count() shouldBe 4

        // Should have 2 edges now (1 original + 1 imported with new ID)
        val edges = graph.edges().asSequence().toList()
        edges shouldHaveSize 2

        // Original edge should be unchanged
        val originalEdge = edges.find { it.id() == 100 }
        originalEdge shouldNotBe null
        originalEdge!!.value<Int>("since") shouldBe 2020

        // New edge should have different ID
        val newEdge = edges.find { it.id() != 100 }
        newEdge shouldNotBe null
        newEdge!!.label() shouldBe "friends"
    }

    "MERGE_PROPERTIES strategy should merge properties of conflicting vertices" {
        val existingVertex = graph.addVertex("id", 1, "name", "Alice", "age", 30)

        val graphsonWithConflict = """
        {
          "version" : "3.0",
          "vertices" : [ {
            "@type" : "g:Vertex",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 1
              },
              "label" : "person",
              "properties" : {
                "name" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 0
                    },
                    "value" : "Alice Updated",
                    "label" : "name"
                  }
                } ],
                "city" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 1
                    },
                    "value" : "New York",
                    "label" : "city"
                  }
                } ]
              }
            }
          } ],
          "edges" : [ ]
        }
        """.trimIndent()

        val mergeMapper = GraphSONMapper.build()
            .idConflictStrategy(IdConflictStrategy.MERGE_PROPERTIES)
            .create()

        mergeMapper.readGraphInto(graphsonWithConflict, graph)

        // Should still have only 1 vertex (merged)
        val vertices = graph.vertices().asSequence().toList()
        vertices shouldHaveSize 1

        val vertex = vertices[0]
        vertex.id() shouldBe 1
        vertex.value<String>("name") shouldBe "Alice Updated"  // Updated from GraphSON
        vertex.value<Int>("age") shouldBe 30  // Preserved from original
        vertex.value<String>("city") shouldBe "New York"  // Added from GraphSON
    }

    "REPLACE_ELEMENT strategy should replace existing vertices completely" {
        val existingVertex = graph.addVertex("id", 1, "name", "Alice", "age", 30)
        val v2 = graph.addVertex("id", 2, "name", "Bob")
        val existingEdge = existingVertex.addEdge("knows", v2, "id", 100)

        val graphsonWithConflict = """
        {
          "version" : "3.0",
          "vertices" : [ {
            "@type" : "g:Vertex",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 1
              },
              "label" : "person",
              "properties" : {
                "name" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 0
                    },
                    "value" : "Alice Replaced",
                    "label" : "name"
                  }
                } ],
                "city" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 1
                    },
                    "value" : "Boston",
                    "label" : "city"
                  }
                } ]
              }
            }
          } ],
          "edges" : [ ]
        }
        """.trimIndent()

        val replaceMapper = GraphSONMapper.build()
            .idConflictStrategy(IdConflictStrategy.REPLACE_ELEMENT)
            .create()

        replaceMapper.readGraphInto(graphsonWithConflict, graph)

        // Should still have 2 vertices (Bob + replaced Alice)
        val vertices = graph.vertices().asSequence().toList()
        vertices shouldHaveSize 2

        val aliceVertex = graph.vertex(1)
        aliceVertex shouldNotBe null
        aliceVertex!!.value<String>("name") shouldBe "Alice Replaced"  // Replaced
        aliceVertex.value<String>("city") shouldBe "Boston"  // New property

        // Age property should be gone (vertex was completely replaced)
        aliceVertex.value<Int>("age") shouldBe null

        // Edge should be gone too (vertex replacement removes edges)
        graph.edges().asSequence().count() shouldBe 0
    }

    "default strategy should be GENERATE_NEW_ID" {
        val existingVertex = graph.addVertex("id", 1, "name", "Alice")

        val graphsonString = """
        {
          "version" : "3.0",
          "vertices" : [ {
            "@type" : "g:Vertex",
            "@value" : {
              "id" : {
                "@type" : "g:Int32",
                "@value" : 1
              },
              "label" : "person",
              "properties" : {
                "name" : [ {
                  "@type" : "g:VertexProperty",
                  "@value" : {
                    "id" : {
                      "@type" : "g:Int64",
                      "@value" : 0
                    },
                    "value" : "Bob",
                    "label" : "name"
                  }
                } ]
              }
            }
          } ],
          "edges" : [ ]
        }
        """.trimIndent()

        // Default mapper should use GENERATE_NEW_ID strategy
        val defaultMapper = GraphSONMapper.create()
        defaultMapper.readGraphInto(graphsonString, graph)

        // Should have 2 vertices (original + imported with new ID)
        graph.vertices().asSequence().count() shouldBe 2
    }

    "round trip serialization should work with GENERATE_NEW_ID strategy" {
        val originalVertex = graph.addVertex("id", 42, "name", "Test")
        val serialized = mapper.writeGraph(graph)

        // Using GENERATE_NEW_ID strategy, this should work
        val flexibleMapper = GraphSONMapper.build()
            .idConflictStrategy(IdConflictStrategy.GENERATE_NEW_ID)
            .create()

        flexibleMapper.readGraphInto(serialized, graph)

        // Should have 2 vertices now (original + imported with new ID)
        val vertices = graph.vertices().asSequence().toList()
        vertices shouldHaveSize 2

        // Both should have the same name but different IDs
        vertices.forEach { vertex ->
            vertex.value<String>("name") shouldBe "Test"
        }

        val ids = vertices.map { it.id() }.toSet()
        ids shouldHaveSize 2  // Should have 2 different IDs
        ids.contains(42) shouldBe true  // Original ID should still be present
    }
})
