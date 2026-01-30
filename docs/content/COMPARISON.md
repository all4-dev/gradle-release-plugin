<a id="top"></a>
<table width="800">
  <tr>
    <td width="200" align="left" style="word-wrap: break-word;"><a href="../content/examples.md">← Examples</a></td>
    <td width="400" align="center"><a href="../index.md">📋 Gradle Release Plugin</a></td>
    <td width="200" align="right" style="word-wrap: break-word;">None →</td>
  </tr>
</table>

<hr/>

# Plugin Comparison Matrix

## Configuration Comparison (4 columns)

| Aspect | `maven-publish` | `vanniktech` | `nexus-publish` | **This Plugin** |
|--------|-----------------|--------------|-----------------|-----------------|
| **Plugin setup** | `maven-publish` + `signing` | `com.vanniktech.maven.publish` | `io.github.gradle-nexus.publish-plugin` | `dev.all4.release` |
| **Provides** | • Maven Local<br>• Custom repos<br>• POM config<br>• Signing<br>• Publications | • Maven Local<br>• Maven Central<br>• GitHub Packages<br>• POM config<br>• Signing<br>• Javadoc/Sources | • Maven Central (optimized)<br>• Staging repos<br>• Auto-close/release<br>• Parallel uploads | • Maven Local<br>• Maven Central<br>• GitHub Packages<br>• GitHub Pages<br>• Gradle Plugin Portal<br>• POM shortcuts<br>• Library groups<br>• Dry-run mode<br>• External imports<br>• Config inheritance |
| **Lines (root)** | ~70 | ~25 | ~30 | ~15 |
| **Lines (per module)** | ~20 | ~5 | ~10 | 1 |
| **Total (5 modules)** | ~170 | ~50 | ~80 | ~20 |

### Side-by-Side Configuration

<table>
<tr>
<th>maven-publish (built-in)</th>
<th>vanniktech</th>
<th>nexus-publish</th>
<th>This Plugin</th>
</tr>
<tr>
<td>

**📚 Libraries:** ✅<br>
**🔌 Plugins:** ❌<br><br>
• Maven Local<br>
• Custom repos<br>
• POM config<br>
• Signing<br>
• Publications

</td>
<td>

**📚 Libraries:** ✅<br>
**🔌 Plugins:** ❌<br><br>
• Maven Local<br>
• Maven Central<br>
• GitHub Packages<br>
• POM config<br>
• Signing<br>
• Javadoc/Sources

</td>
<td>

**📚 Libraries:** ✅<br>
**🔌 Plugins:** ❌<br><br>
• Maven Central (optimized)<br>
• Staging repos<br>
• Auto-close/release<br>
• Parallel uploads

</td>
<td>

**📚 Libraries:** ✅<br>
**🔌 Plugins:** ✅<br><br>
• Maven Local<br>
• Maven Central<br>
• GitHub Packages<br>
• GitHub Pages<br>
• Gradle Plugin Portal<br>
• POM shortcuts<br>
• Library groups<br>
• Dry-run mode<br>
• External imports<br>
• Config inheritance

</td>
</tr>
<tr>
<td>

```kotlin
plugins {
  `maven-publish`
  signing
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
      pom {
        name.set("My Library")
        description.set("...")
        url.set("https://github.com/...")
        licenses {
          license {
            name.set("Apache 2.0")
            url.set("https://...")
          }
        }
        developers {
          developer {
            id.set("johndoe")
            name.set("John Doe")
            email.set("john@...")
          }
        }
        scm {
          url.set("...")
          connection.set("...")
          developerConnection.set("...")
        }
      }
    }
  }
  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg...")
      credentials {
        username = System.getenv("...")
        password = System.getenv("...")
      }
    }
    maven {
      name = "MavenCentral"
      url = uri("https://s01.oss...")
      credentials { ... }
    }
  }
}

signing {
  sign(publishing.publications)
}
```

</td>
<td>

```kotlin
plugins {
  id("com.vanniktech.maven.publish")
}

mavenPublishing {
  publishToMavenCentral(
    SonatypeHost.S01
  )
  signAllPublications()
  
  pom {
    name.set("My Library")
    description.set("...")
    url.set("https://...")
    
    licenses {
      license {
        name.set("Apache 2.0")
        url.set("https://...")
      }
    }
    
    developers {
      developer {
        id.set("johndoe")
        name.set("John Doe")
        email.set("john@...")
      }
    }
    
    scm {
      url.set("...")
      connection.set("...")
      developerConnection.set("...")
    }
  }
}
```

</td>
<td>

```kotlin
plugins {
  `maven-publish`
  signing
  id("io.github.gradle-nexus.publish-plugin")
}

nexusPublishing {
  repositories {
    sonatype {
      nexusUrl.set(uri("https://..."))
      snapshotRepositoryUrl.set(uri("..."))
      username.set(System.getenv("..."))
      password.set(System.getenv("..."))
    }
  }
}

// Still need full maven-publish
// config for POM, signing, etc.
publishing {
  publications {
    create<MavenPublication>("maven") {
      pom {
        // Full POM config...
      }
    }
  }
}

signing {
  sign(publishing.publications)
}
```

</td>
<td>

