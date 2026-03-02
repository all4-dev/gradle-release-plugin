package dev.all4.gradle.release.central

import dev.all4.gradle.release.util.OnePasswordSupport
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

internal abstract class CentralPortalPublishBuildService :
    BuildService<CentralPortalPublishBuildService.Params>,
    AutoCloseable {

    private val logger = Logging.getLogger(CentralPortalPublishBuildService::class.java)

    internal interface Params : BuildServiceParameters {
        val rawUsername: Property<String>
        val rawPassword: Property<String>
        val baseUrl: Property<String>
        val stagingDir: DirectoryProperty
        val groupId: Property<String>
        val autoPublish: Property<Boolean>
        val signingPassphrase: Property<String>
    }

    override fun close() {
        // Safety net: clean up leftover staging dir if it wasn't already
        // handled by uploadStagingBundle(). Upload is done from doLast only.
        val stagingDir = parameters.stagingDir.get().asFile
        if (stagingDir.exists()) {
            logger.info("Central Portal: cleaning up staging directory.")
            stagingDir.deleteRecursively()
        }
    }

    companion object {
        private const val SERVICE_NAME = "central-portal-publish-service"
        private val logger = Logging.getLogger(CentralPortalPublishBuildService::class.java)

        /**
         * Zips the root project's central-staging directory and uploads it to Central Portal.
         * Called from aggregate publish tasks (e.g. publishKoreToMavenCentral) after all
         * subproject publications have staged their artifacts.
         */
        fun uploadStagingBundle(
            project: Project,
            logger: org.gradle.api.logging.Logger,
        ) {
            val stagingDir = project.rootProject.layout.buildDirectory
                .dir("central-staging").get().asFile

            if (!stagingDir.exists() || stagingDir.listFiles().isNullOrEmpty()) {
                logger.lifecycle("Central Portal: staging directory is empty, nothing to upload.")
                return
            }

            // Inject empty javadoc JARs where missing (Central Portal requirement)
            val signingPassphrase = System.getenv("SIGNING_GPG_PASSPHRASE")
                ?: project.findProperty("signing.gnupg.passphrase") as? String
            injectMissingJavadocJars(stagingDir, logger, signingPassphrase)

            val rawUser = project.findProperty("sonatype.username") as? String
                ?: System.getenv("SONATYPE_USERNAME") ?: ""
            val rawPassword = project.findProperty("sonatype.password") as? String
                ?: System.getenv("SONATYPE_PASSWORD") ?: ""

            val username = OnePasswordSupport.resolveHeadless(rawUser)
            val password = OnePasswordSupport.resolveHeadless(rawPassword)

            if (username.isBlank() || password.isBlank()) {
                throw org.gradle.api.GradleException(
                    "Central Portal: credentials not set. Set sonatype.username/sonatype.password or SONATYPE_USERNAME/SONATYPE_PASSWORD."
                )
            }

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
            logger.lifecycle(
                "📦 Central Portal: created bundle ${zipFile.name} (${zipFile.length() / 1024} KB)"
            )

            val service = CentralPortalService(
                baseUrl = "https://central.sonatype.com/api/v1/",
                username = username,
                password = password,
            )

            val deploymentName = "${project.group}-${UUID.randomUUID()}"
            logger.lifecycle(
                "📤 Central Portal: uploading bundle as '$deploymentName' (publishingType=AUTOMATIC)..."
            )
            val deploymentId = service.upload(deploymentName, "AUTOMATIC", zipFile)
            logger.lifecycle(
                "✅ Central Portal: uploaded successfully. Deployment ID: $deploymentId"
            )

            zipFile.delete()
            stagingDir.deleteRecursively()
        }

        /**
         * Scans the staging directory for version directories that have a .pom but no
         * -javadoc.jar, and creates empty javadoc JARs for them. Central Portal requires
         * javadoc JARs for all published components.
         */
        private fun injectMissingJavadocJars(
            stagingDir: File,
            logger: org.gradle.api.logging.Logger,
            signingPassphrase: String? = null,
        ) {
            stagingDir.walkTopDown()
                .filter { it.isFile && it.extension == "pom" && !it.name.contains("maven-metadata") }
                .forEach { pomFile ->
                    val versionDir = pomFile.parentFile
                    val baseName = pomFile.nameWithoutExtension
                    val javadocJar = File(versionDir, "$baseName-javadoc.jar")
                    if (!javadocJar.exists()) {
                        // Create a valid empty JAR (just a ZIP with manifest)
                        ZipOutputStream(FileOutputStream(javadocJar)).use { zos ->
                            zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                            zos.write("Manifest-Version: 1.0\n".toByteArray())
                            zos.closeEntry()
                        }
                        // Generate checksums
                        generateChecksums(javadocJar)
                        // GPG sign
                        gpgSign(javadocJar, signingPassphrase)
                        logger.lifecycle("  + injected javadoc (with checksums + signature): ${javadocJar.name}")
                    }
                }
        }

        private fun generateChecksums(file: File) {
            val bytes = file.readBytes()
            val md5 = java.security.MessageDigest.getInstance("MD5").digest(bytes)
            File(file.path + ".md5").writeText(md5.joinToString("") { "%02x".format(it) })
            val sha1 = java.security.MessageDigest.getInstance("SHA-1").digest(bytes)
            File(file.path + ".sha1").writeText(sha1.joinToString("") { "%02x".format(it) })
        }

        private fun gpgSign(file: File, passphrase: String? = null) {
            val gpgExe = listOf("/opt/homebrew/bin/gpg", "/usr/local/bin/gpg", "/usr/bin/gpg")
                .firstOrNull { File(it).exists() } ?: "gpg"
            val effectivePassphrase = passphrase
                ?: System.getenv("SIGNING_GPG_PASSPHRASE")
            val command = mutableListOf(
                gpgExe, "--batch", "--yes", "--pinentry-mode", "loopback",
                "--armor", "--detach-sign",
            )
            if (effectivePassphrase != null) {
                command.addAll(listOf("--passphrase-fd", "0"))
            }
            command.add(file.absolutePath)
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            if (effectivePassphrase != null) {
                process.outputStream.bufferedWriter().use { it.write(effectivePassphrase) }
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val output = process.inputStream.bufferedReader().readText()
                logger.warn("GPG signing exited with code $exitCode for ${file.name}: $output")
            }
        }

        fun registerIfAbsent(
            project: Project,
            rawUsername: String,
            rawPassword: String,
            baseUrl: String,
            stagingDir: File,
            autoPublish: Boolean,
        ) {
            val groupId = project.group.toString()
            val signingPassphrase = System.getenv("SIGNING_GPG_PASSPHRASE")
                ?: project.findProperty("signing.gnupg.passphrase") as? String
                ?: ""
            val registration =
                project.gradle.sharedServices.registerIfAbsent(
                    SERVICE_NAME,
                    CentralPortalPublishBuildService::class.java,
                ) {
                    maxParallelUsages.set(1)
                    parameters.rawUsername.set(rawUsername)
                    parameters.rawPassword.set(rawPassword)
                    parameters.baseUrl.set(baseUrl)
                    parameters.stagingDir.set(stagingDir)
                    parameters.groupId.set(groupId)
                    parameters.autoPublish.set(autoPublish)
                    parameters.signingPassphrase.set(signingPassphrase)
                }

            // Ensure publish tasks declare usage so Gradle keeps the service alive
            // and calls close() after all publish tasks complete
            project.tasks.configureEach {
                if (name.startsWith("publish") && name.contains("MavenCentral")) {
                    usesService(registration)
                }
            }
        }
    }
}
