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
        implementation(libs.okio.core)
        implementation(libs.kotlinx.serialization.core)
        implementation(libs.kotlinx.serialization.json)
      }
    }
  }
}

// This task makes the JVM program available to ZiplinePluginTest.
val jvmTestRuntimeClasspath by configurations.getting
val launchGreetService by tasks.creating(JavaExec::class) {
  dependsOn(":lib:compileProductionExecutableKotlinJsZipline")
  classpath = jvmTestRuntimeClasspath
  mainClass.set("app.cash.zipline.tests.LaunchGreetServiceJvmKt")
}

zipline {
  mainFunction.set("app.cash.zipline.tests.launchGreetService")
}
