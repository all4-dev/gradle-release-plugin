package dev.all4.gradle

plugins {
  base
}

private val workflowProvider = providers.provider { UnifiedReleaseWorkflow(project) }

tasks.register("releaseDoctor") {
  group = "release"
  description = "Validate release prerequisites (git, 1Password, secret access)"

  doLast {
    workflowProvider.get().doctor()
  }
}

tasks.register("releaseBumpPre") {
  group = "release"
  description = "Bump to next pre-release version and commit"

  doLast {
    val options = project.readReleaseWorkflowOptions()
    workflowProvider.get().bumpPre(options)
  }
}

tasks.register("releasePublishLocal") {
  group = "release"
  description = "Publish plugin artifact to Maven local and print local path + catalog snippet"

  doLast {
    val options = project.readReleaseWorkflowOptions()
    workflowProvider.get().publishLocal(options)
  }
}

tasks.register("releasePublishPortal") {
  group = "release"
  description = "Publish to Gradle Plugin Portal and print portal URL + catalog snippet"

  doLast {
    val options = project.readReleaseWorkflowOptions()
    workflowProvider.get().publishPortal(options)
  }
}

tasks.register("releasePublishCentral") {
  group = "release"
  description = "Publish to Maven Central and print central URL + catalog snippet"

  doLast {
    val options = project.readReleaseWorkflowOptions()
    workflowProvider.get().publishCentral(options)
  }
}

tasks.register("releaseTagAndPublishPreRelease") {
  group = "release"
  description = "Bump alpha, commit, tag, publish (portal + central), push, and print publish mini-report"

  doLast {
    val options = project.readReleaseWorkflowOptions()
    workflowProvider.get().tagAndPublishPreRelease(options)
  }
}

tasks.register("releaseTagAndPublishRelease") {
  group = "release"
  description = "Release stable version from -Prelease.version, publish, push, and print publish mini-report"

  doLast {
    val options = project.readReleaseWorkflowOptions(requireVersion = true)
    workflowProvider.get().tagAndPublishRelease(options)
  }
}
