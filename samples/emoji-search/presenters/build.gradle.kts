import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
}

kotlin {
  jvm()

  js {
    browser()
    binaries.executable()
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(project(":zipline"))
      }
    }
    val jvmMain by getting {
      dependencies {
        implementation(Dependencies.okHttp)
      }
    }
  }
}

// TODO(jwilson): remove this once the Kotlin/JS devserver doesn't crash on boot.
// https://stackoverflow.com/questions/69537840/kotlin-js-gradle-plugin-unable-to-load-webpack-cli-serve-command
rootProject.plugins.withType(org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin::class.java) {
  rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().versions.webpackCli.version = "4.9.0"
}

dependencies {
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, project(":zipline-kotlin-plugin"))
}
