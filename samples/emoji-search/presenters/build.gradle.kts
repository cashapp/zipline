import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin

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
        implementation(Dependencies.sqldelightDriverAndroid)
        implementation(project(":zipline-loader"))
      }
    }
  }
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

val compilerConfiguration by configurations.creating {
}

dependencies {
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, project(":zipline-kotlin-plugin"))
  compilerConfiguration(project(":zipline-gradle-plugin"))
}


// We can't use the Zipline Gradle plugin because it shares our parent project.
val compileZipline by tasks.creating(JavaExec::class) {
  dependsOn("compileProductionExecutableKotlinJs")
  classpath = compilerConfiguration
  main = "app.cash.zipline.gradle.ZiplineCompilerKt"
  args = listOf(
    "$buildDir/compileSync/main/productionExecutable/kotlin",
    "$buildDir/zipline",
  )
}

val jsBrowserProductionRun by tasks.getting {
  dependsOn(compileZipline)
}
