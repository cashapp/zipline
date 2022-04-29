import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(projects.zipline)
  api(libs.okio.core)
  api(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.truth)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(
      javadocJar = JavadocJar.Empty()
    )
  )
}
