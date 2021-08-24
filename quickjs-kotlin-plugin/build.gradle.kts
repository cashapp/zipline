plugins {
  kotlin("jvm")
  kotlin("kapt")
  id("com.vanniktech.maven.publish")
  id("org.jetbrains.dokka")
  id("com.github.gmazzo.buildconfig")
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
  compileOnly("org.jetbrains.kotlin:kotlin-stdlib")

  kapt("com.google.auto.service:auto-service:1.0-rc7")
  compileOnly("com.google.auto.service:auto-service-annotations:1.0-rc7")

  testImplementation(project(":quickjs"))
  testImplementation(project(":quickjs:testing"))
  testImplementation(kotlin("test-junit"))
  testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.4.1")
  testImplementation("com.google.truth:truth:1.0")
}

kotlin {
  sourceSets {
  }
}

buildConfig {
  packageName("app.cash.quickjs.ktbridge.plugin")
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${Ext.kotlinPluginId}\"")
}
