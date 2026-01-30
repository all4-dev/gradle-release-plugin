package dev.all4.gradle

plugins {
  id("org.jetbrains.dokka")
}

val generateModuleDocs by tasks.registering {
  group = "documentation"
  description = "Generates module.md for Dokka from README"

  val readmeFile = projectDir.resolve("../README.md").canonicalFile
  val outputDir = layout.buildDirectory.dir("dokka-includes")
  val outputFile = outputDir.map { it.file("module.md") }

  inputs.files(fileTree(readmeFile.parentFile) { include("README.md") })
  outputs.file(outputFile)

  doLast {
    val outDir = outputDir.get().asFile
    outDir.mkdirs()

    val content = buildString {
      appendLine("# Module ${project.name}")
      appendLine()

      if (readmeFile.exists()) {
        readmeFile.readLines()
          .drop(1) // Skip the first line (# title)
          .forEach { line ->
            // Convert markdown headers: ## -> **bold**, ### -> *italic*
            val converted = when {
              line.startsWith("### ") -> "**${line.removePrefix("### ")}**"
              line.startsWith("## ") -> "## ${line.removePrefix("## ")}"
              else -> line
            }
            appendLine(converted)
          }
      }
    }

    outputFile.get().asFile.writeText(content)
    logger.lifecycle("Generated module docs: ${outputFile.get().asFile}")
  }
}

dokka {
  moduleName.set(project.name)

  dokkaSourceSets.configureEach {
    includes.from(generateModuleDocs.map { it.outputs.files })

    sourceLink {
      localDirectory.set(projectDir.resolve("src"))
      remoteUrl.set(uri("https://github.com/all4-dev/gradle-publish-plugin/tree/main/plugin/src"))
      remoteLineSuffix.set("#L")
    }
  }
}
