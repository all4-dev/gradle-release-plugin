package dev.all4.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

internal data class ReleaseWorkflowOptions(
  val dryRun: Boolean = false,
  val noPush: Boolean = false,
  val skipPublish: Boolean = false,
  val version: String? = null,
)

internal enum class PublishSide {
  LOCAL,
  PORTAL,
  CENTRAL,
}

private data class SecretMapping(
  val keyName: String,
  val secretRef: String,
)

private data class ArtifactDetails(
  val groupId: String,
  val artifactId: String,
  val version: String,
  val pluginId: String,
)

private data class BuildMetadata(
  val groupId: String,
  val version: String,
  val pluginId: String,
)

private data class CommandResult(
  val status: Int,
  val output: String,
)

private object ReleaseWorkflowConstants {
  const val VERSION_PROPERTY = "release.version"
  const val LEGACY_VERSION_PROPERTY = "VERSION"
  const val DRY_RUN_PROPERTY = "release.dryRun"
  const val NO_PUSH_PROPERTY = "release.noPush"
  const val SKIP_PUBLISH_PROPERTY = "release.skipPublish"

  const val PLUGIN_BUILD_FILE = "plugin/build.gradle.kts"
  const val PLUGIN_POM_FILE = "plugin/build/publications/pluginMaven/pom-default.xml"
  const val VERSION_FILE_TARGET = "plugin/build.gradle.kts"

  const val DEFAULT_ARTIFACT_ID = "release-plugin"

  const val GRADLEW = "./gradlew"
  const val PUBLISH_LOCAL_TASK = ":plugin:publishToMavenLocal"
  const val PUBLISH_PORTAL_TASK = ":plugin:publishPlugins"
  const val PUBLISH_CENTRAL_TASK = ":plugin:publishAllPublicationsToMavenCentralRepository"

  const val PLUGIN_PORTAL_BASE_URL = "https://plugins.gradle.org/plugin"
  const val MAVEN_CENTRAL_BASE_URL = "https://repo1.maven.org/maven2"

  val TRUE_VALUES = setOf("1", "true", "yes", "y", "on")
  val FALSE_VALUES = setOf("0", "false", "no", "n", "off")

  val VERSION_DECLARATION_REGEX = Regex(
    pattern = """^version\\s*=\\s*\"([^\"]+)\"\\s*$""",
    option = RegexOption.MULTILINE,
  )

  val GROUP_DECLARATION_REGEX = Regex(
    pattern = """^group\\s*=\\s*\"([^\"]+)\"\\s*$""",
    option = RegexOption.MULTILINE,
  )

  val PLUGIN_ID_REGEX = Regex(
    pattern = "create\\(\\\"release\\\"\\)\\s*\\{.*?id\\s*=\\s*\\\"([^\\\"]+)\\\"",
    options = setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
  )

  val POM_ARTIFACT_ID_REGEX = Regex("""<artifactId>([^<]+)</artifactId>""")
  val STABLE_VERSION_REGEX = Regex("""^\\d+\\.\\d+\\.\\d+$""")
  val ALPHA_VERSION_REGEX = Regex("""^(.*)-alpha\\.(\\d+)$""")
}

private object SecretMappings {
  val portal = listOf(
    SecretMapping(
      keyName = "GRADLE_PUBLISH_KEY",
      secretRef = "op://Private/Gradle Plugin Portal/publishing/key",
    ),
    SecretMapping(
      keyName = "GRADLE_PUBLISH_SECRET",
      secretRef = "op://Private/Gradle Plugin Portal/publishing/secret",
    ),
    SecretMapping(
      keyName = "SIGNING_PASSPHRASE",
      secretRef = "op://Private/GPG Signing Key/publishing/passphrase",
    ),
  )

  val central = listOf(
    SecretMapping(
      keyName = "ORG_GRADLE_PROJECT_mavenCentralUsername",
      secretRef = "op://Private/Sonatype Maven Central/publishing/username",
    ),
    SecretMapping(
      keyName = "ORG_GRADLE_PROJECT_mavenCentralPassword",
      secretRef = "op://Private/Sonatype Maven Central/publishing/password",
    ),
    SecretMapping(
      keyName = "ORG_GRADLE_PROJECT_signing_gnupg_passphrase",
      secretRef = "op://Private/GPG Signing Key/publishing/passphrase",
    ),
  )
}

