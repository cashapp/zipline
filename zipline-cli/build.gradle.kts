import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  kotlin("jvm")
  kotlin("kapt")
  application
  id("com.github.gmazzo.buildconfig")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
}

application {
  mainClass.set("app.cash.zipline.cli.Main")
}

buildConfig {
  packageName("app.cash.zipline.cli")
  buildConfigField("String", "VERSION", "\"${version}\"")
}

// Disable .tar that no one wants.
tasks.named("distTar").configure {
  enabled = false
}

// Remove default .jar output artifact.
configurations.archives.configure {
  artifacts.clear()
}
// Add the distribution .zip as an output artifact.
artifacts {
  archives(tasks.named("distZip"))
}

kotlin {
  sourceSets {
    all {
      languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
    }
  }
}

dependencies {
  api(projects.ziplineLoader)
  implementation(libs.okHttp.core)
  implementation(libs.picocli)

  kapt(libs.picocli.compiler)

  testImplementation(projects.ziplineLoaderTesting)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.serialization.json)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.okio.core)
  testImplementation(libs.okHttp.mockWebServer)
  testImplementation(libs.truth)
}

mavenPublishing {
  configure(KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm")))
}
