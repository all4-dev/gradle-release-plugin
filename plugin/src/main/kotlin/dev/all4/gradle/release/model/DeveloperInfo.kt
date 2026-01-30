package dev.all4.gradle.release.model

import dev.all4.gradle.release.PublishDsl
import org.gradle.api.provider.Property

@PublishDsl
public abstract class DeveloperInfo {
    public abstract val id: Property<String>
    public abstract val name: Property<String>
    public abstract val email: Property<String>
    public abstract val organization: Property<String>
    public abstract val organizationUrl: Property<String>
}
