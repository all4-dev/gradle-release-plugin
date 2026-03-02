import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    `kotlin-dsl`
    `maven-publish`
    signing
    alias(libs.plugins.gradle.plugin.publish)
    id("dev.all4.gradle.kotlin-config")
    id("dev.all4.gradle.quality")
    id("dev.all4.gradle.test-config")
    id("dev.all4.gradle.dokka")
    id("dev.all4.gradle.kover")
}

group = "dev.all4.gradle"

version = "0.1.0-alpha.11"

// Load publishing credentials from properties file
rootDir.resolve("publishing.properties")
    .takeIf { it.exists() }?.let { propsFile ->
    propsFile.readLines()
        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") && "=" in it }
        .forEach { line ->
            val (key, value) = line.split("=", limit = 2)
            project.ext.set(key.trim(), value.trim())
        }
}

/** Resolve a value that may be a 1Password `op://` reference. */
fun resolveOp(raw: String?): String? {
    if (raw == null || !raw.startsWith("op://")) return raw
    return try {
        val process = ProcessBuilder("op", "read", raw)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0) output else null
    } catch (_: Exception) { null }
}
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

val sonatypeUser: String? = resolveOp(findProperty("sonatype.username") as? String)
    ?: System.getenv("SONATYPE_USERNAME")
val sonatypePassword: String? = resolveOp(findProperty("sonatype.password") as? String)
    ?: System.getenv("SONATYPE_PASSWORD")

val centralStagingDir = layout.buildDirectory.dir("central-staging")

publishing {
    repositories {
        maven {
            name = "maven-standalone"
            url = uri(System.getenv("MAVEN_STANDALONE_PATH") ?: error("MAVEN_STANDALONE_PATH is not set"))
        }
        maven {
            name = "MavenCentral"
            url = centralStagingDir.get().asFile.toURI()
        }
    }

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
    val rawPassphrase = findProperty("signing.gnupg.passphrase") as? String
        ?: System.getenv("SIGNING_GPG_PASSPHRASE")
    val gpgPassphrase = resolveOp(rawPassphrase)
    if (gpgPassphrase != null) {
        project.ext.set("signing.gnupg.passphrase", gpgPassphrase)
    }
    sign(publishing.publications)
}

// Native Central Portal publishing — stages locally, then zips and uploads bundle
val publishToPortal by tasks.registering {
    group = "publishing"
    description = "Uploads staged artifacts to Sonatype Central Portal"
    dependsOn(tasks.named("publishAllPublicationsToMavenCentralRepository"))

    doLast {
        val stagingDir = centralStagingDir.get().asFile
        if (!stagingDir.exists() || stagingDir.listFiles().isNullOrEmpty()) {
            logger.lifecycle("Central Portal: staging directory is empty, nothing to upload.")
            return@doLast
        }

        val user = sonatypeUser ?: error("SONATYPE_USERNAME / mavenCentralUsername not set")
        val pass = sonatypePassword ?: error("SONATYPE_PASSWORD / mavenCentralPassword not set")

        // Zip staging directory
        val zipFile = File(stagingDir.parentFile, "central-staging-${UUID.randomUUID()}.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            stagingDir.walkTopDown().forEach { file ->
                if (file.isDirectory) return@forEach
                if (file.name.contains("maven-metadata")) return@forEach
                val entryPath = file.toRelativeString(stagingDir)
                zos.putNextEntry(ZipEntry(entryPath))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        logger.lifecycle("📦 Central Portal: created bundle ${zipFile.name} (${zipFile.length() / 1024} KB)")

        // Upload to Central Portal
        val baseUrl = "https://central.sonatype.com/api/v1/"
        val deploymentName = "${project.group}-${UUID.randomUUID()}"
        val boundary = "----Boundary${System.currentTimeMillis()}"
        val token = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())

        val url = URL("${baseUrl}publisher/upload?name=$deploymentName&publishingType=AUTOMATIC")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "UserToken $token")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        conn.outputStream.use { os ->
            val header = "--$boundary\r\nContent-Disposition: form-data; name=\"bundle\"; filename=\"${zipFile.name}\"\r\nContent-Type: application/octet-stream\r\n\r\n"
            os.write(header.toByteArray())
            zipFile.inputStream().use { it.copyTo(os) }
            os.write("\r\n--$boundary--\r\n".toByteArray())
        }

        val responseCode = conn.responseCode
        val responseBody = (if (responseCode in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""

        zipFile.delete()
        stagingDir.deleteRecursively()

        if (responseCode in 200..299) {
            logger.lifecycle("✅ Central Portal: uploaded successfully. Deployment ID: $responseBody")
        } else {
            error("❌ Central Portal: upload failed (HTTP $responseCode): $responseBody")
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
