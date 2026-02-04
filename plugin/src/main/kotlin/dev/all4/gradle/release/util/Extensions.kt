package dev.all4.gradle.release.util

import java.io.File
import org.gradle.api.Project

internal fun String.capitalized(): String = replaceFirstChar { it.uppercase() }

internal fun String.truncate(maxLength: Int): String =
    if (length <= maxLength) this else take(maxLength - 3) + "..."

internal fun Project.runGit(vararg args: String): String = runGit(args.toList())

internal fun Project.runGit(args: List<String>): String = try {
  ProcessBuilder(args)
      .directory(rootDir)
      .redirectErrorStream(true)
      .start()
      .inputStream.bufferedReader().readText().trim()
} catch (e: Exception) { "" }

internal fun Project.detectRemoteUrl(): org.gradle.api.provider.Provider<String> {
  return providers.exec {
    commandLine("git", "remote", "get-url", "origin")
    isIgnoreExitValue = true
  }.standardOutput.asText.map { url ->
    val trimmed = url.trim()
    when {
      trimmed.startsWith("git@github.com:") ->
          "https://github.com/" + trimmed.removePrefix("git@github.com:").removeSuffix(".git")
      trimmed.startsWith("https://github.com/") ->
          trimmed.removeSuffix(".git")
      trimmed.startsWith("git@gitlab.com:") ->
          "https://gitlab.com/" + trimmed.removePrefix("git@gitlab.com:").removeSuffix(".git")
      trimmed.startsWith("https://gitlab.com/") ->
          trimmed.removeSuffix(".git")
      trimmed.isNotBlank() -> trimmed.removeSuffix(".git")
      else -> ""
    }
  }
}

internal fun File.containsAny(vararg strings: String): Boolean =
    exists() && readText().let { content -> strings.any { content.contains(it) } }

internal fun String.toDirectoryPath(): String = removePrefix(":").replace(":", "/")

internal fun String.toTaskName(): String = capitalized().replace(Regex("[^a-zA-Z0-9]"), "")