internal fun Project.readReleaseWorkflowOptions(requireVersion: Boolean = false): ReleaseWorkflowOptions {
  val versionFromProps = providers.gradleProperty(ReleaseWorkflowConstants.VERSION_PROPERTY).orNull
    ?: providers.gradleProperty(ReleaseWorkflowConstants.LEGACY_VERSION_PROPERTY).orNull

  val options = ReleaseWorkflowOptions(
    dryRun = parseBooleanProperty(ReleaseWorkflowConstants.DRY_RUN_PROPERTY),
    noPush = parseBooleanProperty(ReleaseWorkflowConstants.NO_PUSH_PROPERTY),
    skipPublish = parseBooleanProperty(ReleaseWorkflowConstants.SKIP_PUBLISH_PROPERTY),
    version = versionFromProps,
  )

  if (requireVersion && options.version.isNullOrBlank()) {
    throw GradleException(
      "Missing release version. Use -P${ReleaseWorkflowConstants.VERSION_PROPERTY}=x.y.z " +
        "(or -P${ReleaseWorkflowConstants.LEGACY_VERSION_PROPERTY}=x.y.z).",
    )
  }

  return options
}

private fun Project.parseBooleanProperty(name: String): Boolean {
  val raw = providers.gradleProperty(name).orNull ?: return false
  return when (raw.trim().lowercase()) {
    in ReleaseWorkflowConstants.TRUE_VALUES -> true
    in ReleaseWorkflowConstants.FALSE_VALUES -> false
    else -> throw GradleException(
      "Invalid boolean value for -P$name: '$raw'. Use true/false.",
    )
  }
}

