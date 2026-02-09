#!/usr/bin/env kotlin

import java.io.File
import kotlin.system.exitProcess

// Load credentials from 1Password and publish to Maven Central
println("🔐 Loading credentials from 1Password...")

val opScript = File("build-logic/scripts/op-api-keys.main.kts").absolutePath

val loadCredentials = ProcessBuilder(
    opScript,
    "ORG_GRADLE_PROJECT_mavenCentralUsername=op://Private/Sonatype Maven Central/publishing/username",
    "ORG_GRADLE_PROJECT_mavenCentralPassword=op://Private/Sonatype Maven Central/publishing/password",
    "ORG_GRADLE_PROJECT_signing_gnupg_passphrase=op://Private/GPG Signing Key/publishing/passphrase"
).redirectErrorStream(true)
    .start()

val credentials = loadCredentials.inputStream.bufferedReader().readText()
val exitCode = loadCredentials.waitFor()

if (exitCode != 0) {
    System.err.println("Error loading credentials from 1Password:")
    System.err.println(credentials)
    exitProcess(1)
}

// Parse exported variables
val env = mutableMapOf<String, String>()
credentials.lines().forEach { line ->
    if (line.startsWith("export ")) {
        val parts = line.removePrefix("export ").split("=", limit = 2)
        if (parts.size == 2) {
            val key = parts[0]
            val value = parts[1].trim('\'', '"')
            env[key] = value
        }
    }
}

val mavenUsername = env["ORG_GRADLE_PROJECT_mavenCentralUsername"]
    ?: run {
        System.err.println("Error: ORG_GRADLE_PROJECT_mavenCentralUsername not found")
        exitProcess(1)
    }

val mavenPassword = env["ORG_GRADLE_PROJECT_mavenCentralPassword"]
    ?: run {
        System.err.println("Error: ORG_GRADLE_PROJECT_mavenCentralPassword not found")
        exitProcess(1)
    }

val signingPassphrase = env["ORG_GRADLE_PROJECT_signing_gnupg_passphrase"]
    ?: run {
        System.err.println("Error: ORG_GRADLE_PROJECT_signing_gnupg_passphrase not found")
        exitProcess(1)
    }

// Run Gradle publish task
println("📦 Publishing to Maven Central...")

val gradleProcess = ProcessBuilder(
    "./gradlew",
    ":plugin:publishAllPublicationsToMavenCentralRepository",
    "--no-configuration-cache",
    "-Psigning.gnupg.passphrase=$signingPassphrase"
).apply {
    environment()["ORG_GRADLE_PROJECT_mavenCentralUsername"] = mavenUsername
    environment()["ORG_GRADLE_PROJECT_mavenCentralPassword"] = mavenPassword
}.inheritIO()
    .start()

val gradleExitCode = gradleProcess.waitFor()
exitProcess(gradleExitCode)
