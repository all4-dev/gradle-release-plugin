package dev.all4.gradle.release.tasks

import dev.all4.gradle.release.util.containsAny
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * Task to import external artifacts into a local Maven repository.
 *
 * Usage:
 * ```bash
 * # Import with explicit coordinates (recommended)
 * ./gradlew importArtifact --file=/path/to/lib.aar \
 *   --group=my.domain --name=my-lib --version=1.0.0
 *
 * # With custom prefix (adds prefix to group)
 * ./gradlew importArtifact --file=/path/to/lib.aar \
 *   --group=com.legacy --name=old-lib --version=2.0.0 --prefix=standalone
 * ```
 */
public abstract class ImportArtifactTask : DefaultTask() {

  @get:InputFile
  @get:Optional
  public abstract val file: RegularFileProperty

  @get:Input
  @get:Optional
  public abstract val artifactGroup: Property<String>

  @get:Input
  @get:Optional
  public abstract val artifactName: Property<String>

  @get:Input
  @get:Optional
  public abstract val artifactVersion: Property<String>

  @get:Input
  @get:Optional
  public abstract val prefix: Property<String>

  @get:OutputDirectory
  public abstract val outputDir: DirectoryProperty

  init {
    group = "publishing"
    description = "Import external artifacts into local Maven repository"
    outputDir.convention(project.layout.buildDirectory.dir("external-artifacts"))
  }

  @Option(option = "file", description = "Path to artifact file (jar/aar)")
  public fun setFileOption(path: String) {
    file.set(File(path))
  }

  @Option(option = "group", description = "Maven group ID (e.g., my.domain)")
  public fun setGroupOption(value: String) {
    artifactGroup.set(value)
  }

  @Option(option = "name", description = "Maven artifact name")
  public fun setNameOption(value: String) {
    artifactName.set(value)
  }

  @Option(option = "version", description = "Maven version")
  public fun setVersionOption(value: String) {
    artifactVersion.set(value)
  }

  @Option(option = "prefix", description = "Group prefix (e.g., standalone)")
  public fun setPrefixOption(value: String) {
    prefix.set(value)
  }

  @Option(option = "output", description = "Output directory")
  public fun setOutputOption(path: String) {
    outputDir.set(File(path))
  }

  @TaskAction
  public fun execute() {
    val f = file.orNull?.asFile

    // Validate inputs
    if (f == null) {
      logger.error(
          """
          |❌ Missing --file parameter
          |
          |Usage:
          |  ./gradlew importArtifact --file=/path/to/lib.aar \
          |    --group=my.domain --name=my-lib --version=1.0.0
          |
          |Options:
          |  --file     Path to artifact (required)
          |  --group    Maven group ID (required)
          |  --name     Artifact name (required)
          |  --version  Version (required)
          |  --prefix   Group prefix (optional, e.g., standalone → standalone.my.domain)
          |  --output   Output directory (default: build/external-artifacts)
          """
              .trimMargin())
      return
    }

    if (!f.exists()) {
      logger.error("❌ File not found: ${f.absolutePath}")
      return
    }

    // Try explicit params first, then autodetect from filename
    val detected = autodetect(f)
    val grp = artifactGroup.orNull ?: detected?.group
    val name = artifactName.orNull ?: detected?.name
    val ver = artifactVersion.orNull ?: detected?.version

    if (grp == null || name == null || ver == null) {
      val missing = listOfNotNull(
          if (grp == null) "--group" else null,
          if (name == null) "--name" else null,
          if (ver == null) "--version" else null,
      )
      logger.error(
          """
          |❌ Missing: ${missing.joinToString(", ")}
          |
          |Could not autodetect from filename: ${f.name}
          |
          |Provide explicitly:
          |  ./gradlew importArtifact --file=${f.absolutePath} \
          |    --group=my.domain --name=my-lib --version=1.0.0
          |
          |Or use autodetect-friendly naming:
          |  my.domain__my-lib__1.0.0.aar
          |  my-lib-1.0.0.jar
          """
              .trimMargin())
      return
    }

    if (detected != null && artifactGroup.orNull == null) {
      logger.lifecycle("🔍 Autodetected: $grp:$name:$ver")
    }

    val fullGroup = prefix.orNull?.let { "$it.$grp" } ?: grp
    val ext = f.extension

    val output = outputDir.get().asFile
    val targetDir = File(output, "${fullGroup.replace('.', '/')}/$name/$ver")
    targetDir.mkdirs()

    val targetFile = File(targetDir, "$name-$ver.$ext")
    f.copyTo(targetFile, overwrite = true)

    // Create minimal POM
    val pomFile = File(targetDir, "$name-$ver.pom")
    pomFile.writeText(
        """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<project xmlns="http://maven.apache.org/POM/4.0.0">
        |  <modelVersion>4.0.0</modelVersion>
        |  <groupId>$fullGroup</groupId>
        |  <artifactId>$name</artifactId>
        |  <version>$ver</version>
        |  <packaging>$ext</packaging>
        |</project>
        """
            .trimMargin())

    val alias = (prefix.orNull?.let { "$it-" } ?: "") + name
    val relativePath = targetFile.relativeTo(output).path

    // Update index.md
    updateIndex(
        output = output,
        fullGroup = fullGroup,
        name = name,
        version = ver,
        alias = alias,
        relativePath = relativePath,
    )

    // Check if repository is configured
    val repoConfigured = isRepositoryConfigured(output)

    logger.lifecycle(
        buildString {
          appendLine()
          appendLine("✅ Imported: $fullGroup:$name:$ver")
          appendLine("   → ${targetFile.absolutePath}")
          appendLine()
          appendLine("📝 Version catalog:")
          appendLine("   $alias = { group = \"$fullGroup\", name = \"$name\", version = \"$ver\" }")
          appendLine()
          appendLine("📋 Updated: ${output.resolve("index.md").absolutePath}")

          if (!repoConfigured) {
            appendLine()
            appendLine("⚠️  Repository not configured! Add to settings.gradle.kts:")
            appendLine()
            appendLine("   dependencyResolutionManagement {")
            appendLine("     repositories {")
            appendLine("       maven { url = uri(\"${output.absolutePath}\") }")
            appendLine("     }")
            appendLine("   }")
            appendLine()
            appendLine("   Or in build.gradle.kts:")
            appendLine()
            appendLine("   repositories {")
            appendLine("     maven { url = uri(\"${output.absolutePath}\") }")
            appendLine("   }")
          }
        })
  }

