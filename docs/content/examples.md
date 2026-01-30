<a id="top"></a>
<table width="800">
  <tr>
    <td width="200" align="left" style="word-wrap: break-word;"><a href="../content/custom-repositories.md">← Custom Repositories</a></td>
    <td width="400" align="center"><a href="../index.md">📋 Gradle Release Plugin</a></td>
    <td width="200" align="right" style="word-wrap: break-word;"><a href="../content/COMPARISON.md">Comparison →</a></td>
  </tr>
</table>

<hr/>

# Publishing Examples

Real-world examples for different library types.

## Android Library

```kotlin
// settings.gradle.kts
pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

include(":my-android-lib")
```

```kotlin
// build.gradle.kts (root)
plugins {
  id("dev.all4.release") version "1.0.0"
}

releaseConfig {
  github("mycompany/android-utils")
  group.set("com.mycompany.utils")
  version.set("1.0.0")
  dryRun.set(false)

  pom {
    name.set("Android Utils")
    description.set("Common utilities for Android development")
    license { apache2() }
    developer("devname", "Dev Name", "dev@company.com")
  }

  destinations.local()
}
```

```kotlin
// my-android-lib/build.gradle.kts
plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("dev.all4.release")  // Auto-configures publishing with sources!
}

android {
  namespace = "com.mycompany.utils"
  compileSdk = 35

  defaultConfig {
    minSdk = 24
  }

  // No need to configure publishing manually!
  // The plugin auto-configures: singleVariant("release") { withSourcesJar() }
}

dependencies {
  implementation("androidx.core:core-ktx:1.15.0")
}
```

**Publish:**

```bash
./gradlew :my-android-lib:publishAllPublicationsToMavenLocal
```

---

## JVM Library (Kotlin)

```kotlin
// settings.gradle.kts
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

include(":core", ":utils")
```

```kotlin
// build.gradle.kts (root)
plugins {
  id("dev.all4.release") version "1.0.0"
}

releaseConfig {
  github("mycompany/kotlin-libs")
  group.set("com.mycompany.libs")
  version.set("2.0.0")
  dryRun.set(false)

  pom {
    name.set("Kotlin Libraries")
    description.set("Utilities for Kotlin JVM projects")
    license { mit() }
    developer("devname", "Dev Name", "dev@company.com")
  }

  libraryGroups {
    register("all") {
      modules.addAll(":core", ":utils")
    }
  }

  destinations.local()
  // destinations.production() // Uncomment for Maven Central + GitHub Packages
}
```

```kotlin
// core/build.gradle.kts
plugins {
  kotlin("jvm")
  id("dev.all4.release")
}

dependencies {
  implementation(kotlin("stdlib"))
}
```

```kotlin
// utils/build.gradle.kts
plugins {
  kotlin("jvm")
  id("dev.all4.release")
}

dependencies {
  api(project(":core"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
```

**Publish:**

```bash
# Single module
./gradlew :core:publishAllPublicationsToMavenLocal

# All modules in group
./gradlew publishAllToMavenLocal
```

---

## Kotlin Multiplatform Library

```kotlin
// settings.gradle.kts
pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

include(":shared")
```

```kotlin
// build.gradle.kts (root)
plugins {
  id("dev.all4.release") version "1.0.0"
}

releaseConfig {
  github("mycompany/kmp-library")
  group.set("com.mycompany.kmp")
  version.set("1.0.0")
  dryRun.set(false)

  pom {
    name.set("KMP Shared Library")
    description.set("Cross-platform utilities for Kotlin Multiplatform")
    license { apache2() }
    developer("devname", "Dev Name", "dev@company.com")
  }

  destinations.local()
}
```

```kotlin
// shared/build.gradle.kts
plugins {
  kotlin("multiplatform")
  id("com.android.library")
  id("dev.all4.release")
}

kotlin {
  // Android
  androidTarget {
    publishLibraryVariants("release")
  }

  // iOS
  iosX64()
  iosArm64()
  iosSimulatorArm64()

  // JVM
  jvm()

  // JS
  js(IR) {
    browser()
    nodejs()
  }

  sourceSets {
    commonMain.dependencies {
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
      implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
    }

    androidMain.dependencies {
      implementation("androidx.core:core-ktx:1.15.0")
    }
  }
}

android {
  namespace = "com.mycompany.kmp.shared"
  compileSdk = 35
  defaultConfig { minSdk = 24 }
}
```

**Publish:**

```bash
# All platforms to local
./gradlew :shared:publishAllPublicationsToMavenLocal

# Specific platform
./gradlew :shared:publishJvmPublicationToMavenLocal
./gradlew :shared:publishAndroidReleasePublicationToMavenLocal
./gradlew :shared:publishIosArm64PublicationToMavenLocal
```

---

## Gradle Plugin

```kotlin
// settings.gradle.kts
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

include(":plugin")
```

```kotlin
// build.gradle.kts (root)
plugins {
  id("dev.all4.release") version "1.0.0"
}

releaseConfig {
  github("mycompany/my-gradle-plugin")
  group.set("com.mycompany.gradle")
  version.set("1.0.0")
  dryRun.set(false)

  pom {
    name.set("My Gradle Plugin")
    description.set("A useful Gradle plugin")
    license { apache2() }
    developer("devname", "Dev Name", "dev@company.com")
  }

  destinations.local()
}
```

```kotlin
// plugin/build.gradle.kts
plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
  id("com.gradle.plugin-publish") version "1.3.0"
  id("dev.all4.release")
}

gradlePlugin {
  website.set("https://github.com/mycompany/my-gradle-plugin")
  vcsUrl.set("https://github.com/mycompany/my-gradle-plugin")

  plugins {
    create("myPlugin") {
      id = "com.mycompany.myplugin"
      displayName = "My Plugin"
      description = "Does something useful"
      tags.set(listOf("utility", "kotlin"))
      implementationClass = "com.mycompany.MyPlugin"
    }
  }
}
```

**Publish to local:**

```bash
./gradlew :plugin:publishAllPublicationsToMavenLocal
```

**Publish to Gradle Plugin Portal:**

```bash
# Set credentials in ~/.gradle/gradle.properties:
# gradle.publish.key=your-key
# gradle.publish.secret=your-secret

./gradlew :plugin:publishPlugins
```

---

## Publishing to Production

When ready for production:

```kotlin
releaseConfig {
  dryRun.set(false)

  destinations.production() // Maven Central + GitHub Packages
}
```

**Required environment variables:**

```bash
export GITHUB_ACTOR=your-username
export GITHUB_TOKEN=ghp_xxxxxxxxxxxx
export SONATYPE_USERNAME=your-sonatype-user
export SONATYPE_PASSWORD=your-sonatype-password
```

**Publish:**

```bash
./gradlew publishAllToMavenCentral
./gradlew publishAllToGitHubPackages
```

---

## Consuming Published Libraries

After publishing, add the repository and dependency:

```kotlin
// Consumer project settings.gradle.kts
dependencyResolutionManagement {
  repositories {
    mavenLocal() // For local testing
    mavenCentral()
    maven { url = uri("https://maven.pkg.github.com/mycompany/kotlin-libs") }
  }
}
```

```kotlin
// Consumer project build.gradle.kts
dependencies {
  implementation("com.mycompany.libs:core:2.0.0")
  implementation("com.mycompany.kmp:shared:1.0.0")
}
```

---

[↑ Go to top](#top)
