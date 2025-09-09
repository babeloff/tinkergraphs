plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    `maven-publish`
}


// Note: There is a known deprecation warning from the Kotlin/JS plugin:
// "Invocation of Task.project at execution time has been deprecated"
// This is internal to the Kotlin plugin and will be resolved in future plugin versions.
// It does not affect functionality and is compatible with configuration cache.

group = "org.apache.tinkerpop.kotlin"

version = "1.0.0-SNAPSHOT"

repositories { mavenCentral() }

kotlin {
    jvmToolchain(23)

    // Suppress expect/actual classes beta warning
    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
            }
        }
    }

    // JVM target
    jvm {
        testRuns.named("test") { executionTask.configure { useJUnitPlatform() } }
    }

    // JavaScript target
    js(IR) {
        browser {
            commonWebpackConfig { cssSupport { enabled.set(true) } }
            testTask {
                enabled = false // Disable browser tests to avoid Chrome dependency
            }
        }
        nodejs { testTask { useMocha { timeout = "10s" } } }
        binaries.executable()
    }

    // Native targets - use a more robust approach
    when (val hostOs = System.getProperty("os.name").lowercase()) {
        "mac os x", "macos" -> macosX64("native")
        "linux" -> linuxX64("native")
        else ->
                when {
                    hostOs.startsWith("windows") -> mingwX64("native")
                    hostOs.startsWith("mac") -> macosX64("native")
                    else ->
                            throw GradleException(
                                    "Host OS '$hostOs' is not supported in Kotlin/Native."
                            )
                }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kermit)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.tinkerpop.gremlin.core)
                implementation(libs.tinkerpop.tinkergraph.gremlin)
                implementation(libs.tinkerpop.gremlin.groovy)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.junit.jupiter)
                implementation(libs.kotest.runner.junit5)
                implementation(libs.kotest.assertions.core)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val nativeMain by getting {
            dependencies { implementation(libs.kotlinx.coroutines.core) }
        }
        val nativeTest by getting {
            dependencies {
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])

            pom {
                name.set("TinkerGraph Kotlin Multiplatform")
                description.set(
                        "A Kotlin multiplatform implementation of Apache TinkerPop's TinkerGraph"
                )
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
    description = "Generate KDoc documentation"
    dependsOn("dokkaHtml")
}

// Configure Dokka for KDoc generation
tasks.named("dokkaHtml") { doFirst { mkdir("build/docs/kdoc") } }
