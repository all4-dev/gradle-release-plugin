package dev.all4.gradle.release.model

import dev.all4.gradle.release.PublishDsl
import javax.inject.Inject
import org.gradle.api.provider.SetProperty

/**
 * Declares a library group that participates in `release<Group>` lifecycle tasks.
 *
 * - **include**: modules to publish during release. Empty → all modules from the [LibraryGroup].
 * - **exclude**: modules removed from the include result. Empty → no exclusions.
 */
@PublishDsl
public abstract class ReleaseGroup @Inject constructor(private val name: String) {
    public fun getName(): String = name

    public abstract val include: SetProperty<String>
    public abstract val exclude: SetProperty<String>

    /**
     * Returns the effective module set for this release group given the full
     * module set from the corresponding [LibraryGroup].
     */
    internal fun effectiveModules(allModules: Set<String>): Set<String> {
        val base = include.orNull?.takeIf { it.isNotEmpty() } ?: allModules
        val excluded = exclude.orNull.orEmpty()
        return base - excluded
    }
}
