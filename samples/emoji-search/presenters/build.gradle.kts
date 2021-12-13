import app.cash.zipline.gradle.ZiplineCompileTask
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
  id("app.cash.zipline")
}

kotlin {
  jvm()

  js {
    browser()
    binaries.library()
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation("app.cash.zipline:zipline")
      }
    }
    val jvmMain by getting {
      dependencies {
        implementation(Dependencies.okHttp)
      }
    }
  }
}

dependencies {
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, "app.cash.zipline:zipline-kotlin-plugin")
}

val compileZipline by tasks.creating(ZiplineCompileTask::class) {
  dependsOn(":samples:emoji-search:presenters:compileDevelopmentLibraryKotlinJs")
  inputDir = file("$buildDir/compileSync/main/developmentLibrary/kotlin")
  outputDir = file("$buildDir/zipline")
}
