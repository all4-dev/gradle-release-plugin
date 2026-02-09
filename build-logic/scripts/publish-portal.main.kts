#!/usr/bin/env kotlin

import java.io.File
import kotlin.system.exitProcess

// Load credentials from 1Password and publish to Gradle Plugin Portal
println("🔐 Loading credentials from 1Password...")

val opScript = File("build-logic/scripts/op-api-keys.main.kts").absolutePath

val loadCredentials = ProcessBuilder(
    opScript,
    "GRADLE_PUBLISH_KEY=op://Private/Gradle Plugin Portal/publishing/key",
    "GRADLE_PUBLISH_SECRET=op://Private/Gradle Plugin Portal/publishing/secret",
    "SIGNING_PASSPHRASE=op://Private/GPG Signing Key/publishing/passphrase"
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

val gradlePublishKey = env["GRADLE_PUBLISH_KEY"]
    ?: run {
        System.err.println("Error: GRADLE_PUBLISH_KEY not found")
        exitProcess(1)
    }

val gradlePublishSecret = env["GRADLE_PUBLISH_SECRET"]
    ?: run {
        System.err.println("Error: GRADLE_PUBLISH_SECRET not found")
        exitProcess(1)
    }

val signingPassphrase = env["SIGNING_PASSPHRASE"]
    ?: run {
        System.err.println("Error: SIGNING_PASSPHRASE not found")
        exitProcess(1)
    }

// Run Gradle publish task
println("📦 Publishing to Gradle Plugin Portal...")

val gradleProcess = ProcessBuilder(
    "./gradlew",
    ":plugin:publishPlugins",
    "-Pgradle.publish.key=$gradlePublishKey",
    "-Pgradle.publish.secret=$gradlePublishSecret",
    "-Psigning.gnupg.passphrase=$signingPassphrase"
).inheritIO()
    .start()

val gradleExitCode = gradleProcess.waitFor()
exitProcess(gradleExitCode)
