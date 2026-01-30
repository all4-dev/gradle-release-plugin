package dev.all4.gradle.release.config

import dev.all4.gradle.release.PublishDsl
import org.gradle.api.provider.Property

@PublishDsl
public abstract class ScmConfiguration {
    public abstract val url: Property<String>
    public abstract val connection: Property<String>
    public abstract val developerConnection: Property<String>

    public fun github(repository: String) {
        url.set("https://github.com/$repository")
        connection.set("scm:git:git://github.com/$repository.git")
        developerConnection.set("scm:git:ssh://github.com/$repository.git")
    }
}
