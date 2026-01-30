package dev.all4.gradle.release.model

import java.io.File

public data class ArtifactInfo(
    public val file: File,
    public val groupId: String,
    public val artifactId: String,
    public val version: String,
    public val extension: String,
) {
    public val fullGroupId: String
        get() = groupId

    public val publicationName: String
        get() = "${groupId}__${artifactId}__${version}".replace(".", "-").replace("__", "-")

    public val taskSuffix: String
        get() =
            publicationName.split("-").joinToString("") {
                it.replaceFirstChar { c -> c.uppercase() }
            }

    public companion object {
        private val ARTIFACT_PATTERN = Regex("""^(.+)__(.+)__(.+)\.(aar|jar)$""")

        public fun fromFile(file: File, groupPrefix: String): ArtifactInfo? {
            val match = ARTIFACT_PATTERN.matchEntire(file.name) ?: return null
            val (groupId, artifactId, version, ext) = match.destructured
            val prefixedGroup = if (groupPrefix.isNotEmpty()) "$groupPrefix.$groupId" else groupId
            return ArtifactInfo(file, prefixedGroup, artifactId, version, ext)
        }
    }
}
