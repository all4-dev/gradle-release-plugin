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

private data class CommandResult(
  val status: Int,
  val output: String,
)

internal fun Project.readReleaseWorkflowOptions(requireVersion: Boolean = false): ReleaseWorkflowOptions {
  val versionFromProps = providers.gradleProperty("release.version").orNull
    ?: providers.gradleProperty("VERSION").orNull

  val options = ReleaseWorkflowOptions(
    dryRun = parseBooleanProperty("release.dryRun"),
    noPush = parseBooleanProperty("release.noPush"),
    skipPublish = parseBooleanProperty("release.skipPublish"),
    version = versionFromProps,
  )

  if (requireVersion && options.version.isNullOrBlank()) {
    throw GradleException(
      "Missing release version. Use -Prelease.version=x.y.z (or -PVERSION=x.y.z).",
    )
  }

  return options
}

private fun Project.parseBooleanProperty(name: String): Boolean {
  val raw = providers.gradleProperty(name).orNull ?: return false
  return when (raw.trim().lowercase()) {
    "1", "true", "yes", "y", "on" -> true
    "0", "false", "no", "n", "off" -> false
    else -> throw GradleException(
      "Invalid boolean value for -P$name: '$raw'. Use true/false.",
    )
  }
}

internal class UnifiedReleaseWorkflow(
  private val project: Project,
) {
  private val rootDir: File = project.rootDir
  private val pluginBuildFile = rootDir.resolve("plugin/build.gradle.kts")
  private val pluginPomFile = rootDir.resolve("plugin/build/publications/pluginMaven/pom-default.xml")

  private val portalSecretMappings = listOf(
    SecretMapping(
      "GRADLE_PUBLISH_KEY",
      "op://Private/Gradle Plugin Portal/publishing/key",
    ),
    SecretMapping(
      "GRADLE_PUBLISH_SECRET",
      "op://Private/Gradle Plugin Portal/publishing/secret",
    ),
    SecretMapping(
      "SIGNING_PASSPHRASE",
      "op://Private/GPG Signing Key/publishing/passphrase",
    ),
  )

  private val centralSecretMappings = listOf(
    SecretMapping(
      "ORG_GRADLE_PROJECT_mavenCentralUsername",
      "op://Private/Sonatype Maven Central/publishing/username",
    ),
    SecretMapping(
      "ORG_GRADLE_PROJECT_mavenCentralPassword",
      "op://Private/Sonatype Maven Central/publishing/password",
    ),
    SecretMapping(
      "ORG_GRADLE_PROJECT_signing_gnupg_passphrase",
      "op://Private/GPG Signing Key/publishing/passphrase",
    ),
  )

  fun doctor() {
    ensurePluginBuildFile()
    ensureOpInstalled()
    validateSecretAccess(portalSecretMappings, "portal")
    validateSecretAccess(centralSecretMappings, "central")
    ok("Release doctor checks passed")
  }

  fun bumpPre(options: ReleaseWorkflowOptions) {
    ensurePluginBuildFile()
    ensureCleanGitTree()

    val current = getCurrentVersion()
    val next = nextPreReleaseVersion(current)
    info("Version bump: $current -> $next")

    if (options.dryRun) {
      info("[dry-run] update ${pluginBuildFile.absolutePath}")
    } else {
      setVersion(next)
    }

    commitVersion(next, options.dryRun)
  }

  fun publishLocal(options: ReleaseWorkflowOptions) {
    ensurePluginBuildFile()

    if (options.dryRun) {
      info("[dry-run] ./gradlew :plugin:publishToMavenLocal")
    } else {
      runCommandOrFail(
        command = listOf("./gradlew", ":plugin:publishToMavenLocal"),
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

  private fun publishPortal(options: ReleaseWorkflowOptions, printReport: Boolean) {
    ensurePluginBuildFile()
    ensureOpInstalled()

    val key = readSecret(portalSecretMappings.requiredValue("GRADLE_PUBLISH_KEY"))
    val secret = readSecret(portalSecretMappings.requiredValue("GRADLE_PUBLISH_SECRET"))
    val passphrase = readSecret(portalSecretMappings.requiredValue("SIGNING_PASSPHRASE"))

    if (options.dryRun) {
      info(
        "[dry-run] ./gradlew :plugin:publishPlugins " +
          "-Pgradle.publish.key=*** " +
          "-Pgradle.publish.secret=*** " +
          "-Psigning.gnupg.passphrase=***",
      )
    } else {
      runCommandOrFail(
        command = listOf(
          "./gradlew",
          ":plugin:publishPlugins",
          "-Pgradle.publish.key=$key",
          "-Pgradle.publish.secret=$secret",
          "-Psigning.gnupg.passphrase=$passphrase",
        ),
        label = "Gradle Plugin Portal publish",
        inheritIO = true,
      )
    }

    if (printReport) {
      printPublishMiniReport(listOf(PublishSide.PORTAL))
    }
  }

  private fun publishCentral(options: ReleaseWorkflowOptions, printReport: Boolean) {
    ensurePluginBuildFile()
    ensureOpInstalled()

    val username = readSecret(
      centralSecretMappings.requiredValue("ORG_GRADLE_PROJECT_mavenCentralUsername"),
    )
    val password = readSecret(
      centralSecretMappings.requiredValue("ORG_GRADLE_PROJECT_mavenCentralPassword"),
    )
    val passphrase = readSecret(
      centralSecretMappings.requiredValue("ORG_GRADLE_PROJECT_signing_gnupg_passphrase"),
    )

    if (options.dryRun) {
      info(
        "[dry-run] ./gradlew :plugin:publishAllPublicationsToMavenCentralRepository " +
          "--no-configuration-cache -Psigning.gnupg.passphrase=***",
      )
    } else {
      runCommandOrFail(
        command = listOf(
          "./gradlew",
          ":plugin:publishAllPublicationsToMavenCentralRepository",
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
    }

    if (printReport) {
      printPublishMiniReport(listOf(PublishSide.CENTRAL))
    }
  }

  fun tagAndPublishPreRelease(options: ReleaseWorkflowOptions) {
    ensurePluginBuildFile()
    ensureCleanGitTree()
    doctor()

    val current = getCurrentVersion()
    val next = nextPreReleaseVersion(current)
    info("Version bump: $current -> $next")

    if (options.dryRun) {
      info("[dry-run] update ${pluginBuildFile.absolutePath}")
    } else {
      setVersion(next)
    }

    commitAndTag(next, options.dryRun)

    val publishedSides = mutableListOf<PublishSide>()
    if (!options.skipPublish) {
      info("Publishing to Gradle Plugin Portal...")
      publishPortal(options.copy(skipPublish = true), printReport = false)
      publishedSides += PublishSide.PORTAL

      info("Publishing to Maven Central...")
      publishCentral(options.copy(skipPublish = true), printReport = false)
      publishedSides += PublishSide.CENTRAL
    }

    if (!options.noPush) {
      pushRelease(next, options.dryRun)
    }

    if (publishedSides.isNotEmpty()) {
      printPublishMiniReport(publishedSides)
    }
  }

  fun tagAndPublishRelease(options: ReleaseWorkflowOptions) {
    ensurePluginBuildFile()
    ensureCleanGitTree()
    doctor()

    val targetVersion = options.version?.trim().orEmpty()
    if (targetVersion.isEmpty()) {
      fail("Missing release version. Use -Prelease.version=x.y.z.")
    }
    validateStableVersion(targetVersion)

    val current = getCurrentVersion()
    if (current == targetVersion) {
      fail("Version is already $targetVersion. Provide a different version.")
    }

    info("Version bump: $current -> $targetVersion")
    if (options.dryRun) {
      info("[dry-run] update ${pluginBuildFile.absolutePath}")
    } else {
      setVersion(targetVersion)
    }

    commitAndTag(targetVersion, options.dryRun)

    val publishedSides = mutableListOf<PublishSide>()
    if (!options.skipPublish) {
      info("Publishing to Gradle Plugin Portal...")
      publishPortal(options.copy(skipPublish = true), printReport = false)
      publishedSides += PublishSide.PORTAL

      info("Publishing to Maven Central...")
      publishCentral(options.copy(skipPublish = true), printReport = false)
      publishedSides += PublishSide.CENTRAL
    }

    if (!options.noPush) {
      pushRelease(targetVersion, options.dryRun)
    }

    if (publishedSides.isNotEmpty()) {
      printPublishMiniReport(publishedSides)
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

  private fun getCurrentVersion(): String {
    val content = pluginBuildFile.readText()
    val match = Regex("""^version\s*=\s*"([^"]+)"\s*$""", RegexOption.MULTILINE)
      .find(content)
      ?.groupValues
      ?.getOrNull(1)
    return match ?: fail(
      "Unable to read plugin version.",
      "Expected line like version = \"x.y.z\" in ${pluginBuildFile.absolutePath}",
    )
  }

  private fun getCurrentGroupId(): String {
    val content = pluginBuildFile.readText()
    val match = Regex("""^group\s*=\s*"([^"]+)"\s*$""", RegexOption.MULTILINE)
      .find(content)
      ?.groupValues
      ?.getOrNull(1)
    return match ?: fail(
      "Unable to read plugin group.",
      "Expected line like group = \"x.y.z\" in ${pluginBuildFile.absolutePath}",
    )
  }

  private fun getPluginId(): String {
    val content = pluginBuildFile.readText()
    val match = Regex(
      """create\("release"\)\s*\{.*?id\s*=\s*"([^"]+)"""",
      setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
    )
      .find(content)
      ?.groupValues
      ?.getOrNull(1)

    return match ?: fail(
      "Unable to read Gradle plugin ID.",
      "Expected id = \"...\" in gradlePlugin.plugins block inside ${pluginBuildFile.absolutePath}",
    )
  }

  private fun getArtifactIdOrFallback(): String {
    if (!pluginPomFile.exists()) {
      return "release-plugin"
    }
    val pom = pluginPomFile.readText()
    return Regex("""<artifactId>([^<]+)</artifactId>""")
      .find(pom)
      ?.groupValues
      ?.getOrNull(1)
      ?.trim()
      .orEmpty()
      .ifBlank { "release-plugin" }
  }

  private fun setVersion(newVersion: String) {
    val content = pluginBuildFile.readText()
    val regex = Regex("""^version\s*=\s*"([^"]+)"\s*$""", RegexOption.MULTILINE)
    val updated = regex.replace(content, "version = \"$newVersion\"")
    if (updated == content) {
      fail(
        "Version update failed.",
        "Unable to replace version declaration in ${pluginBuildFile.absolutePath}",
      )
    }
    pluginBuildFile.writeText(updated)
  }

  private fun nextPreReleaseVersion(current: String): String {
    val alphaMatch = Regex("""^(.*)-alpha\.(\d+)$""").find(current)
    if (alphaMatch != null) {
      val base = alphaMatch.groupValues[1]
      val number = alphaMatch.groupValues[2].toInt()
      return "$base-alpha.${number + 1}"
    }
    return "$current-alpha.1"
  }

  private fun validateStableVersion(version: String) {
    if (!Regex("""^\d+\.\d+\.\d+$""").matches(version)) {
      fail("Invalid release version: $version", "Use semantic version format without suffix: x.y.z")
    }
  }

  private fun commitVersion(version: String, dryRun: Boolean) {
    if (dryRun) {
      info("[dry-run] git add plugin/build.gradle.kts")
      info("[dry-run] git commit -m \"chore: bump version to $version\"")
      return
    }
    runCommandOrFail(
      command = listOf("git", "add", "plugin/build.gradle.kts"),
      label = "git add",
    )
    runCommandOrFail(
      command = listOf("git", "commit", "-m", "chore: bump version to $version"),
      label = "git commit",
    )
    ok("Created commit for version $version")
  }

  private fun commitAndTag(version: String, dryRun: Boolean) {
    if (dryRun) {
      info("[dry-run] git add plugin/build.gradle.kts")
      info("[dry-run] git commit -m \"chore: bump version to $version\"")
      info("[dry-run] git tag -a v$version -m \"Release $version\"")
      return
    }
    runCommandOrFail(
      command = listOf("git", "add", "plugin/build.gradle.kts"),
      label = "git add",
    )
    runCommandOrFail(
      command = listOf("git", "commit", "-m", "chore: bump version to $version"),
      label = "git commit",
    )
    runCommandOrFail(
      command = listOf("git", "tag", "-a", "v$version", "-m", "Release $version"),
      label = "git tag",
    )
    ok("Created commit + tag v$version")
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

    val artifact = ArtifactDetails(
      groupId = getCurrentGroupId(),
      artifactId = getArtifactIdOrFallback(),
      version = getCurrentVersion(),
      pluginId = getPluginId(),
    )

    project.logger.lifecycle("")
    project.logger.lifecycle("Publish mini-report")
    project.logger.lifecycle("artifact = ${artifact.groupId}:${artifact.artifactId}:${artifact.version}")
    project.logger.lifecycle("pluginId = ${artifact.pluginId}")
    project.logger.lifecycle("")

    uniqueSides.forEach { side ->
      when (side) {
        PublishSide.LOCAL -> {
          val home = System.getenv("HOME") ?: "~"
          val repoPath = artifact.groupId.replace(".", "/")
          val fullPath = "$home/.m2/repository/$repoPath/${artifact.artifactId}/${artifact.version}"
          project.logger.lifecycle("PATH local: $fullPath")
        }
        PublishSide.PORTAL -> {
          project.logger.lifecycle("URL portal: https://plugins.gradle.org/plugin/${artifact.pluginId}")
        }
        PublishSide.CENTRAL -> {
          val repoPath = artifact.groupId.replace(".", "/")
          project.logger.lifecycle(
            "URL central: https://repo1.maven.org/maven2/$repoPath/${artifact.artifactId}/${artifact.version}/",
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
