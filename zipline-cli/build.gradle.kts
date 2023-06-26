import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  kotlin("jvm")
  application
  id("com.github.gmazzo.buildconfig")
  id("com.vanniktech.maven.publish.base")
}

application {
  mainClass.set("app.cash.zipline.cli.Main")
}

buildConfig {
  useKotlinOutput {
    internalVisibility = true
  }

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
  api(projects.ziplineApiValidator)
  api(projects.ziplineLoader)
  implementation(libs.clikt)
  implementation(libs.okHttp.core)

  testImplementation(projects.ziplineLoaderTesting)
  testImplementation(libs.assertk)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.serialization.json)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.okio.core)
  testImplementation(libs.okHttp.mockWebServer)
}

mavenPublishing {
  configure(KotlinJvm(javadocJar = JavadocJar.Empty()))
}