internal class UnifiedReleaseWorkflow(
  private val project: Project,
) {
  private val rootDir: File = project.rootDir
  private val pluginBuildFile = rootDir.resolve(ReleaseWorkflowConstants.PLUGIN_BUILD_FILE)
  private val pluginPomFile = rootDir.resolve(ReleaseWorkflowConstants.PLUGIN_POM_FILE)

  private var metadataCache: BuildMetadata? = null

  fun doctor() {
    ensurePluginBuildFile()
    ensureOpInstalled()
    validateSecretAccess(SecretMappings.portal, "portal")
    validateSecretAccess(SecretMappings.central, "central")
    ok("Release doctor checks passed")
  }

  fun bumpPre(options: ReleaseWorkflowOptions) {
    ensurePluginBuildFile()
    ensureCleanGitTree()

    val current = currentMetadata().version
    val next = nextPreReleaseVersion(current)
    info("Version bump: $current -> $next")

    updateVersion(next, options.dryRun)
    commitVersion(next, options.dryRun)
  }

  fun publishLocal(options: ReleaseWorkflowOptions) {
    ensurePluginBuildFile()

    if (options.dryRun) {
      info("[dry-run] ${ReleaseWorkflowConstants.GRADLEW} ${ReleaseWorkflowConstants.PUBLISH_LOCAL_TASK}")
    } else {
      runCommandOrFail(
        command = gradlew(ReleaseWorkflowConstants.PUBLISH_LOCAL_TASK),
        label = "Maven local publish",
        inheritIO = true,
      )
    }

    printPublishMiniReport(listOf(PublishSide.LOCAL))
  }

  fun publishPortal(options: ReleaseWorkflowOptions) {
    publishPortal(options, printReport = true)
  }

  fun publishCentral(options: ReleaseWorkflowOptions) {
    publishCentral(options, printReport = true)
  }

  fun tagAndPublishPreRelease(options: ReleaseWorkflowOptions) {
    runTagAndPublishFlow(
      options = options,
      resolveTargetVersion = { current -> nextPreReleaseVersion(current) },
    )
  }

  fun tagAndPublishRelease(options: ReleaseWorkflowOptions) {
    val targetVersion = options.version?.trim().orEmpty()
    if (targetVersion.isEmpty()) {
      fail("Missing release version. Use -P${ReleaseWorkflowConstants.VERSION_PROPERTY}=x.y.z.")
    }
    validateStableVersion(targetVersion)

    runTagAndPublishFlow(
      options = options,
      resolveTargetVersion = { _ -> targetVersion },
      validateTargetVersion = { current, target ->
        if (current == target) {
          fail("Version is already $target. Provide a different version.")
        }
      },
    )
  }

  private fun runTagAndPublishFlow(
    options: ReleaseWorkflowOptions,
    resolveTargetVersion: (currentVersion: String) -> String,
    validateTargetVersion: (currentVersion: String, targetVersion: String) -> Unit = { _, _ -> },
  ) {
    ensurePluginBuildFile()
    ensureCleanGitTree()
    runDoctorIfNeeded(options)

    val currentVersion = currentMetadata().version
    val targetVersion = resolveTargetVersion(currentVersion)
    validateTargetVersion(currentVersion, targetVersion)

    info("Version bump: $currentVersion -> $targetVersion")
    updateVersion(targetVersion, options.dryRun)
    commitAndTag(targetVersion, options.dryRun)

    val publishedSides = publishEnabledDestinations(options)

    if (!options.noPush) {
      pushRelease(targetVersion, options.dryRun)
    }

    if (publishedSides.isNotEmpty()) {
      printPublishMiniReport(publishedSides)
    }
  }

  private fun runDoctorIfNeeded(options: ReleaseWorkflowOptions) {
    if (options.skipPublish) {
      return
    }
    if (options.dryRun) {
      info("[dry-run] skipping doctor checks (no secret access required)")
      return
    }
    doctor()
  }

  private fun publishEnabledDestinations(options: ReleaseWorkflowOptions): List<PublishSide> {
    if (options.skipPublish) {
      return emptyList()
    }

    val publishedSides = mutableListOf<PublishSide>()

    info("Publishing to Gradle Plugin Portal...")
    publishPortal(options, printReport = false)
    publishedSides += PublishSide.PORTAL

    info("Publishing to Maven Central...")
    publishCentral(options, printReport = false)
    publishedSides += PublishSide.CENTRAL

    return publishedSides
  }

  private fun publishPortal(options: ReleaseWorkflowOptions, printReport: Boolean) {
    ensurePluginBuildFile()

    if (options.dryRun) {
      info(
        "[dry-run] ${ReleaseWorkflowConstants.GRADLEW} ${ReleaseWorkflowConstants.PUBLISH_PORTAL_TASK} " +
          "-Pgradle.publish.key=*** " +
          "-Pgradle.publish.secret=*** " +
          "-Psigning.gnupg.passphrase=***",
      )
      if (printReport) {
        printPublishMiniReport(listOf(PublishSide.PORTAL))
      }
      return
    }

    ensureOpInstalled()
    val key = readSecret(SecretMappings.portal.requiredValue("GRADLE_PUBLISH_KEY"))
    val secret = readSecret(SecretMappings.portal.requiredValue("GRADLE_PUBLISH_SECRET"))
    val passphrase = readSecret(SecretMappings.portal.requiredValue("SIGNING_PASSPHRASE"))

    runCommandOrFail(
      command = gradlew(
        ReleaseWorkflowConstants.PUBLISH_PORTAL_TASK,
        "-Pgradle.publish.key=$key",
        "-Pgradle.publish.secret=$secret",
        "-Psigning.gnupg.passphrase=$passphrase",
      ),
      label = "Gradle Plugin Portal publish",
      inheritIO = true,
    )

    if (printReport) {
      printPublishMiniReport(listOf(PublishSide.PORTAL))
    }
  }

  private fun publishCentral(options: ReleaseWorkflowOptions, printReport: Boolean) {
    ensurePluginBuildFile()

    if (options.dryRun) {
      info(
        "[dry-run] ${ReleaseWorkflowConstants.GRADLEW} ${ReleaseWorkflowConstants.PUBLISH_CENTRAL_TASK} " +
          "--no-configuration-cache -Psigning.gnupg.passphrase=***",
      )
      if (printReport) {
        printPublishMiniReport(listOf(PublishSide.CENTRAL))
      }
      return
    }

    ensureOpInstalled()
    val username = readSecret(SecretMappings.central.requiredValue("ORG_GRADLE_PROJECT_mavenCentralUsername"))
    val password = readSecret(SecretMappings.central.requiredValue("ORG_GRADLE_PROJECT_mavenCentralPassword"))
    val passphrase = readSecret(
      SecretMappings.central.requiredValue("ORG_GRADLE_PROJECT_signing_gnupg_passphrase"),
    )

    runCommandOrFail(
      command = gradlew(
        ReleaseWorkflowConstants.PUBLISH_CENTRAL_TASK,
        "--no-configuration-cache",
        "-Psigning.gnupg.passphrase=$passphrase",
      ),
      label = "Maven Central publish",
      inheritIO = true,
      extraEnv = mapOf(
        "ORG_GRADLE_PROJECT_mavenCentralUsername" to username,
        "ORG_GRADLE_PROJECT_mavenCentralPassword" to password,
      ),
    )

    if (printReport) {
      printPublishMiniReport(listOf(PublishSide.CENTRAL))
    }
  }

  private fun ensurePluginBuildFile() {
    if (!pluginBuildFile.exists()) {
      fail(
        "Required file not found: ${pluginBuildFile.absolutePath}",
        "Run this convention from the repository root project.",
      )
    }
  }

  private fun ensureOpInstalled() {
    val result = runCommand(listOf("op", "--version"))
    if (result.status != 0) {
      fail(
        "1Password CLI ('op') not found.",
        "Install it first (brew install 1password-cli) and sign in with 'op signin'.",
      )
    }
    ok("1Password CLI detected (${result.output})")
  }

  private fun validateSecretAccess(mappings: List<SecretMapping>, label: String) {
    mappings.forEach { mapping ->
      readSecret(mapping.secretRef)
      ok("$label: ${mapping.keyName}")
    }
  }

  private fun readSecret(secretRef: String): String {
    val result = runCommand(listOf("op", "read", secretRef))
    if (result.status != 0) {
      fail(
        "Cannot read 1Password secret: $secretRef",
        result.output.ifBlank { "No output from 1Password CLI." },
      )
    }
    return result.output
  }

  private fun ensureCleanGitTree() {
    val result = runCommand(listOf("git", "status", "--porcelain"))
    if (result.status != 0) {
      fail("Cannot inspect git status.", result.output)
    }
    if (result.output.isNotBlank()) {
      fail(
        "Working tree is dirty.",
        "Commit or stash changes before running release tasks.",
      )
    }
    ok("Git working tree is clean")
  }

  private fun currentMetadata(): BuildMetadata {
    metadataCache?.let { return it }

    val content = pluginBuildFile.readText()

    val metadata = BuildMetadata(
      groupId = content.requiredMatch(
        regex = ReleaseWorkflowConstants.GROUP_DECLARATION_REGEX,
        error = "Unable to read plugin group.",
        hint = "Expected line like group = \"x.y.z\" in ${pluginBuildFile.absolutePath}",
      ),
      version = content.requiredMatch(
        regex = ReleaseWorkflowConstants.VERSION_DECLARATION_REGEX,
        error = "Unable to read plugin version.",
        hint = "Expected line like version = \"x.y.z\" in ${pluginBuildFile.absolutePath}",
      ),
      pluginId = content.requiredMatch(
        regex = ReleaseWorkflowConstants.PLUGIN_ID_REGEX,
        error = "Unable to read Gradle plugin ID.",
        hint = "Expected id = \"...\" in gradlePlugin.plugins block inside ${pluginBuildFile.absolutePath}",
      ),
    )

    metadataCache = metadata
    return metadata
  }

  private fun currentArtifactDetails(): ArtifactDetails {
    val metadata = currentMetadata()
    return ArtifactDetails(
      groupId = metadata.groupId,
      artifactId = readArtifactIdOrFallback(),
      version = metadata.version,
      pluginId = metadata.pluginId,
    )
  }

  private fun readArtifactIdOrFallback(): String {
    if (!pluginPomFile.exists()) {
      return ReleaseWorkflowConstants.DEFAULT_ARTIFACT_ID
    }

    val pomContent = pluginPomFile.readText()
    return pomContent.requiredMatchOrNull(ReleaseWorkflowConstants.POM_ARTIFACT_ID_REGEX)
      ?.trim()
      .orEmpty()
      .ifBlank { ReleaseWorkflowConstants.DEFAULT_ARTIFACT_ID }
  }

  private fun updateVersion(targetVersion: String, dryRun: Boolean) {
    if (dryRun) {
      info("[dry-run] update ${pluginBuildFile.absolutePath}")
      return
    }
    setVersion(targetVersion)
  }

  private fun setVersion(newVersion: String) {
    val content = pluginBuildFile.readText()
    val updated = ReleaseWorkflowConstants.VERSION_DECLARATION_REGEX.replace(
      content,
      "version = \"$newVersion\"",
    )

    if (updated == content) {
      fail(
        "Version update failed.",
        "Unable to replace version declaration in ${pluginBuildFile.absolutePath}",
      )
    }

    pluginBuildFile.writeText(updated)
    metadataCache = null
  }

  private fun nextPreReleaseVersion(current: String): String {
    val alphaMatch = ReleaseWorkflowConstants.ALPHA_VERSION_REGEX.find(current)
    if (alphaMatch != null) {
      val base = alphaMatch.groupValues[1]
      val number = alphaMatch.groupValues[2].toInt()
      return "$base-alpha.${number + 1}"
    }
    return "$current-alpha.1"
  }

  private fun validateStableVersion(version: String) {
    if (!ReleaseWorkflowConstants.STABLE_VERSION_REGEX.matches(version)) {
      fail("Invalid release version: $version", "Use semantic version format without suffix: x.y.z")
    }
  }

  private fun commitVersion(version: String, dryRun: Boolean) {
    commitVersionChange(version = version, includeTag = false, dryRun = dryRun)
    if (!dryRun) {
      ok("Created commit for version $version")
    }
  }

  private fun commitAndTag(version: String, dryRun: Boolean) {
    commitVersionChange(version = version, includeTag = true, dryRun = dryRun)
    if (!dryRun) {
      ok("Created commit + tag v$version")
    }
  }

  private fun commitVersionChange(version: String, includeTag: Boolean, dryRun: Boolean) {
    if (dryRun) {
      info("[dry-run] git add ${ReleaseWorkflowConstants.VERSION_FILE_TARGET}")
      info("[dry-run] git commit -m \"chore: bump version to $version\"")
      if (includeTag) {
        info("[dry-run] git tag -a v$version -m \"Release $version\"")
      }
      return
    }

    runCommandOrFail(
      command = listOf("git", "add", ReleaseWorkflowConstants.VERSION_FILE_TARGET),
      label = "git add",
    )

    runCommandOrFail(
      command = listOf("git", "commit", "-m", "chore: bump version to $version"),
      label = "git commit",
    )

    if (includeTag) {
      runCommandOrFail(
        command = listOf("git", "tag", "-a", "v$version", "-m", "Release $version"),
        label = "git tag",
      )
    }
  }

  private fun pushRelease(version: String, dryRun: Boolean) {
    if (dryRun) {
      info("[dry-run] git push origin HEAD")
      info("[dry-run] git push origin v$version")
      return
    }

    runCommandOrFail(
      command = listOf("git", "push", "origin", "HEAD"),
      label = "git push HEAD",
    )

    runCommandOrFail(
      command = listOf("git", "push", "origin", "v$version"),
      label = "git push tag",
    )

    ok("Pushed branch + tag v$version")
  }

  private fun runCommandOrFail(
    command: List<String>,
    label: String,
    inheritIO: Boolean = false,
    extraEnv: Map<String, String> = emptyMap(),
  ) {
    val result = runCommand(
      command = command,
      inheritIO = inheritIO,
      extraEnv = extraEnv,
    )

    if (result.status != 0) {
      fail("$label failed.", result.output.ifBlank { "No additional output." })
    }
  }

  private fun runCommand(
    command: List<String>,
    inheritIO: Boolean = false,
    extraEnv: Map<String, String> = emptyMap(),
  ): CommandResult {
    val processBuilder = ProcessBuilder(command)
      .directory(rootDir)
      .apply { environment().putAll(extraEnv) }

    if (inheritIO) {
      val process = processBuilder.inheritIO().start()
      return CommandResult(
        status = process.waitFor(),
        output = "",
      )
    }

    val process = processBuilder
      .redirectErrorStream(true)
      .start()

    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()

    return CommandResult(
      status = process.waitFor(),
      output = output,
    )
  }

  private fun printPublishMiniReport(sides: List<PublishSide>) {
    val uniqueSides = sides.distinct()
    if (uniqueSides.isEmpty()) {
      return
    }

    val artifact = currentArtifactDetails()

    project.logger.lifecycle("")
    project.logger.lifecycle("Publish mini-report")
    project.logger.lifecycle("artifact = ${artifact.groupId}:${artifact.artifactId}:${artifact.version}")
    project.logger.lifecycle("pluginId = ${artifact.pluginId}")
    project.logger.lifecycle("")

    uniqueSides.forEach { side ->
      when (side) {
        PublishSide.LOCAL -> {
          val home = System.getProperty("user.home") ?: "~"
          val repoPath = artifact.groupId.toRepositoryPath()
          project.logger.lifecycle(
            "PATH local: $home/.m2/repository/$repoPath/${artifact.artifactId}/${artifact.version}",
          )
        }
        PublishSide.PORTAL -> {
          project.logger.lifecycle(
            "URL portal: ${ReleaseWorkflowConstants.PLUGIN_PORTAL_BASE_URL}/${artifact.pluginId}",
          )
        }
        PublishSide.CENTRAL -> {
          val repoPath = artifact.groupId.toRepositoryPath()
          project.logger.lifecycle(
            "URL central: ${ReleaseWorkflowConstants.MAVEN_CENTRAL_BASE_URL}/$repoPath/${artifact.artifactId}/${artifact.version}/",
          )
        }
      }
    }

    project.logger.lifecycle("")
    project.logger.lifecycle("Copy/Paste (libs.versions.toml)")
    project.logger.lifecycle("[versions]")
    project.logger.lifecycle("all4Release = \"${artifact.version}\"")
    project.logger.lifecycle("[plugins]")
    project.logger.lifecycle(
      "all4-release = { id = \"${artifact.pluginId}\", version.ref = \"all4Release\" }",
    )
    project.logger.lifecycle("[libraries]")
    project.logger.lifecycle(
      "all4-release-plugin = { module = \"${artifact.groupId}:${artifact.artifactId}\", version.ref = \"all4Release\" }",
    )
    project.logger.lifecycle("")
    project.logger.lifecycle(
      "all4-release = { id = \"${artifact.pluginId}\", version = \"${artifact.version}\" }",
    )
    project.logger.lifecycle(
      "all4-release-plugin = { module = \"${artifact.groupId}:${artifact.artifactId}\", version = \"${artifact.version}\" }",
    )
  }

  private fun gradlew(vararg args: String): List<String> =
    listOf(ReleaseWorkflowConstants.GRADLEW) + args

  private fun info(message: String) = project.logger.lifecycle("INFO: $message")

  private fun ok(message: String) = project.logger.lifecycle("OK: $message")

  private fun fail(message: String, solution: String? = null): Nothing {
    val withSolution = if (solution.isNullOrBlank()) {
      message
    } else {
      "$message\n\nSolution:\n$solution"
    }
    throw GradleException(withSolution)
  }
}

private fun List<SecretMapping>.requiredValue(key: String): String =
  firstOrNull { it.keyName == key }?.secretRef
    ?: throw GradleException("Missing secret mapping for '$key'.")

private fun String.requiredMatch(regex: Regex, error: String, hint: String): String =
  requiredMatchOrNull(regex) ?: throw GradleException("$error\n\nSolution:\n$hint")

private fun String.requiredMatchOrNull(regex: Regex): String? =
  regex.find(this)?.groupValues?.getOrNull(1)

private fun String.toRepositoryPath(): String = replace(".", "/")
