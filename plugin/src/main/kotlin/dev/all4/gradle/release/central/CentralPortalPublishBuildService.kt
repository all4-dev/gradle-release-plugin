package dev.all4.gradle.release.central

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
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener

internal abstract class CentralPortalPublishBuildService :
    BuildService<CentralPortalPublishBuildService.Params>,
    AutoCloseable,
    OperationCompletionListener {

    private val logger = Logging.getLogger(CentralPortalPublishBuildService::class.java)
    private var buildFailed = false

    internal interface Params : BuildServiceParameters {
        val username: Property<String>
        val password: Property<String>
        val baseUrl: Property<String>
        val stagingDir: DirectoryProperty
        val groupId: Property<String>
        val autoPublish: Property<Boolean>
    }

    override fun onFinish(event: FinishEvent) {
        if (event.result is FailureResult) {
            buildFailed = true
        }
    }

    override fun close() {
        val stagingDir = parameters.stagingDir.get().asFile
        if (!stagingDir.exists() || stagingDir.listFiles().isNullOrEmpty()) {
            logger.info("Central Portal: staging directory is empty, nothing to upload.")
            return
        }

        if (buildFailed) {
            logger.warn("Central Portal: build failed, skipping upload.")
            return
        }

        try {
            val zipFile = File(stagingDir.parentFile, "central-staging-${UUID.randomUUID()}.zip")
            zipStagingDirectory(stagingDir, zipFile)
            logger.lifecycle(
                "📦 Central Portal: created bundle ${zipFile.name} (${zipFile.length() / 1024} KB)"
            )

            val service =
                CentralPortalService(
                    baseUrl = parameters.baseUrl.get(),
                    username = parameters.username.get(),
                    password = parameters.password.get(),
                )

            val deploymentName = "${parameters.groupId.get()}-${UUID.randomUUID()}"
            val publishingType = if (parameters.autoPublish.get()) "AUTOMATIC" else "USER_MANAGED"

            logger.lifecycle(
                "📤 Central Portal: uploading bundle as '$deploymentName' (publishingType=$publishingType)..."
            )
            val deploymentId = service.upload(deploymentName, publishingType, zipFile)
            logger.lifecycle(
                "✅ Central Portal: uploaded successfully. Deployment ID: $deploymentId"
            )

            zipFile.delete()
        } catch (e: Exception) {
            logger.error("❌ Central Portal: upload failed: ${e.message}", e)
            throw e
        }
    }

    private fun zipStagingDirectory(stagingDir: File, zipFile: File) {
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
    }

    companion object {
        private const val SERVICE_NAME = "central-portal-publish-service"

        fun registerIfAbsent(
            project: Project,
            username: String,
            password: String,
            baseUrl: String,
            stagingDir: File,
            autoPublish: Boolean,
        ) {
            val groupId = project.group.toString()
            val registration =
                project.gradle.sharedServices.registerIfAbsent(
                    SERVICE_NAME,
                    CentralPortalPublishBuildService::class.java,
                ) {
                    maxParallelUsages.set(1)
                    parameters.username.set(username)
                    parameters.password.set(password)
                    parameters.baseUrl.set(baseUrl)
                    parameters.stagingDir.set(stagingDir)
                    parameters.groupId.set(groupId)
                    parameters.autoPublish.set(autoPublish)
                }

            val registry = project.extensions.findByType(BuildEventsListenerRegistry::class.java)
            registry?.onTaskCompletion(registration)
        }
    }
}
