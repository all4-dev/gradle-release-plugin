package dev.all4.gradle.release.util

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Provides integration with 1Password CLI for secure credential management.
 *
 * Automatically detects if 1Password CLI is available and resolves secret references.
 *
 * Usage:
 * ```kotlin
 * // Set property with 1Password reference
 * ext.property.set("op://Private/Item/field")
 *
 * // Or set via gradle.properties or environment
 * MY_SECRET=op://Private/Item/field
 * ```
 */
object OnePasswordSupport {

    /**
     * Checks if 1Password CLI is available in the system.
     */
    fun isAvailable(): Boolean {
        return findOpCli() != null
    }

    /**
     * Finds the 1Password CLI executable.
     * Checks common locations: PATH, /usr/local/bin, /opt/homebrew/bin
     */
    private fun findOpCli(): String? {
        val locations = listOf("op", "/usr/local/bin/op", "/opt/homebrew/bin/op")

        for (location in locations) {
            try {
                val process = ProcessBuilder(location, "--version")
                    .redirectErrorStream(true)
                    .start()

                if (process.waitFor() == 0) {
                    return location
                }
            } catch (e: Exception) {
                // Continue to next location
            }
        }
        return null
    }

    /**
     * Resolves a value that might be a 1Password reference.
     *
     * If the value starts with "op://", attempts to resolve it using 1Password CLI.
     * Otherwise returns the value as-is.
     *
     * @param value The value to resolve (may be a 1Password reference or plain text)
     * @param project The Gradle project (for logging)
     * @return The resolved value or the original value if not a reference
     */
    fun resolve(value: String, project: Project): String {
        if (!value.startsWith("op://")) {
            return value
        }

        val opCli = findOpCli()
        if (opCli == null) {
            project.logger.warn(
                "1Password reference detected ($value) but 'op' CLI not found. " +
                "Install from: https://developer.1password.com/docs/cli/get-started#install"
            )
            return value
        }

        return try {
            val process = ProcessBuilder(opCli, "read", value)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                val resolved = output.trimEnd('\n', '\r')
                project.logger.info("✓ Resolved 1Password reference: ${value.substringAfterLast("/")}")
                resolved
            } else {
                project.logger.error(
                    "Failed to resolve 1Password reference: $value\n" +
                    "Error: ${output.trim()}\n" +
                    "Make sure you're signed in: op signin"
                )
                value
            }
        } catch (e: Exception) {
            project.logger.error("Error resolving 1Password reference: $value", e)
            value
        }
    }

    /**
     * Resolves a Gradle Provider that might contain a 1Password reference.
     */
    fun resolveProvider(provider: Provider<String>, project: Project): Provider<String> {
        return provider.map { resolve(it, project) }
    }

    /**
     * Resolves a nullable string that might be a 1Password reference.
     */
    fun resolveOrNull(value: String?, project: Project): String? {
        return value?.let { resolve(it, project) }
    }

    /**
     * Loads multiple secrets from 1Password references in environment variables.
     *
     * Looks for environment variables with the given prefix and resolves any
     * that contain 1Password references.
     *
     * Example:
     * ```bash
     * export OP_MAVEN_USERNAME=op://Private/Maven/username
     * export OP_MAVEN_PASSWORD=op://Private/Maven/password
     * ```
     *
     * @param prefix The prefix to look for (e.g., "OP_")
     * @param project The Gradle project
     * @return Map of variable names (without prefix) to resolved values
     */
    fun loadFromEnvironment(prefix: String, project: Project): Map<String, String> {
        val result = mutableMapOf<String, String>()

        System.getenv().forEach { (key, value) ->
            if (key.startsWith(prefix)) {
                val targetKey = key.removePrefix(prefix)
                val resolved = resolve(value, project)
                result[targetKey] = resolved
            }
        }

        return result
    }

    /**
     * Creates a helper message for users about 1Password integration.
     */
    fun getUsageHelp(): String = """
        |
        |📦 1Password Integration
        |
        |You can use 1Password secret references instead of plain text credentials:
        |
        |1. Install 1Password CLI: https://developer.1password.com/docs/cli/get-started#install
        |2. Sign in: op signin
        |3. Use secret references in gradle.properties or environment variables:
        |
        |   # gradle.properties
        |   maven.username=op://Private/Maven Central/username
        |   maven.password=op://Private/Maven Central/password
        |
        |   # Or environment variables
        |   export MAVEN_USERNAME=op://Private/Maven Central/username
        |
        |Secret references will be automatically resolved at build time.
        |
    """.trimMargin()
}

/**
 * Extension function to resolve 1Password references in project properties.
 */
fun Project.resolveSecret(propertyName: String): String? {
    val value = findProperty(propertyName)?.toString()
    return OnePasswordSupport.resolveOrNull(value, this)
}

/**
 * Extension function to resolve 1Password references in providers.
 */
fun Provider<String>.resolve1Password(project: Project): Provider<String> {
    return OnePasswordSupport.resolveProvider(this, project)
}
