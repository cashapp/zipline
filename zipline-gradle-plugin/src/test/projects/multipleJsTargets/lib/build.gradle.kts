import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin

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

// TODO: Delete this block once we've upgraded to Kotlin 1.6.20+.
rootProject.plugins.withType<NodeJsRootPlugin> {
  val nodeJsRootExtension = rootProject.the<NodeJsRootExtension>()

  // TODO(jwilson): remove this once the Kotlin/JS devserver doesn't crash on boot.
  // https://stackoverflow.com/questions/69537840/kotlin-js-gradle-plugin-unable-to-load-webpack-cli-serve-command
  nodeJsRootExtension.versions.webpackCli.version = "4.9.0"

  // TODO(jwilson): remove this once Kotlin's built-in Node.js supports Apple Silicon.
  //  https://youtrack.jetbrains.com/issue/KT-49109
  nodeJsRootExtension.nodeVersion = "16.0.0"
}