  private fun isRepositoryConfigured(outputDir: File): Boolean {
    val outputPath = outputDir.absolutePath
    val relativePath = outputDir.relativeTo(project.rootProject.projectDir).path
    val searchTerms = arrayOf(outputPath, relativePath, "external-artifacts", "maven-standalone")

    return listOf(
        "settings.gradle.kts", "settings.gradle",
        "build.gradle.kts", "build.gradle"
    ).any { project.rootProject.file(it).containsAny(*searchTerms) }
  }

  private fun updateIndex(
      output: File,
      fullGroup: String,
      name: String,
      version: String,
      alias: String,
      relativePath: String,
  ) {
    val indexFile = output.resolve("index.md")
    val coordinate = "$fullGroup:$name:$version"
    val catalogEntry = "$alias = { group = \"$fullGroup\", name = \"$name\", version = \"$version\" }"
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    val newEntry =
        """
        |### `$coordinate`
        |- **Path:** `$relativePath`
        |- **Catalog:** `$catalogEntry`
        |- **Imported:** $timestamp
        """
            .trimMargin()

    if (!indexFile.exists()) {
      indexFile.writeText(
          """
          |# Standalone Maven Repository
          |
          |Artifacts imported via `./gradlew importArtifact`
          |
          |## Usage
          |
          |```kotlin
          |repositories {
          |  maven { url = uri("path/to/this/directory") }
          |}
          |```
          |
          |## Artifacts
          |
          |$newEntry
          |"""
              .trimMargin() + "\n")
    } else {
      val content = indexFile.readText()
      val lines = content.lines().toMutableList()

      // Check if coordinate already exists
      val existingIndex = lines.indexOfFirst { it.contains("### `$coordinate`") }

      if (existingIndex >= 0) {
        // Remove old entry (header + 3 list items)
        repeat(4) { lines.removeAt(existingIndex) }
        // Insert updated entry
        newEntry.lines().reversed().forEach { lines.add(existingIndex, it) }
      } else {
        // Append new entry
        lines.add("")
        lines.addAll(newEntry.lines())
      }
      indexFile.writeText(lines.joinToString("\n"))
    }
  }

  private data class DetectedCoords(val group: String?, val name: String?, val version: String?)

  private fun autodetect(file: File): DetectedCoords? {
    val baseName = file.nameWithoutExtension

    // Pattern 1: group__name__version (e.g., my.domain__my-lib__1.0.0)
    val fullPattern = Regex("""^(.+)__(.+)__(.+)$""")
    fullPattern.matchEntire(baseName)?.let { m ->
      return DetectedCoords(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }

    // Pattern 2: name-version (e.g., my-lib-1.0.0)
    val simplePattern = Regex("""^(.+)-(\d+\..+)$""")
    simplePattern.matchEntire(baseName)?.let { m ->
      return DetectedCoords(null, m.groupValues[1], m.groupValues[2])
    }

    return null
  }
}
