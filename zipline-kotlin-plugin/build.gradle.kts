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
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
  compileOnly("org.jetbrains.kotlin:kotlin-stdlib")

  kapt("com.google.auto.service:auto-service:1.0")
  compileOnly("com.google.auto.service:auto-service-annotations:1.0")

  testImplementation(project(":zipline"))
  testImplementation(project(":zipline:testing"))
  testImplementation(kotlin("test-junit"))
  testImplementation(Dependencies.kotlinxCoroutinesTest)
  testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.4.1")
  testImplementation("com.google.truth:truth:1.0")
}

kotlin {
  sourceSets {
  }
}

buildConfig {
  packageName("app.cash.zipline.kotlin")
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${Ext.kotlinPluginId}\"")
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(
      javadocJar = JavadocJar.Empty()
    )
  )
}
