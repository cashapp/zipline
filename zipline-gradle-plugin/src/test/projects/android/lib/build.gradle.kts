plugins {
  kotlin("multiplatform")
  id("com.android.library")
  id("app.cash.zipline")
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
        implementation("app.cash.zipline:zipline:${project.property("ziplineVersion")}")
      }
    }
  }
}

android {
  namespace = "app.cash.zipline.tests.android"
  compileSdk = libs.versions.compileSdk.get().toInt()
}
