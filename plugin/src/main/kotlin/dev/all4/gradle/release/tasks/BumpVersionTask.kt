package dev.all4.gradle.release.tasks

import dev.all4.gradle.release.util.ReleaseOperations
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
    val currentVersion = ReleaseOperations.readCurrentVersion(file, versionKey)

    if (currentVersion == null) {
      if (group != null) {
        logger.error("❌ Could not find version.$group in ${file.name}")
        logger.lifecycle("   Add 'version.$group=0.0.1' to gradle.properties")
      } else {
        logger.error("❌ Could not read current version from ${file.name}")
      }
      return
    }

    val newVersion = ReleaseOperations.calculateNewVersion(currentVersion, bump, logger)
    val label = if (group != null) "$group: " else ""
    logger.lifecycle("🆙 Bumping ${label}version: $currentVersion → $newVersion")

    ReleaseOperations.updateVersionInFile(file, currentVersion, newVersion, versionKey)
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
