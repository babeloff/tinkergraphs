plugins {
    kotlin("multiplatform") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.dokka") version "1.9.20"
    `maven-publish`
}

// Note: There is a known deprecation warning from the Kotlin/JS plugin:
// "Invocation of Task.project at execution time has been deprecated"
// This is internal to the Kotlin plugin and will be resolved in future plugin versions.
// It does not affect functionality and is compatible with configuration cache.

group = "org.apache.tinkerpop.kotlin"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(23)

    // Suppress expect/actual classes beta warning
    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }

    // JVM target
    jvm {
        withJava()
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    // JavaScript target
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
            testTask {
                enabled = false // Disable browser tests to avoid Chrome dependency
            }
        }
        nodejs {
            testTask {
                useMocha {
                    timeout = "10s"
                }
            }
        }
        binaries.executable()
    }

    // Native targets - use a more robust approach
    when (val hostOs = System.getProperty("os.name").lowercase()) {
        "mac os x", "macos" -> macosX64("native")
        "linux" -> linuxX64("native")
        else -> when {
            hostOs.startsWith("windows") -> mingwX64("native")
            hostOs.startsWith("mac") -> macosX64("native")
            else -> throw GradleException("Host OS '$hostOs' is not supported in Kotlin/Native.")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("io.github.oshai:kotlin-logging:7.0.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("ch.qos.logback:logback-classic:1.4.14")
                implementation("org.apache.tinkerpop:gremlin-core:3.7.0")
                implementation("org.apache.tinkerpop:tinkergraph-gremlin:3.7.0")
                implementation("org.apache.tinkerpop:gremlin-groovy:3.7.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.11.0")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        val nativeMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
        val nativeTest by getting
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])

            pom {
                name.set("TinkerGraph Kotlin Multiplatform")
                description.set("A Kotlin multiplatform implementation of Apache TinkerPop's TinkerGraph")
                url.set("https://github.com/apache/tinkerpop")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("tinkerpop")
                        name.set("Apache TinkerPop")
                        email.set("dev@tinkerpop.apache.org")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/apache/tinkerpop.git")
                    developerConnection.set("scm:git:ssh://github.com:apache/tinkerpop.git")
                    url.set("https://github.com/apache/tinkerpop/tree/master")
                }
            }
        }
    }
}

// Documentation generation tasks
tasks.register("generateDocs") {
    group = "documentation"
    description = "Generate all documentation including KDoc and AsciiDoc"
    dependsOn("dokkaHtml", "buildAsciiDoc")
}

tasks.register<Exec>("buildAsciiDoc") {
    group = "documentation"
    description = "Build AsciiDoc documentation using external tools"

    doFirst {
        mkdir("build/docs")
    }

    commandLine("bash", "-c", """
        if command -v asciidoctor >/dev/null 2>&1; then
            echo "Building HTML documentation..."
            asciidoctor -r asciidoctor-diagram docs/roadmap.adoc -o build/docs/roadmap.html

            if command -v asciidoctor-pdf >/dev/null 2>&1; then
                echo "Building PDF documentation..."
                asciidoctor-pdf -r asciidoctor-diagram docs/roadmap.adoc -o build/docs/roadmap.pdf
            else
                echo "Warning: asciidoctor-pdf not found, skipping PDF generation"
            fi

            if command -v asciidoctor-revealjs >/dev/null 2>&1; then
                echo "Building reveal.js presentation..."
                asciidoctor-revealjs docs/roadmap.adoc -o build/docs/roadmap-slides.html
            else
                echo "Warning: asciidoctor-revealjs not found, skipping slides generation"
            fi
        else
            echo "Warning: asciidoctor not found, skipping AsciiDoc documentation generation"
            echo "Install using: pixi run docs-setup"
        fi
    """.trimIndent())
}

// Configure Dokka for KDoc generation
tasks.named("dokkaHtml") {
    doFirst {
        mkdir("build/docs/kdoc")
    }
}
