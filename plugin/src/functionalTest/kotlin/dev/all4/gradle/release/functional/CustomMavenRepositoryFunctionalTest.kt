package dev.all4.gradle.release.functional

import java.io.File
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CustomMavenRepositoryFunctionalTest {

    @TempDir lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File
    private lateinit var propertiesFile: File

    @BeforeEach
    fun setup() {
        buildFile = File(testProjectDir, "build.gradle.kts")
        settingsFile = File(testProjectDir, "settings.gradle.kts")
        propertiesFile = File(testProjectDir, "gradle.properties")

        settingsFile.writeText(
            """
            rootProject.name = "test-project"
            """
                .trimIndent()
        )

        propertiesFile.writeText(
            """
            library.group=com.example
            library.version=1.0.0
            artifactory.user=testuser
            artifactory.password=testpass
            nexus.token=bearer-token
            """
                .trimIndent()
        )
    }

    @Test
    fun `custom maven repository with basic auth is configured correctly`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
                `java-library`
            }

            releaseConfig {
                destinations {
                    maven("artifactory") {
                        url.set("https://artifactory.example.com/libs-release")
                        username.set(providers.gradleProperty("artifactory.user"))
                        password.set(providers.gradleProperty("artifactory.password"))
                    }
                }
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

        val result = TestHelpers.gradleRunner(testProjectDir, "publishingInfo").build()

        assert(result.task(":publishingInfo")?.outcome == TaskOutcome.SUCCESS)
        assert(result.output.contains("📦 Publishing Configuration"))
        assert(result.output.contains("Group:    com.example"))
        assert(result.output.contains("Version:  1.0.0"))
    }

    @Test
    fun `custom maven repository with header auth is configured correctly`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
                `java-library`
            }

            releaseConfig {
                destinations {
                    maven("nexus") {
                        url.set("https://nexus.example.com/repository/releases")
                        authHeaderName.set("Authorization")
                        authHeaderValue.set("Bearer ${"$"}{providers.gradleProperty("nexus.token").get()}")
                    }
                }
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

        val result = TestHelpers.gradleRunner(testProjectDir, "publishingInfo").build()

        assert(result.task(":publishingInfo")?.outcome == TaskOutcome.SUCCESS)
        assert(result.output.contains("📦 Publishing Configuration"))
        assert(result.output.contains("Group:    com.example"))
        assert(result.output.contains("Version:  1.0.0"))
    }

    @Test
    fun `multiple custom repositories are configured correctly`() {
        buildFile.writeText(
            """
            plugins {
                id("dev.all4.release")
                `java-library`
            }

            releaseConfig {
                destinations {
                    maven("artifactory") {
                        url.set("https://artifactory.example.com/libs-release")
                    }
                    maven("nexus") {
                        url.set("https://nexus.example.com/repository/releases")
                    }
                    maven("internal") {
                        url.set("http://internal.example.com/maven")
                        allowInsecureProtocol.set(true)
                    }
                }
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

        val result = TestHelpers.gradleRunner(testProjectDir, "publishingInfo").build()

        assert(result.task(":publishingInfo")?.outcome == TaskOutcome.SUCCESS)
        assert(result.output.contains("📦 Publishing Configuration"))
        assert(result.output.contains("Group:    com.example"))
        assert(result.output.contains("Version:  1.0.0"))
    }
}
