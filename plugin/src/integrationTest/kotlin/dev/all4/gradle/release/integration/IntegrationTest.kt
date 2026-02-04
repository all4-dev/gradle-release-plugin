package dev.all4.gradle.release.integration

import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class IntegrationTest {

    @TempDir lateinit var testProjectDir: File

    @Test
    fun `complete multi-project publishing workflow`() {
        val settingsFile = File(testProjectDir, TestConstants.FileNames.SETTINGS_GRADLE_KTS)

        settingsFile.writeText(
            """
            rootProject.name = "my-awesome-library"
            include(":core", ":utils", ":api", ":impl")
            """
        )

        listOf("core", "utils", "api", "impl").forEach { subproject ->
            val subprojectDir = File(testProjectDir, subproject)
            subprojectDir.mkdirs()

            File(subprojectDir, TestConstants.FileNames.BUILD_GRADLE_KTS)
                .writeText(
                    """
                plugins {
                    id("${TestConstants.PluginIds.PUBLISH_PLUGIN}")
                    `java-library`
                }
                """
                )
        }

        val buildFile = File(testProjectDir, TestConstants.FileNames.BUILD_GRADLE_KTS)

        buildFile.writeText(
            """
            plugins {
                id("${TestConstants.PluginIds.PUBLISH_PLUGIN}")
            }

            releaseConfig {
                group.set("com.example.mylibrary")
                version.set("2.1.0")
                dryRun.set(false)
                autoPublishOnBuild.set(true)

                destinations {
                    local()
                    production()
                }

                libraryGroups {
                    register("core") {
                        modules.addAll(":core", ":utils")
                        changelogPath.set("changelogs/core")
                    }
                    register("platform") {
                        modules.addAll(":api", ":impl")
                        changelogPath.set("changelogs/platform")
                    }
                }

                github("myorg/my-awesome-library")

                pom {
                    name.set("My Awesome Library")
                    description.set("A comprehensive library for awesome things")
                    url.set("https://github.com/myorg/my-awesome-library")
                    inceptionYear.set("2023")

                    license {
                        apache2()
                    }

                    developer("john-doe", "John Doe", "john@example.com")
                    developer("jane-smith", "Jane Smith", "jane@example.com")

                    scm {
                        github("myorg/my-awesome-library")
                    }
                }
            }
            """
        )

        val changelogsDir = File(testProjectDir, "changelogs")
        val coreChangelogDir = File(changelogsDir, "core")
        val platformChangelogDir = File(changelogsDir, "platform")

        coreChangelogDir.mkdirs()
        platformChangelogDir.mkdirs()

        TestHelpers.createChangelog(coreChangelogDir)
        TestHelpers.createChangelog(platformChangelogDir)

        val externalDir = File(testProjectDir, "external")
        externalDir.mkdirs()

        TestHelpers.createTestArtifact(externalDir, "com.example__legacy-lib__1.0.0.jar")
        TestHelpers.createTestArtifact(externalDir, "com.example__legacy-utils__2.0.0.aar")

        val result =
            TestHelpers.gradleRunner(testProjectDir, TestHelpers.PUBLISHING_INFO_TASK).build()

        TestHelpers.assertTaskSuccess(result, TestHelpers.PUBLISHING_INFO_TASK)

        TestHelpers.assertOutputContains(result, "core")
        TestHelpers.assertOutputContains(result, "platform")

        TestHelpers.assertDestinationEnabled(result, "Maven Local", true)
        TestHelpers.assertDestinationEnabled(result, "Maven Standalone", true)
        TestHelpers.assertDestinationEnabled(result, "Maven Central", true)
        TestHelpers.assertDestinationEnabled(result, "GitHub Packages", true)
    }

    @Test
    fun `minimal configuration workflow`() {
        val settingsFile = File(testProjectDir, TestConstants.FileNames.SETTINGS_GRADLE_KTS)

        settingsFile.writeText(
            """
            rootProject.name = "minimal-lib"
            include(":core")
            """
        )

        File(
                File(testProjectDir, "core").apply { mkdirs() },
                TestConstants.FileNames.BUILD_GRADLE_KTS,
            )
            .writeText(
                """
            plugins {
                id("${TestConstants.PluginIds.PUBLISH_PLUGIN}")
                `java-library`
            }
            """
            )

        File(testProjectDir, TestConstants.FileNames.BUILD_GRADLE_KTS)
            .writeText(
                """
            plugins {
                id("${TestConstants.PluginIds.PUBLISH_PLUGIN}")
            }

            releaseConfig {
                group.set("com.example.minimal")
                version.set("1.0.0")

                destinations {
                    local()
                }

                libraryGroups {
                    register("core") {
                        modules.add(":core")
                    }
                }
            }
            """
            )

        val result =
            TestHelpers.gradleRunner(testProjectDir, TestHelpers.PUBLISHING_INFO_TASK).build()

        TestHelpers.assertTaskSuccess(result, TestHelpers.PUBLISHING_INFO_TASK)
        TestHelpers.assertOutputContains(result, "core")
        TestHelpers.assertDestinationEnabled(result, "Maven Local", true)
        TestHelpers.assertDestinationEnabled(result, "Maven Standalone", true)
        TestHelpers.assertDestinationEnabled(result, "Maven Central", false)
    }

    @Test
    fun `GitHub-only publishing workflow`() {
        val settingsFile = File(testProjectDir, TestConstants.FileNames.SETTINGS_GRADLE_KTS)

        settingsFile.writeText(
            """
            rootProject.name = "github-lib"
            include(":api", ":impl")
            """
        )

        listOf("api", "impl").forEach { subproject ->
            File(
                    File(testProjectDir, subproject).apply { mkdirs() },
                    TestConstants.FileNames.BUILD_GRADLE_KTS,
                )
                .writeText(
                    """
                plugins {
                    id("${TestConstants.PluginIds.PUBLISH_PLUGIN}")
                    `java-library`
                }
                """
                )
        }

        File(testProjectDir, TestConstants.FileNames.BUILD_GRADLE_KTS)
            .writeText(
                """
            plugins {
                id("${TestConstants.PluginIds.PUBLISH_PLUGIN}")
            }

            releaseConfig {
                group.set("com.github.user")
                version.set("3.0.0")
                dryRun.set(false)

                destinations {
                    githubPackages.enabled.set(true)
                }

                github("user/github-lib")

                pom {
                    license {
                        mit()
                    }

                    developer("github-user", "GitHub User", "user@example.com")
                }

                libraryGroups {
                    register("library") {
                        modules.addAll(":api", ":impl")
                    }
                }
            }
            """
            )

        val result =
            TestHelpers.gradleRunner(testProjectDir, TestHelpers.PUBLISHING_INFO_TASK).build()

        TestHelpers.assertTaskSuccess(result, TestHelpers.PUBLISHING_INFO_TASK)
        TestHelpers.assertOutputContains(result, "library")
        TestHelpers.assertDestinationEnabled(result, "GitHub Packages", true)
        TestHelpers.assertDestinationEnabled(result, "Maven Local", false)
    }

    @Test
    fun `custom maven repositories workflow`() {
        val settingsFile = File(testProjectDir, TestConstants.FileNames.SETTINGS_GRADLE_KTS)

        settingsFile.writeText(
            """
            rootProject.name = "enterprise-lib"
            include(":core", ":utils")
            """
        )

        listOf("core", "utils").forEach { subproject ->
            File(
                    File(testProjectDir, subproject).apply { mkdirs() },
                    TestConstants.FileNames.BUILD_GRADLE_KTS,
                )
                .writeText(
                    """
                plugins {
                    id("${TestConstants.PluginIds.PUBLISH_PLUGIN}")
                    `java-library`
                }
                """
                )
        }

        File(testProjectDir, TestConstants.FileNames.BUILD_GRADLE_KTS)
            .writeText(
                """
            plugins {
                id("${TestConstants.PluginIds.PUBLISH_PLUGIN}")
            }

            releaseConfig {
                group.set("com.enterprise.library")
                version.set("1.5.2")
                dryRun.set(false)

                destinations {
                    maven("enterprise-release") {
                        url.set("https://repo.enterprise.com/releases")
                        username.set("release-user")
                        password.set("release-pass")
                    }
                }

                libraryGroups {
                    register("foundation") {
                        modules.addAll(":core", ":utils")
                    }
                }

                pom {
                    name.set("Enterprise Library")
                    description.set("Internal enterprise library")
                    inceptionYear.set("2020")
                }
            }
            """
            )

        val result =
            TestHelpers.gradleRunner(testProjectDir, TestHelpers.PUBLISHING_INFO_TASK).build()

        TestHelpers.assertTaskSuccess(result, TestHelpers.PUBLISHING_INFO_TASK)
        TestHelpers.assertOutputContains(result, "foundation")
        TestHelpers.assertOutputContains(result, ":core")
        TestHelpers.assertOutputContains(result, ":utils")
    }
}
