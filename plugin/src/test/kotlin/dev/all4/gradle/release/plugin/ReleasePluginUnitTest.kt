package dev.all4.gradle.release.plugin

import dev.all4.gradle.release.ReleasePlugin
import dev.all4.gradle.release.PublishingExtension
import org.assertj.core.api.Assertions.assertThat
import org.gradle.kotlin.dsl.findByType
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ReleasePluginUnitTest {

  @TempDir lateinit var tempDir: File

  @Test
  fun `plugin creates releaseConfig extension on root project`() {
    val project = ProjectBuilder.builder().withProjectDir(tempDir).build()

    project.plugins.apply(ReleasePlugin::class.java)

    val ext = project.extensions.findByType<PublishingExtension>()
    assertThat(ext).isNotNull()
  }

  @Test
  fun `plugin sets default mavenStandalone path`() {
    val project = ProjectBuilder.builder().withProjectDir(tempDir).build()

    project.plugins.apply(ReleasePlugin::class.java)

    val ext = project.extensions.findByType<PublishingExtension>()!!
    assertThat(ext.destinations.mavenStandalone.path.isPresent).isTrue()
  }

  @Test
  fun `plugin reads library group from properties`() {
    val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
    project.extensions.extraProperties.set("library.group", "com.test")

    project.plugins.apply(ReleasePlugin::class.java)

    val ext = project.extensions.findByType<PublishingExtension>()!!
    assertThat(ext.group.orNull).isEqualTo("com.test")
  }

  @Test
  fun `plugin reads library version from properties`() {
    val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
    project.extensions.extraProperties.set("library.version", "2.0.0")

    project.plugins.apply(ReleasePlugin::class.java)

    val ext = project.extensions.findByType<PublishingExtension>()!!
    assertThat(ext.version.orNull).isEqualTo("2.0.0")
  }

  @Test
  fun `subproject plugin applies maven-publish`() {
    val rootProject = ProjectBuilder.builder().withProjectDir(tempDir).build()
    val subDir = File(tempDir, "subproject").apply { mkdirs() }
    val subproject = ProjectBuilder.builder()
        .withParent(rootProject)
        .withProjectDir(subDir)
        .withName("subproject")
        .build()

    subproject.plugins.apply(ReleasePlugin::class.java)

    assertThat(subproject.plugins.hasPlugin("maven-publish")).isTrue()
  }

  @Test
  fun `subproject inherits version from root extension`() {
    val rootProject = ProjectBuilder.builder().withProjectDir(tempDir).build()
    rootProject.plugins.apply(ReleasePlugin::class.java)
    val rootExt = rootProject.extensions.findByType<PublishingExtension>()!!
    rootExt.version.set("3.0.0")

    val subDir = File(tempDir, "subproject").apply { mkdirs() }
    val subproject = ProjectBuilder.builder()
        .withParent(rootProject)
        .withProjectDir(subDir)
        .withName("subproject")
        .build()

    subproject.plugins.apply(ReleasePlugin::class.java)

    assertThat(subproject.version).isEqualTo("3.0.0")
  }

  @Test
  fun `subproject inherits group from root extension`() {
    val rootProject = ProjectBuilder.builder().withProjectDir(tempDir).build()
    rootProject.plugins.apply(ReleasePlugin::class.java)
    val rootExt = rootProject.extensions.findByType<PublishingExtension>()!!
    rootExt.group.set("com.example.root")

    val subDir = File(tempDir, "subproject").apply { mkdirs() }
    val subproject = ProjectBuilder.builder()
        .withParent(rootProject)
        .withProjectDir(subDir)
        .withName("subproject")
        .build()

    subproject.plugins.apply(ReleasePlugin::class.java)

    assertThat(subproject.group).isEqualTo("com.example.root")
  }

  @Test
  fun `extension can configure library groups`() {
    val project = ProjectBuilder.builder().withProjectDir(tempDir).build()

    project.plugins.apply(ReleasePlugin::class.java)
    val ext = project.extensions.findByType<PublishingExtension>()!!

    ext.libraryGroups.create("core") {
      modules.set(setOf(":core", ":core-api"))
      description.set("Core modules")
    }

    assertThat(ext.libraryGroups).hasSize(1)
    assertThat(ext.libraryGroups.getByName("core").modules.get())
        .containsExactlyInAnyOrder(":core", ":core-api")
  }

  @Test
  fun `extension can configure all destinations`() {
    val project = ProjectBuilder.builder().withProjectDir(tempDir).build()

    project.plugins.apply(ReleasePlugin::class.java)
    val ext = project.extensions.findByType<PublishingExtension>()!!

    ext.destinations {
      mavenLocal.enabled.set(true)
      mavenStandalone.enabled.set(true)
      githubPackages.enabled.set(true)
      githubPages.enabled.set(true)
      mavenCentral.enabled.set(true)
      gradlePluginPortal.enabled.set(true)
    }

    assertThat(ext.destinations.mavenLocal.enabled.get()).isTrue()
    assertThat(ext.destinations.mavenStandalone.enabled.get()).isTrue()
    assertThat(ext.destinations.githubPackages.enabled.get()).isTrue()
    assertThat(ext.destinations.githubPages.enabled.get()).isTrue()
    assertThat(ext.destinations.mavenCentral.enabled.get()).isTrue()
    assertThat(ext.destinations.gradlePluginPortal.enabled.get()).isTrue()
  }

  @Test
  fun `extension can configure pom with all fields`() {
    val project = ProjectBuilder.builder().withProjectDir(tempDir).build()

    project.plugins.apply(ReleasePlugin::class.java)
    val ext = project.extensions.findByType<PublishingExtension>()!!

    ext.pom {
      name.set("Test Library")
      description.set("A test library")
      url.set("https://example.com")
      inceptionYear.set("2024")
      license { apache2() }
      developer("dev1", "Developer", "dev@example.com")
      scm { github("owner/repo") }
    }

    assertThat(ext.pom.name.get()).isEqualTo("Test Library")
    assertThat(ext.pom.license.name.get()).isEqualTo("Apache-2.0")
    assertThat(ext.pom.developers.get()).hasSize(1)
    assertThat(ext.pom.scm.url.get()).isEqualTo("https://github.com/owner/repo")
  }
}
