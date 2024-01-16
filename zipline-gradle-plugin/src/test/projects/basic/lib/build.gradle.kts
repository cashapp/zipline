import app.cash.zipline.gradle.ZiplineCompileTask
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

// This task makes the JVM program available to ZiplinePluginTest.
val jvmTestRuntimeClasspath by configurations.getting
val launchGreetService by tasks.creating(JavaExec::class) {
  dependsOn(":lib:compileProductionExecutableKotlinJsZipline")
  classpath = jvmTestRuntimeClasspath
  mainClass.set("app.cash.zipline.tests.LaunchGreetServiceJvmKt")
}

zipline {
  mainFunction.set("app.cash.zipline.tests.launchGreetService")
  version.set("1.2.3")
  metadata.put("build_timestamp", "2023-10-25T12:00:00T")
}

plugins.withType<YarnPlugin> {
  the<YarnRootExtension>().yarnLockAutoReplace = true
}
