import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

buildscript {
  dependencies {
    classpath(libs.android.gradle.plugin)
    classpath(libs.kotlin.gradle.plugin)
    classpath(libs.kotlin.compose.plugin)
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

allprojects {
  plugins.withId("org.jetbrains.kotlin.multiplatform") {
    configure<KotlinMultiplatformExtension> {
      jvmToolchain(17)
    }
  }
  plugins.withId("org.jetbrains.kotlin.jvm") {
    configure<KotlinJvmProjectExtension> {
      jvmToolchain(17)
    }
  }
  plugins.withId("org.jetbrains.kotlin.android") {
    configure<KotlinAndroidProjectExtension> {
      jvmToolchain(17)
    }
  }
}
