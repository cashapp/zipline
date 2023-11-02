import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

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

    // TODO: remove this once a stable Compose compiler is released for Kotlin 1.9.20.
    maven(url = "https://androidx.dev/storage/compose-compiler/repository/")
  }
}

allprojects {
  repositories {
    mavenCentral()
    google()

    // TODO: remove this once a stable Compose compiler is released for Kotlin 1.9.20.
    maven(url = "https://androidx.dev/storage/compose-compiler/repository/")
  }
}

allprojects {
  plugins.withId("org.jetbrains.kotlin.multiplatform") {
    configure<KotlinMultiplatformExtension> {
      jvmToolchain(11)
    }
  }
  plugins.withId("org.jetbrains.kotlin.jvm") {
    configure<KotlinJvmProjectExtension> {
      jvmToolchain(11)
    }
  }
  plugins.withId("org.jetbrains.kotlin.android") {
    configure<KotlinAndroidProjectExtension> {
      jvmToolchain(11)
    }
  }
}
