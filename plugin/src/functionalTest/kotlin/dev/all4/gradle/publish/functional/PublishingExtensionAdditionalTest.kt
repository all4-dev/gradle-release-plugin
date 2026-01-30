package dev.all4.gradle.release.functional

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PublishingExtensionAdditionalTest {

    @TempDir lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        buildFile = File(testProjectDir, "build.gradle.kts")
        settingsFile = File(testProjectDir, "settings.gradle.kts")

        settingsFile.writeText(
            """
            rootProject.name = "test-project"
            """
                .trimIndent()
        )
    }

    @Test
    fun `extension configures all destinations with all preset`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")

                destinations {
                    all() // Enable all destinations
                }
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("publishingInfo")
                .withPluginClasspath()
                .build()

        assert(result.task(":publishingInfo")?.outcome == TaskOutcome.SUCCESS)
        assert(result.output.contains("Maven Local:          ✅"))
        assert(result.output.contains("Maven Standalone:     ✅"))
        assert(result.output.contains("GitHub Pages:         ✅"))
        assert(result.output.contains("GitHub Packages:      ✅"))
        assert(result.output.contains("Gradle Plugin Portal: ❌"))
        assert(result.output.contains("Maven Central:        ✅"))
    }

    @Test
    fun `extension configures production preset correctly`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")

                destinations {
                    production() // Enable production destinations
                }
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("publishingInfo")
                .withPluginClasspath()
                .build()

        assert(result.task(":publishingInfo")?.outcome == TaskOutcome.SUCCESS)
        assert(result.output.contains("Maven Local:          ❌"))
        assert(result.output.contains("Maven Standalone:     ❌"))
        assert(result.output.contains("GitHub Pages:         ❌"))
        assert(result.output.contains("GitHub Packages:      ✅"))
        assert(result.output.contains("Gradle Plugin Portal: ❌"))
        assert(result.output.contains("Maven Central:        ✅"))
    }

    @Test
    fun `extension configures local preset correctly`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")

                destinations {
                    local() // Enable local destinations
                }
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("publishingInfo")
                .withPluginClasspath()
                .build()

        assert(result.task(":publishingInfo")?.outcome == TaskOutcome.SUCCESS)
        assert(result.output.contains("Maven Local:          ✅"))
        assert(result.output.contains("Maven Standalone:     ✅"))
        assert(result.output.contains("GitHub Pages:         ❌"))
        assert(result.output.contains("GitHub Packages:      ❌"))
        assert(result.output.contains("Gradle Plugin Portal: ❌"))
        assert(result.output.contains("Maven Central:        ❌"))
    }

    @Test
    fun `extension configures github shorthand correctly`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")

                github("example/mylib") // GitHub shorthand

                destinations {
                    githubPackages { enabled.set(true) }
                }
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("publishingInfo")
                .withPluginClasspath()
                .build()

        assert(result.task(":publishingInfo")?.outcome == TaskOutcome.SUCCESS)
        // The github shorthand should configure pom.url and scm
        assert(result.output.contains("📦 Publishing Configuration"))
    }

    @Test
    fun `extension configures license shorthands correctly`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")

                pom {
                    license { mit() }
                }
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("publishingInfo")
                .withPluginClasspath()
                .build()

        assert(result.task(":publishingInfo")?.outcome == TaskOutcome.SUCCESS)
    }

    @Test
    fun `extension configures multiple developers correctly`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")

                pom {
                    developer("dev1", "Developer One", "dev1@example.com")
                    developer("dev2", "Developer Two", "dev2@example.com")
                    developer {
                        id.set("dev3")
                        name.set("Developer Three")
                        email.set("dev3@example.com")
                        organization.set("Example Inc")
                        organizationUrl.set("https://example.com")
                    }
                }
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("publishingInfo")
                .withPluginClasspath()
                .build()

        assert(result.task(":publishingInfo")?.outcome == TaskOutcome.SUCCESS)
    }

    @Test
    fun `extension configures scm shorthand correctly`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")

                pom {
                    scm { github("example/mylib") }
                }
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("publishingInfo")
                .withPluginClasspath()
                .build()

        assert(result.task(":publishingInfo")?.outcome == TaskOutcome.SUCCESS)
    }

    @Test
    fun `extension handles empty configuration gracefully`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                // Minimal configuration
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("publishingInfo")
                .withPluginClasspath()
                .build()

        assert(result.task(":publishingInfo")?.outcome == TaskOutcome.SUCCESS)
        assert(result.output.contains("Group:    (not set)"))
        assert(result.output.contains("Version:  (not set)"))
        assert(result.output.contains("Dry Run:  true"))
    }

    @Test
    fun `extension configures complex library groups correctly`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")

                libraryGroups {
                    register("core") {
                        modules.addAll(":core:api", ":core:impl", ":core:utils")
                        description.set("Core library modules")
                        changelogPath.set("docs/core/CHANGELOG.md")
                    }
                    register("ui") {
                        modules.addAll(":ui:components", ":ui:theme")
                        description.set("UI components and themes")
                        changelogPath.set("docs/ui/CHANGELOG.md")
                    }
                    register("platform") {
                        modules.addAll(":platform:jvm", ":platform:js")
                        description.set("Platform-specific implementations")
                    }
                }
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("publishingInfo")
                .withPluginClasspath()
                .build()

        assert(result.task(":publishingInfo")?.outcome == TaskOutcome.SUCCESS)
        assert(result.output.contains("📚 Library Groups:"))
        assert(result.output.contains("• core"))
        assert(result.output.contains("• ui"))
        assert(result.output.contains("• platform"))
    }
}
