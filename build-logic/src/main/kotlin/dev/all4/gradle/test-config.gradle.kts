package dev.all4.gradle

import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.invoke

plugins {
  `java-gradle-plugin`
  id("dev.all4.gradle.testing")
}

// Functional Test Source Set
val functionalTest: SourceSet by sourceSets.creating {
  compileClasspath += sourceSets.main.get().output
  runtimeClasspath += sourceSets.main.get().output
}

// Integration Test Source Set
val integrationTest: SourceSet by sourceSets.creating {
  compileClasspath += sourceSets.main.get().output
  runtimeClasspath += sourceSets.main.get().output
}

configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["functionalTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])
configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

// Register test source sets with gradlePlugin
gradlePlugin {
  testSourceSets(functionalTest, integrationTest)
}

// Functional Test Task
val functionalTestTask by tasks.registering(Test::class) {
  group = "verification"
  description = "Runs functional tests using GradleRunner"
  testClassesDirs = functionalTest.output.classesDirs
  classpath = functionalTest.runtimeClasspath
  shouldRunAfter(tasks.test)
}

// Integration Test Task
val integrationTestTask by tasks.registering(Test::class) {
  group = "verification"
  description = "Runs integration tests"
  testClassesDirs = integrationTest.output.classesDirs
  classpath = integrationTest.runtimeClasspath
  shouldRunAfter(tasks.test)
  shouldRunAfter(functionalTestTask)
}

tasks.check {
  dependsOn(functionalTestTask)
  dependsOn(integrationTestTask)
}
