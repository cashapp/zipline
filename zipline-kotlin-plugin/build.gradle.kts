import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  kotlin("kapt")
  id("com.github.gmazzo.buildconfig")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  compileOnly(kotlin("compiler-embeddable"))
  compileOnly(kotlin("stdlib"))

  kapt("com.google.auto.service:auto-service:1.0.1")
  compileOnly("com.google.auto.service:auto-service-annotations:1.0.1")
}

buildConfig {
  useKotlinOutput {
    internalVisibility = true
  }

  packageName("app.cash.zipline.kotlin")
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${libs.plugins.zipline.kotlin.get()}\"")
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(
      javadocJar = JavadocJar.Empty()
    )
  )
}
