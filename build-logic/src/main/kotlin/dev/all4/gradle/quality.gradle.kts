package dev.all4.gradle

import io.gitlab.arturbosch.detekt.Detekt

plugins {
  id("com.ncorti.ktfmt.gradle")
  id("io.gitlab.arturbosch.detekt")
}

ktfmt {
  kotlinLangStyle()
  maxWidth.set(100)
}

// rootDir is the applying project (e.g., plugin/), parentFile is the main project root
val projectRoot: File = rootDir.parentFile

detekt {
  buildUponDefaultConfig = true
  config.setFrom(projectRoot.resolve("build-logic/config/detekt/detekt.yml"))
  basePath = projectRoot.absolutePath
}

tasks.withType<Detekt>().configureEach {
  reports {
    html.required.set(true)
    xml.required.set(true)
    sarif.required.set(true)
  }
}
