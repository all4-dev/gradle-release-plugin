package dev.all4.gradle.release.destinations

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

public abstract class GradlePluginPortalDestination : BaseDestination() {
    public abstract val pluginIdPrefix: Property<String>
    public abstract val websiteUrl: Property<String>
    public abstract val vcsUrl: Property<String>
    public abstract val tags: ListProperty<String>
}
