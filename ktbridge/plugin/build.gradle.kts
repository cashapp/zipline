plugins {
  kotlin("jvm")
  kotlin("kapt")
  id("com.github.gmazzo.buildconfig")
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")

  kapt("com.google.auto.service:auto-service:1.0-rc7")
  compileOnly("com.google.auto.service:auto-service-annotations:1.0-rc7")

  testImplementation(project(":ktbridge"))
  testImplementation(project(":ktbridge:testing"))
  testImplementation(kotlin("test-junit"))
  testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.4.1")
  testImplementation("com.google.truth:truth:1.0")
}

kotlin {
  sourceSets {
    val test by getting {
      // Include jsMain as JVM test sources! This lets our tests can see createBridgeToJs() with
      // just `inheritClasspath = true`.
      kotlin.srcDir("$rootDir/ktbridge/src/jsMain")
    }
  }
}

buildConfig {
  packageName("app.cash.quickjs.ktbridge.plugin")
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${Ext.kotlinPluginId}\"")
}
