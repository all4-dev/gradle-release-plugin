package dev.all4.gradle.release

import dev.all4.gradle.release.central.CentralPortalPublishBuildService
import dev.all4.gradle.release.model.ChangelogMode
import dev.all4.gradle.release.util.OnePasswordSupport
import dev.all4.gradle.release.util.capitalized
import dev.all4.gradle.release.util.detectRemoteUrl
import dev.all4.gradle.release.util.toTaskName
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.publish.PublishingExtension as GradlePublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.authentication.http.HttpHeaderAuthentication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.credentials
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

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

    private fun applyToRoot(project: Project) {
        with(project) {
            val ext = extensions.create("releaseConfig", PublishingExtension::class.java)
            configureDefaults(ext)

            tasks.register(
                "importArtifact",
                dev.all4.gradle.release.tasks.ImportArtifactTask::class.java,
            )
            tasks.register(
                "generateChangelog",
                dev.all4.gradle.release.tasks.GenerateChangelogTask::class.java,
            )
            tasks.register(
                "createRelease",
                dev.all4.gradle.release.tasks.CreateReleaseTask::class.java,
            )
            tasks.register("bumpVersion", dev.all4.gradle.release.tasks.BumpVersionTask::class.java)
            tasks.register(
                "initPublishingProperties",
                dev.all4.gradle.release.tasks.InitPublishingPropertiesTask::class.java,
            ) {
                group = "publishing"
                description =
                    "Creates a publishing.properties template with credential placeholders"
                outputFile.set(project.layout.projectDirectory.file(ext.propertiesFile))
                mavenCentralEnabled.set(ext.destinations.mavenCentral.enabled)
                githubPackagesEnabled.set(ext.destinations.githubPackages.enabled)
            }

            val validateCredentials =
                tasks.register(
                    "validatePublishingCredentials",
                    dev.all4.gradle.release.tasks.ValidateCredentialsTask::class.java,
                ) {
                    group = "publishing"
                    description = "Validates that publishing.properties is not tracked by git"
                    propertiesFile.set(project.layout.projectDirectory.file(ext.propertiesFile))
                }

            afterEvaluate {
                loadPublishingProperties(ext)
                ensureChangelogs(ext)
                registerAggregateTasks(ext, validateCredentials)
                registerChangelogTasks(ext)
                warnUnresolvedVersions(ext)
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

    private fun Project.loadPublishingProperties(ext: PublishingExtension) {
        val path = ext.propertiesFile.orNull ?: return
        val propsFile = layout.projectDirectory.file(path).asFile
        if (!propsFile.exists()) return
        val props = java.util.Properties()
        propsFile.inputStream().buffered().use { props.load(it) }
        allprojects {
            props.forEach { key, value ->
                extensions.extraProperties.set(key.toString(), value.toString())
            }
        }
    }

    private fun Project.ensureChangelogs(ext: PublishingExtension) {
        for (libGroup in ext.libraryGroups) {
            if (!libGroup.changelogEnabled.get()) continue

            when (libGroup.changelogMode.get()) {
                ChangelogMode.CENTRALIZED -> {
                    val changelogFile = rootProject.file(libGroup.changelogPath.get())
                    if (!changelogFile.exists()) {
                        changelogFile.parentFile?.mkdirs()
                        changelogFile.writeText(createPlaceholderChangelog(libGroup.getName()))
                        logger.lifecycle("📝 Created changelog: ${libGroup.changelogPath.get()}")
                    }
                }
                ChangelogMode.PER_PROJECT -> {
                    for (modulePath in libGroup.modules.get()) {
                        val moduleProject = findProject(modulePath) ?: continue
                        val changelogFile = moduleProject.file("CHANGELOG.md")
                        if (!changelogFile.exists()) {
                            changelogFile.parentFile?.mkdirs()
                            changelogFile.writeText(createPlaceholderChangelog(moduleProject.name))
                            logger.lifecycle(
                                "📝 Created changelog: ${moduleProject.projectDir}/CHANGELOG.md"
                            )
                        }
                    }
                }
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

    private fun Project.registerAggregateTasks(
        ext: PublishingExtension,
        validateCredentials: org.gradle.api.tasks.TaskProvider<*>,
    ) {
        for (libGroup in ext.libraryGroups) {
            val groupName = libGroup.getName()
            val capitalizedName = groupName.capitalized()

            if (ext.destinations.mavenStandalone.enabled.get()) {
                tasks.register("publish${capitalizedName}ToStandalone") {
                    group = "publishing"
                    description = "Publishes $groupName to maven-standalone"

                    doFirst {
                        validateLibraryGroupForPublishing(libGroup)

                        val configuredVersion =
                            libGroup.version.orNull ?: ext.version.orNull ?: "(not set)"
                        if (ext.dryRun.get()) {
                            logger.lifecycle(
                                "🔍 [DRY-RUN] Would publish $groupName v$configuredVersion"
                            )
                        } else {
                            logger.lifecycle("📦 Publishing $groupName v$configuredVersion")
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

            if (ext.destinations.mavenLocal.enabled.get()) {
                tasks.register("publish${capitalizedName}ToMavenLocal") {
                    group = "publishing"
                    description = "Publishes $groupName to Maven Local (~/.m2/repository)"

                    doFirst { validateLibraryGroupForPublishing(libGroup) }

                    if (!ext.dryRun.get()) {
                        for (modulePath in libGroup.modules.get()) {
                            if (findProject(modulePath) != null) {
                                dependsOn("$modulePath:publishToMavenLocal")
                            }
                        }
                    }
                }
            }

            if (ext.destinations.githubPages.enabled.get()) {
                tasks.register("publish${capitalizedName}ToGitHubPages") {
                    group = "publishing"
                    description = "Publishes $groupName to GitHub Pages"

                    doFirst { validateLibraryGroupForPublishing(libGroup) }

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

            if (ext.destinations.githubPackages.enabled.get()) {
                tasks.register("publish${capitalizedName}ToGitHubPackages") {
                    group = "publishing"
                    description = "Publishes $groupName to GitHub Packages"

                    doFirst { validateLibraryGroupForPublishing(libGroup) }

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

            if (ext.destinations.mavenCentral.enabled.get()) {
                tasks.register("publish${capitalizedName}ToMavenCentral") {
                    group = "publishing"
                    description = "Publishes $groupName to Maven Central"

                    doFirst { validateLibraryGroupForPublishing(libGroup) }

                    if (!ext.dryRun.get()) {
                        for (modulePath in libGroup.modules.get()) {
                            if (findProject(modulePath) != null) {
                                dependsOn(
                                    "$modulePath:publishAllPublicationsToMavenCentralRepository"
                                )
                            }
                        }
                    }

                    if (ext.destinations.mavenCentral.useCentralPortal.getOrElse(false)) {
                        doLast {
                            CentralPortalPublishBuildService.uploadStagingBundle(
                                project = this@registerAggregateTasks,
                                logger = logger,
                            )
                        }
                    }
                }
            }
        }

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

        tasks.register("publishingInfo") {
            group = "help"
            description = "Shows publishing configuration"
            doLast { logger.lifecycle(buildPublishingInfo(ext)) }
        }

        tasks.configureEach {
            if (name.startsWith("publish") && group == "publishing") {
                dependsOn(validateCredentials)
            }
        }
    }

    private fun Project.validateLibraryGroupForPublishing(
        libGroup: dev.all4.gradle.release.model.LibraryGroup
    ) {
        val groupName = libGroup.getName()
        val modulePaths = libGroup.moduleProjectPaths()
        if (modulePaths.isEmpty()) {
            throw GradleException(
                """
                |❌ releaseConfig.libraryGroups["$groupName"] has no modules configured.
                |
                |✅ Solution:
                |  - Add at least one module project path.
                |  - Example:
                |      libraryGroups {
                |          register("$groupName") { modules.add(":libs:$groupName") }
                |      }
                """
                    .trimMargin()
            )
        }

        val availablePaths = rootProject.allprojects.map { it.path }.toSet()
        val missingPaths = modulePaths.filterNot { it in availablePaths }
        if (missingPaths.isNotEmpty()) {
            val availablePreview = availablePaths.sorted().take(25).joinToString(", ")
            val details =
                missingPaths.joinToString("\n") { invalidPath ->
                    val suggestion = suggestModulePath(invalidPath, availablePaths)
                    if (suggestion != null && suggestion != invalidPath) {
                        """  - $invalidPath (did you mean "$suggestion"?)"""
                    } else {
                        "  - $invalidPath"
                    }
                }
            throw GradleException(
                """
                |❌ Invalid module path(s) in releaseConfig.libraryGroups["$groupName"].
                |
                |$details
                |
                |✅ Solution:
                |  1) Use Gradle project paths that exist in settings.gradle(.kts).
                |  2) Prefer format like ":libs:logger" (not custom aliases).
                |
                |Available project paths (first 25):
                |  $availablePreview
                """
                    .trimMargin()
            )
        }

        val modulesWithUnsetVersion =
            modulePaths.mapNotNull { modulePath ->
                val moduleProject = rootProject.findProject(modulePath) ?: return@mapNotNull null
                val moduleVersion = moduleProject.version.toString()
                if (isUnsetVersion(moduleVersion)) {
                    modulePath to moduleVersion
                } else {
                    null
                }
            }

        if (modulesWithUnsetVersion.isNotEmpty()) {
            val versionKey =
                libGroup.versionKey.orNull?.takeIf { it.isNotBlank() } ?: "version.$groupName"
            val groupVersion = libGroup.version.orNull?.takeIf { it.isNotBlank() } ?: "(not set)"
            val rootVersion =
                rootProject.extensions.findByType<PublishingExtension>()?.version?.orNull?.takeIf {
                    it.isNotBlank()
                } ?: "(not set)"
            val details =
                modulesWithUnsetVersion.joinToString("\n") { (modulePath, version) ->
                    "  - $modulePath (version=$version)"
                }
            throw GradleException(
                """
                |❌ Cannot publish releaseConfig.libraryGroups["$groupName"] because one or more modules have version not set (blank/"unspecified"/"undefined").
                |
                |$details
                |
                |Current release config:
                |  - releaseConfig.libraryGroups["$groupName"].version = $groupVersion
                |  - releaseConfig.version = $rootVersion
                |
                |Publishing these modules will create invalid Maven coordinates (for example ending in ":unspecified").
                |
                |✅ Solution:
                |  1) Set explicit version in each affected module build.gradle(.kts), for example:
                |       version = property("$versionKey").toString()
                |  2) Define that key in gradle.properties (or your version catalog source), for example:
                |       $versionKey=1.0.0-alpha.3
                |  3) Ensure your releaseConfig.libraryGroups["$groupName"].modules points to modules with explicit version.
                """
                    .trimMargin()
            )
        }
    }

    private fun isUnsetVersion(version: String): Boolean {
        val normalized = version.trim()
        if (normalized.isEmpty()) return true
        return normalized.equals("unspecified", ignoreCase = true) ||
            normalized.equals("undefined", ignoreCase = true)
    }

    private fun suggestModulePath(invalidPath: String, availablePaths: Set<String>): String? {
        val normalized = invalidPath.trim()
        if (normalized.isBlank()) return null

        val leaf = normalized.substringAfterLast(':')
        val exactLeafMatches = availablePaths.filter { it.substringAfterLast(':') == leaf }.sorted()
        if (exactLeafMatches.isNotEmpty()) return exactLeafMatches.first()

        val partialMatches =
            availablePaths.filter { it.contains(":$leaf") || it.contains(leaf) }.sorted()
        return partialMatches.firstOrNull()
    }

    private fun Project.registerChangelogTasks(ext: PublishingExtension) {
        val remoteUrlProvider = detectRemoteUrl()

        for (libGroup in ext.libraryGroups) {
            val groupName = libGroup.getName()
            val capitalizedName = groupName.capitalized()

            tasks.register(
                "generateChangelog$capitalizedName",
                dev.all4.gradle.release.tasks.GenerateChangelogTask::class.java,
            ) {
                this.groupName.set(groupName)
                this.modulePaths.set(libGroup.modules)
                this.remoteUrl.set(remoteUrlProvider)
            }
        }
    }

    private fun Project.warnUnresolvedVersions(ext: PublishingExtension) {
        for (libGroup in ext.libraryGroups) {
            val groupName = libGroup.getName()
            val groupVersion = libGroup.version.orNull ?: ext.version.orNull
            for (modulePath in libGroup.modules.get()) {
                val moduleProject = findProject(modulePath) ?: continue
                val moduleVersion = moduleProject.version.toString()
                if (isUnsetVersion(moduleVersion) && groupVersion == null) {
                    logger.warn(
                        "⚠️  releaseConfig.libraryGroups[\"$groupName\"] module $modulePath " +
                            "has no resolvable version. Set libraryGroup.version, " +
                            "releaseConfig.version, or module-level project.version " +
                            "to avoid 'unspecified' publications."
                    )
                }
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
                val changelogInfo =
                    when (libGroup.changelogMode.get()) {
                        ChangelogMode.CENTRALIZED -> libGroup.changelogPath.get()
                        ChangelogMode.PER_PROJECT -> "per-project (each module's CHANGELOG.md)"
                    }
                appendLine("    Changelog: $changelogInfo")
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

    private fun applyToModule(project: Project) {
        with(project) {
            pluginManager.apply("maven-publish")

            val publishingExt = rootProject.extensions.findByType<PublishingExtension>()

            val propertyVersion =
                publishingExt?.version?.orNull
                    ?: findProperty("library.version") as? String
                    ?: resolveVersionFromLibraryGroup(publishingExt, path)
            val propertyGroup =
                publishingExt?.group?.orNull ?: findProperty("library.group") as? String

            if (propertyVersion != null) version = propertyVersion
            if (propertyGroup != null) group = propertyGroup

            configureAndroidPublishing()

            afterEvaluate {
                configureRepositories(publishingExt)
                ensureJavadocJar(publishingExt)
                configurePom(publishingExt)

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
            try {
                val androidExt = extensions.findByName("android") ?: return@withPlugin

                val groovyObj = androidExt as groovy.lang.GroovyObject
                groovyObj.invokeMethod(
                    "publishing",
                    arrayOf(
                        closureOf<Any> {
                            val publishing = this as groovy.lang.GroovyObject
                            publishing.invokeMethod(
                                "singleVariant",
                                arrayOf(
                                    "release",
                                    closureOf<Any> {
                                        val variant = this as groovy.lang.GroovyObject
                                        variant.invokeMethod("withSourcesJar", emptyArray<Any>())
                                    },
                                ),
                            )
                        }
                    ),
                )
                logger.info("Auto-configured Android library publishing with sources")
            } catch (e: Exception) {
                logger.debug("Could not auto-configure Android publishing: ${e.message}")
            }
        }
    }

    private fun <T> closureOf(action: T.() -> Unit): groovy.lang.Closure<Unit> =
        object : groovy.lang.Closure<Unit>(this) {
            @Suppress("UNCHECKED_CAST") override fun call(): Unit = (delegate as T).action()
        }

    private fun Project.configureRepositories(publishingExt: PublishingExtension?) {
        extensions.configure<GradlePublishingExtension> {
            repositories {
                if (publishingExt?.destinations?.mavenLocal?.enabled?.orNull == true) {
                    mavenLocal()
                }

                val standaloneConfig = publishingExt?.destinations?.mavenStandalone
                if (standaloneConfig?.enabled?.orNull == true && standaloneConfig.path.isPresent) {
                    maven {
                        name = "MavenStandalone"
                        url = standaloneConfig.path.get().toURI()
                    }
                }

                val ghPackagesConfig = publishingExt?.destinations?.githubPackages
                if (
                    ghPackagesConfig?.enabled?.orNull == true &&
                        ghPackagesConfig.repository.isPresent
                ) {
                    val githubActor =
                        (findProperty("GITHUB_ACTOR") as? String ?: System.getenv("GITHUB_ACTOR"))
                            ?.let { OnePasswordSupport.resolve(it, this@configureRepositories) }
                    val githubToken =
                        (findProperty("GITHUB_TOKEN") as? String ?: System.getenv("GITHUB_TOKEN"))
                            ?.let { OnePasswordSupport.resolve(it, this@configureRepositories) }

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

                val ghPagesConfig = publishingExt?.destinations?.githubPages
                if (ghPagesConfig?.enabled?.orNull == true && ghPagesConfig.repoPath.isPresent) {
                    maven {
                        name = "GhPagesMaven"
                        url = java.io.File(ghPagesConfig.repoPath.get()).resolve("maven").toURI()
                    }
                }

                val mavenCentralConfig = publishingExt?.destinations?.mavenCentral
                if (mavenCentralConfig?.enabled?.orNull == true) {
                    // Raw credentials — NOT resolved at configuration time.
                    // 1Password op:// references are resolved lazily at execution time.
                    val rawUser = findProperty("sonatype.username") as? String
                        ?: System.getenv("SONATYPE_USERNAME")
                    val rawPassword = findProperty("sonatype.password") as? String
                        ?: System.getenv("SONATYPE_PASSWORD")

                    if (mavenCentralConfig.useCentralPortal.getOrElse(false)) {
                        // Use root project staging dir so all subprojects stage to one place
                        val stagingDir = rootProject.layout.buildDirectory
                            .dir("central-staging").get().asFile
                        maven {
                            name = "MavenCentral"
                            url = stagingDir.toURI()
                        }

                        CentralPortalPublishBuildService.registerIfAbsent(
                            project = this@configureRepositories,
                            rawUsername = rawUser ?: "",
                            rawPassword = rawPassword ?: "",
                            baseUrl =
                                mavenCentralConfig.stagingUrl.orNull
                                    ?: "https://central.sonatype.com/api/v1/",
                            stagingDir = stagingDir,
                            autoPublish = true,
                        )
                    } else {
                        val mavenRepo = maven {
                            name = "MavenCentral"
                            url =
                                uri(
                                    mavenCentralConfig.stagingUrl.orNull
                                        ?: "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                                )
                            credentials {
                                // Placeholders — resolved lazily below
                                username = ""
                                password = ""
                            }
                        }
                        // Defer 1Password resolution until a publish task is in the graph
                        gradle.taskGraph.whenReady {
                            val hasPublishTask = allTasks.any {
                                it.name.startsWith("publish") && it.name.contains("MavenCentral")
                            }
                            if (hasPublishTask) {
                                mavenRepo.credentials {
                                    username = rawUser
                                        ?.let { OnePasswordSupport.resolve(it, this@configureRepositories) }
                                        ?: ""
                                    password = rawPassword
                                        ?.let { OnePasswordSupport.resolve(it, this@configureRepositories) }
                                        ?: ""
                                }
                            }
                        }
                    }

                    configureSigning()

                    if (rawUser == null || rawPassword == null) {
                        logger.warn(
                            "Maven Central enabled but sonatype.username/sonatype.password not set. Publishing to Maven Central will fail."
                        )
                    }
                }

                publishingExt?.destinations?.customRepos?.forEach { customRepo ->
                    if (customRepo.enabled.orNull == true && customRepo.url.isPresent) {
                        maven {
                            name = customRepo.getName().toTaskName()
                            url = uri(customRepo.url.get())

                            if (customRepo.allowInsecureProtocol.orNull == true) {
                                isAllowInsecureProtocol = true
                            }

                            if (customRepo.username.isPresent && customRepo.password.isPresent) {
                                val resolvedUsername =
                                    OnePasswordSupport.resolve(
                                        customRepo.username.get(),
                                        this@configureRepositories,
                                    )
                                val resolvedPassword =
                                    OnePasswordSupport.resolve(
                                        customRepo.password.get(),
                                        this@configureRepositories,
                                    )
                                credentials {
                                    username = resolvedUsername
                                    password = resolvedPassword
                                }
                            }

                            if (
                                customRepo.authHeaderName.isPresent &&
                                    customRepo.authHeaderValue.isPresent
                            ) {
                                val resolvedHeaderValue =
                                    OnePasswordSupport.resolve(
                                        customRepo.authHeaderValue.get(),
                                        this@configureRepositories,
                                    )
                                credentials(HttpHeaderCredentials::class) {
                                    name = customRepo.authHeaderName.get()
                                    value = resolvedHeaderValue
                                }
                                authentication { create<HttpHeaderAuthentication>("header") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Project.configureSigning() {
        pluginManager.apply(SigningPlugin::class.java)

        val extra = extensions.extraProperties
        val gpgExe = findProperty("signing.gnupg.executable") as? String
        if (gpgExe == null) {
            val defaultGpg = listOf("/opt/homebrew/bin/gpg", "/usr/local/bin/gpg", "/usr/bin/gpg")
                .firstOrNull { java.io.File(it).exists() }
            if (defaultGpg != null) {
                extra.set("signing.gnupg.executable", defaultGpg)
            }
        }

        extensions.configure<SigningExtension> {
            useGpgCmd()
            // Only required when actually publishing (not during sync/configuration)
            isRequired = false

            val publishing = extensions.getByType(GradlePublishingExtension::class.java)
            sign(publishing.publications)
        }

        // Defer passphrase injection and make signing required only when publishing
        gradle.taskGraph.whenReady {
            val hasPublishTask = allTasks.any {
                it.name.startsWith("publish") && it.name.contains("MavenCentral")
            }
            if (hasPublishTask) {
                val gpgPassphrase = System.getenv("SIGNING_GPG_PASSPHRASE")
                    ?: findProperty("signing.gnupg.passphrase") as? String
                if (gpgPassphrase != null) {
                    extra.set("signing.gnupg.passphrase", gpgPassphrase)
                }
                extensions.configure<SigningExtension> {
                    isRequired = !version.toString().endsWith("-SNAPSHOT")
                }
            }
        }
    }

    private fun Project.ensureJavadocJar(@Suppress("UNUSED_PARAMETER") publishingExt: PublishingExtension?) {
        // No-op at configuration time. Javadoc JARs are injected into the staging
        // directory by uploadStagingBundle() before zipping, to satisfy Central Portal.
    }

    private fun Project.configurePom(publishingExt: PublishingExtension?) {
        val pomConfig = publishingExt?.pom
        val libraryGroup = publishingExt?.group?.orNull ?: findProperty("library.group") as? String
        val libraryVersion =
            publishingExt?.version?.orNull
                ?: findProperty("library.version") as? String
                ?: resolveVersionFromLibraryGroup(publishingExt, path)
                ?: version.toString()

        val libraryArtifactId =
            findProperty("library.artifactId") as? String
                ?: detectArtifactIdFromGroups(publishingExt)

        extensions.configure<GradlePublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                if (libraryGroup != null) groupId = libraryGroup
                if (libraryArtifactId != null) {
                    val suffix =
                        if (artifactId.contains("-")) "-${artifactId.substringAfter("-")}" else ""
                    artifactId = "$libraryArtifactId$suffix"
                }
                version = libraryVersion

                pom {
                    name.set(pomConfig?.name?.orNull ?: project.name)
                    description.set(pomConfig?.description?.orNull ?: project.description ?: "")
                    pomConfig?.url?.orNull?.let { url.set(it) }
                    pomConfig?.inceptionYear?.orNull?.let { inceptionYear.set(it) }

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
     * Resolves the version for a subproject by finding the libraryGroup that contains it
     * and returning that group's version, or else the root-level releaseConfig.version.
     */
    private fun resolveVersionFromLibraryGroup(
        ext: PublishingExtension?,
        projectPath: String,
    ): String? {
        if (ext == null) return null
        for (libGroup in ext.libraryGroups) {
            if (libGroup.containsModule(projectPath)) {
                libGroup.version.orNull?.let { return it }
            }
        }
        return null
    }

    /**
     * Auto-detect artifact ID from libraryGroups configuration. If module belongs to a group and
     * module name != group name, prefix with group name. Example: module "charts" in group "theme"
     * → "theme-charts"
     */
    private fun Project.detectArtifactIdFromGroups(publishingExt: PublishingExtension?): String? {
        if (publishingExt == null) return null

        val modulePath = path
        val moduleName = name

        for (libGroup in publishingExt.libraryGroups) {
            val groupName = libGroup.getName()
            val modules = libGroup.modules.orNull ?: continue

            if (modulePath in modules) {
                return if (moduleName == groupName) {
                    null
                } else {
                    "$groupName-$moduleName"
                }
            }
        }
        return null
    }
}
