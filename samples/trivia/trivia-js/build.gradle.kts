import app.cash.zipline.gradle.ZiplineCompileTask
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
  id("io.github.tret9.zipline")
}

kotlin {
  js {
    browser()
    binaries.executable()
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation("io.github.tret9:zipline")
        implementation(project(":trivia:trivia-shared"))
      }
    }
  }
}

zipline {
  mainFunction.set("app.cash.zipline.samples.trivia.launchZipline")
}

plugins.withType<YarnPlugin> {
  the<YarnRootExtension>().yarnLockAutoReplace = true
}
