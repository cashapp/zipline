apply(plugin = "app.cash.zipline")

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
}

kotlin {
  jvm()
  js {
    browser()
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation("app.cash.zipline:zipline")
      }
    }
  }
}
