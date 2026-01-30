package dev.all4.gradle

import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.GroupingEntityType
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  id("org.jetbrains.kotlinx.kover")
}

val libs = the<LibrariesForLibs>()

// Kover CLI and Agent configurations for GradleRunner coverage
val koverCli: Configuration by configurations.creating {
  isCanBeConsumed = false
  isTransitive = true
  isCanBeResolved = true
}

val koverAgent: Configuration by configurations.creating {
  isCanBeConsumed = false
  isTransitive = true
  isCanBeResolved = true
}

dependencies {
  koverCli(libs.kover.cli)
  koverAgent(libs.kover.jvm.agent)
}

// Kover JVM agent report file for GradleRunner coverage
val koverAgentReport = layout.buildDirectory.file("kover/agent-report.ic")

kover {
  currentProject {
    instrumentation {
      // Exclude Gradle-generated classes from instrumentation
      excludedClasses.addAll("org.gradle.kotlin.dsl.*")
    }
    sources {
      // Only include the main source set (exclude test source sets from reports)
      includedSourceSets.addAll("main")
    }
  }

  reports {
    filters {
      excludes {
        // Gradle generated DSL classes
        packages("org.gradle.kotlin.dsl")
        // Marker annotations (no logic to test)
        classes("dev.all4.gradle.release.ReleaseDsl")
        // Plugin implementation (tested via functional tests in a separate JVM)
        classes("dev.all4.gradle.release.ReleasePlugin")
        classes("dev.all4.gradle.release.ExternalArtifactImporter")
        // Exclude classes with common "generated" annotations
        annotatedBy(
          "javax.annotation.processing.Generated",
        )
      }
    }

    total {
      html {
        title = "Coverage Report"
        onCheck = true
      }
      xml {
        onCheck = true
      }
      log {
        onCheck = true
        header = "📊 Coverage"
        format = "  <entity> line coverage: <value>%"
        groupBy = GroupingEntityType.APPLICATION
        coverageUnits = CoverageUnit.LINE
        aggregationForGroup = AggregationType.COVERED_PERCENTAGE
      }
    }

    verify {
      rule("line-coverage") {
        bound {
          minValue = 43
          coverageUnits = CoverageUnit.LINE
        }
      }
    }
  }
}
