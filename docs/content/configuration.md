<a id="top"></a>
<table width="800">
  <tr>
    <td width="200" align="left" style="word-wrap: break-word;"><a href="../content/installation.md">← Installation</a></td>
    <td width="400" align="center"><a href="../index.md">📋 Gradle Release Plugin</a></td>
    <td width="200" align="right" style="word-wrap: break-word;"><a href="../content/tasks.md">Tasks →</a></td>
  </tr>
</table>

<hr/>

# Configuration

## Quick Start

```kotlin
releaseConfig {
  github("owner/repo")  // Configures pom, scm, and githubPackages
  dryRun.set(false)

  pom {
    name.set("My Library")
    description.set("A great Kotlin library")
    license { apache2() }
    developer("coffeeaddict", "Coffee Addict", "need@morecoffee.dev")
  }

  libraryGroups {
    register("core") { modules.addAll(":core:api", ":core:impl") }
  }

  destinations.local()
}
```

## Full Configuration

```kotlin
releaseConfig {
  // Global settings
  group.set("my.domain.mylib")
  version.set("1.0.0")
  dryRun.set(false)

  // GitHub shorthand
  github("example/mylib")

  // POM metadata
  pom {
    name.set("My Library")
    description.set("A great Kotlin library")
    inceptionYear.set("2024")

    // License shorthands: apache2(), mit(), gpl3(), bsd3()
    license { apache2() }

    // Developer shorthand
    developer("coffeeaddict", "Coffee Addict", "need@morecoffee.dev")

    // Or full configuration
    developer {
      id.set("bugslayer9000")
      name.set("Bug Slayer 9000")
      email.set("no-bugs-allowed@example.com")
      organization.set("Stack Overflow Copy-Paste Inc")
      organizationUrl.set("https://stackoverflow.com")
    }
  }

  // Library groups
  libraryGroups {
    register("core") {
      modules.addAll(":libs:core:api", ":libs:core:impl")
      changelogPath.set("changelogs/core/CHANGELOG.md")
    }
  }

  // Destinations
  destinations {
    mavenLocal.enabled.set(true)
    mavenStandalone {
      enabled.set(true)
      path.set(rootProject.projectDir.resolve("build/maven-repo"))
    }
    githubPackages.enabled.set(true)
    githubPages {
      enabled.set(true)
      repoPath.set("/path/to/dist-repo")
    }
    mavenCentral.enabled.set(true)
  }
}
```

## Destination Presets

| Preset | Enables |
|--------|---------|
| `local()` | mavenLocal + mavenStandalone |
| `production()` | mavenCentral + githubPackages |
| `all()` | All destinations |

## Module Configuration

```kotlin
// build.gradle.kts (submodule)
plugins {
  id("dev.all4.release")
}
// That's it! Inherits config from root.
```
