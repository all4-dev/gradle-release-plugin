/*
 * Plugin as Included Build
 *
 * This plugin is developed as an independent Gradle project and included in the root build
 * via `pluginManagement { includeBuild("plugin") }`. This pattern allows:
 * - Independent plugin development and testing
 * - Immediate reflection of changes without publishing
 * - The example project to use the plugin as if it were published
 *
 * Reference: https://docs.gradle.org/current/userguide/composite_builds.html
 */
rootProject.name = "release-plugin"

pluginManagement {
    includeBuild("../build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
