import app.cash.zipline.gradle.ZiplineCompileTask

plugins {
  kotlin("multiplatform")
  id("app.cash.zipline")
}

kotlin {
  js("blue") {
    browser()
    binaries.executable()
  }

  js("red") {
    browser()
    binaries.executable()
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation("app.cash.zipline:zipline:${project.property("ziplineVersion")}")
      }
    }
  }
}

tasks.withType(ZiplineCompileTask::class) {
  mainFunction.set("")
}
