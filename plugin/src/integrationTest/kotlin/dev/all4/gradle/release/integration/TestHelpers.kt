package dev.all4.gradle.release.integration

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

object TestHelpers {
    const val PUBLISHING_INFO_TASK = "publishingInfo"

    private val koverAgentJar: String? = System.getProperty("kover.agent.jar.path")
    private val koverReportPath: String? = System.getProperty("kover.agent.report.path")

    /**
     * Creates a GradleRunner with Kover JVM agent coverage support. Configures org.gradle.jvmargs
     * to attach the Kover agent to the Gradle daemon.
     */
    fun gradleRunner(projectDir: File, vararg arguments: String): GradleRunner {
        if (koverAgentJar != null && koverReportPath != null) {
            val propsFile = File(projectDir, "gradle.properties")
            val existingContent = if (propsFile.exists()) propsFile.readText() else ""

            if (!existingContent.contains("javaagent")) {
                val argsFile = File(projectDir, "kover-agent.args")
                argsFile.writeText("report.file=$koverReportPath\n")

                val jvmArgs =
                    "org.gradle.jvmargs=-javaagent:$koverAgentJar=file:${argsFile.absolutePath}"
                propsFile.appendText("\n$jvmArgs\n")
            }
        }
        return GradleRunner.create()
            .forwardOutput()
            .withProjectDir(projectDir)
            .withArguments(*arguments)
            .withPluginClasspath()
    }

    fun assertTaskSuccess(result: org.gradle.testkit.runner.BuildResult, taskName: String) {
        assert(result.task(":$taskName")?.outcome == TaskOutcome.SUCCESS) {
            "Task $taskName should succeed"
        }
    }

    fun assertOutputContains(result: org.gradle.testkit.runner.BuildResult, vararg texts: String) {
        texts.forEach { text ->
            assert(result.output.contains(text)) { "Output should contain: $text" }
        }
    }

    fun assertDestinationEnabled(
        result: org.gradle.testkit.runner.BuildResult,
        destination: String,
        enabled: Boolean = true,
    ) {
        val status = if (enabled) "✅" else "❌"
        val pattern = Regex("$destination:\\s+$status")
        val state = if (enabled) "enabled" else "disabled"
        assert(pattern.containsMatchIn(result.output)) {
            "Expected '$destination' to be $state ($status)"
        }
    }

    fun File.createFile(name: String, content: String = "fake content"): File {
        val file = File(this, name)
        file.writeText(content)
        return file
    }

    fun createTestArtifact(importDir: File, name: String): File =
        importDir.createFile(name, "fake artifact content")

    fun createChangelog(dir: File): File {
        val changelog = File(dir, "CHANGELOG.md")
        changelog.writeText("# Changelog\n\n## [Unreleased]\n")
        return changelog
    }
}

object TestConstants {
    object FileNames {
        const val BUILD_GRADLE_KTS = "build.gradle.kts"
        const val SETTINGS_GRADLE_KTS = "settings.gradle.kts"
    }

    object PluginIds {
        const val PUBLISH_PLUGIN = "dev.all4.release"
    }

    object TaskNames {
        const val LIST_EXTERNAL_ARTIFACTS = "listExternalArtifacts"
    }
}
