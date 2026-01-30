package dev.all4.gradle.release.integration

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional tests for PublishPlugin in subprojects using Gradle TestKit. These tests verify the
 * plugin auto-detects subprojects and configures them correctly.
 */
class PublishPluginSubprojectFunctionalTest {

    @TempDir lateinit var projectDir: File

    private lateinit var rootBuildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        rootBuildFile = File(projectDir, "build.gradle.kts")
        settingsFile = File(projectDir, "settings.gradle.kts")
    }

    @Test
    fun `module plugin can be applied to subproject`() {
        settingsFile.writeText(
            """
            rootProject.name = "test-project"
            include(":lib")
            """
                .trimIndent()
        )

        rootBuildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")
            }
            """
                .trimIndent()
        )

        val libDir = File(projectDir, "lib")
        libDir.mkdirs()

        File(libDir, "build.gradle.kts")
            .writeText(
                """
            plugins {
                kotlin("jvm") version "2.1.0"
                id("dev.all4.release")
            }
            """
                    .trimIndent()
            )

        val srcDir = File(libDir, "src/main/kotlin/com/example")
        srcDir.mkdirs()
        File(srcDir, "Lib.kt")
            .writeText(
                """
            package com.example
            class Lib {
                fun hello() = "Hello"
            }
            """
                    .trimIndent()
            )

        val result =
            GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments(":lib:tasks", "--stacktrace")
                .build()

        assertThat(result.output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `module plugin inherits group and version from root`() {
        settingsFile.writeText(
            """
            rootProject.name = "test-project"
            include(":lib")
            """
                .trimIndent()
        )

        rootBuildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example.mylib")
                version.set("2.5.0")
            }
            """
                .trimIndent()
        )

        val libDir = File(projectDir, "lib")
        libDir.mkdirs()

        File(libDir, "build.gradle.kts")
            .writeText(
                """
            plugins {
                kotlin("jvm") version "2.1.0"
                id("dev.all4.release")
            }

            tasks.register("printVersion") {
                doLast {
                    println("PROJECT_GROUP=${'$'}{project.group}")
                    println("PROJECT_VERSION=${'$'}{project.version}")
                }
            }
            """
                    .trimIndent()
            )

        val srcDir = File(libDir, "src/main/kotlin/com/example")
        srcDir.mkdirs()
        File(srcDir, "Lib.kt")
            .writeText(
                """
            package com.example
            class Lib
            """
                    .trimIndent()
            )

        val result =
            GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments(":lib:printVersion", "--stacktrace")
                .build()

        assertThat(result.output).contains("PROJECT_GROUP=com.example.mylib")
        assertThat(result.output).contains("PROJECT_VERSION=2.5.0")
    }

    @Test
    fun `module plugin works without root plugin`() {
        settingsFile.writeText(
            """
            rootProject.name = "standalone-module"
            """
                .trimIndent()
        )

        rootBuildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "2.1.0"
                id("dev.all4.release")
            }

            group = "com.standalone"
            version = "1.0.0"
            """
                .trimIndent()
        )

        val srcDir = File(projectDir, "src/main/kotlin/com/standalone")
        srcDir.mkdirs()
        File(srcDir, "Main.kt")
            .writeText(
                """
            package com.standalone
            fun main() = println("Hello")
            """
                    .trimIndent()
            )

        val result =
            GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("tasks", "--stacktrace")
                .build()

        assertThat(result.output).contains("BUILD SUCCESSFUL")
    }
}
