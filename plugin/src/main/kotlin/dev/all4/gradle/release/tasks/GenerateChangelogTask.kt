package dev.all4.gradle.release.tasks

import dev.all4.gradle.release.util.detectRemoteUrl
import dev.all4.gradle.release.util.runGit
import dev.all4.gradle.release.util.toDirectoryPath
import dev.all4.gradle.release.util.truncate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * Task to generate changelog entries from git commits for a library group.
 *
 * Usage:
 * ```bash
 * ./gradlew generateChangelog --group=core --since=v1.0.0
 * ./gradlew generateChangelog --group=core --since=2024-01-01
 * ```
 */
public abstract class GenerateChangelogTask : DefaultTask() {

  @get:Input
  @get:Optional
  public abstract val groupName: Property<String>

  @get:Input
  @get:Optional
  public abstract val modulePaths: SetProperty<String>

  @get:Input
  @get:Optional
  public abstract val since: Property<String>

  @get:Input
  @get:Optional
  public abstract val remoteUrl: Property<String>

  init {
    group = "documentation"
    description = "Generate changelog from git commits for a library group"
    since.convention("HEAD~20")
  }

  @Option(option = "group", description = "Library group name")
  public fun setGroupOption(value: String) {
    groupName.set(value)
  }

  @Option(option = "since", description = "Starting point (tag, commit, or date like 2024-01-01)")
  public fun setSinceOption(value: String) {
    since.set(value)
  }

  @TaskAction
  public fun execute() {
    val group = groupName.orNull
    val paths = modulePaths.orNull?.toList() ?: emptyList()

    if (group == null && paths.isEmpty()) {
      logger.lifecycle(
          """
          |📝 Generate Changelog from Git Commits
          |
          |Usage:
          |  ./gradlew generateChangelog --group=<name> [--since=<ref>]
          |
          |Options:
          |  --group   Library group name (from libraryGroups config)
          |  --since   Starting point: tag (v1.0.0), commit, or date (2024-01-01)
          |            Default: HEAD~20 (last 20 commits)
          |
          |Examples:
          |  ./gradlew generateChangelog --group=core
          |  ./gradlew generateChangelog --group=core --since=v1.0.0
          |  ./gradlew generateChangelog --group=core --since=2024-01-01
          """
              .trimMargin())
      return
    }

    val remote = remoteUrl.orNull ?: project.detectRemoteUrl()

    val dirPaths = paths.map { it.toDirectoryPath() }

    val commits = getCommits(dirPaths, since.get())

    if (commits.isEmpty()) {
      logger.lifecycle("No commits found for ${group ?: "specified paths"} since ${since.get()}")
      return
    }

    val output = buildString {
      appendLine()
      appendLine("═══════════════════════════════════════════════════════════════════════════")
      appendLine("⚠️  THIS IS A HELPER FOR REVIEW - Edit before adding to CHANGELOG.md")
      appendLine("═══════════════════════════════════════════════════════════════════════════")
      appendLine()
      appendLine("## Changelog for ${group ?: "library"}")
      appendLine()
      appendLine("Generated from ${commits.size} commits since `${since.get()}`")
      appendLine()

      for (commit in commits) {
        val commitLink = if (remote != null) {
          "$remote/commit/${commit.hash}"
        } else {
          commit.shortHash
        }
        val truncatedMsg = commit.message.truncate(80)
        appendLine("─────────────────────────────────────────────────────────────────────────────")
        appendLine("📅 ${commit.date}  |  🔗 ${commit.shortHash}  |  📁 ${commit.filesChanged} files")
        appendLine("   $truncatedMsg")
        appendLine("   → $commitLink")
        appendLine("   📝 YOUR NOTE: ")
      }

      appendLine()
      appendLine("═══════════════════════════════════════════════════════════════════════════")
      appendLine("📋 DRAFT for CHANGELOG.md (review and edit!):")
      appendLine("═══════════════════════════════════════════════════════════════════════════")
      appendLine()
      appendLine("```markdown")
      appendLine("## [Unreleased]")
      appendLine()

      val grouped = commits.groupBy { categorizeCommit(it.message) }
      for ((category, categoryCommits) in grouped) {
        if (categoryCommits.isNotEmpty()) {
          appendLine("### $category")
          for (c in categoryCommits) {
            val link = if (remote != null) "([${c.shortHash}]($remote/commit/${c.hash}))" else ""
            val cleanMsg = cleanCommitMessage(c.message)
            appendLine("- ${cleanMsg.truncate(60)} $link")
          }
          appendLine()
        }
      }
      appendLine("```")
    }

    logger.lifecycle(output)
  }

  private data class CommitInfo(
      val hash: String,
      val shortHash: String,
      val date: String,
      val message: String,
      val filesChanged: Int,
  )

  private fun getCommits(paths: List<String>, since: String): List<CommitInfo> {
    val commits = mutableListOf<CommitInfo>()

    try {
      val logArgs = mutableListOf("git", "log", "--format=%H|%h|%ct|%s", "$since..HEAD")
      if (paths.isNotEmpty()) {
        logArgs.add("--")
        logArgs.addAll(paths)
      }

      val logOutput = project.runGit(logArgs)
      if (logOutput.isBlank()) return emptyList()

      for (line in logOutput.lines().filter { it.isNotBlank() }) {
        val parts = line.split("|", limit = 4)
        if (parts.size >= 4) {
          val hash = parts[0]
          val shortHash = parts[1]
          val timestamp = parts[2].toLongOrNull() ?: 0
          val message = parts[3]

          val filesOutput = project.runGit("git", "diff-tree", "--no-commit-id", "--name-only", "-r", hash)
          val filesChanged = filesOutput.lines().filter { it.isNotBlank() }.size

          val date = Instant.ofEpochSecond(timestamp)
              .atZone(ZoneId.systemDefault())
              .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

          commits.add(CommitInfo(hash, shortHash, date, message, filesChanged))
        }
      }
    } catch (e: Exception) {
      logger.debug("Error getting commits: ${e.message}")
    }

    return commits
  }

  private fun categorizeCommit(message: String): String {
    return when {
      message.startsWith("feat") -> "Added"
      message.startsWith("fix") -> "Fixed"
      message.startsWith("docs") -> "Documentation"
      message.startsWith("refactor") -> "Changed"
      message.startsWith("perf") -> "Performance"
      message.startsWith("test") -> "Tests"
      message.startsWith("chore") -> "Maintenance"
      message.contains("breaking", ignoreCase = true) -> "Breaking Changes"
      message.contains("deprecat", ignoreCase = true) -> "Deprecated"
      message.contains("remov", ignoreCase = true) -> "Removed"
      else -> "Changed"
    }
  }

  private fun cleanCommitMessage(message: String): String {
    return message
        .removePrefix("feat:")
        .removePrefix("feat(")
        .removePrefix("fix:")
        .removePrefix("fix(")
        .removePrefix("docs:")
        .removePrefix("refactor:")
        .removePrefix("chore:")
        .removePrefix("perf:")
        .removePrefix("test:")
        .replace(Regex("^[^)]+\\):\\s*"), "") // Remove scope like "feat(core): "
        .trim()
  }
}
