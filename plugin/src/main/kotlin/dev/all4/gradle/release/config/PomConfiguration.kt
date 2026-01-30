package dev.all4.gradle.release.config

import dev.all4.gradle.release.PublishDsl
import dev.all4.gradle.release.model.DeveloperInfo
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

@PublishDsl
public abstract class PomConfiguration @Inject constructor(private val objects: ObjectFactory) {
    public abstract val name: Property<String>
    public abstract val description: Property<String>
    public abstract val url: Property<String>
    public abstract val inceptionYear: Property<String>

    public val license: LicenseConfiguration = objects.newInstance(LicenseConfiguration::class.java)
    public val developers: ListProperty<DeveloperInfo> = objects.listProperty(DeveloperInfo::class.java)
    public val scm: ScmConfiguration = objects.newInstance(ScmConfiguration::class.java)

    public fun license(action: Action<LicenseConfiguration>): Unit = action.execute(license)

    public fun developer(action: Action<DeveloperInfo>) {
        val dev = objects.newInstance(DeveloperInfo::class.java)
        action.execute(dev)
        developers.add(dev)
    }

    public fun developer(id: String, name: String, email: String) {
        val dev = objects.newInstance(DeveloperInfo::class.java)
        dev.id.set(id)
        dev.name.set(name)
        dev.email.set(email)
        developers.add(dev)
    }

    public fun scm(action: Action<ScmConfiguration>): Unit = action.execute(scm)
}
