package dev.all4.gradle.release.config

import dev.all4.gradle.release.PublishDsl
import org.gradle.api.provider.Property

@PublishDsl
public abstract class LicenseConfiguration {
    public abstract val name: Property<String>
    public abstract val url: Property<String>
    public abstract val distribution: Property<String>

    public fun apache2() {
        name.set("Apache-2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0")
    }

    public fun mit() {
        name.set("MIT")
        url.set("https://opensource.org/licenses/MIT")
    }

    public fun gpl3() {
        name.set("GPL-3.0")
        url.set("https://www.gnu.org/licenses/gpl-3.0.html")
    }

    public fun bsd3() {
        name.set("BSD-3-Clause")
        url.set("https://opensource.org/licenses/BSD-3-Clause")
    }

    init {
        distribution.convention("repo")
    }
}
