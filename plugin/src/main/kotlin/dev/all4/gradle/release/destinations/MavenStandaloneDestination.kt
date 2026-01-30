package dev.all4.gradle.release.destinations

import org.gradle.api.provider.Property

public abstract class MavenStandaloneDestination : BaseDestination() {
    public abstract val path: Property<java.io.File>
}
