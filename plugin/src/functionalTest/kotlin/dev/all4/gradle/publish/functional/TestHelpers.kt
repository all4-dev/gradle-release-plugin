package dev.all4.gradle.release.functional

import java.io.File
import org.gradle.testkit.runner.GradleRunner

/** Test helpers for functional tests with Kover JVM agent coverage support */
object TestHelpers {
    // Kover JVM agent paths from system properties
    private val koverAgentJar: String? = System.getProperty("kover.agent.jar.path")
    private val koverReportPath: String? = System.getProperty("kover.agent.report.path")

    /**
     * Creates a GradleRunner with Kover JVM agent coverage support. Configures org.gradle.jvmargs
     * to attach the Kover agent to the Gradle daemon.
     */
    fun gradleRunner(projectDir: File, vararg arguments: String): GradleRunner {
        // Configure Kover JVM agent via gradle.properties
        if (koverAgentJar != null && koverReportPath != null) {
            val propsFile = File(projectDir, "gradle.properties")
            val existingContent = if (propsFile.exists()) propsFile.readText() else ""

            if (!existingContent.contains("javaagent")) {
                // Create an args file for Kover agent
                val argsFile = File(projectDir, "kover-agent.args")
                argsFile.writeText("report.file=$koverReportPath\n")

                // Add a JVM agent to gradle.properties
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
}
