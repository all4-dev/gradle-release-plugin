plugins {
    `kotlin-dsl`
    signing
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.maven.publish)
    id("dev.all4.gradle.kotlin-config")
    id("dev.all4.gradle.quality")
    id("dev.all4.gradle.test-config")
    id("dev.all4.gradle.dokka")
    id("dev.all4.gradle.kover")
}

group = "dev.all4.gradle"

version = "0.1.0-alpha.4"

// Kover configurations from convention plugin
val koverCli: Configuration by configurations
val koverAgent: Configuration by configurations
val koverAgentReport: Provider<RegularFile> = layout.buildDirectory.file("kover/agent-report.ic")

dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.testing)
    testImplementation(gradleTestKit())
}

val originUrl =
    providers
        .exec { commandLine("git", "remote", "get-url", "origin") }
        .standardOutput
        .asText
        .map { it.trim() }
        .map { url ->
            // Convert SSH format to HTTPS
            when {
                url.startsWith("git@github.com:") ->
                    url.replace("git@github.com:", "https://github.com/")
                        .removeSuffix(".git")
                url.startsWith("git@") ->
                    url.replace(Regex("git@([^:]+):"), "https://$1/")
                        .removeSuffix(".git")
                else -> url.removeSuffix(".git")
            }
        }

gradlePlugin {
    website.set(originUrl)
    vcsUrl.set(originUrl)

    plugins {
        create("release") {
            id = "dev.all4.release"
            displayName = "All4dev Gradle Release Plugin"
            description =
                "A complete release toolkit: version bumping, changelog generation, git tagging, GitHub releases, and multi-destination publishing (Maven Central, GitHub Packages, etc.)."
            tags.set(
                listOf(
                    "publishing",
                    "maven",
                    "maven-central",
                    "github-packages",
                    "kotlin",
                    "multiplatform",
                )
            )
            implementationClass = "dev.all4.gradle.release.ReleasePlugin"
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("Gradle Release Plugin")
            description.set(
                "A complete release toolkit: version bumping, changelog generation, " +
                "git tagging, GitHub releases, and multi-destination publishing."
            )
            url.set("https://github.com/all4-dev/gradle-release-plugin")
            inceptionYear.set("2025")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            developers {
                developer {
                    id.set("liviolopez")
                    name.set("Livio Lopez")
                    email.set("dev@all4.dev")
                }
            }
            scm {
                url.set("https://github.com/all4-dev/gradle-release-plugin")
                connection.set("scm:git:git://github.com/all4-dev/gradle-release-plugin.git")
                developerConnection.set("scm:git:ssh://github.com/all4-dev/gradle-release-plugin.git")
            }
        }
    }

}

signing {
    useGpgCmd()
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)

    pom {
        name.set("Gradle Release Plugin")
        description.set(
            "A complete release toolkit: version bumping, changelog generation, " +
            "git tagging, GitHub releases, and multi-destination publishing."
        )
        url.set("https://github.com/all4-dev/gradle-release-plugin")
        inceptionYear.set("2025")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("liviolopez")
                name.set("Livio Lopez")
                email.set("dev@all4.dev")
            }
        }
        scm {
            url.set("https://github.com/all4-dev/gradle-release-plugin")
            connection.set("scm:git:git://github.com/all4-dev/gradle-release-plugin.git")
            developerConnection.set("scm:git:ssh://github.com/all4-dev/gradle-release-plugin.git")
        }
    }
}

// Configure Kover agent for functional/integration tests
val functionalTestTask = tasks.named<Test>("functionalTestTask")
val integrationTestTask = tasks.named<Test>("integrationTestTask")

listOf(functionalTestTask, integrationTestTask).forEach { testTask ->
    testTask.configure {
        doFirst {
            val agentJar = koverAgent.filter { it.name.startsWith("kover-jvm-agent") }.singleFile
            systemProperty("kover.agent.jar.path", agentJar.absolutePath)
            systemProperty("kover.agent.report.path", koverAgentReport.get().asFile.absolutePath)
            layout.buildDirectory.dir("tmp/${name}/work/.gradle-test-kit/caches")
                .get().asFile.deleteRecursively()
        }
    }
}

// Kover agent report tasks
fun koverReportArgs(reports: List<File>, htmlDir: File) = mutableListOf<String>().apply {
    add("report")
    reports.forEach { add(it.absolutePath) }
    addAll(listOf("--classfiles", tasks.compileKotlin.get().destinationDirectory.get().asFile.absolutePath))
    addAll(listOf("--html", htmlDir.absolutePath))
    addAll(listOf("--exclude", "org.gradle.kotlin.dsl.*"))
    sourceSets.main.get().kotlin.sourceDirectories.forEach { addAll(listOf("--src", it.canonicalPath)) }
}

val koverAgentHtmlReport by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Generates Kover HTML report from GradleRunner tests"
    dependsOn(functionalTestTask, integrationTestTask)
    mainClass.set("kotlinx.kover.cli.MainKt")
    classpath = koverCli
    args = koverReportArgs(
        listOf(koverAgentReport.get().asFile),
        layout.buildDirectory.dir("reports/kover/agent-html").get().asFile
    )
}

val koverMergedHtmlReport by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Generates merged Kover HTML report (all tests)"
    dependsOn(tasks.test, functionalTestTask, integrationTestTask, "koverHtmlReport")
    finalizedBy(koverAgentHtmlReport)
    mainClass.set("kotlinx.kover.cli.MainKt")
    classpath = koverCli
    args = koverReportArgs(
        listOf(layout.buildDirectory.file("kover/test.ic").get().asFile, koverAgentReport.get().asFile),
        layout.buildDirectory.dir("reports/kover/merged-html").get().asFile
    )
    doLast {
        val dir = layout.buildDirectory.dir("reports/kover").get().asFile
        logger.lifecycle("\n✅ Coverage: file://${dir}/html  file://${dir}/agent-html  file://${dir}/merged-html")
    }
}
