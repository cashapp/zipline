import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  kotlin("multiplatform")
  kotlin("native.cocoapods")
}

kotlin {
  iosArm64()
  iosX64()
  iosSimulatorArm64()
  macosArm64()
  macosX64()

  cocoapods {
    noPodspec()
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation("app.cash.zipline:zipline")
        implementation(projects.emojiSearch.presenters)
      }
    }
  }
}
