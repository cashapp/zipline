import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  kotlin("kapt")
  id("com.github.gmazzo.buildconfig")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("com.github.johnrengelman.shadow")
}

dependencies {
  compileOnly(kotlin("compiler"))

  kapt("com.google.auto.service:auto-service:1.0")
  compileOnly("com.google.auto.service:auto-service-annotations:1.0")
}

buildConfig {
  packageName("app.cash.zipline.kotlin")
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${libs.plugins.zipline.kotlin.get()}\"")
}

val shadowJar = tasks.named("shadowJar", ShadowJar::class.java)
shadowJar.configure {
  relocate("com.intellij", "org.jetbrains.kotlin.com.intellij")
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
