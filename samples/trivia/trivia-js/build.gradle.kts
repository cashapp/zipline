import app.cash.zipline.gradle.ZiplineCompileTask

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
  id("app.cash.zipline")
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


zipline {
  mainFunction.set("app.cash.zipline.samples.trivia.launchZipline")
}

