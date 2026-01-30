package dev.all4.gradle.release.destinations

import org.gradle.api.provider.Property

public abstract class MavenCentralDestination : BaseDestination() {
    public abstract val stagingUrl: Property<String>
    public abstract val profileId: Property<String>
    public abstract val useCentralPortal: Property<Boolean>

    init {
        useCentralPortal.convention(false)
    }
}
