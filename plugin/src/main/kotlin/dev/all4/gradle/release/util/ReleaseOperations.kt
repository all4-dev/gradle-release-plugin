package dev.all4.gradle.release.util

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import org.gradle.api.Project
import org.gradle.api.logging.Logger

internal object ReleaseOperations {

    /**
     * Creates an annotated git tag, pushes it to the remote, and creates a GitHub release via API.
     *
     * @return true if the tag was created and pushed successfully
     */
    fun createTagAndRelease(
        project: Project,
        version: String,
        tagPrefix: String = "v",
        prerelease: Boolean = version.contains("-"),
        draft: Boolean = false,
        releaseNotes: String? = null,
        changelogPath: String? = null,
        skipGitHub: Boolean = false,
    ): Boolean {
        val logger = project.logger
        val tagName = "$tagPrefix$version"

        val existingTags = project.runGit("git", "tag", "-l", tagName)
        if (existingTags.isNotBlank()) {
            logger.error("❌ Tag '$tagName' already exists")
            return false
        }

        val status = project.runGit("git", "status", "--porcelain")
        if (status.isNotBlank()) {
            logger.warn("⚠️  Warning: You have uncommitted changes")
        }

        logger.lifecycle("📌 Creating tag: $tagName")
        val tagResult = project.runGit("git", "tag", "-a", tagName, "-m", "Release $version")
        if (tagResult.contains("error") || tagResult.contains("fatal")) {
            logger.error("❌ Failed to create tag: $tagResult")
            return false
        }

        logger.lifecycle("🚀 Pushing tag to remote...")
        val pushResult = project.runGit("git", "push", "origin", tagName)
        if (pushResult.contains("error") || pushResult.contains("fatal")) {
            logger.error("❌ Failed to push tag: $pushResult")
            return false
        }

        logger.lifecycle("✅ Tag '$tagName' created and pushed")

        if (!skipGitHub) {
            createGitHubRelease(
                project = project,
                tagName = tagName,
                version = version,
                tagPrefix = tagPrefix,
                prerelease = prerelease,
                draft = draft,
                releaseNotes = releaseNotes,
                changelogPath = changelogPath,
            )
        }

        return true
    }

    /**
     * Bumps the version in gradle.properties.
     *
     * @return the new version string, or null if the bump failed
     */
    fun bumpVersion(
        project: Project,
        versionKey: String?,
        bumpType: String,
        commit: Boolean,
    ): String? {
        val logger = project.logger
        val file = findVersionFile(project)
        if (file == null) {
            logger.error("❌ Could not find version file (gradle.properties or build.gradle.kts)")
            return null
        }

        val currentVersion = readCurrentVersion(file, versionKey)
        if (currentVersion == null) {
            if (versionKey != null) {
                logger.error("❌ Could not find $versionKey in ${file.name}")
                logger.lifecycle("   Add '$versionKey=0.0.1' to gradle.properties")
            } else {
                logger.error("❌ Could not read current version from ${file.name}")
            }
            return null
        }

        val newVersion = calculateNewVersion(currentVersion, bumpType, logger)
        val groupLabel = if (versionKey != null) "${versionKey}: " else ""
        logger.lifecycle("🆙 Bumping ${groupLabel}version: $currentVersion → $newVersion")

        updateVersionInFile(file, currentVersion, newVersion, versionKey)
        logger.lifecycle("✅ Updated ${file.name}")

        if (commit) {
            val msg = if (versionKey != null) "chore(${versionKey}): bump version to $newVersion"
                      else "chore: bump version to $newVersion"
            commitVersionChange(project, file, msg, logger)
        }

        logger.lifecycle("📦 New version: $newVersion")
        return newVersion
    }

    // ── GitHub release ──────────────────────────────────────────────────

