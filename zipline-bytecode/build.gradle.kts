import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(project(":zipline"))
  api(Dependencies.okio)
  api(Dependencies.kotlinxSerialization)
  implementation(Dependencies.kotlinxSerializationJson)

  testImplementation(Dependencies.junit)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.truth)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(
      javadocJar = JavadocJar.Empty()
    )
  )
}
