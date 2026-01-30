plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.ktfmt.gradle.plugin)
  implementation(libs.detekt.gradle.plugin)
  implementation(libs.dokka.gradle.plugin)
  implementation(libs.kover.gradle.plugin)
  implementation(libs.gradle.plugin.publish)
  implementation(libs.kotlin.bcv.gradle.plugin)
  // https://github.com/gradle/gradle/issues/15383 - still needed in Gradle 9.x
  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
