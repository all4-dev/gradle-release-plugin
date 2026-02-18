package dev.all4.gradle.release.functional

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PublishPluginAdditionalTest {

    @TempDir lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File
    private lateinit var subprojectBuildFile: File

    @BeforeEach
    fun setup() {
        buildFile = File(testProjectDir, "build.gradle.kts")
        settingsFile = File(testProjectDir, "settings.gradle.kts")

        val subprojectDir = File(testProjectDir, "core")
        subprojectDir.mkdirs()
        subprojectBuildFile = File(subprojectDir, "build.gradle.kts")

        settingsFile.writeText(
            """
            rootProject.name = "test-project"
            include("core")
            """
                .trimIndent()
        )
    }

    @Test
    fun `plugin creates aggregate tasks for library groups`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")
                dryRun.set(false)

                libraryGroups {
                    register("core") {
                        modules.addAll(":core")
                    }
                    register("utils") {
                        modules.addAll(":core") // Reuse for test
                    }
                }

                destinations {
                    mavenStandalone { enabled.set(true) }
                }
            }
            """
                .trimIndent()
        )

        subprojectBuildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
                `java-library`
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--group=publishing")
                .withPluginClasspath()
                .build()

        assert(result.task(":tasks")?.outcome == TaskOutcome.SUCCESS)
        assert(result.output.contains("publishCoreToStandalone"))
        assert(result.output.contains("publishUtilsToStandalone"))
        assert(result.output.contains("publishAllToStandalone"))
    }

    @Test
    fun `plugin configures maven publishing on subproject`() {
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
                        modules.addAll(":core")
                    }
                }

                destinations {
                    mavenStandalone { enabled.set(true) }
                }
            }
            """
                .trimIndent()
        )

        subprojectBuildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
                `java-library`
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation("com.google.guava:guava:31.1-jre")
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments(":core:tasks", "--group=publishing")
                .withPluginClasspath()
                .build()

        assert(result.task(":core:tasks")?.outcome == TaskOutcome.SUCCESS)
        assert(result.output.contains("publishToMavenLocal"))
        assert(result.output.contains("publishAllPublicationsToMavenStandaloneRepository"))
    }

    @Test
    fun `plugin respects dry run mode`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")
                dryRun.set(true) // Enable dry run

                libraryGroups {
                    register("core") {
                        modules.addAll(":core")
                    }
                }

                destinations {
                    mavenStandalone { enabled.set(true) }
                }
            }
            """
                .trimIndent()
        )

        subprojectBuildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
                `java-library`
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("publishCoreToStandalone")
                .withPluginClasspath()
                .build()

        assert(result.task(":publishCoreToStandalone")?.outcome == TaskOutcome.SUCCESS)
        assert(result.output.contains("🔍 [DRY-RUN]"))
        assert(!result.output.contains("📦 Publishing"))
    }

    @Test
    fun `plugin fails with friendly error when library group has invalid module path`() {
        settingsFile.writeText(
            """
            rootProject.name = "test-project"
            include(":libs:logger")
            """
                .trimIndent()
        )

        val loggerDir = File(testProjectDir, "libs/logger")
        loggerDir.mkdirs()
        File(loggerDir, "build.gradle.kts")
            .writeText(
                """
            plugins {
                id("dev.all4.release")
                `java-library`
            }
            version = "1.0.0"
            """
                    .trimIndent()
            )

        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                libraryGroups {
                    register("logger") {
                        modules.add(":libs:core:logger")
                    }
                }
                destinations {
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
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("publishLoggerToStandalone", "--stacktrace")
                .withPluginClasspath()
                .buildAndFail()

        assert(result.output.contains("Invalid module path(s)"))
        assert(result.output.contains(":libs:core:logger"))
        assert(result.output.contains("did you mean \":libs:logger\"?"))
        assert(result.output.contains("✅ Solution"))
    }

    @Test
    fun `plugin fails with friendly error when module version is unspecified`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                libraryGroups {
                    register("core") {
                        modules.add(":core")
                    }
                }
                destinations {
                    mavenStandalone {
                        enabled.set(true)
                        path.set(file("build/maven-standalone"))
                    }
                }
            }
            """
                .trimIndent()
        )

        subprojectBuildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
                `java-library`
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("publishCoreToStandalone", "--stacktrace")
                .withPluginClasspath()
                .buildAndFail()

        assert(result.output.contains("version \"unspecified\""))
        assert(result.output.contains(":core (version=unspecified)"))
        assert(result.output.contains("✅ Solution"))
        assert(result.output.contains("version = property(\"version.core\").toString()"))
    }

    @Test
    fun `plugin auto-detects root vs subproject`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }
            """
                .trimIndent()
        )

        val rootResult =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("publishingInfo")
                .withPluginClasspath()
                .build()

        assert(rootResult.task(":publishingInfo")?.outcome == TaskOutcome.SUCCESS)
        assert(rootResult.output.contains("📦 Publishing Configuration"))

        subprojectBuildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
                `java-library`
            }
            """
                .trimIndent()
        )

        val subprojectResult =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments(":core:publishingInfo")
                .withPluginClasspath()
                .build()

        assert(subprojectResult.task(":core:publishingInfo")?.outcome == TaskOutcome.SUCCESS)
    }

    @Test
    fun `plugin configures pom metadata correctly`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")

                pom {
                    name.set("Test Library")
                    description.set("A test library")
                    url.set("https://example.com")
                    inceptionYear.set("2024")

                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }

                    developer("testdev", "Test Developer", "test@example.com")

                    scm {
                        url.set("https://github.com/example/test")
                        connection.set("scm:git:git://github.com/example/test.git")
                        developerConnection.set("scm:git:ssh://github.com/example/test.git")
                    }
                }

                libraryGroups {
                    register("core") {
                        modules.addAll(":core")
                    }
                }

                destinations {
                    mavenStandalone { enabled.set(true) }
                }
            }
            """
                .trimIndent()
        )

        subprojectBuildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
                `java-library`
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments(":core:tasks", "--group=publishing")
                .withPluginClasspath()
                .build()

        assert(result.task(":core:tasks")?.outcome == TaskOutcome.SUCCESS)
        assert(result.output.contains("publishToMavenLocal"))
        assert(result.output.contains("publishAllPublicationsToMavenStandaloneRepository"))
    }

    @Test
    fun `plugin configures custom repositories correctly`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")

                destinations {
                    maven("custom") {
                        url.set("https://custom.example.com/maven")
                        username.set("user")
                        password.set("pass")
                    }
                }

                libraryGroups {
                    register("core") {
                        modules.addAll(":core")
                    }
                }
            }
            """
                .trimIndent()
        )

        subprojectBuildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
                `java-library`
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments(":core:publishingInfo")
                .withPluginClasspath()
                .build()

        assert(result.task(":core:publishingInfo")?.outcome == TaskOutcome.SUCCESS)
    }

    @Test
    fun `plugin handles missing library groups gracefully`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")

                destinations {
                    mavenStandalone { enabled.set(true) }
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
        assert(result.output.contains("(none configured)"))
    }
}
