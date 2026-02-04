package dev.all4.gradle.release.tasks

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * Task to bump project version using semantic versioning.
 *
 * Usage:
 * ```bash
 * ./gradlew bumpVersion --bump=patch   # 1.0.0 → 1.0.1
 * ./gradlew bumpVersion --bump=minor   # 1.0.0 → 1.1.0
 * ./gradlew bumpVersion --bump=major   # 1.0.0 → 2.0.0
 * ./gradlew bumpVersion --bump=pre     # 1.0.0-alpha.1 → 1.0.0-alpha.2
 * ./gradlew bumpVersion --bump=release # 1.0.0-alpha.3 → 1.0.0
 * ./gradlew bumpVersion --bump=1.2.3   # Set explicit version
 * ```
 *
 * Updates version in gradle.properties or build.gradle.kts
 */
public abstract class BumpVersionTask : DefaultTask() {

  @get:Input
  @get:Optional
  public abstract val bumpType: Property<String>

  @get:Input
  @get:Optional
  public abstract val groupName: Property<String>

  @get:Input
  @get:Optional
  public abstract val commitChanges: Property<Boolean>

  @get:Input
  @get:Optional
  public abstract val tagAfterBump: Property<Boolean>

  @get:InputFile
  @get:Optional
  public abstract val versionFile: RegularFileProperty

  @get:Input
  public abstract val rootDir: Property<File>

  init {
    group = "versioning"
    description = "Bump project version (patch, minor, major, pre, release, or explicit X.Y.Z)"
    bumpType.convention("patch")
    commitChanges.convention(false)
    tagAfterBump.convention(false)
    rootDir.convention(project.rootDir)

    val gradleProps = project.rootProject.file("gradle.properties")
    if (gradleProps.exists()) {
      versionFile.set(gradleProps)
    } else {
      val buildFile = project.rootProject.file("build.gradle.kts")
      if (buildFile.exists()) {
        versionFile.set(buildFile)
      }
    }
  }

  @Option(option = "bump", description = "Bump type: patch, minor, major, pre, release, or explicit X.Y.Z")
  public fun setBumpOption(value: String) {
    bumpType.set(value)
  }

  @Option(option = "group", description = "Library group to bump (e.g., logger, theme). Bumps version.<group> property.")
  public fun setGroupOption(value: String) {
    groupName.set(value)
  }

  @Option(option = "commit", description = "Commit version change to git")
  public fun setCommitOption(value: String) {
    commitChanges.set(value.toBoolean())
  }

  @Option(option = "tag", description = "Create git tag after bump")
  public fun setTagOption(value: String) {
    tagAfterBump.set(value.toBoolean())
  }

  @TaskAction
  public fun execute() {
    val bump = bumpType.get()
    val group = groupName.orNull

    if (!versionFile.isPresent) {
      logger.error("❌ Could not find version file (gradle.properties or build.gradle.kts)")
      return
    }

    val file = versionFile.get().asFile
    val versionKey = if (group != null) "version.$group" else null
    val currentVersion = readCurrentVersion(file, versionKey)
    
    if (currentVersion == null) {
      if (group != null) {
        logger.error("❌ Could not find version.$group in ${file.name}")
        logger.lifecycle("   Add 'version.$group=0.0.1' to gradle.properties")
      } else {
        logger.error("❌ Could not read current version from ${file.name}")
      }
      return
    }

    val newVersion = calculateNewVersion(currentVersion, bump)
    val label = if (group != null) "$group: " else ""
    logger.lifecycle("🆙 Bumping ${label}version: $currentVersion → $newVersion")

    updateVersionInFile(file, currentVersion, newVersion, versionKey)
    logger.lifecycle("✅ Updated ${file.name}")

    if (commitChanges.get()) {
      val msg = if (group != null) "chore($group): bump version to $newVersion" 
                else "chore: bump version to $newVersion"
      commitVersionChange(file, newVersion, msg)
    }

    if (tagAfterBump.get()) {
      val tag = if (group != null) "$group/v$newVersion" else "v$newVersion"
      createTag(tag, newVersion)
    }

    logger.lifecycle("")
    logger.lifecycle("📦 New version: $newVersion")
  }

  private fun readCurrentVersion(file: File, versionKey: String? = null): String? {
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

  private fun calculateNewVersion(current: String, bump: String): String {
    if (bump.matches(Regex("""^\d+\.\d+\.\d+.*"""))) {
      return bump
    }

    val versionRegex = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-([a-zA-Z]+)\.?(\d+)?)?$""")
    val match = versionRegex.find(current)

    if (match == null) {
      logger.warn("⚠️  Version '$current' is not semantic, using as-is")
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

  private fun updateVersionInFile(file: File, oldVersion: String, newVersion: String, versionKey: String? = null) {
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

  private fun commitVersionChange(file: File, version: String, message: String) {
    logger.lifecycle("📝 Committing version change...")
    
    val workDir = rootDir.get()
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

  private fun createTag(tag: String, version: String) {
    logger.lifecycle("🏷️  Creating tag $tag...")

    val workDir = rootDir.get()
    val gitTag = ProcessBuilder("git", "tag", "-a", tag, "-m", "Release $version")
        .directory(workDir)
        .redirectErrorStream(true)
        .start()
    val result = gitTag.inputStream.bufferedReader().readText()
    gitTag.waitFor()

    if (gitTag.exitValue() == 0) {
      logger.lifecycle("✅ Created tag $tag")
    } else {
      logger.warn("⚠️  Could not create tag: $result")
    }
  }
}
