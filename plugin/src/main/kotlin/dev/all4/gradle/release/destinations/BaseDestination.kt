package dev.all4.gradle.release.destinations

import dev.all4.gradle.release.PublishDsl
import org.gradle.api.provider.Property

@PublishDsl
public abstract class BaseDestination {
    public abstract val enabled: Property<Boolean>

    init {
        enabled.convention(false)
    }
}
