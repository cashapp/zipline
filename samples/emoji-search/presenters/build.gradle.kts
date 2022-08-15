import app.cash.zipline.gradle.ZiplineCompileTask

apply(plugin = "app.cash.zipline")

plugins {
  kotlin("multiplatform")
  id("com.android.library")
  kotlin("plugin.serialization")
}

kotlin {
  android()

  js {
    browser()
    binaries.executable()
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation("app.cash.zipline:zipline")
      }
    }
    val androidMain by getting {
      dependencies {
        implementation(libs.okHttp.core)
        implementation(libs.sqldelight.driver.android)
        implementation("app.cash.zipline:zipline-loader")
      }
    }
  }
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "app.cash.zipline.samples.emojisearch.presenters"
}

tasks.withType(ZiplineCompileTask::class) {
  mainFunction.set("app.cash.zipline.samples.emojisearch.preparePresenters")
}
