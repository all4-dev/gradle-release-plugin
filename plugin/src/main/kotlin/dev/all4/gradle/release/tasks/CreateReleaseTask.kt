package dev.all4.gradle.release.tasks

import dev.all4.gradle.release.util.ReleaseOperations
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
      val newVersion = ReleaseOperations.bumpVersion(
          project = project,
          versionKey = null,
          bumpType = bump,
          commit = true,
      )
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

    ReleaseOperations.createTagAndRelease(
        project = project,
        version = version,
        tagPrefix = tagPrefix.get(),
        prerelease = prerelease.get(),
        draft = draft.get(),
        releaseNotes = releaseNotes.orNull,
        changelogPath = changelogPath.orNull,
        skipGitHub = skipGitHub.get(),
    )
  }
}
