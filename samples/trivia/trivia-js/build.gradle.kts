import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
}

kotlin {
  js {
    browser()
    binaries.executable()
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.zipline)
        implementation(project(":samples:trivia:trivia-shared"))
      }
    }
  }
}

val compilerConfiguration by configurations.creating {
}

dependencies {
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.ziplineKotlinPlugin)
  compilerConfiguration(projects.ziplineGradlePlugin)
}

// We can't use the Zipline Gradle plugin because it shares our parent project.
val compileZipline by tasks.creating(JavaExec::class) {
  dependsOn("compileProductionExecutableKotlinJs")
  classpath = compilerConfiguration
  main = "app.cash.zipline.gradle.ZiplineCompilerKt"
  args = listOf(
    "$buildDir/compileSync/main/productionExecutable/kotlin",
    "$buildDir/zipline",
    "app.cash.zipline.samples.trivia.launchZipline()"
  )
}

val jsBrowserProductionRun by tasks.getting {
  dependsOn(compileZipline)
}
