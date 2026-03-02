package dev.all4.gradle.release.functional

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ValidateCredentialsFunctionalTest {

    @TempDir lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        buildFile = File(testProjectDir, "build.gradle.kts")
        settingsFile = File(testProjectDir, "settings.gradle.kts")

        settingsFile.writeText("""rootProject.name = "test-project"""")

        // Initialize a git repo in the temp directory
        git("init")
        git("config", "user.email", "test@test.com")
        git("config", "user.name", "Test")
    }

    private fun git(vararg args: String): String {
        val process =
            ProcessBuilder("git", *args)
                .directory(testProjectDir)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        return output
    }

    private fun minimalBuildScript(): String =
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

    @Test
    fun `task passes when properties file does not exist`() {
        buildFile.writeText(minimalBuildScript())

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("validatePublishingCredentials")
                .withPluginClasspath()
                .build()

        assert(result.task(":validatePublishingCredentials")?.outcome == TaskOutcome.SUCCESS)
    }

    @Test
    fun `task passes when properties file is gitignored`() {
        buildFile.writeText(minimalBuildScript())

        File(testProjectDir, ".gitignore").writeText("publishing.properties\n")
        File(testProjectDir, "publishing.properties").writeText("sonatype.username=secret\n")

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("validatePublishingCredentials")
                .withPluginClasspath()
                .build()

        assert(result.task(":validatePublishingCredentials")?.outcome == TaskOutcome.SUCCESS)
    }

    @Test
    fun `task fails when properties file is not gitignored`() {
        buildFile.writeText(minimalBuildScript())

        File(testProjectDir, "publishing.properties").writeText("sonatype.username=secret\n")

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("validatePublishingCredentials")
                .withPluginClasspath()
                .buildAndFail()

        assert(result.output.contains("publishing.properties is not ignored by git"))
        assert(result.output.contains("Add \"publishing.properties\" to your .gitignore"))
        assert(result.output.contains("git rm --cached"))
    }

    @Test
    fun `task fails when properties file is gitignored but already tracked`() {
        buildFile.writeText(minimalBuildScript())

        // Create and commit the file first
        File(testProjectDir, "publishing.properties").writeText("sonatype.username=secret\n")
        git("add", "publishing.properties")
        git("commit", "-m", "add props")

        // Now add to gitignore — git check-ignore still reports it as not ignored
        // because tracked files override gitignore
        File(testProjectDir, ".gitignore").writeText("publishing.properties\n")

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("validatePublishingCredentials")
                .withPluginClasspath()
                .buildAndFail()

        assert(result.output.contains("publishing.properties is not ignored by git"))
    }

    @Test
    fun `task is wired as dependency of publish tasks`() {
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

        settingsFile.writeText(
            """
            rootProject.name = "test-project"
            include("core")
            """
                .trimIndent()
        )

        val coreDir = File(testProjectDir, "core").apply { mkdirs() }
        File(coreDir, "build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("dev.all4.release")
                    `java-library`
                }
                """
                    .trimIndent()
            )

        // Create un-ignored properties file to trigger failure
        File(testProjectDir, "publishing.properties").writeText("sonatype.username=secret\n")

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("publishCoreToStandalone", "--dry-run")
                .withPluginClasspath()
                .build()

        // dry-run prints the task graph — validatePublishingCredentials should be listed
        assert(result.output.contains("validatePublishingCredentials"))
    }

    @Test
    fun `task uses custom propertiesFile path from extension`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
            }

            releaseConfig {
                group.set("com.example")
                version.set("1.0.0")
                propertiesFile.set("gradle/credentials.properties")
            }
            """
                .trimIndent()
        )

        // Create the file at the custom path, not gitignored
        File(testProjectDir, "gradle").mkdirs()
        File(testProjectDir, "gradle/credentials.properties")
            .writeText("sonatype.username=secret\n")

        val result =
            GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir)
                .withArguments("validatePublishingCredentials")
                .withPluginClasspath()
                .buildAndFail()

        assert(result.output.contains("credentials.properties is not ignored by git"))
    }
}