    private fun createGitHubRelease(
        project: Project,
        tagName: String,
        version: String,
        tagPrefix: String,
        prerelease: Boolean,
        draft: Boolean,
        releaseNotes: String?,
        changelogPath: String?,
    ) {
        val logger = project.logger
        val token = System.getenv("GITHUB_TOKEN")
        if (token.isNullOrBlank()) {
            logger.warn("⚠️  GITHUB_TOKEN not set, skipping GitHub release creation")
            logger.lifecycle("   Set GITHUB_TOKEN to create GitHub releases automatically")
            return
        }

        val remoteUrl = project.detectRemoteUrl().orNull
        if (remoteUrl.isNullOrBlank() || !remoteUrl.contains("github.com")) {
            logger.warn("⚠️  Not a GitHub repository, skipping release creation")
            return
        }

        val repoPath = remoteUrl
            .removePrefix("https://github.com/")
            .removeSuffix(".git")

        val apiUrl = "https://api.github.com/repos/$repoPath/releases"

        val notes = releaseNotes ?: generateReleaseNotes(project, tagName, tagPrefix, changelogPath)
        val body = buildJsonBody(tagName, version, notes, draft, prerelease)

        logger.lifecycle("📦 Creating GitHub release...")

        try {
            val connection = URI(apiUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.doOutput = true

            connection.outputStream.bufferedWriter().use { it.write(body) }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().readText()
                val htmlUrl = extractHtmlUrl(response)
                logger.lifecycle("✅ GitHub release created: $htmlUrl")
            } else {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                logger.error("❌ Failed to create GitHub release (HTTP $responseCode): $error")
            }
        } catch (e: Exception) {
            logger.error("❌ Failed to create GitHub release: ${e.message}")
        }
    }

    internal fun generateReleaseNotes(
        project: Project,
        tagName: String,
        tagPrefix: String,
        changelogPath: String?,
    ): String {
        val logger = project.logger
        val changelogNotes = readChangelogSection(project, tagName.removePrefix(tagPrefix), changelogPath)
        if (changelogNotes != null) {
            logger.lifecycle("📝 Using release notes from CHANGELOG.md")
            return changelogNotes
        }

        val lastTag = project.runGit("git", "describe", "--tags", "--abbrev=0", "HEAD^")
        val since = if (lastTag.isNotBlank() && !lastTag.contains("fatal")) lastTag else "HEAD~20"

        val commits = project.runGit("git", "log", "--format=- %s", "$since..$tagName")

        return if (commits.isNotBlank()) {
            "## What's Changed\n\n$commits"
        } else {
            "Release $tagName"
        }
    }

