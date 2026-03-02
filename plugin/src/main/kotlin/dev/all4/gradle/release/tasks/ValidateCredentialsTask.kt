package dev.all4.gradle.release.tasks

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Validates that `publishing.properties` is listed in `.gitignore` before any publish task runs.
 *
 * The file can contain real credentials or 1Password `op://` references that leak vault structure.
 * This task fails fast with a clear error message and fix steps if the file is tracked by git.
 *
 * Runs every time (no `@Output`). Silently passes when the file does not exist or the project
 * is not inside a git repository.
 */
public abstract class ValidateCredentialsTask : DefaultTask() {

    @get:Internal
    public abstract val propertiesFile: RegularFileProperty

    @TaskAction
    public fun execute() {
        val file = propertiesFile.orNull?.asFile ?: return
        if (!file.exists()) return

        val repoRoot = findGitRepoRoot(file) ?: return

        val process =
            ProcessBuilder("git", "check-ignore", "-q", file.absolutePath)
                .directory(repoRoot)
                .redirectErrorStream(true)
                .start()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw GradleException(
                """
                |
                |❌ ${file.name} is not ignored by git.
                |
                |This file can contain credentials or 1Password op:// references
                |that leak vault structure. It must be listed in .gitignore.
                |
                |✅ Fix:
                |  1) Add "${file.name}" to your .gitignore
                |  2) If already tracked, remove it:  git rm --cached ${file.name}
                """
                    .trimMargin()
            )
        }
    }

    private fun findGitRepoRoot(file: File): File? {
        val projectRoot = project.rootDir
        if (file.absolutePath.startsWith(projectRoot.absolutePath)) {
            return projectRoot
        }
        return try {
            val output =
                ProcessBuilder(
                        "git",
                        "-C",
                        file.parentFile.absolutePath,
                        "rev-parse",
                        "--show-toplevel",
                    )
                    .redirectErrorStream(true)
                    .start()
                    .inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            if (output.isNotBlank()) File(output) else null
        } catch (_: Exception) {
            null
        }
    }
}
