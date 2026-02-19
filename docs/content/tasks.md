<a id="top"></a>
<table width="800">
  <tr>
    <td width="200" align="left" style="word-wrap: break-word;"><a href="../content/configuration.md">← Configuration</a></td>
    <td width="400" align="center"><a href="../index.md">📋 Gradle Release Plugin</a></td>
    <td width="200" align="right" style="word-wrap: break-word;"><a href="../content/custom-repositories.md">Custom Repositories →</a></td>
  </tr>
</table>

<hr/>

# Available Tasks

## Plugin Tasks

Tasks registered by this plugin:

| Task | Description |
|------|-------------|
| `bumpVersion` | Bump version in `gradle.properties` |
| `createRelease` | Create git tag and GitHub release |
| `generateChangelog` | Generate changelog entries for a library group |
| `importArtifact` | Import external JARs/AARs into local Maven repo |
| `publishingInfo` | Show current publishing configuration |

## Release Workflow Task Examples

```bash
./gradlew bumpVersion --bump=patch
./gradlew createRelease --version=1.2.3
./gradlew generateChangelog --group=core --since=v1.1.0
```

## Per-Group Publishing Tasks

For each `libraryGroup` registered, these tasks are created (where `<Group>` is the capitalized group name). A task exists only when the corresponding destination is enabled in `releaseConfig.destinations`:

| Task | Destination |
|------|-------------|
| `publish<Group>ToMavenLocal` | `~/.m2/repository` |
| `publish<Group>ToStandalone` | Local standalone repo |
| `publish<Group>ToGitHubPackages` | GitHub Packages |
| `publish<Group>ToGitHubPages` | GitHub Pages Maven repo |
| `publish<Group>ToMavenCentral` | Maven Central (Sonatype) |

**Example:** If you have `libraryGroups { register("core") { ... } }`, you get:
- `publishCoreToMavenLocal`
- `publishCoreToStandalone`
- etc.

## Aggregate Tasks

| Task | Description |
|------|-------------|
| `publishAllToStandalone` | Publish all library groups to standalone |

## Import Artifact Task

```bash
./gradlew importArtifact \
  --file=/path/to/library.aar \
  --group=com.example \
  --name=my-lib \
  --version=1.0.0 \
  --prefix=standalone \
  --output=gradle/maven-standalone
```

## Environment Variables

| Variable | Description |
|----------|-------------|
| `GITHUB_ACTOR` | GitHub username |
| `GITHUB_TOKEN` | GitHub personal access token |
| `SONATYPE_USERNAME` | Maven Central username |
| `SONATYPE_PASSWORD` | Maven Central password |

Or set in `gradle.properties`:

```properties
GITHUB_ACTOR=your-username
GITHUB_TOKEN=ghp_xxxxxxxxxxxx
sonatype.username=your-username
sonatype.password=your-password
```

## Dry Run Mode

Default is `dryRun = true` (safe mode). Tasks log what they would do without actually publishing.

```kotlin
releaseConfig {
  dryRun.set(false) // Enable actual publishing
}
```

## Android Libraries

When the plugin detects `com.android.library`, it **automatically configures**:

```kotlin
android.publishing {
  singleVariant("release") {
    withSourcesJar()
  }
}
```

This means your published AAR will include a `-sources.jar`, allowing IDEs to show source code when navigating to library classes.

**No manual configuration needed** — just apply the plugin and publish.

To also include Javadoc (optional), add manually:

```kotlin
android.publishing {
  singleVariant("release") {
    withSourcesJar()   // Already auto-configured
    withJavadocJar()   // Add this if you want docs
  }
}
```
