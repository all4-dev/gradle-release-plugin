package dev.all4.gradle.release.destinations

import javax.inject.Inject
import org.gradle.api.provider.Property

public abstract class CustomMavenRepository @Inject constructor(private val name: String) :
    BaseDestination() {
    public fun getName(): String = name

    public abstract val url: Property<String>
    public abstract val username: Property<String>
    public abstract val password: Property<String>
    public abstract val authHeaderName: Property<String>
    public abstract val authHeaderValue: Property<String>
    public abstract val allowInsecureProtocol: Property<Boolean>
    public abstract val snapshots: Property<Boolean>
    public abstract val releases: Property<Boolean>

    init {
        enabled.convention(true)
        allowInsecureProtocol.convention(false)
        snapshots.convention(true)
        releases.convention(true)
    }
}
