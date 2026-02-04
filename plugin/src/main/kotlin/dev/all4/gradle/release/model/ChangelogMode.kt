package dev.all4.gradle.release.model

/**
 * Defines where changelogs are stored for a library group.
 */
public enum class ChangelogMode {
    /**
     * Single changelog in a centralized directory (default).
     * Path: `changelogs/{groupName}/{artifactId}/CHANGELOG.md`
     */
    CENTRALIZED,

    /**
     * Each module has its own changelog in its project directory.
     * Path: `{moduleDir}/CHANGELOG.md`
     */
    PER_PROJECT
}
