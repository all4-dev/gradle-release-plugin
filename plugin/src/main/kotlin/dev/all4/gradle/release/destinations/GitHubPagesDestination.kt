package dev.all4.gradle.release.destinations

import org.gradle.api.provider.Property

public abstract class GitHubPagesDestination : BaseDestination() {
    public abstract val repoPath: Property<String>
    public abstract val pagesUrl: Property<String>
    public abstract val branch: Property<String>

    init {
        branch.convention("gh-pages")
    }
}
