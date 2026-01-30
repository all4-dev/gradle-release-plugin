package dev.all4.gradle.release.destinations

import org.gradle.api.provider.Property

public abstract class GitHubPackagesDestination : BaseDestination() {
    public abstract val repository: Property<String>
}
