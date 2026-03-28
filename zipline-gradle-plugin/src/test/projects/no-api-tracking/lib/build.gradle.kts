import app.cash.zipline.gradle.ZiplineCompileTask
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

plugins {
  kotlin("multiplatform")
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
        implementation("io.github.tret9:zipline:${project.property("ziplineVersion")}")
      }
    }
  }
}

zipline {
  mainFunction.set("app.cash.zipline.tests.launchGreetService")
  apiTracking.set(false)
}

plugins.withType<YarnPlugin> {
  the<YarnRootExtension>().yarnLockAutoReplace = true
}
