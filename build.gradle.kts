plugins {
  base
  id("dev.all4.gradle.release-workflow")
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.ktfmt) apply false
  alias(libs.plugins.detekt) apply false
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.kover) apply false
}
