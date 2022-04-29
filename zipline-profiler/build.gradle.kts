import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(projects.zipline)
  api(libs.okio.core)

  testImplementation(libs.truth)
  testImplementation(libs.junit)
}

configure<MavenPublishBaseExtension> {
  configure(KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm")))
}
