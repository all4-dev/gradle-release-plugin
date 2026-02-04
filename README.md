# Gradle Release Plugin

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/dev.all4.release)](https://plugins.gradle.org/plugin/dev.all4.release)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A complete release toolkit: **version bumping**, **changelog generation**, **git tagging**, **GitHub releases**, and **multi-destination publishing**.

## Quick Start

```kotlin
plugins {
  id("dev.all4.release") version "0.1.0"
}

releaseConfig {
  github("owner/repo")

  pom {
    name.set("My Library")
    license { apache2() }
    developer("dev", "Developer", "dev@example.com")
  }

  destinations {
    mavenLocal.enabled.set(true)
    mavenCentral.enabled.set(true)
  }
}
```

## Tasks

```bash
# Version management
./gradlew bumpVersion --bump=patch   # 1.0.0 → 1.0.1
./gradlew bumpVersion --bump=minor   # 1.0.0 → 1.1.0
./gradlew bumpVersion --bump=major   # 1.0.0 → 2.0.0

# Release (bump + tag + GitHub release)
./gradlew createRelease --bump=patch
./gradlew createRelease --version=1.0.0

# Changelog
./gradlew generateChangelog --since=v0.9.0

# Import external artifacts
./gradlew importArtifact --file=legacy.jar --group=com.example --name=legacy
```

## Features

| Feature | Description |
|---------|-------------|
| **Version bumping** | Semantic versioning (patch/minor/major) |
| **Git tagging** | Automatic tag creation and push |
| **GitHub releases** | Create releases via API with auto-generated notes |
| **Changelog generation** | Generate from git commits |
| **Multi-destination** | Maven Local, Central, GitHub Packages/Pages, Plugin Portal |
| **Library groups** | Publish multiple modules together |
| **Changelog modes** | Centralized or per-project changelogs |
| **Dry-run mode** | Preview before publishing |
| **External artifacts** | Import legacy JARs/AARs |
| **🔐 1Password integration** | Automatic secret resolution with 1Password CLI |

## Changelog Configuration

Configure how changelogs are managed per library group:

```kotlin
releaseConfig {
  libraryGroups {
    register("core") {
      modules.add(":core")
      
      // Option 1: Centralized changelog (default)
      changelogMode.set(ChangelogMode.CENTRALIZED)
      changelogPath.set("changelogs/core/CHANGELOG.md")
      
      // Option 2: Per-project changelog (each module has its own)
      changelogMode.set(ChangelogMode.PER_PROJECT)
      // Creates CHANGELOG.md in each module's directory
      
      // Disable changelog generation entirely
      changelogEnabled.set(false)
    }
  }
}
```

| Mode | Description |
|------|-------------|
| `CENTRALIZED` | Single changelog at configured path (default) |
| `PER_PROJECT` | Each module maintains its own `CHANGELOG.md` |

## Publishing to Maven Central

### 1. Setup credentials

```properties
# ~/.gradle/gradle.properties
sonatypeUsername=your-token-username
sonatypePassword=your-token-password

# GPG signing
signing.gnupg.keyName=YOUR_KEY_ID
signing.gnupg.passphrase=your-passphrase
```

### 2. Publish

```bash
./gradlew publishToMavenCentral
```

## Documentation

- [Configuration](docs/content/configuration.md)
- [Tasks](docs/content/tasks.md)
- [Examples](docs/content/examples.md)
- [External Artifacts](docs/content/custom-repositories.md)
- [🔐 1Password Integration](docs/1password/) - Secure credential management

## License

Apache 2.0 — see [LICENSE](LICENSE)