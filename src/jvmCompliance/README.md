# JVM Compliance Test Suite

This directory contains the complete Apache TinkerPop compliance test suite for the JVM target, providing comprehensive validation of TinkerGraph implementation conformance.

## Overview

The `jvmCompliance` source set is a dedicated test environment for Apache TinkerPop compliance validation, separated from regular unit and integration tests to provide:

- **Independent Execution**: Run compliance tests separately with `./gradlew jvmComplianceTest`
- **Clean Separation**: Compliance validation isolated from functional testing
- **TinkerPop Parity**: Complete coverage of upstream Apache TinkerPop reference tests
- **Gradle Integration**: Full Kotlin Multiplatform build system integration

## Directory Structure

```
src/jvmCompliance/java/org/apache/tinkerpop/gremlin/tinkergraph/
├── *.java                                 # Root-level compliance tests (15 files)
├── jsr223/                                # JSR-223 scripting compliance (1 file)
├── process/                               # Process API compliance (7 files)
│   └── traversal/
│       ├── step/sideEffect/              # Step execution tests (2 files)  
│       └── strategy/
│           ├── decoration/               # Strategy decoration tests (2 files)
│           └── optimization/             # Strategy optimization tests (2 files)
└── structure/                            # Graph structure compliance (18 files)
```

## Test Coverage

### 📊 **Complete Apache TinkerPop Coverage**
- **Total Files**: 47 Java test classes
- **Coverage**: 100% parity with Apache TinkerPop reference tests
- **Test Methods**: ~200+ individual test cases
- **Areas Covered**: Structure, Process, JSR-223, Serialization, Transactions

### 🎯 **Test Categories**

#### **Graph Structure Tests** (`structure/`)
- Core graph operations and CRUD
- Vertex and edge management
- Property handling and validation  
- ID management strategies
- Serialization (JSON, Gryo v1/v2/v3)
- Transaction graph implementations
- Shuffle variants for randomization testing

#### **Process API Tests** (`process/`)
- Graph traversal execution
- Step-by-step processing validation
- Strategy pattern implementations
- Computer-based processing
- Integration with different providers

#### **Scripting Tests** (`jsr223/`)
- Gremlin language script engine compliance
- JSR-223 integration validation

#### **Root Level Tests**
- Basic TinkerGraph functionality
- Provider implementations
- Feature flag testing
- UUID handling
- Transaction support

## Running Tests

### **All Compliance Tests**
```bash
./gradlew jvmComplianceTest
```

### **Specific Test Categories**
```bash
# Structure tests only
./gradlew jvmComplianceTest --tests "*structure*"

# Process tests only  
./gradlew jvmComplianceTest --tests "*process*"

# JSR-223 tests only
./gradlew jvmComplianceTest --tests "*jsr223*"
```

### **Individual Test Classes**
```bash
./gradlew jvmComplianceTest --tests "TinkerGraphTest"
./gradlew jvmComplianceTest --tests "*Provider*"
```

## Test Configuration

The compliance tests are configured with:
- **JUnit Platform**: Modern JUnit 5 test execution
- **Extended Timeouts**: Longer execution time for comprehensive validation
- **Detailed Logging**: Full test execution reporting
- **Stack Traces**: Complete error reporting for failures
- **Selective Execution**: Run independently of main test suite

## Expected Behavior

### **Test Outcomes**
- **Passing Tests**: Indicate full Apache TinkerPop compliance
- **Failing Tests**: May require implementation-specific modifications
- **Skipped Tests**: Tests disabled with `@Disabled` annotation

### **Common Modifications**
Some tests may need customization for your TinkerGraph implementation:

```java
@Disabled("Custom implementation variation")
@Test
public void testSpecificBehavior() {
    // Test that doesn't apply to this implementation
}
```

## Dependencies

The compliance tests have access to:
- **Apache TinkerPop Core**: Full gremlin-core API
- **TinkerGraph Implementation**: Your tinkergraph-gremlin implementation
- **Groovy Support**: For Gremlin-Groovy integration
- **JUnit 5**: Modern testing framework
- **Kotest**: Additional assertion libraries

## Maintenance

### **Adding New Compliance Tests**
1. Place new tests in appropriate subdirectory structure
2. Follow Apache TinkerPop naming conventions
3. Use package: `org.apache.tinkerpop.gremlin.tinkergraph[.subdirs]`
4. Integrate with existing test providers

### **Updating from Upstream**
When Apache TinkerPop releases updates:
1. Review new compliance requirements
2. Add corresponding test files to appropriate directories
3. Update package declarations to match directory structure
4. Test and adapt for implementation-specific behavior

## Integration

This compliance test suite integrates with:
- **Gradle Build**: Custom `jvmCompliance` source set
- **CI/CD**: Separate compliance validation step
- **Documentation**: Compliance reporting and analysis
- **Multiplatform**: JVM-specific validation within broader multiplatform testing

---

**Result**: Complete Apache TinkerPop compliance validation for JVM implementations with clean separation from functional testing.