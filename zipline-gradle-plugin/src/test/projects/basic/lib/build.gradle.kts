import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin

plugins {
  kotlin("multiplatform")
  id("app.cash.zipline")
}

kotlin {
  jvm {
    withJava()
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
    val jvmMain by getting {
      dependencies {
        implementation("app.cash.zipline:zipline-loader:${project.property("ziplineVersion")}")
        implementation("com.squareup.okio:okio:3.0.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.2")
      }
    }
  }
}

// This task makes the JVM program available to ZiplinePluginTest.
val jvmTestRuntimeClasspath by configurations.getting
val launchGreetService by tasks.creating(JavaExec::class) {
  dependsOn(":lib:compileProductionMainZipline")
  classpath = jvmTestRuntimeClasspath
  mainClass.set("app.cash.zipline.tests.LaunchGreetServiceJvmKt")
}

rootProject.plugins.withType<NodeJsRootPlugin> {
  val nodeJsRootExtension = rootProject.the<NodeJsRootExtension>()

  // TODO(jwilson): remove this once the Kotlin/JS devserver doesn't crash on boot.
  // https://stackoverflow.com/questions/69537840/kotlin-js-gradle-plugin-unable-to-load-webpack-cli-serve-command
  nodeJsRootExtension.versions.webpackCli.version = "4.9.0"

  // TODO(jwilson): remove this once Kotlin's built-in Node.js supports Apple Silicon.
  //  https://youtrack.jetbrains.com/issue/KT-49109
  nodeJsRootExtension.nodeVersion = "16.0.0"
}

val jsBrowserProductionRun by tasks.getting {
  dependsOn(":compileProductionExecutableKotlinJsZipline")
}
