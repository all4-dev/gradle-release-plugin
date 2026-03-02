package dev.all4.gradle.release.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * Generates a `publishing.properties` template with credential placeholders
 * based on the enabled publishing destinations.
 *
 * Usage:
 * ```bash
 * ./gradlew initPublishingProperties
 * ./gradlew initPublishingProperties --op   # use 1Password op:// placeholders
 * ```
 *
 * If the file already exists it is left untouched — delete it to regenerate.
 */
public abstract class InitPublishingPropertiesTask : DefaultTask() {

    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    @get:Input
    @get:Optional
    public abstract val useOnePassword: Property<Boolean>

    init {
        useOnePassword.convention(false)
    }

    @Option(option = "op", description = "Use 1Password op:// reference placeholders")
    public fun setOnePasswordOption(value: String) {
        useOnePassword.set(value.toBoolean())
    }

    @get:Input
    public abstract val mavenCentralEnabled: Property<Boolean>

    @get:Input
    public abstract val githubPackagesEnabled: Property<Boolean>

    @TaskAction
    public fun execute() {
        val file = outputFile.get().asFile

        if (file.exists()) {
            logger.lifecycle("⏭️  ${file.name} already exists — delete it to regenerate.")
            return
        }

        val op = useOnePassword.get()
        val mavenCentral = mavenCentralEnabled.get()
        val githubPackages = githubPackagesEnabled.get()

        if (!mavenCentral && !githubPackages) {
            logger.lifecycle("⏭️  No remote destinations enabled — nothing to generate.")
            return
        }

        val content = buildString {
            if (op) {
                appendLine("# Publishing credentials (1Password op:// references, resolved at execution time)")
            } else {
                appendLine("# Publishing credentials")
                appendLine("# ⚠️  Do NOT commit this file with real values — add it to .gitignore.")
            }

            if (mavenCentral) {
                appendLine()
                appendLine("# Maven Central / Sonatype")
                if (op) {
                    appendLine("sonatype.username=op://vault/item/username")
                    appendLine("sonatype.password=op://vault/item/password")
                } else {
                    appendLine("sonatype.username=")
                    appendLine("sonatype.password=")
                }

                appendLine()
                appendLine("# GPG Signing")
                if (op) {
                    appendLine("signing.gnupg.passphrase=op://vault/item/passphrase")
                } else {
                    appendLine("signing.gnupg.passphrase=")
                }
            }

            if (githubPackages) {
                appendLine()
                appendLine("# GitHub Packages")
                if (op) {
                    appendLine("GITHUB_ACTOR=op://vault/item/username")
                    appendLine("GITHUB_TOKEN=op://vault/item/token")
                } else {
                    appendLine("GITHUB_ACTOR=")
                    appendLine("GITHUB_TOKEN=")
                }
            }
        }

        file.parentFile?.mkdirs()
        file.writeText(content)
        logger.lifecycle("✅ Created ${file.name} with credential placeholders")
    }
}