    internal fun readChangelogSection(
        project: Project,
        version: String,
        changelogPath: String?,
    ): String? {
        val changelogFile = changelogPath?.let { project.file(it) }
            ?: project.file("CHANGELOG.md").takeIf { it.exists() }
            ?: project.rootProject.file("CHANGELOG.md").takeIf { it.exists() }
            ?: return null

        if (!changelogFile.exists()) return null

        val content = changelogFile.readText()

        val versionPattern = if (version.equals("unreleased", ignoreCase = true)) {
            """## \[Unreleased\]"""
        } else {
            """## \[${Regex.escape(version)}\]"""
        }

        val sectionRegex = Regex(
            """$versionPattern[^\n]*\n(.*?)(?=\n## \[|$)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        val match = sectionRegex.find(content) ?: return null
        val section = match.groupValues[1].trim()

        return section.ifBlank { null }
    }

    internal fun buildJsonBody(
        tagName: String,
        version: String,
        notes: String,
        draft: Boolean,
        prerelease: Boolean,
    ): String {
        val escapedNotes = notes
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t")

        return """
          {
            "tag_name": "$tagName",
            "name": "v$version",
            "body": "$escapedNotes",
            "draft": $draft,
            "prerelease": $prerelease
          }
        """.trimIndent()
    }

    private fun extractHtmlUrl(json: String): String {
        val regex = """"html_url"\s*:\s*"([^"]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1) ?: "https://github.com"
    }

    // ── Version helpers ─────────────────────────────────────────────────

    internal fun findVersionFile(project: Project): File? {
        val gradleProps = project.rootProject.file("gradle.properties")
        if (gradleProps.exists() && gradleProps.readText().contains("version")) return gradleProps

        val buildFile = project.rootProject.file("build.gradle.kts")
        if (buildFile.exists() && buildFile.readText().contains("version")) return buildFile

        val subBuildFile = project.file("build.gradle.kts")
        if (subBuildFile.exists() && subBuildFile.readText().contains("version")) return subBuildFile

        return null
    }

    internal fun readCurrentVersion(file: File, versionKey: String? = null): String? {
        val content = file.readText()

        if (versionKey != null) {
            val keyRegex = Regex("""^${Regex.escape(versionKey)}\s*=\s*(.+)$""", RegexOption.MULTILINE)
            val match = keyRegex.find(content)
            return match?.groupValues?.get(1)?.trim()?.removeSurrounding("\"")
        }

        val libVersionMatch = Regex("""^library\.version\s*=\s*(.+)$""", RegexOption.MULTILINE).find(content)
        if (libVersionMatch != null) {
            return libVersionMatch.groupValues[1].trim().removeSurrounding("\"")
        }

        val propsMatch = Regex("""^version\s*=\s*(.+)$""", RegexOption.MULTILINE).find(content)
        if (propsMatch != null) {
            return propsMatch.groupValues[1].trim().removeSurrounding("\"")
        }

        val ktsMatch = Regex("""version\s*=\s*"([^"]+)"""").find(content)
        if (ktsMatch != null) {
            return ktsMatch.groupValues[1]
        }

        val groovyMatch = Regex("""version\s*[=]?\s*['"]([^'"]+)['"]""").find(content)
        if (groovyMatch != null) {
            return groovyMatch.groupValues[1]
        }

        return null
    }

    internal fun calculateNewVersion(current: String, bump: String, logger: Logger? = null): String {
        if (bump.matches(Regex("""^\d+\.\d+\.\d+.*"""))) {
            return bump
        }

        val versionRegex = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-([a-zA-Z]+)\.?(\d+)?)?$""")
        val match = versionRegex.find(current)

        if (match == null) {
            logger?.warn("⚠️  Version '$current' is not semantic, using as-is")
            return current
        }

        val major = match.groupValues[1].toInt()
        val minor = match.groupValues[2].toInt()
        val patch = match.groupValues[3].toInt()
        val prereleaseLabel = match.groupValues[4].ifEmpty { null }
        val prereleaseNum = match.groupValues[5].toIntOrNull()

        return when (bump.lowercase()) {
            "major" -> "${major + 1}.0.0"
            "minor" -> "$major.${minor + 1}.0"
            "patch" -> "$major.$minor.${patch + 1}"
            "prerelease", "pre" -> {
                if (prereleaseLabel != null && prereleaseNum != null) {
                    "$major.$minor.$patch-$prereleaseLabel.${prereleaseNum + 1}"
                } else if (prereleaseLabel != null) {
                    "$major.$minor.$patch-$prereleaseLabel.1"
                } else {
                    "$major.$minor.$patch-alpha.1"
                }
            }
            "release" -> {
                "$major.$minor.$patch"
            }
            "alpha" -> "$major.$minor.$patch-alpha.1"
            "beta" -> "$major.$minor.$patch-beta.1"
            "rc" -> "$major.$minor.$patch-rc.1"
            else -> {
                if (prereleaseLabel != null && prereleaseNum != null) {
                    "$major.$minor.$patch-$prereleaseLabel.${prereleaseNum + 1}"
                } else {
                    "$major.$minor.${patch + 1}"
                }
            }
        }
    }

    internal fun updateVersionInFile(
        file: File,
        oldVersion: String,
        newVersion: String,
        versionKey: String? = null,
    ) {
        var content = file.readText()

        if (versionKey != null) {
            content = content.replace(
                Regex("""^(${Regex.escape(versionKey)}\s*=\s*).*$""", RegexOption.MULTILINE),
                "$1$newVersion"
            )
            file.writeText(content)
            return
        }

        content = content.replace(
            Regex("""^(library\.version\s*=\s*).*$""", RegexOption.MULTILINE),
            "$1$newVersion"
        )

        content = content.replace(
            Regex("""^(version\s*=\s*).*$""", RegexOption.MULTILINE),
            "$1$newVersion"
        )

        content = content.replace(
            Regex("""(version\s*=\s*")${Regex.escape(oldVersion)}(")"""),
            "$1$newVersion$2"
        )

        content = content.replace(
            Regex("""(version\s*[=]?\s*['"])${Regex.escape(oldVersion)}(['"])"""),
            "$1$newVersion$2"
        )

        file.writeText(content)
    }

    private fun commitVersionChange(project: Project, file: File, message: String, logger: Logger) {
        logger.lifecycle("📝 Committing version change...")

        val workDir = project.rootDir
        val gitAdd = ProcessBuilder("git", "add", file.absolutePath)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()
        gitAdd.waitFor()

        val gitCommit = ProcessBuilder("git", "commit", "-m", message)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()
        val result = gitCommit.inputStream.bufferedReader().readText()
        gitCommit.waitFor()

        if (gitCommit.exitValue() == 0) {
            logger.lifecycle("✅ Committed version bump")
        } else {
            logger.warn("⚠️  Could not commit: $result")
        }
    }
}
