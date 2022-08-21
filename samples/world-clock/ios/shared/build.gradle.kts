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
        implementation(projects.worldClock.presenters)
      }
    }
  }
}
