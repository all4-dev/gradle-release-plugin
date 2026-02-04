package dev.all4.gradle.release.model

import dev.all4.gradle.release.PublishDsl
import javax.inject.Inject
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

@PublishDsl
public abstract class LibraryGroup @Inject constructor(private val name: String) {
    public fun getName(): String = name

    /**
     * Accepted formats:
     * - Gradle project path: `:libs:core:api`
     * - Type-safe accessor string: `projects.libs.core.api`
     */
    public abstract val modules: SetProperty<String>

    public abstract val version: Property<String>

    public abstract val changelogPath: Property<String>

    public abstract val description: Property<String>

    public abstract val versionKey: Property<String>

    public abstract val changelogEnabled: Property<Boolean>

    /**
     * Defines where changelogs are stored:
     * - [ChangelogMode.CENTRALIZED]: Single changelog at [changelogPath] (default)
     * - [ChangelogMode.PER_PROJECT]: Each module has its own CHANGELOG.md in its project directory
     */
    public abstract val changelogMode: Property<ChangelogMode>

    public fun module(value: String) {
        modules.add(normalizeModuleNotation(value))
    }

    public fun modules(vararg values: String) {
        modules.addAll(values.map(::normalizeModuleNotation))
    }

    internal fun moduleProjectPaths(): Set<String> =
        modules.orNull.orEmpty().map(::normalizeModuleNotation).toSet()

    internal fun containsModule(value: String): Boolean =
        normalizeModuleNotation(value) in moduleProjectPaths()

    init {
        changelogPath.convention("changelogs/$name/CHANGELOG.md")
        description.convention("")
        versionKey.convention("version.$name")
        changelogEnabled.convention(true)
        changelogMode.convention(ChangelogMode.CENTRALIZED)
    }

    private fun normalizeModuleNotation(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return trimmed

        if (trimmed.startsWith(":")) return trimmed

        if (trimmed.startsWith(TYPE_SAFE_ACCESSOR_PREFIX)) {
            val path =
                trimmed
                    .removePrefix(TYPE_SAFE_ACCESSOR_PREFIX)
                    .removeSuffix(TYPE_SAFE_DEPENDENCY_PATH_SUFFIX)
                    .removeSuffix(TYPE_SAFE_PATH_SUFFIX)
                    .replace('.', ':')
            return if (path.startsWith(":")) path else ":$path"
        }

        val projectMatch = PROJECT_DEPENDENCY_REGEX.matchEntire(trimmed)
        if (projectMatch != null) return projectMatch.groupValues[1]

        return trimmed
    }

    private companion object {
        private const val TYPE_SAFE_ACCESSOR_PREFIX: String = "projects."
        private const val TYPE_SAFE_PATH_SUFFIX: String = ".path"
        private const val TYPE_SAFE_DEPENDENCY_PATH_SUFFIX: String = ".dependencyProject.path"
        private val PROJECT_DEPENDENCY_REGEX: Regex = Regex("^project\\s+'(:[^']+)'$")
    }
}
