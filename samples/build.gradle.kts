buildscript {
  dependencies {
    classpath(libs.android.gradle.plugin)
    classpath(libs.kotlin.gradle.plugin)
    classpath(libs.kotlin.serialization)
    classpath(libs.shadowJar.gradle.plugin)
    classpath(libs.cklib.gradle.plugin)
    classpath("app.cash.zipline:zipline-gradle-plugin")
  }
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
}

allprojects {
  repositories {
    mavenCentral()
    google()
  }
}
