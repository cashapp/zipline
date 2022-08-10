import app.cash.zipline.gradle.ZiplineCompileTask

apply(plugin = "app.cash.zipline")

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
}

kotlin {
  jvm()

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
    val jvmMain by getting {
      dependencies {
        implementation(libs.okHttp.core)
        implementation(libs.sqldelight.driver.android)
        implementation("app.cash.zipline:zipline-loader")
      }
    }
  }
}

tasks.withType(ZiplineCompileTask::class) {
  mainFunction.set("app.cash.zipline.samples.emojisearch.preparePresenters")
}
