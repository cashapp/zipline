import app.cash.zipline.gradle.ZiplineCompileTask

apply(plugin = "app.cash.zipline")

plugins {
  kotlin("multiplatform")
  id("com.android.library")
  kotlin("plugin.serialization")
}

kotlin {
  ios()
  iosSimulatorArm64("ios")

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
    val hostMain by creating {
      dependsOn(commonMain)
      dependencies {
        implementation("app.cash.zipline:zipline-loader")
        api(libs.okio.core)
      }
    }
    val iosMain by getting {
      dependsOn(hostMain)
    }
    val androidMain by getting {
      dependsOn(hostMain)
      dependencies {
        implementation(libs.okHttp.core)
        implementation(libs.sqldelight.driver.android)
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
