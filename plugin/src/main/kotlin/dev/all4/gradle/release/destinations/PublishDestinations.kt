package dev.all4.gradle.release.destinations

import dev.all4.gradle.release.PublishDsl
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory

@PublishDsl
public abstract class PublishDestinations @Inject constructor(private val objects: ObjectFactory) {
    public val mavenLocal: MavenLocalDestination = objects.newInstance(MavenLocalDestination::class.java)
    public val mavenStandalone: MavenStandaloneDestination =
        objects.newInstance(MavenStandaloneDestination::class.java)
    public val githubPages: GitHubPagesDestination =
        objects.newInstance(GitHubPagesDestination::class.java)
    public val githubPackages: GitHubPackagesDestination =
        objects.newInstance(GitHubPackagesDestination::class.java)
    public val gradlePluginPortal: GradlePluginPortalDestination =
        objects.newInstance(GradlePluginPortalDestination::class.java)
    public val mavenCentral: MavenCentralDestination =
        objects.newInstance(MavenCentralDestination::class.java)
    public val customRepos: NamedDomainObjectContainer<CustomMavenRepository> =
        objects.domainObjectContainer(CustomMavenRepository::class.java)

    public fun mavenLocal(action: Action<MavenLocalDestination>): Unit = action.execute(mavenLocal)

    public fun mavenStandalone(action: Action<MavenStandaloneDestination>): Unit =
        action.execute(mavenStandalone)

    public fun githubPages(action: Action<GitHubPagesDestination>): Unit = action.execute(githubPages)

    public fun githubPackages(action: Action<GitHubPackagesDestination>): Unit = action.execute(githubPackages)

    public fun gradlePluginPortal(action: Action<GradlePluginPortalDestination>): Unit =
        action.execute(gradlePluginPortal)

    public fun mavenCentral(action: Action<MavenCentralDestination>): Unit = action.execute(mavenCentral)

    public fun maven(name: String, action: Action<CustomMavenRepository>): Unit {
        customRepos.register(name) { action.execute(this) }
    }

    public fun customRepos(action: Action<NamedDomainObjectContainer<CustomMavenRepository>>): Unit =
        action.execute(customRepos)

    public fun local() {
        mavenLocal.enabled.set(true)
        mavenStandalone.enabled.set(true)
    }

    public fun production() {
        mavenCentral.enabled.set(true)
        githubPackages.enabled.set(true)
    }

    public fun all() {
        mavenLocal.enabled.set(true)
        mavenStandalone.enabled.set(true)
        mavenCentral.enabled.set(true)
        githubPackages.enabled.set(true)
        githubPages.enabled.set(true)
    }
}
