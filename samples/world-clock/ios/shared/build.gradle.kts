plugins {
  kotlin("multiplatform")
  kotlin("native.cocoapods")
}

kotlin {
  iosArm64()
  iosX64()
  iosSimulatorArm64()

  cocoapods {
    noPodspec()
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation("app.cash.zipline:zipline")
        implementation(libs.kotlinx.coroutines.core)
        implementation(projects.worldClock.presenters)
      }
    }
  }
}
