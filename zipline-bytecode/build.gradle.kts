import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  id("com.vanniktech.maven.publish.base")
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-opt-in=app.cash.zipline.EngineApi",
    )
  }
}

dependencies {
  api(projects.zipline)
  api(libs.okio.core)
  api(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.assertk)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(
      javadocJar = JavadocJar.Empty()
    )
  )
}
