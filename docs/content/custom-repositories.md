<a id="top"></a>
<table width="800">
  <tr>
    <td width="200" align="left" style="word-wrap: break-word;"><a href="../content/tasks.md">← Tasks</a></td>
    <td width="400" align="center"><a href="../index.md">📋 Gradle Release Plugin</a></td>
    <td width="200" align="right" style="word-wrap: break-word;"><a href="../content/examples.md">Examples →</a></td>
  </tr>
</table>

<hr/>

# Custom Maven Repositories

Configure private or authenticated Maven repositories.

## Basic Authentication

```kotlin
releaseConfig {
  destinations {
    maven("artifactory") {
      url.set("https://artifactory.example.com/libs-release")
      username.set(providers.gradleProperty("artifactory.user"))
      password.set(providers.gradleProperty("artifactory.password"))
    }
  }
}
```

## Token-based Authentication

```kotlin
releaseConfig {
  destinations {
    maven("nexus") {
      url.set("https://nexus.example.com/repository/releases")
      authHeaderName.set("Authorization")
      authHeaderValue.set("Bearer ${providers.gradleProperty("nexus.token").get()}")
    }
  }
}
```

## Internal HTTP Repository

```kotlin
releaseConfig {
  destinations {
    maven("internal") {
      url.set("http://internal.example.com/maven")
      allowInsecureProtocol.set(true)
      username.set("deploy")
      password.set(providers.environmentVariable("INTERNAL_MAVEN_PASSWORD"))
    }
  }
}
```

## Options

| Option | Description |
|--------|-------------|
| `username` + `password` | Basic authentication |
| `authHeaderName` + `authHeaderValue` | Header auth (tokens) |
| `allowInsecureProtocol` | Allow HTTP |
| `snapshots` / `releases` | Configuration flags available in DSL (currently not enforced by routing logic) |