```kotlin
plugins {
  id("dev.all4.release")
}

releaseConfig {
  github("example/mylib")
  dryRun.set(false)
  
  pom {
    name.set("My Library")
    description.set("...")
    license { apache2() }
    developer("johndoe", 
      "John Doe", 
      "john@...")
  }
  
  libraryGroups {
    register("core") { 
      modules.addAll(":a", ":b") 
    }
  }
  
  destinations.local()
  destinations.production()
}

// Submodules: 1 line
// id("dev.all4.release")
```

</td>
</tr>
</table>

### Why Only This Plugin Publishes Gradle Plugins?

Publishing to **Gradle Plugin Portal** requires:
1. `java-gradle-plugin` — generates plugin descriptors
2. `com.gradle.plugin-publish` — handles portal registration, plugin markers, and metadata

The other plugins focus exclusively on **Maven artifacts** (JARs/AARs):

| Plugin | Reason |
|--------|--------|
| `maven-publish` | Core Gradle plugin, only handles Maven POMs and repos |
| `vanniktech` | Wrapper around maven-publish, same scope |
| `nexus-publish` | Only optimizes Maven Central staging/release flow |

> **Note:** You *can* combine `vanniktech` with `java-gradle-plugin` + `com.gradle.plugin-publish` manually, but it requires extra configuration. This plugin integrates everything in one DSL.

---

### Key Differences

| Capability | maven-publish | vanniktech | nexus-publish | This Plugin |
|------------|---------------|------------|---------------|-------------|
| POM shortcuts | ❌ Verbose | ⚠️ Some | ❌ Verbose | ✅ `apache2()`, `developer()` |
| GitHub shorthand | ❌ | ❌ | ❌ | ✅ `github("user/repo")` |
| Multi-destination | ⚠️ Manual each | ✅ | ⚠️ Central only | ✅ `local()`, `production()` |
| Library groups | ❌ | ❌ | ❌ | ✅ |
| Dry-run mode | ❌ | ❌ | ❌ | ✅ Default safe |
| Config inheritance | ❌ | ⚠️ Partial | ❌ | ✅ Full |
| Central staging | ⚠️ Manual | ✅ Auto | ✅ Optimized | ✅ |

---

## Honest Assessment

This plugin is essentially a **convenience wrapper** around existing Gradle publishing infrastructure. Here's an honest comparison:

## Feature Comparison

| Feature | `maven-publish` (built-in) | `nexus-publish` | `vanniktech` | **This Plugin** |
|---------|---------------------------|-----------------|--------------|-----------------|
| Maven Local | ✅ Built-in | ✅ | ✅ | ✅ |
| Maven Central | ⚠️ Manual setup | ✅ Optimized | ✅ | ✅ Wrapper |
| GitHub Packages | ⚠️ Manual setup | ❌ | ✅ | ✅ |
| Gradle Plugin Portal | ❌ | ❌ | ✅ | ✅ |
| POM Configuration | ✅ Verbose | ✅ | ✅ DSL | ✅ DSL |
| Signing | ✅ Manual | ✅ | ✅ Auto | ✅ |
| Multi-module | ✅ Manual each | ✅ | ✅ | ✅ Groups |
| Dry-run mode | ❌ | ❌ | ❌ | ✅ |
| Library groups | ❌ | ❌ | ❌ | ✅ |
| GitHub shorthand | ❌ | ❌ | ❌ | ✅ |
| External artifact import | ❌ | ❌ | ❌ | ✅ |
| Changelog management | ❌ | ❌ | ❌ | ✅ |
| Learning curve | High | Medium | Low | Low |
| Maintenance burden | You | Community | Community | You |
| Maturity | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐ |

## What This Plugin Actually Adds

### Unique Features (not in alternatives)
1. **Library Groups** - Publish multiple modules together with shared config
2. **Dry-run mode** - Safe by default, preview before publishing
3. **External artifact importing** - Re-publish legacy JARs/AARs
4. **GitHub shorthand** - `github("user/repo")` configures pom.url, scm, and packages
5. **Changelog placeholders** - Auto-creates changelog structure

### Just Convenience (exists elsewhere)
- POM DSL shortcuts (`license { apache2() }`)
- Unified multi-destination config
- Auto-detection root vs subproject

## When to Use This Plugin

✅ **Good fit:**
- Multi-module projects with grouped releases
- Teams that want dry-run safety
- Projects needing to import legacy artifacts
- Quick GitHub-centric setup

❌ **Not needed:**
- Single-module projects → use `vanniktech` or raw `maven-publish`
- Only Maven Central → use `nexus-publish`
- Need battle-tested, community-maintained solution → use alternatives

## Verdict

**Honest take:** This plugin provides ~20% unique value (library groups, dry-run, external imports) and ~80% convenience wrappers around existing functionality.

If you're building a multi-module library with grouped releases and want safety defaults, it's useful. Otherwise, consider established alternatives with larger communities.

## Alternatives

| Plugin | Best For | Link |
|--------|----------|------|
| `maven-publish` | Full control, no dependencies | Built-in |
| `io.github.gradle-nexus.publish-plugin` | Maven Central publishing | [GitHub](https://github.com/gradle-nexus/publish-plugin) |
| `com.vanniktech.maven.publish` | Simple, well-documented | [GitHub](https://github.com/vanniktech/gradle-maven-publish-plugin) |
| `com.gradle.plugin-publish` | Gradle plugins only | Built-in |

---

[↑ Go to top](#top)
