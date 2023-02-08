import app.cash.zipline.gradle.ZiplineCompileTask

plugins {
  kotlin("multiplatform")
  id("app.cash.zipline")
}

kotlin {
  jvm()

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
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1")
      }
    }
  }
}

// This task makes the JVM program available to ZiplinePluginTest.
val jvmTestRuntimeClasspath by configurations.getting
val launchGreetService by tasks.creating(JavaExec::class) {
  dependsOn(":lib:jsBrowserProductionWebpackZipline")
  classpath = jvmTestRuntimeClasspath
  mainClass.set("app.cash.zipline.tests.LaunchGreetServiceJvmKt")
}

zipline {
  mainFunction.set("app.cash.zipline.tests.launchGreetService")
}
