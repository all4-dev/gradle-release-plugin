package dev.all4.gradle.release.functional

import java.io.File
import org.gradle.testkit.runner.GradleRunner

object TestHelpers {
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
}
