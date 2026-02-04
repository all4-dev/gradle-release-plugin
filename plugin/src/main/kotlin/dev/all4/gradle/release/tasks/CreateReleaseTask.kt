package dev.all4.gradle.release.tasks

import dev.all4.gradle.release.util.detectRemoteUrl
import dev.all4.gradle.release.util.runGit
import java.net.HttpURLConnection
import java.net.URI
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * Task to create git tags and GitHub releases.
 *
 * Usage:
 * ```bash
 * ./gradlew createRelease --version=1.0.0
 * ./gradlew createRelease --version=1.0.0 --draft
 * ./gradlew createRelease --version=1.0.0 --prerelease
 * ```
 *
 * Requires GITHUB_TOKEN environment variable for GitHub release creation.
 */
public abstract class CreateReleaseTask : DefaultTask() {

  @get:Input
  @get:Optional
  public abstract val releaseVersion: Property<String>

  @get:Input
  @get:Optional
  public abstract val tagPrefix: Property<String>

  @get:Input
  @get:Optional
  public abstract val releaseNotes: Property<String>

  @get:Input
  @get:Optional
  public abstract val draft: Property<Boolean>

  @get:Input
  @get:Optional
  public abstract val prerelease: Property<Boolean>

  @get:Input
  @get:Optional
  public abstract val skipGitHub: Property<Boolean>

  @get:Input
  @get:Optional
  public abstract val bumpType: Property<String>

  @get:Input
  @get:Optional
  public abstract val changelogPath: Property<String>

  init {
    group = "publishing"
    description = "Creates git tag and GitHub release"
    tagPrefix.convention("v")
    draft.convention(false)
    prerelease.convention(false)
    skipGitHub.convention(false)
  }

  @Option(option = "version", description = "Release version (e.g., 1.0.0)")
  public fun setVersionOption(value: String) {
    releaseVersion.set(value)
  }

  @Option(option = "prefix", description = "Tag prefix (default: v)")
  public fun setPrefixOption(value: String) {
    tagPrefix.set(value)
  }

  @Option(option = "notes", description = "Release notes")
  public fun setNotesOption(value: String) {
    releaseNotes.set(value)
  }

  @Option(option = "draft", description = "Create as draft release")
  public fun setDraftOption(value: String) {
    draft.set(value.toBoolean())
  }

  @Option(option = "prerelease", description = "Mark as pre-release")
  public fun setPrereleaseOption(value: String) {
    prerelease.set(value.toBoolean())
  }

  @Option(option = "skip-github", description = "Only create git tag, skip GitHub release")
  public fun setSkipGitHubOption(value: String) {
    skipGitHub.set(value.toBoolean())
  }

  @Option(option = "bump", description = "Bump version before release (patch, minor, major)")
  public fun setBumpOption(value: String) {
    bumpType.set(value)
  }

  @TaskAction
  public fun execute() {
    val bump = bumpType.orNull
    if (bump != null && releaseVersion.orNull == null) {
      val newVersion = bumpAndGetVersion(bump)
      if (newVersion != null) {
        releaseVersion.set(newVersion)
      }
    }
    val version = releaseVersion.orNull
    if (version == null) {
      logger.error(
          """
          |❌ Missing --version parameter
          |
          |Usage:
          |  ./gradlew createRelease --version=1.0.0
          |
          |Options:
          |  --version=X.Y.Z    Release version (required)
          |  --prefix=v         Tag prefix (default: v)
          |  --notes="..."      Release notes
          |  --draft=true       Create as draft
          |  --prerelease=true  Mark as pre-release
          |  --skip-github=true Only create git tag
          """
              .trimMargin())
      return
    }

    val prefix = tagPrefix.get()
    val tagName = "$prefix$version"

    val existingTags = project.runGit("git", "tag", "-l", tagName)
    if (existingTags.isNotBlank()) {
      logger.error("❌ Tag '$tagName' already exists")
      return
    }

    val status = project.runGit("git", "status", "--porcelain")
    if (status.isNotBlank()) {
      logger.warn("⚠️  Warning: You have uncommitted changes")
    }

    logger.lifecycle("📌 Creating tag: $tagName")
    val tagResult = project.runGit("git", "tag", "-a", tagName, "-m", "Release $version")
    if (tagResult.contains("error") || tagResult.contains("fatal")) {
      logger.error("❌ Failed to create tag: $tagResult")
      return
    }

    logger.lifecycle("🚀 Pushing tag to remote...")
    val pushResult = project.runGit("git", "push", "origin", tagName)
    if (pushResult.contains("error") || pushResult.contains("fatal")) {
      logger.error("❌ Failed to push tag: $pushResult")
      return
    }

    logger.lifecycle("✅ Tag '$tagName' created and pushed")

    if (!skipGitHub.get()) {
      createGitHubRelease(tagName, version)
    }
  }

