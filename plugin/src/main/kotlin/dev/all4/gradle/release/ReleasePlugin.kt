package dev.all4.gradle.release

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.publish.PublishingExtension as GradlePublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.authentication.http.HttpHeaderAuthentication
import dev.all4.gradle.release.util.capitalized
import dev.all4.gradle.release.util.detectRemoteUrl
import dev.all4.gradle.release.util.toTaskName
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.credentials
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.withType

/**
 * Unified publishing plugin that auto-detects whether it's applied to the root project or a
 * subproject and configures accordingly.
 * - **Root project**: Creates the `releaseConfig` extension and registers aggregate tasks.
 * - **Subprojects**: Reads configuration from the root's extension and configures repositories/POM.
 *
 * Usage:
 * ```kotlin
 * // In root build.gradle.kts
 * plugins {
 *   id("dev.all4.release")
 * }
 *
 * releaseConfig {
 *   group.set("com.example")
 *   version.set("1.0.0")
 *   // ...
 * }
 *
 * // In submodule build.gradle.kts
 * plugins {
 *   id("dev.all4.release")
 * }
 * // No additional configuration needed - reads from root
 * ```
 */
public class ReleasePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        if (target == target.rootProject) {
            applyToRoot(target)
        } else {
            applyToModule(target)
        }
    }

    // ========== ROOT PROJECT CONFIGURATION ==========

    private fun applyToRoot(project: Project) {
        with(project) {
            val ext =
                extensions.create<PublishingExtension>(
                    "releaseConfig",
                    PublishingExtension::class.java,
                )
            configureDefaults(ext)

            // Register importArtifact task
            tasks.register("importArtifact", dev.all4.gradle.release.tasks.ImportArtifactTask::class.java)

            // Register generateChangelog task (generic)
            tasks.register("generateChangelog", dev.all4.gradle.release.tasks.GenerateChangelogTask::class.java)

            // Register createRelease task
            tasks.register("createRelease", dev.all4.gradle.release.tasks.CreateReleaseTask::class.java)

            // Register bumpVersion task
            tasks.register("bumpVersion", dev.all4.gradle.release.tasks.BumpVersionTask::class.java)

            afterEvaluate {
                ensureChangelogs(ext)
                registerAggregateTasks(ext)
                registerChangelogTasks(ext)
            }
        }
    }

    private fun Project.configureDefaults(ext: PublishingExtension) {
        ext.destinations.mavenStandalone.path.convention(
            project.layout.projectDirectory.dir("build/maven-repo").asFile
        )
        findProperty("library.group")?.let { ext.group.set(it.toString()) }
        findProperty("library.version")?.let { ext.version.set(it.toString()) }
    }

    private fun Project.ensureChangelogs(ext: PublishingExtension) {
        for (libGroup in ext.libraryGroups) {
            val changelogFile = rootProject.file(libGroup.changelogPath.get())
            if (!changelogFile.exists()) {
                changelogFile.parentFile?.mkdirs()
                changelogFile.writeText(createPlaceholderChangelog(libGroup.getName()))
                logger.lifecycle("📝 Created changelog: ${libGroup.changelogPath.get()}")
            }
        }
    }

    private fun createPlaceholderChangelog(name: String): String {
        val today =
            java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return """
            |# Changelog
            |
            |All notable changes to **$name** will be documented in this file.
            |
            |The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
            |and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
            |
            |## [Unreleased]
            |
            |### Added
            |- Initial setup
            |
            |## [0.0.1] - $today
            |
            |### Added
            |- Initial release
            |
        """
            .trimMargin()
    }

    private fun Project.registerAggregateTasks(ext: PublishingExtension) {
        for (libGroup in ext.libraryGroups) {
            val groupName = libGroup.getName()
            val capitalizedName = groupName.capitalized()

            // Aggregate task for standalone
            if (ext.destinations.mavenStandalone.enabled.get()) {
                tasks.register("publish${capitalizedName}ToStandalone") {
                    group = "publishing"
                    description = "Publishes $groupName to maven-standalone"

                    doFirst {
                        if (ext.dryRun.get()) {
                            logger.lifecycle(
                                "🔍 [DRY-RUN] Would publish $groupName v${ext.version.get()}"
                            )
                        } else {
                            logger.lifecycle("📦 Publishing $groupName v${ext.version.get()}")
                        }
                    }

                    if (!ext.dryRun.get()) {
                        for (modulePath in libGroup.modules.get()) {
                            if (findProject(modulePath) != null) {
                                dependsOn(
                                    "$modulePath:publishAllPublicationsToMavenStandaloneRepository"
                                )
                            }
                        }
                    }

                    doLast {
                        val path = ext.destinations.mavenStandalone.path.get()
                        logger.lifecycle("✅ Published $groupName to $path")
                    }
                }
            }

            // Aggregate task for Maven Local
            if (ext.destinations.mavenLocal.enabled.get()) {
                tasks.register("publish${capitalizedName}ToMavenLocal") {
                    group = "publishing"
                    description = "Publishes $groupName to Maven Local (~/.m2/repository)"

                    if (!ext.dryRun.get()) {
                        for (modulePath in libGroup.modules.get()) {
                            if (findProject(modulePath) != null) {
                                dependsOn("$modulePath:publishToMavenLocal")
                            }
                        }
                    }
                }
            }

            // Aggregate task for GitHub Pages
            if (ext.destinations.githubPages.enabled.get()) {
                tasks.register("publish${capitalizedName}ToGitHubPages") {
                    group = "publishing"
                    description = "Publishes $groupName to GitHub Pages"

                    if (!ext.dryRun.get()) {
                        for (modulePath in libGroup.modules.get()) {
                            if (findProject(modulePath) != null) {
                                dependsOn(
                                    "$modulePath:publishAllPublicationsToGhPagesMavenRepository"
                                )
                            }
                        }
                    }
                }
            }

            // Aggregate task for GitHub Packages
            if (ext.destinations.githubPackages.enabled.get()) {
                tasks.register("publish${capitalizedName}ToGitHubPackages") {
                    group = "publishing"
                    description = "Publishes $groupName to GitHub Packages"

                    if (!ext.dryRun.get()) {
                        for (modulePath in libGroup.modules.get()) {
                            if (findProject(modulePath) != null) {
                                dependsOn(
                                    "$modulePath:publishAllPublicationsToGitHubPackagesRepository"
                                )
                            }
                        }
                    }
                }
            }

            // Aggregate task for Maven Central
            if (ext.destinations.mavenCentral.enabled.get()) {
                tasks.register("publish${capitalizedName}ToMavenCentral") {
                    group = "publishing"
                    description = "Publishes $groupName to Maven Central"

                    if (!ext.dryRun.get()) {
                        for (modulePath in libGroup.modules.get()) {
                            if (findProject(modulePath) != null) {
                                dependsOn(
                                    "$modulePath:publishAllPublicationsToMavenCentralRepository"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Publish all groups to standalone
        if (ext.destinations.mavenStandalone.enabled.get()) {
            tasks.register("publishAllToStandalone") {
                group = "publishing"
                description = "Publishes all library groups to maven-standalone"
                for (libGroup in ext.libraryGroups) {
                    val capitalizedName = libGroup.getName().replaceFirstChar { it.uppercase() }
                    dependsOn("publish${capitalizedName}ToStandalone")
                }
            }
        }

        // Info task
        tasks.register("publishingInfo") {
            group = "help"
            description = "Shows publishing configuration"
            doLast { logger.lifecycle(buildPublishingInfo(ext)) }
        }
    }

    private fun Project.registerChangelogTasks(ext: PublishingExtension) {
        val remoteUrlProvider = detectRemoteUrl()

        // Register per-group changelog tasks
        for (libGroup in ext.libraryGroups) {
            val groupName = libGroup.getName()
            val capitalizedName = groupName.capitalized()

            tasks.register(
                "generateChangelog$capitalizedName",
                dev.all4.gradle.release.tasks.GenerateChangelogTask::class.java
            ) {
                this.groupName.set(groupName)
                this.modulePaths.set(libGroup.modules)
                this.remoteUrl.set(remoteUrlProvider)
            }
        }
    }

    private fun buildPublishingInfo(ext: PublishingExtension): String = buildString {
        appendLine()
        appendLine("📦 Publishing Configuration")
        appendLine("═══════════════════════════════════════════════════════════════")
        appendLine("  Group:    ${ext.group.orNull ?: "(not set)"}")
        appendLine("  Version:  ${ext.version.orNull ?: "(not set)"}")
        appendLine("  Dry Run:  ${ext.dryRun.get()}")

        appendLine("\n📚 Library Groups:")
        if (ext.libraryGroups.isEmpty()) {
            appendLine("  (none configured)")
        } else {
            for (libGroup in ext.libraryGroups) {
                appendLine("  • ${libGroup.getName()}")
                appendLine("    Modules: ${libGroup.modules.get().joinToString(", ")}")
                appendLine("    Changelog: ${libGroup.changelogPath.get()}")
            }
        }

        appendLine("\n🎯 Destinations:")
        val mavenLocal = if (ext.destinations.mavenLocal.enabled.get()) "✅" else "❌"
        val standalonePath = ext.destinations.mavenStandalone.path.orNull
        val standalone =
            if (ext.destinations.mavenStandalone.enabled.get()) "✅ $standalonePath" else "❌"
        val githubPages = if (ext.destinations.githubPages.enabled.get()) "✅" else "❌"
        val githubPackages = if (ext.destinations.githubPackages.enabled.get()) "✅" else "❌"
        val pluginPortal = if (ext.destinations.gradlePluginPortal.enabled.get()) "✅" else "❌"
        val mavenCentral = if (ext.destinations.mavenCentral.enabled.get()) "✅" else "❌"
        appendLine("  Maven Local:          $mavenLocal")
        appendLine("  Maven Standalone:     $standalone")
        appendLine("  GitHub Pages:         $githubPages")
        appendLine("  GitHub Packages:      $githubPackages")
        appendLine("  Gradle Plugin Portal: $pluginPortal")
        appendLine("  Maven Central:        $mavenCentral")

        appendLine("\n🔧 Available Tasks:")
        for (libGroup in ext.libraryGroups) {
            val name = libGroup.getName().capitalized()
            if (ext.destinations.mavenLocal.enabled.get())
                appendLine("  • publish${name}ToMavenLocal")
            if (ext.destinations.mavenStandalone.enabled.get())
                appendLine("  • publish${name}ToStandalone")
            if (ext.destinations.githubPages.enabled.get())
                appendLine("  • publish${name}ToGitHubPages")
            if (ext.destinations.githubPackages.enabled.get())
                appendLine("  • publish${name}ToGitHubPackages")
            if (ext.destinations.mavenCentral.enabled.get())
                appendLine("  • publish${name}ToMavenCentral")
        }
        if (ext.destinations.mavenStandalone.enabled.get()) appendLine("  • publishAllToStandalone")
        appendLine("  • publishingInfo")
        appendLine("  • importArtifact --file=<path> or --dir=<path>")
        appendLine()
    }

    // ========== SUBPROJECT/MODULE CONFIGURATION ==========

    private fun applyToModule(project: Project) {
        with(project) {
            pluginManager.apply("maven-publish")

            // Get publishing extension from root project (or create if not exists)
            val publishingExt = rootProject.extensions.findByType<PublishingExtension>()

            // Read version and group from extension or properties
            val propertyVersion =
                publishingExt?.version?.orNull ?: findProperty("library.version") as? String
            val propertyGroup =
                publishingExt?.group?.orNull ?: findProperty("library.group") as? String

            if (propertyVersion != null) version = propertyVersion
            if (propertyGroup != null) group = propertyGroup

            // Auto-configure Android library publishing with sources
            configureAndroidPublishing()

            afterEvaluate {
                configureRepositories(publishingExt)
                configurePom(publishingExt)

                // Add publishingInfo task to subproject if root has publishing extension
                if (publishingExt != null) {
                    tasks.register("publishingInfo") {
                        group = "help"
                        description = "Shows publishing configuration"
                        doLast { logger.lifecycle(buildPublishingInfo(publishingExt)) }
                    }
                }
            }
        }
    }

    private fun Project.configureAndroidPublishing() {
        pluginManager.withPlugin("com.android.library") {
            // Use Groovy/dynamic invocation to avoid compile-time dependency on AGP
            try {
                val androidExt = extensions.findByName("android") ?: return@withPlugin

                // Call: android.publishing { singleVariant("release") { withSourcesJar() } }
                val groovyObj = androidExt as groovy.lang.GroovyObject
                groovyObj.invokeMethod("publishing", arrayOf(
                    closureOf<Any> {
                        val publishing = this as groovy.lang.GroovyObject
                        publishing.invokeMethod("singleVariant", arrayOf("release",
                            closureOf<Any> {
                                val variant = this as groovy.lang.GroovyObject
                                variant.invokeMethod("withSourcesJar", emptyArray<Any>())
                            }
                        ))
                    }
                ))
                logger.info("Auto-configured Android library publishing with sources")
            } catch (e: Exception) {
                logger.debug("Could not auto-configure Android publishing: ${e.message}")
            }
        }
    }

    private fun <T> closureOf(action: T.() -> Unit): groovy.lang.Closure<Unit> =
        object : groovy.lang.Closure<Unit>(this) {
            @Suppress("UNCHECKED_CAST")
            override fun call(): Unit = (delegate as T).action()
        }

    private fun Project.configureRepositories(publishingExt: PublishingExtension?) {
        extensions.configure<GradlePublishingExtension> {
            repositories {
                // Maven Local
                if (publishingExt?.destinations?.mavenLocal?.enabled?.orNull == true) {
                    mavenLocal()
                }

                // Maven Standalone
                val standaloneConfig = publishingExt?.destinations?.mavenStandalone
                if (standaloneConfig?.enabled?.orNull == true && standaloneConfig.path.isPresent) {
                    maven {
                        name = "MavenStandalone"
                        url = standaloneConfig.path.get().toURI()
                    }
                }

                // GitHub Packages
                val ghPackagesConfig = publishingExt?.destinations?.githubPackages
                if (
                    ghPackagesConfig?.enabled?.orNull == true &&
                        ghPackagesConfig.repository.isPresent
                ) {
                    val githubActor =
                        findProperty("GITHUB_ACTOR") as? String ?: System.getenv("GITHUB_ACTOR")
                    val githubToken =
                        findProperty("GITHUB_TOKEN") as? String ?: System.getenv("GITHUB_TOKEN")

                    if (githubActor != null && githubToken != null) {
                        maven {
                            name = "GitHubPackages"
                            url =
                                uri(
                                    "https://maven.pkg.github.com/${ghPackagesConfig.repository.get()}"
                                )
                            credentials {
                                username = githubActor
                                password = githubToken
                            }
                        }
                    } else {
                        logger.warn("GitHub Packages enabled but GITHUB_ACTOR/GITHUB_TOKEN not set")
                    }
                }

                // GitHub Pages
                val ghPagesConfig = publishingExt?.destinations?.githubPages
                if (ghPagesConfig?.enabled?.orNull == true && ghPagesConfig.repoPath.isPresent) {
                    maven {
                        name = "GhPagesMaven"
                        url = java.io.File(ghPagesConfig.repoPath.get()).resolve("maven").toURI()
                    }
                }

                // Maven Central
                val mavenCentralConfig = publishingExt?.destinations?.mavenCentral
                if (mavenCentralConfig?.enabled?.orNull == true) {
                    val sonatypeUser =
                        findProperty("sonatype.username") as? String
                            ?: System.getenv("SONATYPE_USERNAME")
                    val sonatypePassword =
                        findProperty("sonatype.password") as? String
                            ?: System.getenv("SONATYPE_PASSWORD")

                    if (sonatypeUser != null && sonatypePassword != null) {
                        maven {
                            name = "MavenCentral"
                            url =
                                uri(
                                    mavenCentralConfig.stagingUrl.orNull
                                        ?: "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                                )
                            credentials {
                                username = sonatypeUser
                                password = sonatypePassword
                            }
                        }
                    } else {
                        logger.warn(
                            "Maven Central enabled but SONATYPE_USERNAME/SONATYPE_PASSWORD not set"
                        )
                    }
                }

                // Custom Maven repositories
                publishingExt?.destinations?.customRepos?.forEach { customRepo ->
                    if (customRepo.enabled.orNull == true && customRepo.url.isPresent) {
                        maven {
                            name = customRepo.getName().toTaskName()
                            url = uri(customRepo.url.get())

                            if (customRepo.allowInsecureProtocol.orNull == true) {
                                isAllowInsecureProtocol = true
                            }

                            // Basic auth (username/password)
                            if (customRepo.username.isPresent && customRepo.password.isPresent) {
                                credentials {
                                    username = customRepo.username.get()
                                    password = customRepo.password.get()
                                }
                            }

                            // Header-based auth (token)
                            if (
                                customRepo.authHeaderName.isPresent &&
                                    customRepo.authHeaderValue.isPresent
                            ) {
                                credentials(HttpHeaderCredentials::class) {
                                    name = customRepo.authHeaderName.get()
                                    value = customRepo.authHeaderValue.get()
                                }
                                authentication { create<HttpHeaderAuthentication>("header") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Project.configurePom(publishingExt: PublishingExtension?) {
        val pomConfig = publishingExt?.pom
        val libraryGroup = publishingExt?.group?.orNull ?: findProperty("library.group") as? String
        val libraryVersion =
            publishingExt?.version?.orNull
                ?: findProperty("library.version") as? String
                ?: version.toString()

        // Auto-detect artifactId from libraryGroups or use property
        val libraryArtifactId = findProperty("library.artifactId") as? String
            ?: detectArtifactIdFromGroups(publishingExt)

        extensions.configure<GradlePublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                if (libraryGroup != null) groupId = libraryGroup
                if (libraryArtifactId != null) {
                    // Replace base artifact ID, preserving platform suffix (e.g., -jvm, -js)
                    val suffix = if (artifactId.contains("-")) "-${artifactId.substringAfter("-")}" else ""
                    artifactId = "$libraryArtifactId$suffix"
                }
                version = libraryVersion

                pom {
                    name.set(pomConfig?.name?.orNull ?: project.name)
                    description.set(pomConfig?.description?.orNull ?: project.description ?: "")
                    pomConfig?.url?.orNull?.let { url.set(it) }
                    pomConfig?.inceptionYear?.orNull?.let { inceptionYear.set(it) }

                    // License
                    val licenseConfig = pomConfig?.license
                    if (licenseConfig?.name?.isPresent == true) {
                        licenses {
                            license {
                                name.set(licenseConfig.name.get())
                                licenseConfig.url.orNull?.let { url.set(it) }
                                licenseConfig.distribution.orNull?.let { distribution.set(it) }
                            }
                        }
                    }

                    // Developers
                    val developersList = pomConfig?.developers?.orNull
                    if (!developersList.isNullOrEmpty()) {
                        developers {
                            developersList.forEach { dev ->
                                developer {
                                    dev.id.orNull?.let { id.set(it) }
                                    dev.name.orNull?.let { name.set(it) }
                                    dev.email.orNull?.let { email.set(it) }
                                    dev.organization.orNull?.let { organization.set(it) }
                                    dev.organizationUrl.orNull?.let { organizationUrl.set(it) }
                                }
                            }
                        }
                    }

                    // SCM
                    val scmConfig = pomConfig?.scm
                    if (scmConfig?.url?.isPresent == true) {
                        scm {
                            scmConfig.url.orNull?.let { url.set(it) }
                            scmConfig.connection.orNull?.let { connection.set(it) }
                            scmConfig.developerConnection.orNull?.let {
                                developerConnection.set(it)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Auto-detect artifact ID from libraryGroups configuration.
     * If module belongs to a group and module name != group name, prefix with group name.
     * Example: module "charts" in group "theme" → "theme-charts"
     */
    private fun Project.detectArtifactIdFromGroups(publishingExt: PublishingExtension?): String? {
        if (publishingExt == null) return null

        val modulePath = path
        val moduleName = name

        for (libGroup in publishingExt.libraryGroups) {
            val groupName = libGroup.getName()
            val modules = libGroup.modules.orNull ?: continue

            if (modulePath in modules) {
                // Module belongs to this group
                return if (moduleName == groupName) {
                    // Module name matches group name, no prefix needed
                    null
                } else {
                    // Prefix with group name: charts → theme-charts (AndroidX standard)
                    "$groupName-$moduleName"
                }
            }
        }
        return null
    }
}
