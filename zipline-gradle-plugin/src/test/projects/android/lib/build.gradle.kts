plugins {
  kotlin("multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("app.cash.zipline")
}

kotlin {
  android {
    namespace = "app.cash.zipline.tests.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
  }

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
