plugins {
  kotlin("multiplatform")
  id("com.android.library")
  id("io.github.tret9.zipline")
}

kotlin {
  androidTarget()

  js {
    browser()
    binaries.executable()
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation("io.github.tret9:zipline:${project.property("ziplineVersion")}")
      }
    }
  }
}

android {
  namespace = "app.cash.zipline.tests.android"
  compileSdk = libs.versions.compileSdk.get().toInt()
}