  private fun createGitHubRelease(tagName: String, version: String) {
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

    val notes = releaseNotes.orNull ?: generateReleaseNotes(tagName)

    val body = buildJsonBody(tagName, version, notes)

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

  private fun generateReleaseNotes(tagName: String): String {
    val changelogNotes = readChangelogSection(tagName.removePrefix(tagPrefix.get()))
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

  /**
   * Read release notes from CHANGELOG.md for a specific version.
   * Supports Keep a Changelog format.
   */
  private fun readChangelogSection(version: String): String? {
    val changelogFile = changelogPath.orNull?.let { project.file(it) }
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

  private fun buildJsonBody(tagName: String, version: String, notes: String): String {
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
        "draft": ${draft.get()},
        "prerelease": ${prerelease.get()}
      }
    """.trimIndent()
  }

  private fun extractHtmlUrl(json: String): String {
    val regex = """"html_url"\s*:\s*"([^"]+)"""".toRegex()
    return regex.find(json)?.groupValues?.get(1) ?: "https://github.com"
  }

  private fun bumpAndGetVersion(bump: String): String? {
    val versionFile = findVersionFile() ?: return null
    val currentVersion = readCurrentVersion(versionFile) ?: return null
    val newVersion = calculateNewVersion(currentVersion, bump)

    logger.lifecycle("🆙 Bumping version: $currentVersion → $newVersion")
    updateVersionInFile(versionFile, currentVersion, newVersion)

    val gitAdd = ProcessBuilder("git", "add", versionFile.absolutePath)
        .directory(project.rootDir).redirectErrorStream(true).start()
    gitAdd.waitFor()

    val gitCommit = ProcessBuilder("git", "commit", "-m", "chore: bump version to $newVersion")
        .directory(project.rootDir).redirectErrorStream(true).start()
    gitCommit.waitFor()

    return newVersion
  }

  private fun findVersionFile(): java.io.File? {
    val gradleProps = project.rootProject.file("gradle.properties")
    if (gradleProps.exists() && gradleProps.readText().contains("version")) return gradleProps

    val buildFile = project.rootProject.file("build.gradle.kts")
    if (buildFile.exists() && buildFile.readText().contains("version")) return buildFile

    val subBuildFile = project.file("build.gradle.kts")
    if (subBuildFile.exists() && subBuildFile.readText().contains("version")) return subBuildFile

    return null
  }

  private fun readCurrentVersion(file: java.io.File): String? {
    val content = file.readText()
    val propsMatch = Regex("""^version\s*=\s*(.+)$""", RegexOption.MULTILINE).find(content)
    if (propsMatch != null) return propsMatch.groupValues[1].trim().removeSurrounding("\"")

    val ktsMatch = Regex("""version\s*=\s*"([^"]+)"""").find(content)
    if (ktsMatch != null) return ktsMatch.groupValues[1]

    return null
  }

  private fun calculateNewVersion(current: String, bump: String): String {
    if (bump.matches(Regex("""^\d+\.\d+\.\d+.*"""))) return bump

    val parts = current.split(".")
    if (parts.size < 3) return "$current.0"

    val major = parts[0].toIntOrNull() ?: 0
    val minor = parts[1].toIntOrNull() ?: 0
    val patch = parts[2].replace(Regex("[^0-9].*"), "").toIntOrNull() ?: 0

    return when (bump.lowercase()) {
      "major" -> "${major + 1}.0.0"
      "minor" -> "$major.${minor + 1}.0"
      else -> "$major.$minor.${patch + 1}"
    }
  }

  private fun updateVersionInFile(file: java.io.File, oldVersion: String, newVersion: String) {
    var content = file.readText()
    content = content.replace(
        Regex("""^(version\s*=\s*).*$""", RegexOption.MULTILINE), "$1$newVersion")
    content = content.replace(
        Regex("""(version\s*=\s*")${Regex.escape(oldVersion)}(")"""), "$1$newVersion$2")
    file.writeText(content)
  }
}
