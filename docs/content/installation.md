<a id="top"></a>
<table width="800">
  <tr>
    <td width="200" align="left" style="word-wrap: break-word;">← None</td>
    <td width="400" align="center"><a href="../index.md">📋 Gradle Release Plugin</a></td>
    <td width="200" align="right" style="word-wrap: break-word;"><a href="../content/configuration.md">Configuration →</a></td>
  </tr>
</table>

<hr/>

# Installation

## Gradle Plugin Portal (Recommended)

```kotlin
// settings.gradle.kts
pluginManagement {
  repositories {
    gradlePluginPortal()
  }
}

// build.gradle.kts (root project and modules)
plugins {
  id("dev.all4.release") version "0.1.0-alpha.6"
}
```

The plugin auto-detects root vs subproject and configures accordingly.

## Maven Central

```kotlin
// settings.gradle.kts
pluginManagement {
  repositories {
    mavenCentral()
  }
}

// build.gradle.kts
buildscript {
  dependencies {
    classpath("dev.all4.gradle:release-plugin:0.1.0-alpha.6")
  }
}

apply(plugin = "dev.all4.release")
```
