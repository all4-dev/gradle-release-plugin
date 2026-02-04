package dev.all4.gradle.release

import dev.all4.gradle.release.config.PomConfiguration
import dev.all4.gradle.release.destinations.PublishDestinations
import dev.all4.gradle.release.model.LibraryGroup
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * Root-level publishing extension for configuring library groups and destinations.
 *
 * Usage:
 * ```kotlin
 * releaseConfig {
 *   group.set("my.domain.mylib")
 *   version.set("1.0.0")
 *   github("owner/mylib")
 *   destinations { local() }
 *   libraryGroups { register("core") { modules.add(":core") } }
 * }
 * ```
 */
@PublishDsl
public abstract class PublishingExtension @Inject constructor(private val objects: ObjectFactory) {
    public abstract val group: Property<String>
    public abstract val version: Property<String>
    public abstract val dryRun: Property<Boolean>
    public abstract val autoPublishOnBuild: Property<Boolean>

    public val pom: PomConfiguration = objects.newInstance(PomConfiguration::class.java)
    public val libraryGroups: NamedDomainObjectContainer<LibraryGroup> =
        objects.domainObjectContainer(LibraryGroup::class.java)
    public val destinations: PublishDestinations = objects.newInstance(PublishDestinations::class.java)

    public fun github(repository: String) {
        val url = "https://github.com/$repository"
        pom.url.set(url)
        pom.scm.github(repository)
        destinations.githubPackages.repository.set(repository)
    }

    public fun pom(action: Action<PomConfiguration>) {
      action.execute(pom)
    }

    public fun libraryGroups(action: Action<NamedDomainObjectContainer<LibraryGroup>>) {
      action.execute(libraryGroups)
    }

    public fun destinations(action: Action<PublishDestinations>) {
      action.execute(destinations)
    }

    init {
        dryRun.convention(true)
        autoPublishOnBuild.convention(false)
    }
}
