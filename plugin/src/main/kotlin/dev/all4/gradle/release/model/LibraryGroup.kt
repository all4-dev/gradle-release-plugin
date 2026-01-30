package dev.all4.gradle.release.model

import dev.all4.gradle.release.PublishDsl
import javax.inject.Inject
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

@PublishDsl
public abstract class LibraryGroup @Inject constructor(private val name: String) {
    public fun getName(): String = name

    public abstract val modules: SetProperty<String>

    public abstract val version: Property<String>

    public abstract val changelogPath: Property<String>

    public abstract val description: Property<String>

    public abstract val versionKey: Property<String>

    init {
        changelogPath.convention("changelogs/$name/CHANGELOG.md")
        description.convention("")
        versionKey.convention("version.$name")
    }
}
