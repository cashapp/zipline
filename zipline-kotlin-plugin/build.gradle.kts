import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  compileOnly(projects.ziplineKotlinPluginHosted)
}

val shadowJar = tasks.register("embeddedPlugin", ShadowJar::class.java) {
  configurations = listOf(project.configurations.getByName("compileClasspath"))
  relocate("com.intellij", "org.jetbrains.kotlin.com.intellij")
  archiveBaseName.set("embedded")
  archiveVersion.set("")
  destinationDirectory.set(File(buildDir, "repackaged"))
}

configurations {
  // replace the standard jar with the one built by 'shadowJar' in both api and runtime variants
  apiElements.get().outgoing.apply {
    artifacts.clear()
    artifact(shadowJar.flatMap { it.archiveFile })
  }
  runtimeElements.get().outgoing.apply {
    artifacts.clear()
    artifact(shadowJar.flatMap { it.archiveFile })
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(
      javadocJar = JavadocJar.Empty()
    )
  )
}
