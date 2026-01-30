package dev.all4.gradle

import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

val libs = the<LibrariesForLibs>()

kotlin {
  explicitApi()

  jvmToolchain(libs.versions.jdk.get().toInt())

  compilerOptions {
    apiVersion = KotlinVersion.KOTLIN_2_1
    languageVersion = apiVersion
    jvmTarget = JvmTarget.fromTarget(libs.versions.jdk.get())
    freeCompilerArgs.add("-Xjdk-release=${libs.versions.jdk.get()}")
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.release = libs.versions.jdk.get().toInt()
}
