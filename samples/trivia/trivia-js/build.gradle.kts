import app.cash.zipline.gradle.ZiplineCompileTask

apply(plugin = "app.cash.zipline")

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
}

kotlin {
  js {
    browser()
    binaries.executable()
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation("app.cash.zipline:zipline")
        implementation(project(":trivia:trivia-shared"))
      }
    }
  }
}


tasks.withType(ZiplineCompileTask::class) {
  mainFunction.set("app.cash.zipline.samples.trivia.launchZipline")
}

