import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

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

  if (project.property("enableK2").toString().toBooleanStrict()) {
    targets.configureEach {
      compilations.configureEach {
        compilerOptions.options.languageVersion.set(
          org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0,
        )
      }
    }
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

val optimizeMode = project.property("optimizeMode") as String

// This task makes the JVM program available to ZiplinePluginTest.
val jvmTestRuntimeClasspath by configurations.getting
val launchCrashService by tasks.creating(JavaExec::class) {
  when (optimizeMode) {
    "development" -> dependsOn(":lib:compileDevelopmentExecutableKotlinJsZipline")
    else -> dependsOn(":lib:jsBrowserProductionWebpackZipline")
  }
  classpath = jvmTestRuntimeClasspath
  mainClass.set("app.cash.zipline.tests.LaunchCrashServiceJvmKt")
  args(optimizeMode)
}

zipline {
  mainFunction.set("app.cash.zipline.tests.launchCrashService")

  when (optimizeMode) {
    "optimizeForSmallArtifactSize" -> optimizeForSmallArtifactSize()
    "optimizeForDeveloperExperience" -> optimizeForDeveloperExperience()
    "development" -> Unit
  }
}

plugins.withType<YarnPlugin> {
  the<YarnRootExtension>().yarnLockAutoReplace = true
}
