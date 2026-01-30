package dev.all4.gradle.release.functional

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional tests for PublishPlugin using Gradle TestKit. These tests run actual Gradle builds to
 * verify plugin behavior.
 */
class PublishPluginFunctionalTest {

    @TempDir lateinit var projectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        buildFile = File(projectDir, "build.gradle.kts")
        settingsFile = File(projectDir, "settings.gradle.kts")

        settingsFile.writeText(
            """
            rootProject.name = "test-project"
            """
                .trimIndent()
        )
    }

    @Test
    fun `plugin can be applied successfully`() {
        buildFile.writeText(
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

        val result =
            GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("tasks", "--stacktrace")
                .build()

        assertThat(result.output).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `publishingInfo task shows configuration`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")
                dryRun.set(true)

                pom {
                    name.set("Test Library")
                    description.set("A test library")
                }

                destinations {
                    mavenLocal {
                        enabled.set(true)
                    }
                }
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("publishingInfo", "--stacktrace")
                .build()

        assertThat(result.output).contains("Publishing Configuration")
        assertThat(result.output).contains("com.example")
        assertThat(result.output).contains("1.0.0")
    }

    @Test
    fun `plugin creates changelog when library group is configured`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")

                libraryGroups {
                    create("core") {
                        modules.set(listOf(":core"))
                        description.set("Core module")
                    }
                }
            }
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

        val changelogFile = File(projectDir, "changelogs/core/CHANGELOG.md")
        assertThat(changelogFile).exists()
    }

    @Test
    fun `plugin works with multiple destinations`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")
                dryRun.set(true)

                destinations {
                    mavenLocal {
                        enabled.set(true)
                    }
                    mavenStandalone {
                        enabled.set(true)
                        path.set(file("build/maven-standalone"))
                    }
                }
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("publishingInfo", "--stacktrace")
                .build()

        assertThat(result.output).contains("Maven Local")
        assertThat(result.output).contains("Maven Standalone")
    }

    @Test
    fun `plugin configures POM metadata correctly`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")

                pom {
                    name.set("My Library")
                    description.set("A great library")
                    url.set("https://github.com/example/mylib")
                    inceptionYear.set("2024")

                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }

                    developer {
                        id.set("johndoe")
                        name.set("John Doe")
                        email.set("john@example.com")
                    }

                    scm {
                        url.set("https://github.com/example/mylib")
                        connection.set("scm:git:git://github.com/example/mylib.git")
                    }
                }
            }
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
